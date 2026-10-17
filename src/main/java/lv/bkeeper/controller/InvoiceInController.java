package lv.bkeeper.controller;

import lv.bkeeper.domain.InvoiceIn;
import lv.bkeeper.domain.Supplier;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.InvoiceInRepository;
import lv.bkeeper.service.DocumentStorageService;
import lv.bkeeper.service.InvoiceOcrService;
import lv.bkeeper.service.JournalService;
import lv.bkeeper.service.SupplierMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/invoices/in")
@RequiredArgsConstructor
public class InvoiceInController {

    private final InvoiceInRepository    repo;
    private final CompanyRepository      companyRepo;
    private final InvoiceOcrService      ocrService;
    private final JournalService         journalService;
    private final DocumentStorageService storageService;
    private final SupplierMatchingService supplierMatcher;
    private final lv.bkeeper.repository.SupplierRepository supplierRepo;

    // ── List ─────────────────────────────────────────────────────────────────

    @GetMapping
    public String list(@RequestParam(required = false) Integer year,
                       @RequestParam(required = false) Integer month,
                       Model model) {
        LocalDate now = LocalDate.now();
        int y = year  != null ? year  : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        model.addAttribute("invoices",     repo.findByPeriod(y, m));
        model.addAttribute("displayYear",  y);
        model.addAttribute("displayMonth", m);
        model.addAttribute("months",       DashboardController.MONTHS);
        model.addAttribute("currentPage",  "invoices-in");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "invoices/in-list";
    }

