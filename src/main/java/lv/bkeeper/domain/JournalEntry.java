package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Ieņēmumu un izdevumu uzskaites žurnāls.
 * The legal accounting record for simplified bookkeeping (vienkāršais ieraksts).
 * Auto-populated — never entered manually.
 */
@Data
@Builder
@Table("journal_entry")
public class JournalEntry {

    @Id
    private Long id;

    private String entryDate;     // YYYY-MM-DD
    private String description;
    private String reference;     // invoice number or payroll ref

    /**
     * Entry source type:
     *   INCOME_INVOICE       = from invoice_out
     *   EXPENSE_INVOICE      = from invoice_in
     *   PAYROLL              = from payroll batch
     *   ASSET_DEPRECIATION   = monthly depreciation
     *   CORRECTION           = manual correction
     */
    private String entryType;

    private Double incomeAmount;   // ieņēmumi
    private Double expenseAmount;  // izdevumi

    private Integer periodYear;
    private Integer periodMonth;

    // At most one source reference will be set
    private Long invoiceOutId;
    private Long invoiceInId;
    private String payrollRef;     // e.g. "PAYROLL-2026-07"
    private Long assetId;

    private LocalDateTime createdAt;
}
