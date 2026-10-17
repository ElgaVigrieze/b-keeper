package lv.bkeeper.service;

import lv.bkeeper.domain.Customer;
import lv.bkeeper.domain.InvoiceOut;
import lv.bkeeper.repository.AppSettingRepository;
import lv.bkeeper.repository.CustomerRepository;
import lv.bkeeper.repository.InvoiceOutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Imports outgoing invoices from a Google Drive folder.
 *
 * Files are downloaded, stored in the local document archive, run through
 * the deterministic template reader / AI extraction and saved as ISSUED
 * invoices; clients that are not in the customer database yet are created
 * from the invoice's client block.
 *
 * Also supports:
 *  - refreshing drafts: a file that was imported before is re-imported when
 *    its invoice is still a DRAFT (reviewed/finalised invoices are never
 *    overwritten);
 *  - automatic checks: {@link #runCheckNow()} imports every new file and
 *    stores when the last check ran (shown on the import page).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriveImportService {

    /** app_setting key holding the Google Drive folder id. */
    private static final String FOLDER_SETTING = GoogleDriveService.FOLDER_SETTING_KEY;

    /** app_setting keys recording the last automatic/manual folder check. */
    public static final String LAST_CHECK_AT_KEY    = "google_drive_last_check_at";
    public static final String LAST_CHECK_COUNT_KEY = "google_drive_last_check_count";
    public static final String LAST_CHECK_OK_KEY    = "google_drive_last_check_ok";

    private static final DateTimeFormatter CHECK_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final GoogleDriveService      drive;
    private final InvoiceOutRepository    repo;
    private final InvoiceOcrService       ocr;
    private final DocumentStorageService  storage;
    private final JournalService          journalService;
    private final CustomerMatchingService customerMatcher;
    private final CustomerRepository      customerRepo;
    private final AppSettingRepository    settings;

    /**
     * Import outcome.
     * createdIds = all invoices saved (new ones + refreshed drafts).
     */
    public record ImportSummary(List<Long> createdIds, List<String> errors,
                                int skipped, int refreshed) {
    }

    /**
     * Imports the given Drive files as issued invoices; when a client is not
     * in the customer database yet, it is created from the invoice's data.
     * Files whose invoice is still a DRAFT are refreshed (the old draft is
     * replaced with freshly imported data); files that were reviewed and
     * finalised are skipped — their data is never overwritten.
     */
    public synchronized ImportSummary importFiles(List<String> fileIds) {
        List<Long> createdIds = new ArrayList<>();
        List<String> errors   = new ArrayList<>();
        int skipped   = 0;
        int refreshed = 0;

        // A Drive file can have several rows (e.g. imported more than once in
        // the past) — group them so every old copy is cleaned up on refresh.
        Map<String, List<InvoiceOut>> byDriveId = new HashMap<>();
        repo.findAllImported().forEach(i ->
                byDriveId.computeIfAbsent(i.getDriveFileId(), k -> new ArrayList<>()).add(i));

        for (String fileId : fileIds) {
            List<InvoiceOut> priors = byDriveId.getOrDefault(fileId, List.of());
            boolean anyFinalized = priors.stream().anyMatch(p -> !"DRAFT".equals(p.getStatus()));
            if (anyFinalized) {
                skipped++;
                continue;
            }
            GoogleDriveService.DriveFile file = null;
            try {
                file = drive.getFile(fileId);
                InvoiceOut saved = importOne(file);
                if (!priors.isEmpty()) {
                    // Replace the outdated draft(s) with the freshly imported one.
                    for (InvoiceOut prior : priors) {
                        journalService.deleteByInvoiceOutId(prior.getId());
                        repo.deleteById(prior.getId());
                        storage.delete(DocumentStorageService.DIR_OUT, prior.getPdfFilename());
                    }
                    refreshed++;
                    log.info("Refreshed draft from Drive ('{}'): {} old row(s) replaced", file.name(), priors.size());
                }
                createdIds.add(saved.getId());
            } catch (Exception e) {
                String label = file != null ? file.name() : fileId;
                log.error("Drive import failed for '{}'", label, e);
                errors.add(label + ": " + shorten(e.getMessage()));
            }
        }
        return new ImportSummary(createdIds, errors, skipped, refreshed);
    }

    /** Imports every file in the folder that has never been imported before. */
    public ImportSummary importNewFromFolder() throws Exception {
        String folderId = settings.findValue(FOLDER_SETTING).orElse("");
        if (!drive.hasKeyFile() || folderId.isBlank()) {
            throw new IllegalStateException("Google Drive nav konfigurēts");
        }
        List<GoogleDriveService.DriveFile> files = drive.listFiles(folderId);
        Set<String> imported = new HashSet<>(repo.findImportedDriveFileIds());
        List<String> ids = files.stream()
                .filter(f -> !f.isFolder() && f.isImportable() && !imported.contains(f.id()))
                .map(GoogleDriveService.DriveFile::id)
                .toList();
        return importFiles(ids);
    }

    /**
     * Manual ("Pārbaudīt tagad") or scheduled folder check: imports new
     * files and records when the check ran, so the page can show it.
     */
    public ImportSummary runCheckNow() throws Exception {
        try {
            ImportSummary s = importNewFromFolder();
            recordCheck(true, s.createdIds().size());
            return s;
        } catch (Exception e) {
            recordCheck(false, 0);
            throw e;
        }
    }

    /** True when the service account key and the folder are configured. */
    public boolean configured() {
        return drive.hasKeyFile() && !settings.findValue(FOLDER_SETTING).orElse("").isBlank();
    }

    private void recordCheck(boolean ok, int count) {
        try {
            settings.put(LAST_CHECK_AT_KEY, LocalDateTime.now().format(CHECK_TIME));
            settings.put(LAST_CHECK_COUNT_KEY, String.valueOf(count));
            settings.put(LAST_CHECK_OK_KEY, ok ? "1" : "0");
        } catch (Exception e) {
            log.warn("Could not store Drive check info: {}", e.getMessage());
        }
    }

    // ── Single file import ────────────────────────────────────────────────────

    /** Downloads one Drive file and creates an issued invoice from it. */
    private InvoiceOut importOne(GoogleDriveService.DriveFile file) throws Exception {
        if (!file.isImportable()) {
            throw new IllegalStateException("neatbalstīts faila tips (" + file.mimeType() + ")");
        }

        // 1. Download + store locally (kept as attaisnojuma dokuments)
        byte[] data = drive.download(file);
        String storedPath = storage.store(data, file.effectiveFilename(), DocumentStorageService.DIR_OUT);

        // 2. Extraction: deterministic template reader first, AI fallback
        Map<String, String> extracted;
        try {
            extracted = ocr.extractFromBytes(data, file.effectiveFilename(), true);
        } catch (Exception e) {
            log.warn("Extraction failed for '{}': {}", file.name(), e.getMessage());
            extracted = Map.of();
        }

        // 3. Build invoice
        InvoiceOut invoice = buildInvoice(file, extracted, storedPath);

        // 4. Trusted client data: use an existing customer, or create one
        //    from the invoice's client block so it lands in the client list.
        customerMatcher.findMatch(
                        extracted.get("clientName"),
                        extracted.get("clientVatNumber"),
                        extracted.get("clientRegNumber"))
                .or(() -> createCustomer(invoice))
                .ifPresent(c -> applyCustomer(invoice, c));

        recalcVat(invoice);

        LocalDateTime now = LocalDateTime.now();
        invoice.setCreatedAt(now);
        invoice.setUpdatedAt(now);

        InvoiceOut saved = repo.save(invoice);
        journalService.recordInvoiceOut(saved);
        log.info("Imported '{}' from Drive as issued invoice #{}", file.name(), saved.getId());
        return saved;
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private InvoiceOut buildInvoice(GoogleDriveService.DriveFile file, Map<String, String> e, String storedPath) {
        LocalDate issueDate = parseDate(e.get("issueDate"));
        if (issueDate == null) issueDate = file.modifiedDate();
        if (issueDate == null) issueDate = LocalDate.now();

        Double amountExcl = parseDouble(e.get("amountExclVat"));
        Double vatRate    = parseDouble(e.get("vatRate"));
        Double vatAmount  = parseDouble(e.get("vatAmount"));
        Double amountIncl = parseDouble(e.get("amountInclVat"));

        if (vatRate == null) vatRate = 21.0;

        // If only the gross total was extracted, derive the subtotal
        if (amountExcl == null && amountIncl != null && amountIncl > 0 && vatRate > 0) {
            amountExcl = round2(amountIncl / (1 + vatRate / 100.0));
        }
        if (amountExcl == null) amountExcl = 0.0;

        return InvoiceOut.builder()
                .invoiceNumber(firstNonBlank(e.get("invoiceNumber"), nameStem(file.name())))
                .issueDate(issueDate)
                .dueDate(parseDate(e.get("dueDate")))
                .clientName(firstNonBlank(e.get("clientName"), "Nezināms klients"))
                .clientRegNumber(blankToNull(e.get("clientRegNumber")))
                .clientVatNumber(blankToNull(e.get("clientVatNumber")))
                .amountExclVat(amountExcl)
                .vatRate(vatRate)
                .vatAmount(vatAmount != null ? vatAmount : 0.0)
                .amountInclVat(amountIncl != null ? amountIncl : 0.0)
                .pvnLine(41)
                .documentTypeCode("1")
                .description(firstNonBlank(e.get("description"), "Importēts no Google Drive"))
                .periodYear(issueDate.getYear())
                .periodMonth(issueDate.getMonthValue())
                .status("ISSUED")
                .pdfFilename(storedPath)
                .driveFileId(file.id())
                .build();
    }

    /**
     * Creates a customer from the invoice's client block when the matcher
     * found no match, so imported invoices also populate the client list.
     * Invoices without a known client name are skipped.
     */
    private Optional<Customer> createCustomer(InvoiceOut invoice) {
        String name = invoice.getClientName();
        if (name == null || name.isBlank() || "Nezināms klients".equals(name)) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        Customer customer = Customer.builder()
                .name(name)
                .regNumber(invoice.getClientRegNumber())
                .vatNumber(invoice.getClientVatNumber())
                .country("LV")
                .defaultPvnLine(invoice.getPvnLine() != null ? invoice.getPvnLine() : 41)
                .defaultDocType(invoice.getDocumentTypeCode() != null ? invoice.getDocumentTypeCode() : "1")
                .defaultVatRate(invoice.getVatRate() != null ? invoice.getVatRate() : 21.0)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Customer saved = customerRepo.save(customer);
        log.info("Created customer '{}' from imported invoice", name);
        return Optional.of(saved);
    }

    private void applyCustomer(InvoiceOut invoice, Customer c) {
        invoice.setClientName(c.getName());
        invoice.setClientRegNumber(c.getRegNumber());
        invoice.setClientVatNumber(c.getVatNumber());
        if (c.getDefaultPvnLine() != null) invoice.setPvnLine(c.getDefaultPvnLine());
        if (c.getDefaultDocType() != null) invoice.setDocumentTypeCode(c.getDefaultDocType());
        if (c.getDefaultVatRate() != null) invoice.setVatRate(c.getDefaultVatRate());
        log.info("Invoice client matched to customer '{}'", c.getName());
    }

    private void recalcVat(InvoiceOut inv) {
        if (inv.getAmountExclVat() == null || inv.getVatRate() == null) return;
        double vat = round2(inv.getAmountExclVat() * inv.getVatRate() / 100.0);
        if (inv.getVatAmount() == null || inv.getVatAmount() == 0.0) inv.setVatAmount(vat);
        if (inv.getAmountInclVat() == null || inv.getAmountInclVat() == 0.0) {
            inv.setAmountInclVat(round2(inv.getAmountExclVat() + inv.getVatAmount()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); }
        catch (Exception e) { return null; }
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.replace(",", ".").replaceAll("[^0-9.]", ""))
                    .setScale(2, RoundingMode.HALF_UP).doubleValue();
        } catch (Exception e) { return null; }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** "invoice_2026_05.pdf" → "invoice_2026_05" */
    private String nameStem(String filename) {
        if (filename == null || filename.isBlank()) return "Rēķins";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String shorten(String message) {
        if (message == null) return "nezināma kļūda";
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }
}
