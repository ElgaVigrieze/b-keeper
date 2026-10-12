package lv.bkeeper.controller;

import lv.bkeeper.domain.Customer;
import lv.bkeeper.domain.InvoiceOut;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.CustomerRepository;
import lv.bkeeper.repository.InvoiceOutRepository;
import lv.bkeeper.service.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/invoices/out")
@RequiredArgsConstructor
public class InvoiceOutController {

    private final InvoiceOutRepository repo;
    private final CompanyRepository    companyRepo;
    private final JournalService       journalService;
    private final CustomerRepository   customerRepo;

    @GetMapping
    public String list(@RequestParam(required = false) Integer year,
                       @RequestParam(required = false) Integer month,
                       Model model) {
        LocalDate now = LocalDate.now();
        int y = year  != null ? year  : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        model.addAttribute("invoices",     repo.findActiveByPeriod(y, m));
        model.addAttribute("displayYear",  y);
        model.addAttribute("displayMonth", m);
        model.addAttribute("months",       DashboardController.MONTHS);
        model.addAttribute("currentPage",  "invoices-out");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "invoices/out-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        LocalDate now = LocalDate.now();
        model.addAttribute("invoice", InvoiceOut.builder()
                .issueDate(now)
                .vatRate(21.0)
                .pvnLine(41)
                .documentTypeCode("1")
                .status("DRAFT")
                .periodYear(now.getYear())
                .periodMonth(now.getMonthValue())
                .build());
        model.addAttribute("customers",   customerRepo.findByActiveTrueOrderByName());
        model.addAttribute("currentPage", "invoices-out");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "invoices/out-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        repo.findById(id).ifPresent(inv -> model.addAttribute("invoice", inv));
        model.addAttribute("customers",   customerRepo.findByActiveTrueOrderByName());
        model.addAttribute("currentPage", "invoices-out");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "invoices/out-form";
    }

    // ── Apply customer from dropdown ──────────────────────────────────────────

    @PostMapping("/apply-customer")
    public String applyCustomer(
            @RequestParam Long customerId,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String issueDate,
            @RequestParam(required = false) String dueDate,
            @RequestParam(required = false) String amountExclVat,
            @RequestParam(required = false) String vatRate,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer periodYear,
            @RequestParam(required = false) Integer periodMonth,
            Model model) {

        LocalDate now = LocalDate.now();
        InvoiceOut invoice = InvoiceOut.builder()
                .issueDate(now).vatRate(21.0).pvnLine(41)
                .documentTypeCode("1").status("DRAFT")
                .periodYear(now.getYear()).periodMonth(now.getMonthValue())
                .build();

        invoice.setId(id);
        invoice.setInvoiceNumber(invoiceNumber);
        if (status != null && !status.isBlank()) invoice.setStatus(status);

        parseLocalDate(issueDate).ifPresent(d -> {
            invoice.setIssueDate(d);
            invoice.setPeriodYear(d.getYear());
            invoice.setPeriodMonth(d.getMonthValue());
        });
        parseLocalDate(dueDate).ifPresent(invoice::setDueDate);
        if (periodYear  != null) invoice.setPeriodYear(periodYear);
        if (periodMonth != null) invoice.setPeriodMonth(periodMonth);
        parseDecimal(amountExclVat).ifPresent(invoice::setAmountExclVat);
        parseDecimal(vatRate).ifPresent(invoice::setVatRate);
        if (description != null) invoice.setDescription(description);

        // Apply customer data from DB
        customerRepo.findById(customerId).ifPresent(c -> {
            invoice.setClientName(c.getName());
            invoice.setClientRegNumber(c.getRegNumber());
            invoice.setClientVatNumber(c.getVatNumber());
            if (c.getDefaultPvnLine() != null) invoice.setPvnLine(c.getDefaultPvnLine());
            if (c.getDefaultDocType() != null) invoice.setDocumentTypeCode(c.getDefaultDocType());
            if (c.getDefaultVatRate() != null) invoice.setVatRate(c.getDefaultVatRate());
        });

        recalcVat(invoice);

        model.addAttribute("invoice",     invoice);
        model.addAttribute("customers",   customerRepo.findByActiveTrueOrderByName());
        model.addAttribute("currentPage", "invoices-out");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "invoices/out-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute InvoiceOut submitted, RedirectAttributes ra) {
        recalcVat(submitted);

        // Derive period from issue date — no manual entry needed
        if (submitted.getIssueDate() != null) {
            submitted.setPeriodYear(submitted.getIssueDate().getYear());
            submitted.setPeriodMonth(submitted.getIssueDate().getMonthValue());
        }

        LocalDateTime now = LocalDateTime.now();

        if (submitted.getId() != null) {
            repo.findById(submitted.getId()).ifPresent(existing -> {
                applySubmittedFields(existing, submitted);
                existing.setUpdatedAt(now);
                repo.save(existing);
                journalService.recordInvoiceOut(existing);
            });
        } else {
            submitted.setCreatedAt(now);
            submitted.setUpdatedAt(now);
            InvoiceOut saved = repo.save(submitted);
            journalService.recordInvoiceOut(saved);
        }

        ra.addFlashAttribute("success", "Rēķins saglabāts");
        return "redirect:/invoices/out?year=" + submitted.getPeriodYear()
                + "&month=" + submitted.getPeriodMonth();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        journalService.deleteByInvoiceOutId(id);
        repo.deleteById(id);
        ra.addFlashAttribute("success", "Rēķins dzēsts");
        return "redirect:/invoices/out";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam String status,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDate,
                               RedirectAttributes ra) {
        repo.findById(id).ifPresent(inv -> {
            inv.setStatus(status);
            if ("PAID".equals(status) && paidDate != null) inv.setPaidDate(paidDate);
            inv.setUpdatedAt(LocalDateTime.now());
            repo.save(inv);
            journalService.recordInvoiceOut(inv); // update journal entry if status changed
        });
        ra.addFlashAttribute("success", "Statuss atjaunināts");
        return "redirect:/invoices/out";
    }

