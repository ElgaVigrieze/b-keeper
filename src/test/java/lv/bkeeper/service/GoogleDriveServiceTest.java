package lv.bkeeper.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests GoogleDriveService against a mock Google OAuth + Drive API.
 * Verifies:
 *  - the RS256 JWT is signed with the service account private key
 *  - the access token is fetched once and cached
 *  - folder listing parses files (PDF, Google Doc, Google Sheet, folder)
 *  - downloads use the Authorization header (plain files, Google Docs/Sheets exported as PDF)
 */
class GoogleDriveServiceTest {

    private static final String TEST_PDF = "%PDF-1.4 fake invoice content";

    private static KeyPair keyPair;
    private static HttpServer server;
    private static String baseUrl;

    private static final AtomicInteger tokenCalls = new AtomicInteger();
    private static final AtomicReference<String> lastAuthHeader = new AtomicReference<>();

    @TempDir
    static Path tempDir;

    // ── Server setup ─────────────────────────────────────────────────────────

    @BeforeAll
    static void startServer() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // Token endpoint — validates the signed JWT
        server.createContext("/token", (HttpExchange ex) -> {
            tokenCalls.incrementAndGet();
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String assertion = null;
            for (String part : body.split("&")) {
                if (part.startsWith("assertion=")) assertion = part.substring("assertion=".length());
            }
            boolean valid = verifyJwt(assertion)
                    && body.contains("jwt-bearer");
            respond(ex, valid ? 200 : 400,
                    valid ? "{\"access_token\":\"test-token\",\"expires_in\":3600}"
                          : "{\"error\":\"invalid_grant\"}");
        });

        // Drive API endpoints
        server.createContext("/drive/v3/files", (HttpExchange ex) -> {
            lastAuthHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
            String path  = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getQuery() == null ? "" : ex.getRequestURI().getQuery();

            if (path.equals("/drive/v3/files")) {
                respond(ex, 200, """
                        {"files":[
                          {"id":"file-1","name":"Rēķins 1.pdf","mimeType":"application/pdf",
                           "modifiedTime":"2026-05-12T10:30:00.000Z","size":"12345"},
                          {"id":"doc-1","name":"Izraksts 5","mimeType":"application/vnd.google-apps.document",
                           "modifiedTime":"2026-06-01T08:00:00.000Z"},
                          {"id":"sheet-1","name":"Rēķins VS26/01","mimeType":"application/vnd.google-apps.spreadsheet",
                           "modifiedTime":"2026-07-15T09:00:00.000Z"},
                          {"id":"folder-1","name":"Apakšmape","mimeType":"application/vnd.google-apps.folder"}
                        ]}""");
            } else if (path.equals("/drive/v3/files/file-1") && query.contains("alt=media")) {
                respondBytes(ex, 200, TEST_PDF.getBytes(StandardCharsets.UTF_8));
            } else if (path.equals("/drive/v3/files/file-1")) {
                respond(ex, 200, """
                        {"id":"file-1","name":"Rēķins 1.pdf","mimeType":"application/pdf",
                         "modifiedTime":"2026-05-12T10:30:00.000Z","size":"12345"}""");
            } else if (path.equals("/drive/v3/files/doc-1/export")) {
                respondBytes(ex, 200, TEST_PDF.getBytes(StandardCharsets.UTF_8));
            } else if (path.equals("/drive/v3/files/sheet-1/export")) {
                respondBytes(ex, 200, TEST_PDF.getBytes(StandardCharsets.UTF_8));
            } else {
                respond(ex, 404, "{\"error\":\"not found\"}");
            }
        });

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        tokenCalls.set(0);
        lastAuthHeader.set(null);
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static GoogleDriveService service() throws IOException {
        Path keyFile = tempDir.resolve("service-account.json");
        Files.writeString(keyFile, serviceAccountJson(), StandardCharsets.UTF_8);
        return new GoogleDriveService(baseUrl + "/drive/v3", baseUrl + "/token", keyFile.toString());
    }

    private static String serviceAccountJson() {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return "{"
                + "\"type\":\"service_account\","
                + "\"client_email\":\"bkeeper@test-project.iam.gserviceaccount.com\","
                + "\"private_key\":\"" + pem.replace("\n", "\\n") + "\","
                + "\"token_uri\":\"" + baseUrl + "/token\""
                + "}";
    }

