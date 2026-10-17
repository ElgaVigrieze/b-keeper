package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Monthly payroll calculation for one employee.
 * Maps directly to the DDZ (Darba devēja ziņojums) form columns.
 */
@Data
@Builder
@Table("payroll_entry")
public class PayrollEntry {

    @Id
    private Long id;

    private Long employeeId;

    private Integer periodYear;
    private Integer periodMonth;

    // ── DDZ column 3 ──────────────────────────────────────────
    private Double grossSalary;           // darba ienākumi

    // ── DDZ column 4 ──────────────────────────────────────────
    private Double vsaoiEmployer;         // VSAOI darba devējs 23.59%

    // ── DDZ column 7 ──────────────────────────────────────────
    private Double iin;                   // iedzīvotāju ienākuma nodoklis 23%

    // ── DDZ column 8 ──────────────────────────────────────────
    private Double riskDuty;             // uzņēmēj. riska nodeva 0.46%

    // ── DDZ column 10 ─────────────────────────────────────────
    private Integer hoursWorked;

    // ── DDZ column 11 ─────────────────────────────────────────
    private Double vsaoiEmployee;        // darba ņēmēja VSAOI 10.50% → pensiju apdrošināšana

    // ── DDZ column 12 ─────────────────────────────────────────
    private Double solidarityTax;        // solidaritātes nodoklis (0 for most employees)

    // ── Net pay (not on DDZ, but needed for payment) ──────────
    private Double netSalary;

    // ── DDZ correction columns 5, 6, 13, 14 ──────────────────
    private Double salaryCorrection;
    private Double vsaoiCorrection;
    private Double pensionCorrection;
    private Double solidarityCorrection;

    // ── DDZ column 9 ──────────────────────────────────────────
    private LocalDate paymentDate;       // obligāto iemaksu veikšanas datums

    // ── DDZ column 16 ─────────────────────────────────────────
    private boolean paidInCash;

    private Long edsSubmissionId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