    private java.util.Optional<LocalDate> parseLocalDate(String s) {
        if (s == null || s.isBlank()) return java.util.Optional.empty();
        try { return java.util.Optional.of(LocalDate.parse(s)); }
        catch (Exception e) { return java.util.Optional.empty(); }
    }

    private java.util.Optional<Double> parseDecimal(String s) {
        if (s == null || s.isBlank()) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(
                    new BigDecimal(s.replace(",", ".").replaceAll("[^0-9.]", ""))
                            .setScale(2, RoundingMode.HALF_UP).doubleValue());
        } catch (Exception e) { return java.util.Optional.empty(); }
    }

    private void recalcVat(InvoiceOut inv) {
        if (inv.getAmountExclVat() != null && inv.getVatRate() != null) {
            double vat = Math.round(inv.getAmountExclVat() * inv.getVatRate() / 100 * 100.0) / 100.0;
            inv.setVatAmount(vat);
            inv.setAmountInclVat(inv.getAmountExclVat() + vat);
        }
    }

    private void applySubmittedFields(InvoiceOut existing, InvoiceOut s) {
        existing.setInvoiceNumber(s.getInvoiceNumber());
        existing.setIssueDate(s.getIssueDate());
        existing.setDueDate(s.getDueDate());
        existing.setClientName(s.getClientName());
        existing.setClientRegNumber(s.getClientRegNumber());
        existing.setClientVatNumber(s.getClientVatNumber());
        existing.setAmountExclVat(s.getAmountExclVat());
        existing.setVatRate(s.getVatRate());
        existing.setVatAmount(s.getVatAmount());
        existing.setAmountInclVat(s.getAmountInclVat());
        existing.setPvnLine(s.getPvnLine());
        existing.setDocumentTypeCode(s.getDocumentTypeCode());
        existing.setDescription(s.getDescription());
        existing.setPeriodYear(s.getPeriodYear());
        existing.setPeriodMonth(s.getPeriodMonth());
        existing.setStatus(s.getStatus());
    }
}
