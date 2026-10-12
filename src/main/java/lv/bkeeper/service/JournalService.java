package lv.bkeeper.service;

import lv.bkeeper.domain.InvoiceIn;
import lv.bkeeper.domain.InvoiceOut;
import lv.bkeeper.domain.JournalEntry;
import lv.bkeeper.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maintains the Ieņēmumu un izdevumu uzskaites žurnāls automatically.
 * Called whenever an invoice is created, updated, or deleted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalEntryRepository journalRepo;

    // ── Outgoing invoice (income) ─────────────────────────────────────────────

    public void recordInvoiceOut(InvoiceOut invoice) {
        // Remove any existing entry for this invoice (idempotent)
        deleteByInvoiceOutId(invoice.getId());

        if ("CANCELLED".equals(invoice.getStatus())) return;

        LocalDate date = invoice.getIssueDate();
        JournalEntry entry = JournalEntry.builder()
                .entryDate(date.toString())
                .description("Rēķins: " + invoice.getClientName())
                .reference(invoice.getInvoiceNumber())
                .entryType("INCOME_INVOICE")
                .incomeAmount(invoice.getAmountExclVat())
                .expenseAmount(0.0)
                .periodYear(date.getYear())
                .periodMonth(date.getMonthValue())
                .invoiceOutId(invoice.getId())
                .createdAt(LocalDateTime.now())
                .build();

        journalRepo.save(entry);
        log.debug("Journal: recorded income invoice {} -> EUR {}", invoice.getInvoiceNumber(), invoice.getAmountExclVat());
    }

    public void deleteByInvoiceOutId(Long invoiceOutId) {
        if (invoiceOutId == null) return;
        journalRepo.findByPeriodYearAndPeriodMonthOrderByEntryDate(0, 0); // warm up
        journalRepo.deleteByInvoiceOutId(invoiceOutId);
    }

    // ── Incoming invoice (expense) ────────────────────────────────────────────

    public void recordInvoiceIn(InvoiceIn invoice) {
        deleteByInvoiceInId(invoice.getId());

        LocalDate date = invoice.getInvoiceDate();
        JournalEntry entry = JournalEntry.builder()
                .entryDate(date.toString())
                .description("Pirkums: " + invoice.getSupplierName())
                .reference(invoice.getInvoiceNumber())
                .entryType("EXPENSE_INVOICE")
                .incomeAmount(0.0)
                .expenseAmount(invoice.getAmountExclVat())
                .periodYear(date.getYear())
                .periodMonth(date.getMonthValue())
                .invoiceInId(invoice.getId())
                .createdAt(LocalDateTime.now())
                .build();

        journalRepo.save(entry);
        log.debug("Journal: recorded expense invoice {} -> EUR {}", invoice.getInvoiceNumber(), invoice.getAmountExclVat());
    }

    public void deleteByInvoiceInId(Long invoiceInId) {
        if (invoiceInId == null) return;
        journalRepo.deleteByInvoiceInId(invoiceInId);
    }
}