    private static boolean verifyJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) return false;

            Base64.Decoder b64 = Base64.getUrlDecoder();
            String header  = new String(b64.decode(parts[0]), StandardCharsets.UTF_8);
            String payload = new String(b64.decode(parts[1]), StandardCharsets.UTF_8);

            if (!header.contains("RS256")) return false;
            if (!payload.contains("bkeeper@test-project.iam.gserviceaccount.com")) return false;
            if (!payload.contains("drive.readonly")) return false;

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(keyPair.getPublic());
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            return sig.verify(b64.decode(parts[2]));
        } catch (Exception e) {
            return false;
        }
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        respondBytes(ex, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respondBytes(HttpExchange ex, int status, byte[] body) throws IOException {
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void extractFolderIdHandlesUrlsAndRawIds() {
        assertEquals("1AbCdEfGhIjKlMnOpQrStUvWxYz",
                GoogleDriveService.extractFolderId("https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQrStUvWxYz?usp=sharing"));
        assertEquals("1AbCdEfGhIjKlMnOpQrStUvWxYz",
                GoogleDriveService.extractFolderId("https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz"));
        assertEquals("1AbCdEfGhIjKlMnOpQrStUvWxYz",
                GoogleDriveService.extractFolderId("1AbCdEfGhIjKlMnOpQrStUvWxYz"));
        assertEquals("", GoogleDriveService.extractFolderId("https://example.com/not-a-drive-link/abc"));
        assertEquals("", GoogleDriveService.extractFolderId("   "));
        assertEquals("", GoogleDriveService.extractFolderId(null));
    }

    @Test
    void listsFilesWithMetadata() throws Exception {
        GoogleDriveService drive = service();
        List<GoogleDriveService.DriveFile> files = drive.listFiles("folder-123");

        assertEquals(4, files.size());

        GoogleDriveService.DriveFile pdf = files.get(0);
        assertEquals("file-1", pdf.id());
        assertEquals("Rēķins 1.pdf", pdf.name());
        assertEquals("PDF", pdf.typeLabel());
        assertEquals(12345L, pdf.size());
        assertEquals("12 KB", pdf.sizeLabel());
        assertEquals(java.time.LocalDate.of(2026, 5, 12), pdf.modifiedDate());
        assertTrue(pdf.isImportable());
        assertFalse(pdf.isFolder());

        GoogleDriveService.DriveFile doc = files.get(1);
        assertTrue(doc.isGoogleDoc());
        assertTrue(doc.isImportable());
        assertEquals("Izraksts 5.pdf", doc.effectiveFilename());

        GoogleDriveService.DriveFile sheet = files.get(2);
        assertTrue(sheet.isGoogleSheet());
        assertTrue(sheet.isImportable());
        assertEquals("Google Sheet", sheet.typeLabel());
        assertEquals("Rēķins VS26/01.pdf", sheet.effectiveFilename());

        GoogleDriveService.DriveFile folder = files.get(3);
        assertTrue(folder.isFolder());
        assertFalse(folder.isImportable());

        // Authorization header was sent to the Drive API
        assertEquals("Bearer test-token", lastAuthHeader.get());
    }

    @Test
    void downloadsPlainFilesAndExportsGoogleNativeFiles() throws Exception {
        GoogleDriveService drive = service();
        List<GoogleDriveService.DriveFile> files = drive.listFiles("folder-123");

        byte[] pdfBytes = drive.download(files.get(0));
        assertEquals(TEST_PDF, new String(pdfBytes, StandardCharsets.UTF_8));

        byte[] exportedDocBytes = drive.download(files.get(1));
        assertEquals(TEST_PDF, new String(exportedDocBytes, StandardCharsets.UTF_8));

        byte[] exportedSheetBytes = drive.download(files.get(2));
        assertEquals(TEST_PDF, new String(exportedSheetBytes, StandardCharsets.UTF_8));
    }

    @Test
    void cachesAccessTokenAcrossCalls() throws Exception {
        tokenCalls.set(0);
        GoogleDriveService drive = service();

        drive.listFiles("folder-123");
        drive.getFile("file-1");
        drive.listFiles("folder-123");

        assertEquals(1, tokenCalls.get(), "access token should be fetched once and reused");
    }

    @Test
    void rejectsInvalidServiceAccountJson() {
        GoogleDriveService drive = new GoogleDriveService(baseUrl + "/drive/v3", baseUrl + "/token",
                tempDir.resolve("bad-key.json").toString());

        assertThrows(IOException.class, () -> drive.saveServiceAccountKey("not json at all"));
        assertThrows(IOException.class, () -> drive.saveServiceAccountKey("{\"type\":\"service_account\"}"));
    }

    @Test
    void savesAndReadsBackServiceAccountKey() throws Exception {
        Path keyFile = tempDir.resolve("saved-key.json");
        GoogleDriveService drive = new GoogleDriveService(baseUrl + "/drive/v3", baseUrl + "/token",
                keyFile.toString());

        assertFalse(drive.hasKeyFile());
        drive.saveServiceAccountKey(serviceAccountJson());
        assertTrue(drive.hasKeyFile());
        assertEquals("bkeeper@test-project.iam.gserviceaccount.com", drive.serviceAccountEmail().orElse(""));

        drive.removeServiceAccountKey();
        assertFalse(drive.hasKeyFile());
    }
}
