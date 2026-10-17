package lv.bkeeper.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured invoice data from uploaded files.
 *
 * Pipeline:
 *   PDF with embedded text  → PDFBox → mistral-nemo → structured fields
 *   Image / scanned PDF     → Tesseract OCR → mistral-nemo → structured fields
 *
 * Date extraction uses two layers:
 *   1. mistral-nemo (LLM) — handles most formats
 *   2. Regex fallback on raw text — catches what the LLM misses
 *   Both feed through normaliseDate() which handles numeric and Latvian month formats.
 */
@Slf4j
@Service
public class InvoiceOcrService {

    private static final String OLLAMA_URL     = "http://localhost:11434/api/generate";
    private static final String TEXT_MODEL     = "mistral-nemo";
    private static final int    MIN_TEXT       = 50;
    private static final String TESSERACT_PATH = "C:\\Program Files\\Tesseract-OCR\\tesseract.exe";

    // ── Date formatters ───────────────────────────────────────────────────────

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,             // 2026-08-20
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),    // 20.08.2026
            DateTimeFormatter.ofPattern("d.MM.yyyy"),     // 6.08.2026
            DateTimeFormatter.ofPattern("d.M.yyyy"),      // 6.8.2026
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),    // 20/08/2026
            DateTimeFormatter.ofPattern("d/M/yyyy"),      // 6/8/2026
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),    // 2026.08.20
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),    // 20-08-2026
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    );

    // Latvian month names (nominative and genitive forms both appear on invoices)
    private static final Map<String, Integer> LV_MONTHS = new LinkedHashMap<>();
    static {
        LV_MONTHS.put("janvāris",  1); LV_MONTHS.put("janvārī",   1); LV_MONTHS.put("janvāra",   1);
        LV_MONTHS.put("februāris", 2); LV_MONTHS.put("februārī",  2); LV_MONTHS.put("februāra",  2);
        LV_MONTHS.put("marts",     3); LV_MONTHS.put("martā",     3); LV_MONTHS.put("marta",     3);
        LV_MONTHS.put("aprīlis",   4); LV_MONTHS.put("aprīlī",    4); LV_MONTHS.put("aprīļa",    4);
        LV_MONTHS.put("maijs",     5); LV_MONTHS.put("maijā",     5); LV_MONTHS.put("maija",     5);
        LV_MONTHS.put("jūnijs",    6); LV_MONTHS.put("jūnijā",    6); LV_MONTHS.put("jūnija",    6);
        LV_MONTHS.put("jūlijs",    7); LV_MONTHS.put("jūlijā",    7); LV_MONTHS.put("jūlija",    7);
        LV_MONTHS.put("augusts",   8); LV_MONTHS.put("augustā",   8); LV_MONTHS.put("augusta",   8);
        LV_MONTHS.put("septembris",9); LV_MONTHS.put("septembrī", 9); LV_MONTHS.put("septembra", 9);
        LV_MONTHS.put("oktobris", 10); LV_MONTHS.put("oktobrī",  10); LV_MONTHS.put("oktobra",  10);
        LV_MONTHS.put("novembris",11); LV_MONTHS.put("novembrī", 11); LV_MONTHS.put("novembra", 11);
        LV_MONTHS.put("decembris",12); LV_MONTHS.put("decembrī", 12); LV_MONTHS.put("decembra", 12);
    }

    // Regex for numeric date patterns (dd.mm.yyyy, yyyy-mm-dd, etc.)
    private static final List<Pattern> DATE_REGEXES = List.of(
            Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b"),      // yyyy-mm-dd
            Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\b"),  // d.m.yyyy
            Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b"),      // d/m/yyyy
            Pattern.compile("\\b(\\d{1,2})-(\\d{1,2})-(\\d{4})\\b")       // d-m-yyyy
    );

    // Latvian natural-language date: "2026. gada 6. augustā" or "6. augustā, 2026"
    private static final Pattern LV_DATE_PATTERN = Pattern.compile(
            "(\\d{4})[.]\\s*gada\\s+(\\d{1,2})[.]\\s*(\\w+)",   // 2026. gada 6. augustā
            Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern LV_DATE_PATTERN2 = Pattern.compile(
            "(\\d{1,2})[.]\\s*(\\w+)\\s+(\\d{4})",              // 6. augustā 2026
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // Keywords before dates on LV/EU invoices
    private static final List<String> DATE_LABELS = List.of(
            "rēķina datums", "rekina datums", "datums", "invoice date",
            "date", "datum", "ausstellungsdatum", "data wystawienia",
            "izrakstīšanas datums", "issued", "issued on"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient   httpClient   = HttpClient.newHttpClient();

    // ── Public API ────────────────────────────────────────────────────────────

    /** Extract incoming-invoice fields (supplier, purchase amounts). */
    public Map<String, String> extractFromFile(MultipartFile file) {
        return extractFromFile(file, false);
    }

    /**
     * Extract invoice fields from an uploaded file.
     * @param outgoing true = outgoing invoice schema (client/recipient fields),
     *                 false = incoming invoice schema (supplier fields)
     */
    public Map<String, String> extractFromFile(MultipartFile file, boolean outgoing) {
        try {
            return extractFromBytes(file.getBytes(), file.getOriginalFilename(), outgoing);
        } catch (Exception e) {
            log.error("Invoice OCR failed reading upload", e);
            return Map.of();
        }
    }

    public Map<String, String> extractFromPdf(MultipartFile file) {
        return extractFromFile(file);
    }

    /**
     * Extract invoice fields from raw bytes (e.g. a file downloaded from Google Drive).
     */
    public Map<String, String> extractFromBytes(byte[] data, String originalFilename, boolean outgoing) {
        try {
            String filename = Optional.ofNullable(originalFilename)
                    .map(String::toLowerCase).orElse("");

            String text = null;

            if (filename.endsWith(".pdf")) {
                text = extractPdfText(data);
                if (text == null) {
                    log.info("PDF has no embedded text — trying Tesseract");
                    text = ocrWithTesseract(data, originalFilename);
                }
            } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")
                    || filename.endsWith(".png") || filename.endsWith(".webp")) {
                log.info("Image file '{}' — running Tesseract OCR", filename);
                text = ocrWithTesseract(data, originalFilename);
            }

            if (text == null || text.isBlank()) {
                log.info("No extractable text from '{}' — user will fill the form manually", filename);
                return Map.of();
            }

            // Deterministic template reader first (outgoing invoices): when the
            // fixed layout is recognised, every field is read directly and the
            // LLM step is skipped entirely (instant, repeatable, no AI needed).
            Map<String, String> extracted = new LinkedHashMap<>();
            if (outgoing) {
                extracted.putAll(TemplateInvoiceParser.parseOutgoing(text));
                if (TemplateInvoiceParser.hasCoreFields(extracted)) {
                    log.info("Template parser: all core fields found ({} fields) — LLM skipped", extracted.size());
                } else {
                    log.info("Template parser: only {} fields, not complete — asking {}", extracted.size(), TEXT_MODEL);
                    callTextModel(text, true).forEach(extracted::putIfAbsent);
                }
            } else {
                extracted.putAll(callTextModel(text, false));
            }

            Map<String, String> result = new HashMap<>(postProcess(extracted, outgoing));

            // Regex fallback for the main date if the LLM missed it
            String dateKey = outgoing ? "issueDate" : "invoiceDate";
            String date = result.get(dateKey);
            if (date == null || date.isBlank()) {
                String found = findDateInText(text);
                if (found != null) {
                    log.info("LLM missed date — fallback found: '{}'", found);
                    result.put(dateKey, found);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("Invoice OCR failed", e);
            return Map.of();
        }
    }

    // ── PDF text extraction ───────────────────────────────────────────────────

    private String extractPdfText(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            String text = new PDFTextStripper().getText(doc);
            int nonWs = text == null ? 0 : text.replaceAll("\\s+", "").length();
            log.info("PDFBox: {} chars ({} non-whitespace)", text == null ? 0 : text.length(), nonWs);
            if (nonWs >= MIN_TEXT) return text;
            log.info("PDFBox insufficient — scanned PDF");
        } catch (Exception e) {
            log.warn("PDFBox failed: {}", e.getMessage());
        }
        return null;
    }

    // ── Tesseract OCR ─────────────────────────────────────────────────────────

    private String ocrWithTesseract(byte[] data, String originalFilename) {
        File tmp = null;
        try {
            String origName = Optional.ofNullable(originalFilename).orElse("file.jpg");
            String ext = origName.contains(".") ? origName.substring(origName.lastIndexOf('.')) : ".jpg";
            tmp = File.createTempFile("bkeeper_ocr_", ext);
            Files.write(tmp.toPath(), data);

            ProcessBuilder pb = new ProcessBuilder(
                    TESSERACT_PATH, tmp.getAbsolutePath(), "stdout",
                    "-l", "lav+eng",
                    "--psm", "3"   // fully automatic page segmentation
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }
            proc.waitFor();

            String text = sb.toString().trim();
            log.info("Tesseract: {} chars extracted", text.length());
            if (text.length() > 100) log.info("Tesseract sample: {}", text.substring(0, Math.min(200, text.length())));
            return text.isBlank() ? null : text;

        } catch (Exception e) {
            log.error("Tesseract OCR failed: {}", e.getMessage());
            return null;
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    // ── LLM call ─────────────────────────────────────────────────────────────

    private Map<String, String> callTextModel(String text, boolean outgoing) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", TEXT_MODEL);
            body.put("prompt", buildPrompt(text, outgoing));
            body.put("stream", false);
            body.put("format", "json");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() != 200) {
                log.warn("Ollama HTTP {}", resp.statusCode());
                return Map.of();
            }

            String response = objectMapper.readTree(resp.body()).path("response").asString();
            log.info("Ollama response: {}", response);
            return parseJson(response);

        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private String buildPrompt(String text, boolean outgoing) {
        String schema = outgoing ? OUTGOING_SCHEMA : INCOMING_SCHEMA;

        String intro = outgoing
                ? "You are an invoice data extraction assistant. The user's company ISSUED this invoice "
                  + "to a client (outgoing invoice). Extract data and return ONLY a JSON object. "
                  + "No markdown, no explanation — pure JSON only.\n\n"
                  + "RULES:\n"
                  + "- invoiceNumber: labels like 'Rēķins Nr.', 'Rēķina numurs', 'Nr.', "
                  + "  'Invoice number'. Output only the number itself (e.g. 'VS26/01', not the label).\n"
                  + "- issueDate: labels like 'Rēķina datums', 'Datums', 'Izrakstīšanas datums', "
                  + "  'Invoice date', 'Date', 'Datum' — or the first date on the page, often "
                  + "  right below the invoice number. Output MUST be YYYY-MM-DD.\n"
                  + "  Examples: '20.08.2026'→'2026-08-20', '2026. gada 6. augustā'→'2026-08-06'.\n"
                  + "- dueDate: labels like 'Apmaksas termiņš', 'Samaksas termiņš', 'Due date' "
                  + "  (often prefixed with 'līdz'). YYYY-MM-DD, empty if not found. "
                  + "  The due date is NEVER the issue date.\n"
                  + "- clientName: the RECIPIENT of the invoice — labels 'Klients', 'Pircējs', "
                  + "  'Saņēmējs', 'Pasūtītājs', 'Bill to', 'Invoice to'. "
                  + "  'Izpildītājs' is the seller/issuer — NEVER the client.\n"
                  + "- clientRegNumber / clientVatNumber: registration / VAT number from the "
                  + "  client's section (after the client name / 'Pasūtītājs'), "
                  + "  NOT the issuer's 'Reģistrācijas Nr.'.\n"
                  + "- amountExclVat: pre-VAT total ('Cena'/'Summa'); vatRate: from 'PVN 21%' → '21'; "
                  + "  vatAmount: the 'PVN' amount; amountInclVat: 'Kopā' (with VAT).\n"
                  + "- description: the service/product description from the item table "
                  + "  ('Nosaukums' column).\n"
                  + "- Numbers: dot as decimal separator (e.g. 5.21). Strip currency symbols (€, EUR).\n"
                  + "- Empty string for missing fields\n\n"
                : "You are an invoice data extraction assistant. Extract data and return ONLY a JSON object. "
                  + "No markdown, no explanation — pure JSON only.\n\n"
                  + "RULES:\n"
                  + "- invoiceDate: find labels like 'Datums', 'Rēķina datums', 'Invoice date', 'Date', 'Datum', 'gada'. "
                  + "  Output MUST be YYYY-MM-DD. Examples: '20.08.2026'→'2026-08-20', "
                  + "  '2026. gada 6. augustā'→'2026-08-06'. NEVER leave empty if any date appears.\n"
                  + "- Numbers: dot as decimal separator (e.g. 5.21). Strip currency symbols (€, EUR).\n"
                  + "- Empty string for missing fields\n\n";

        return intro + schema + "\n\nInvoice text:\n---\n"
                + text.substring(0, Math.min(text.length(), 3000))
                + "\n---";
    }

    private static final String INCOMING_SCHEMA =
            "{\n"
            + "  \"invoiceNumber\":     \"\",\n"
            + "  \"invoiceDate\":       \"YYYY-MM-DD\",\n"
            + "  \"supplierName\":      \"\",\n"
            + "  \"supplierVatNumber\": \"\",\n"
            + "  \"supplierCountry\":   \"\",\n"
            + "  \"amountExclVat\":     \"\",\n"
            + "  \"vatRate\":           \"\",\n"
            + "  \"vatAmount\":         \"\",\n"
            + "  \"amountInclVat\":     \"\",\n"
            + "  \"originalCurrency\":  \"\",\n"
            + "  \"originalAmount\":    \"\",\n"
            + "  \"description\":       \"\"\n"
            + "}";

    private static final String OUTGOING_SCHEMA =
            "{\n"
            + "  \"invoiceNumber\":     \"\",\n"
            + "  \"issueDate\":         \"YYYY-MM-DD\",\n"
            + "  \"dueDate\":           \"\",\n"
            + "  \"clientName\":        \"\",\n"
            + "  \"clientRegNumber\":   \"\",\n"
            + "  \"clientVatNumber\":   \"\",\n"
            + "  \"amountExclVat\":     \"\",\n"
            + "  \"vatRate\":           \"\",\n"
            + "  \"vatAmount\":         \"\",\n"
            + "  \"amountInclVat\":     \"\",\n"
            + "  \"description\":       \"\"\n"
            + "}";

    // ── Regex + Latvian date fallback ─────────────────────────────────────────

    private String findDateInText(String text) {
        String lower = text.toLowerCase();

        // 1. Try Latvian natural-language format: "2026. gada 6. augustā"
        Matcher lv1 = LV_DATE_PATTERN.matcher(text);
        if (lv1.find()) {
            String d = parseLvDate(lv1.group(2), lv1.group(3), lv1.group(1));
            if (d != null) { log.info("Latvian date (fmt1): '{}'", d); return d; }
        }
        Matcher lv2 = LV_DATE_PATTERN2.matcher(text);
        if (lv2.find()) {
            String d = parseLvDate(lv2.group(1), lv2.group(2), lv2.group(3));
            if (d != null) { log.info("Latvian date (fmt2): '{}'", d); return d; }
        }

        // 2. Near a known label
        for (String label : DATE_LABELS) {
            int idx = lower.indexOf(label);
            if (idx < 0) continue;
            String window = text.substring(idx, Math.min(idx + 80, text.length()));
            String found = extractFirstNumericDate(window);
            if (found != null) { log.info("Date near label '{}': '{}'", label, found); return found; }
        }

        // 3. First numeric date anywhere in first 2000 chars
        String found = extractFirstNumericDate(text.substring(0, Math.min(text.length(), 2000)));
        if (found != null) log.info("Date via general scan: '{}'", found);
        return found;
    }

    /** Parse day + Latvian month word + year into YYYY-MM-DD */
    private String parseLvDate(String dayStr, String monthWord, String yearStr) {
        try {
            String mw = monthWord.toLowerCase().trim();
            Integer month = LV_MONTHS.get(mw);
            if (month == null) return null;
            int day  = Integer.parseInt(dayStr.trim());
            int year = Integer.parseInt(yearStr.trim());
            return LocalDate.of(year, month, day).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractFirstNumericDate(String window) {
        for (Pattern p : DATE_REGEXES) {
            Matcher m = p.matcher(window);
            if (m.find()) {
                String raw  = m.group(0);
                String norm = normaliseDate(raw);
                if (norm.matches("20\\d{2}-\\d{2}-\\d{2}")) return norm;
            }
        }
        return null;
    }

    // ── Post-processing ───────────────────────────────────────────────────────

    private Map<String, String> postProcess(Map<String, String> raw, boolean outgoing) {
        Map<String, String> result = new HashMap<>(raw);

        // Normalise date fields
        List<String> dateKeys = outgoing
                ? List.of("issueDate", "dueDate")
                : List.of("invoiceDate");
        for (String key : dateKeys) {
            String date = result.get(key);
            if (date != null && !date.isBlank()) {
                String norm = normaliseDate(date);
                result.put(key, norm);
                if (!norm.equals(date)) log.info("Date [{}] normalised: '{}' → '{}'", key, date, norm);
            }
        }

        for (String key : List.of("amountExclVat", "vatRate", "vatAmount", "amountInclVat", "originalAmount")) {
            String val = result.get(key);
            if (val != null && !val.isBlank()) {
                String norm = normaliseNumber(val);
                result.put(key, norm);
                if (!norm.equals(val)) log.info("Number [{}]: '{}' → '{}'", key, val, norm);
            }
        }

        log.info("Final extracted fields: {}", result);
        return result;
    }

    private String normaliseDate(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        // Try standard formatters
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(s, fmt).toString(); }
            catch (DateTimeParseException ignored) {}
        }

        // Try Latvian month name inline (e.g. "2026-08-06" already handled above,
        // but also "6. augustā 2026" that might come back from LLM)
        Matcher lv1 = LV_DATE_PATTERN.matcher(s);
        if (lv1.find()) {
            String d = parseLvDate(lv1.group(2), lv1.group(3), lv1.group(1));
            if (d != null) return d;
        }
        Matcher lv2 = LV_DATE_PATTERN2.matcher(s);
        if (lv2.find()) {
            String d = parseLvDate(lv2.group(1), lv2.group(2), lv2.group(3));
            if (d != null) return d;
        }

        return s; // return as-is; mapExtractedToInvoice will try LocalDate.parse
    }

    private String normaliseNumber(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String s = raw.replaceAll("[^0-9.,]", "").trim();
        int lastDot = s.lastIndexOf('.'), lastComma = s.lastIndexOf(',');
        String n;
        if (lastDot >= 0 && lastComma >= 0) {
            n = lastDot > lastComma ? s.replace(",", "") : s.replace(".", "").replace(",", ".");
        } else if (lastComma >= 0) {
            n = s.replace(",", ".");
        } else {
            n = s;
        }
        try { new BigDecimal(n); return n; }
        catch (NumberFormatException e) { return raw; }
    }

    // ── JSON parsing ─────────────────────────────────────────────────────────

    private Map<String, String> parseJson(String json) {
        try {
            String s = json.trim();
            if (s.startsWith("```")) {
                s = s.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```\\s*$", "").trim();
            }
            int start = s.indexOf('{'), end = s.lastIndexOf('}');
            if (start >= 0 && end > start) s = s.substring(start, end + 1);
            JsonNode node = objectMapper.readTree(s);
            Map<String, String> result = new HashMap<>();
            node.properties().forEach(e -> result.put(e.getKey(), e.getValue().asString("")));
            return result;
        } catch (Exception e) {
            log.error("JSON parse failed: {}", e.getMessage());
            return Map.of();
        }
    }
}