    // ── Manual new form ───────────────────────────────────────────────────────

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("invoice",       blankInvoice());
        model.addAttribute("currentPage",   "invoices-in");
        model.addAttribute("extractedInfo", "");
        model.addAttribute("matchedSupplier", "");
        model.addAttribute("suppliers",     supplierRepo.findByActiveTrueOrderByName());
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "invoices/in-form";
    }

    // ── Upload PDF → store + OCR + supplier match → pre-filled form ──────────

    @PostMapping("/upload")
    public String uploadAndExtract(@RequestParam("file") MultipartFile file, Model model) {
        InvoiceIn invoice    = blankInvoice();
        String    info       = "";
        String    matchedMsg = "";

        if (file.isEmpty()) {
            info = "Fails netika augšupielādēts.";
        } else if (!isSupportedFile(file)) {
            info = "Lūdzu augšupielādējiet PDF vai attēla failu.";
        } else {
            try {
                // 1. Store file
                String storedPath = storageService.store(file);
                invoice.setPdfFilename(storedPath);

                // 2. OCR extraction
                Map<String, String> extracted = ocrService.extractFromFile(file);

                if (!extracted.isEmpty()) {
                    invoice = mapExtractedToInvoice(extracted, invoice);
                    invoice.setPdfFilename(storedPath);
                    info = "AI izvilka datus no dokumenta.";
                    log.info("Invoice after mapping — date: {}, number: {}, supplier: {}",
                            invoice.getInvoiceDate(), invoice.getInvoiceNumber(), invoice.getSupplierName());
                } else {
                    info = "Dokuments saglabāts. AI nevarēja izvilkt datus — aizpildiet manuāli.";
                }

                // 3. Supplier matching — overwrite OCR supplier data with trusted DB data
                Optional<Supplier> match = supplierMatcher.findMatch(
                        extracted.getOrDefault("supplierName", ""),
                        extracted.getOrDefault("supplierVatNumber", "")
                );

                if (match.isPresent()) {
                    Supplier s = match.get();
                    invoice.setSupplierName(s.getName());
                    invoice.setSupplierVatNumber(s.getVatNumber());
                    invoice.setSupplierCountry(s.getCountry());
                    invoice.setPvnLine(s.getDefaultPvnLine());
                    invoice.setDocumentTypeCode(s.getDefaultDocType());
                    invoice.setVatRate(s.getDefaultVatRate());
                    invoice.setExpenseCategory(s.getDefaultCategory());
                    // Recalc VAT with trusted rate
                    if (invoice.getAmountExclVat() != null) recalcVat(invoice);
                    matchedMsg = "Piegādātājs atpazīts: " + s.getName()
                            + " (" + s.getVatNumber() + ") — dati ielādēti no datu bāzes.";
                    log.info("Supplier matched: {}", s.getName());
                } else if (!extracted.getOrDefault("supplierName", "").isBlank()) {
                    matchedMsg = "Piegādātājs nav atpazīts — lūdzu pārbaudiet datus.";
                }

            } catch (Exception e) {
                log.error("Upload failed", e);
                info = "Kļūda: " + e.getMessage();
            }
        }

        model.addAttribute("invoice",          invoice);
        model.addAttribute("extractedInfo",    info);
        model.addAttribute("matchedSupplier",  matchedMsg);
        model.addAttribute("suppliers",        supplierRepo.findByActiveTrueOrderByName());
        model.addAttribute("currentPage",      "invoices-in");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "invoices/in-form";
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        repo.findById(id).ifPresent(inv -> model.addAttribute("invoice", inv));
        model.addAttribute("extractedInfo",   "");
        model.addAttribute("matchedSupplier", "");
        model.addAttribute("suppliers",       supplierRepo.findByActiveTrueOrderByName());
        model.addAttribute("currentPage",     "invoices-in");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "invoices/in-form";
    }

    // ── Apply supplier from dropdown ─────────────────────────────────────────

    @PostMapping("/apply-supplier")
    public String applySupplier(
            @RequestParam Long supplierId,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String invoiceDate,
            @RequestParam(required = false) String amountExclVat,
            @RequestParam(required = false) String vatRate,
            @RequestParam(required = false) String vatAmount,
            @RequestParam(required = false) String amountInclVat,
            @RequestParam(required = false) String originalCurrency,
            @RequestParam(required = false) String originalAmount,
            @RequestParam(required = false) String pdfFilename,
            @RequestParam(required = false) Integer periodYear,
            @RequestParam(required = false) Integer periodMonth,
            Model model) {

        // Build invoice from the passed params (preserves amounts user already entered)
        InvoiceIn invoice = blankInvoice();
        invoice.setId(id);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setPdfFilename(pdfFilename);

        // Parse date
        if (invoiceDate != null && !invoiceDate.isBlank()) {
            try {
                java.time.LocalDate d = java.time.LocalDate.parse(invoiceDate);
                invoice.setInvoiceDate(d);
                invoice.setPeriodYear(d.getYear());
                invoice.setPeriodMonth(d.getMonthValue());
            } catch (Exception ignored) {}
        }
        if (periodYear  != null) invoice.setPeriodYear(periodYear);
        if (periodMonth != null) invoice.setPeriodMonth(periodMonth);

        // Parse amounts
        parseDouble(amountExclVat).ifPresent(invoice::setAmountExclVat);
        parseDouble(vatRate).ifPresent(invoice::setVatRate);
        parseDouble(vatAmount).ifPresent(invoice::setVatAmount);
        parseDouble(amountInclVat).ifPresent(invoice::setAmountInclVat);
        parseDouble(originalAmount).ifPresent(invoice::setOriginalAmount);
        if (originalCurrency != null && !originalCurrency.isBlank())
            invoice.setOriginalCurrency(originalCurrency);

        // Apply supplier — overwrites supplier fields from DB
        supplierRepo.findById(supplierId).ifPresent(s -> {
            invoice.setSupplierName(s.getName());
            invoice.setSupplierVatNumber(s.getVatNumber());
            invoice.setSupplierCountry(s.getCountry() != null ? s.getCountry() : "LV");
            invoice.setPvnLine(s.getDefaultPvnLine() != null ? s.getDefaultPvnLine() : 62);
            invoice.setDocumentTypeCode(s.getDefaultDocType() != null ? s.getDefaultDocType() : "A");
            invoice.setVatRate(s.getDefaultVatRate() != null ? s.getDefaultVatRate() : 21.0);
            invoice.setExpenseCategory(s.getDefaultCategory() != null ? s.getDefaultCategory() : "GENERAL");
            invoice.setDeductibleVat(true);
            recalcVat(invoice);
            log.info("Applied supplier: {} / {}", s.getName(), s.getVatNumber());
        });

        model.addAttribute("invoice",        invoice);
        model.addAttribute("extractedInfo",  "");
        model.addAttribute("matchedSupplier","");
        model.addAttribute("suppliers",       supplierRepo.findByActiveTrueOrderByName());
        model.addAttribute("currentPage",     "invoices-in");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "invoices/in-form";
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        repo.findById(id).ifPresent(inv -> {
            journalService.deleteByInvoiceInId(id);
            storageService.delete(inv.getPdfFilename());
            repo.deleteById(id);
        });
        ra.addFlashAttribute("success", "Rēķins dzēsts");
        return "redirect:/invoices/in";
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @PostMapping("/save")
    public String save(@ModelAttribute InvoiceIn submitted, RedirectAttributes ra) {
        recalcVat(submitted);
        if (submitted.getInvoiceDate() != null) {
            submitted.setPeriodYear(submitted.getInvoiceDate().getYear());
            submitted.setPeriodMonth(submitted.getInvoiceDate().getMonthValue());
        }

        LocalDateTime now = LocalDateTime.now();
        if (submitted.getId() != null) {
            repo.findById(submitted.getId()).ifPresent(existing -> {
                applySubmittedFields(existing, submitted);
                existing.setUpdatedAt(now);
                repo.save(existing);
                journalService.recordInvoiceIn(existing);
            });
        } else {
            submitted.setCreatedAt(now);
            submitted.setUpdatedAt(now);
            InvoiceIn saved = repo.save(submitted);
            journalService.recordInvoiceIn(saved);
        }

        ra.addFlashAttribute("success", "Rēķins saglabāts");
        return "redirect:/invoices/in?year=" + submitted.getPeriodYear()
                + "&month=" + submitted.getPeriodMonth();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InvoiceIn blankInvoice() {
        LocalDate now = LocalDate.now();
        return InvoiceIn.builder()
                .invoiceDate(now)
                .vatRate(21.0)
                .pvnLine(62)
                .documentTypeCode("A")
                .supplierCountry("LV")
                .expenseCategory("GENERAL")
                .deductibleVat(true)
                .periodYear(now.getYear())
                .periodMonth(now.getMonthValue())
                .build();
    }

    private InvoiceIn mapExtractedToInvoice(Map<String, String> e, InvoiceIn base) {
        if (!e.getOrDefault("invoiceNumber", "").isBlank())
            base.setInvoiceNumber(e.get("invoiceNumber"));

        String dateStr = e.getOrDefault("invoiceDate", "");
        if (!dateStr.isBlank()) {
            try {
                LocalDate d = LocalDate.parse(dateStr);
                base.setInvoiceDate(d);
                base.setPeriodYear(d.getYear());
                base.setPeriodMonth(d.getMonthValue());
            } catch (Exception ignored) {}
        }

        if (!e.getOrDefault("supplierName", "").isBlank())
            base.setSupplierName(e.get("supplierName"));
        if (!e.getOrDefault("supplierVatNumber", "").isBlank())
            base.setSupplierVatNumber(e.get("supplierVatNumber"));

        String country = e.getOrDefault("supplierCountry", "LV");
        base.setSupplierCountry(country.isBlank() ? "LV" : country.toUpperCase());

        parseDouble(e.get("amountExclVat")).ifPresent(base::setAmountExclVat);
        parseDouble(e.get("vatRate")).ifPresent(base::setVatRate);
        parseDouble(e.get("vatAmount")).ifPresent(base::setVatAmount);
        parseDouble(e.get("amountInclVat")).ifPresent(base::setAmountInclVat);

        String origCurrency = e.getOrDefault("originalCurrency", "");
        if (!origCurrency.isBlank() && !origCurrency.equals("EUR")) {
            base.setOriginalCurrency(origCurrency);
            parseDouble(e.get("originalAmount")).ifPresent(base::setOriginalAmount);
            base.setPvnLine(55);
            base.setDocumentTypeCode("G");
        }
        return base;
    }

    private java.util.Optional<Double> parseDouble(String s) {
        if (s == null || s.isBlank()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(
                    new BigDecimal(s.replace(",", ".").replaceAll("[^0-9.]", ""))
                            .setScale(2, RoundingMode.HALF_UP).doubleValue());
        } catch (Exception e) { return java.util.Optional.empty(); }
    }

    private boolean isSupportedFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return false;
        String l = name.toLowerCase();
        return l.endsWith(".pdf") || l.endsWith(".jpg") || l.endsWith(".jpeg")
                || l.endsWith(".png") || l.endsWith(".webp");
    }

    private void recalcVat(InvoiceIn inv) {
        if (inv.getAmountExclVat() != null && inv.getVatRate() != null
                && (inv.getVatAmount() == null || inv.getVatAmount() == 0)) {
            double vat = Math.round(inv.getAmountExclVat() * inv.getVatRate() / 100 * 100.0) / 100.0;
            inv.setVatAmount(vat);
            inv.setAmountInclVat(inv.getAmountExclVat() + vat);
        }
    }

    private void applySubmittedFields(InvoiceIn existing, InvoiceIn s) {
        existing.setInvoiceNumber(s.getInvoiceNumber());
        existing.setInvoiceDate(s.getInvoiceDate());
        existing.setSupplierName(s.getSupplierName());
        existing.setSupplierVatNumber(s.getSupplierVatNumber());
        existing.setSupplierCountry(s.getSupplierCountry());
        existing.setAmountExclVat(s.getAmountExclVat());
        existing.setVatRate(s.getVatRate());
        existing.setVatAmount(s.getVatAmount());
        existing.setAmountInclVat(s.getAmountInclVat());
        existing.setOriginalCurrency(s.getOriginalCurrency());
        existing.setOriginalAmount(s.getOriginalAmount());
        existing.setPvnLine(s.getPvnLine());
        existing.setDocumentTypeCode(s.getDocumentTypeCode());
        existing.setExpenseCategory(s.getExpenseCategory());
        existing.setDeductibleVat(s.isDeductibleVat());
        existing.setPeriodYear(s.getPeriodYear());
        existing.setPeriodMonth(s.getPeriodMonth());
        if (s.getPdfFilename() != null && !s.getPdfFilename().isBlank())
            existing.setPdfFilename(s.getPdfFilename());
    }
}
