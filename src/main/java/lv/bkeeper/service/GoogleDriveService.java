package lv.bkeeper.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Drive client for importing invoice files.
 *
 * Authentication uses a Google Cloud service account key (JSON file).
 * The share target folder in Drive is shared with the service account email.
 *
 * The OAuth2 flow is implemented directly against Google's REST API
 * (RS256-signed JWT → access token), so no Google SDK dependencies are needed.
 *
 * Credentials file location: ~/bkeeper/google/service-account.json
 */
@Slf4j
@Service
public class GoogleDriveService {

    private static final String DEFAULT_DRIVE_API = "https://www.googleapis.com/drive/v3";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String SCOPE          = "https://www.googleapis.com/auth/drive.readonly";
    private static final String JWT_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    /** app_setting key holding the Google Drive folder id used for invoice import. */
    public static final String FOLDER_SETTING_KEY = "google_drive_folder_id";

    /** File types that can be imported as invoices. */
    private static final List<String> IMPORTABLE_MIME_TYPES = List.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/vnd.google-apps.document",     // Google Docs — exported to PDF
            "application/vnd.google-apps.spreadsheet"   // Google Sheets — exported to PDF
    );

    private static final Pattern FOLDER_URL_PATTERN = Pattern.compile("/folders/([A-Za-z0-9_-]{5,})");
    private static final Pattern ID_PARAM_PATTERN   = Pattern.compile("[?&]id=([A-Za-z0-9_-]{5,})");
    private static final Pattern RAW_ID_PATTERN     = Pattern.compile("[A-Za-z0-9_-]{5,}");

    private final String driveApiBase;
    private final String tokenUriOverride;   // empty → token_uri from the service account key
    private final Path   keyFile;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient   httpClient   = HttpClient.newHttpClient();

    private volatile String cachedAccessToken;
    private volatile long   tokenExpiryMillis;

    public GoogleDriveService(
            @Value("${bkeeper.google.drive-api-base:" + DEFAULT_DRIVE_API + "}") String driveApiBase,
            @Value("${bkeeper.google.token-uri:}") String tokenUriOverride,
            @Value("${bkeeper.google.key-file:}") String keyFileOverride) {
        this.driveApiBase     = driveApiBase;
        this.tokenUriOverride = tokenUriOverride != null ? tokenUriOverride.trim() : "";
        this.keyFile = (keyFileOverride != null && !keyFileOverride.isBlank())
                ? Paths.get(keyFileOverride.trim())
                : Paths.get(System.getProperty("user.home"), "bkeeper", "google", "service-account.json");
    }

    // ── Configuration / credentials ───────────────────────────────────────────

    /** True when a service account key file exists on disk. */
    public boolean hasKeyFile() {
        return Files.exists(keyFile);
    }

    public Path keyFilePath() {
        return keyFile;
    }

    /**
     * Validates and stores a pasted Google service account JSON key.
     * The key is written to ~/bkeeper/google/service-account.json.
     */
    public void saveServiceAccountKey(String json) throws IOException {
        if (json == null || json.isBlank()) {
            throw new IOException("Tukšs JSON saturs.");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IOException("Nav derīgs JSON fails: " + e.getMessage());
        }
        if (node.path("client_email").asString("").isBlank()
                || node.path("private_key").asString("").isBlank()) {
            throw new IOException("JSON failā trūkst 'client_email' vai 'private_key' — "
                    + "pārliecinieties, ka augšupielādējāt servisa konta atslēgu.");
        }

        Files.createDirectories(keyFile.getParent());
        Files.writeString(keyFile, json, StandardCharsets.UTF_8);
        clearCachedToken();
        log.info("Saved Google service account key for {}", serviceAccountEmail().orElse("?"));
    }

    /** Removes the stored service account key. */
    public void removeServiceAccountKey() throws IOException {
        Files.deleteIfExists(keyFile);
        clearCachedToken();
        log.info("Removed Google service account key");
    }

    /** The service account email (share the Drive folder with this address). */
    public Optional<String> serviceAccountEmail() {
        try {
            JsonNode node = loadKey();
            String email = node.path("client_email").asString("");
            return email.isBlank() ? Optional.empty() : Optional.of(email);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Accepts a Drive folder link, a link with ?id=..., or a raw folder id.
     * Returns the extracted folder id, or "" if the input is not a folder reference.
     */
    public static String extractFolderId(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.isEmpty()) return "";

        Matcher m = FOLDER_URL_PATTERN.matcher(s);
        if (m.find()) return m.group(1);

        m = ID_PARAM_PATTERN.matcher(s);
        if (m.find()) return m.group(1);

        return RAW_ID_PATTERN.matcher(s).matches() ? s : "";
    }

    // ── Drive API ─────────────────────────────────────────────────────────────

    /**
     * Lists invoice files (PDF / images / Google Docs / Google Sheets) directly inside a Drive folder.
     * Sorted by modification time, newest first.
     */
    public List<DriveFile> listFiles(String folderId) throws IOException, InterruptedException {
        List<DriveFile> files = new ArrayList<>();
        String pageToken = null;

        do {
            StringBuilder url = new StringBuilder(driveApiBase)
                    .append("/files?q=")
                    .append(encodeQuery("'" + folderId + "' in parents and trashed = false"))
                    .append("&fields=")
                    .append(encodeQuery("nextPageToken,files(id,name,mimeType,modifiedTime,size)"))
                    .append("&orderBy=")
                    .append(encodeQuery("modifiedTime desc"))
                    .append("&pageSize=1000")
                    .append("&supportsAllDrives=true&includeItemsFromAllDrives=true");
            if (pageToken != null) url.append("&pageToken=").append(pageToken);

            JsonNode body = getJson(url.toString());
            for (JsonNode f : body.path("files")) {
                files.add(toDriveFile(f));
            }
            String next = body.path("nextPageToken").asString("");
            pageToken = next.isBlank() ? null : next;
        } while (pageToken != null);

        log.info("Google Drive folder {}: {} files listed", folderId, files.size());
        return files;
    }

    /** Fetches metadata for a single file. */
    public DriveFile getFile(String fileId) throws IOException, InterruptedException {
        JsonNode node = getJson(driveApiBase + "/files/" + fileId
                + "?fields=" + encodeQuery("id,name,mimeType,modifiedTime,size")
                + "&supportsAllDrives=true");
        return toDriveFile(node);
    }

    /** Downloads file content (Google Docs and Google Sheets are exported to PDF). */
    public byte[] download(DriveFile file) throws IOException, InterruptedException {
        String url = file.isGoogleNative()
                ? driveApiBase + "/files/" + file.id() + "/export?mimeType=application/pdf"
                : driveApiBase + "/files/" + file.id() + "?alt=media&supportsAllDrives=true";

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken())
                .GET()
                .build();

        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            String body = new String(resp.body(), StandardCharsets.UTF_8);
            throw new IOException("Lejupielāde neizdevās (HTTP " + resp.statusCode() + "): "
                    + body.substring(0, Math.min(300, body.length())));
        }
        log.info("Downloaded '{}' from Drive ({} bytes)", file.name(), resp.body().length);
        return resp.body();
    }

    // ── OAuth2 (service account JWT) ──────────────────────────────────────────

    /**
     * Returns a cached access token, refreshing it via a signed JWT when needed.
     */
    private synchronized String accessToken() throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < tokenExpiryMillis - 60_000) {
            return cachedAccessToken;
        }

        JsonNode key = loadKey();
        String email = key.path("client_email").asString("");
        String pem   = key.path("private_key").asString("");
        if (email.isBlank() || pem.isBlank()) {
            throw new IOException("Servisa konta atslēgā trūkst client_email vai private_key.");
        }
        String tokenUri = tokenUriOverride.isBlank()
                ? key.path("token_uri").asString(DEFAULT_TOKEN_URI)
                : tokenUriOverride;

        String jwt = buildJwt(email, pem, tokenUri);
        String form = "grant_type=" + encodeQuery(JWT_GRANT_TYPE) + "&assertion=" + jwt;

        HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() != 200) {
            throw new IOException("Google autorizācija neizdevās (HTTP " + resp.statusCode() + "): "
                    + resp.body().substring(0, Math.min(300, resp.body().length())));
        }

        JsonNode body = objectMapper.readTree(resp.body());
        String token  = body.path("access_token").asString("");
        long expiresIn = body.path("expires_in").asLong(3600);
        if (token.isBlank()) {
            throw new IOException("Google atbildē nav access_token.");
        }

        cachedAccessToken = token;
        tokenExpiryMillis = now + expiresIn * 1000;
        log.debug("Google access token refreshed (expires in {}s)", expiresIn);
        return token;
    }

    private void clearCachedToken() {
        cachedAccessToken = null;
        tokenExpiryMillis = 0;
    }

    /** Builds a signed RS256 JWT for the service account. */
    private String buildJwt(String email, String pem, String audience) throws IOException {
        try {
            long now = System.currentTimeMillis() / 1000;

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");

            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", email);
            claims.put("scope", SCOPE);
            claims.put("aud", audience);
            claims.put("iat", now);
            claims.put("exp", now + 3600);

            String signingInput = b64Url(objectMapper.writeValueAsString(header))
                    + "." + b64Url(objectMapper.writeValueAsString(claims));

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey(pem));
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));

            return signingInput + "." + b64Url(signature.sign());
        } catch (Exception e) {
            throw new IOException("Neizdevās parakstīt Google JWT: " + e.getMessage(), e);
        }
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(body);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String b64Url(String s) {
        return b64Url(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JsonNode loadKey() throws IOException {
        if (!Files.exists(keyFile)) {
            throw new IOException("Nav atrasta Google servisa konta atslēga (" + keyFile + "). "
                    + "Pievienojiet to sadaļā Iestatījumi.");
        }
        try {
            return objectMapper.readTree(Files.readString(keyFile, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IOException("Neizdevās nolasīt servisa konta atslēgu: " + e.getMessage());
        }
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken())
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (resp.statusCode() != 200) {
            throw new IOException("Google Drive API kļūda (HTTP " + resp.statusCode() + "): "
                    + resp.body().substring(0, Math.min(300, resp.body().length())));
        }
        return objectMapper.readTree(resp.body());
    }

    private DriveFile toDriveFile(JsonNode f) {
        return new DriveFile(
                f.path("id").asString(""),
                f.path("name").asString(""),
                f.path("mimeType").asString(""),
                f.path("modifiedTime").asString(""),
                parseSize(f.path("size")));
    }

    /** Drive returns sizes as strings (e.g. "12345"). */
    private static Long parseSize(JsonNode size) {
        if (size == null || size.isMissingNode() || size.isNull()) return null;
        try {
            return Long.parseLong(size.asString(""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String encodeQuery(String value) {
        // URLEncoder uses '+' for spaces — Drive expects %20
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    /** A file in Google Drive. */
    public record DriveFile(String id, String name, String mimeType,
                            String modifiedTime, Long size) {

        public boolean isGoogleDoc() {
            return "application/vnd.google-apps.document".equals(mimeType);
        }

        public boolean isGoogleSheet() {
            return "application/vnd.google-apps.spreadsheet".equals(mimeType);
        }

        /** Google Docs/Sheets are stored in Drive format — downloaded via PDF export. */
        public boolean isGoogleNative() {
            return isGoogleDoc() || isGoogleSheet();
        }

        public boolean isFolder() {
            return "application/vnd.google-apps.folder".equals(mimeType);
        }

        public boolean isImportable() {
            return IMPORTABLE_MIME_TYPES.contains(mimeType);
        }

        /** Filename as it will be stored locally (Google Docs/Sheets become PDF). */
        public String effectiveFilename() {
            return isGoogleNative() ? name + ".pdf" : name;
        }

        /** File type badge shown in the import list. */
        public String typeLabel() {
            if (isGoogleDoc()) return "Google Doc";
            if (isGoogleSheet()) return "Google Sheet";
            if (mimeType == null) return "?";
            return switch (mimeType) {
                case "application/pdf" -> "PDF";
                case "image/jpeg" -> "JPEG";
                case "image/png" -> "PNG";
                case "image/webp" -> "WEBP";
                default -> mimeType.contains("/") ? mimeType.substring(mimeType.indexOf('/') + 1) : mimeType;
            };
        }

        /** Human-readable file size for the import list ("" when unknown). */
        public String sizeLabel() {
            if (size == null || size <= 0) return "";
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return Math.round(size / 1024.0) + " KB";
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }

        /** Modification date (may be null if Drive did not report it). */
        public LocalDate modifiedDate() {
            if (modifiedTime == null || modifiedTime.isBlank()) return null;
            try {
                return OffsetDateTime.parse(modifiedTime).toLocalDate();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
