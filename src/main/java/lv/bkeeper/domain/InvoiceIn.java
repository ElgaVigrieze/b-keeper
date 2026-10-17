package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Incoming invoice (ienākošais rēķins).
 * Source for PVN 1-I (domestic) or PVN 1-II (EU) annex and expense journal entries.
 */
@Data
@Builder
@Table("invoice_in")
public class InvoiceIn {

    @Id
    private Long id;

    private String invoiceNumber;     // supplier's invoice number

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate invoiceDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paidDate;

    // ── Supplier ──────────────────────────────────────────────
    private String supplierName;
    private String supplierVatNumber; // for PVN annex
    private String supplierCountry;   // ISO 2-letter, e.g. LV, PL, CZ

    // ── Amounts (EUR) ─────────────────────────────────────────
    private Double amountExclVat;
    private Double vatRate;
    private Double vatAmount;
    private Double amountInclVat;

    // ── EU purchases: original currency ──────────────────────
    private String originalCurrency; // e.g. PLN, CZK — null for EUR
    private Double originalAmount;   // amount in original currency

    // ── PVN classification ────────────────────────────────────
    /**
     * PVN form line:
     *   62  = domestic input VAT (PVN 1-I)
     *   55  = EU goods standard rate (PVN 1-II)
     *   61  = import
     *   54  = received services (reverse charge)
     */
    private Integer pvnLine;

    /**
     * Document type code:
     *   A  = rēķins (invoice)       ← most domestic invoices
     *   G  = ES preces (EU goods)   ← PVN 1-II
     *   T  = telekomunikācijas
     *   N  = pakalpojumi no ārvalsts (reverse charge services)
     *   SP = simplified invoice
     */
    private String documentTypeCode;

    private String expenseCategory;  // GENERAL | FUEL | IT | RENT | SERVICES | MATERIALS
    private boolean deductibleVat;   // true = priekšnodoklis atskaitāms
    private String pdfFilename;      // stored PDF filename for audit

    private Integer periodYear;
    private Integer periodMonth;

    private Long journalEntryId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
