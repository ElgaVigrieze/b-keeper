package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Outgoing invoice (izejošais rēķins).
 * Source for PVN 1-III annex and income journal entries.
 */
@Data
@Builder
@Table("invoice_out")
public class InvoiceOut {

    @Id
    private Long id;

    private String invoiceNumber;    // e.g. VS26/36

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate issueDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paidDate;      // null = unpaid

    // ── Client ────────────────────────────────────────────────
    private String clientName;
    private String clientRegNumber;
    private String clientVatNumber;  // for PVN 1-III

    // ── Amounts ───────────────────────────────────────────────
    private Double amountExclVat;
    private Double vatRate;          // e.g. 21.0
    private Double vatAmount;
    private Double amountInclVat;

    // ── PVN classification ────────────────────────────────────
    /**
     * PVN form line number:
     *   41  = standard rate domestic (most common)
     *   43  = zero rate domestic
     *   45  = EU supply (goods)
     *   48  = services abroad
     *   49  = exempt
     */
    private Integer pvnLine;

    /**
     * Document type code used in PVN annex:
     *   1 = rēķins (invoice)
     *   X = kases čeks (cash)
     *   T = telekomunikācijas
     */
    private String documentTypeCode;

    private String description;
    private Integer periodYear;
    private Integer periodMonth;

    private String status;           // DRAFT | ISSUED | PAID | OVERDUE | CANCELLED

    private Long journalEntryId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
