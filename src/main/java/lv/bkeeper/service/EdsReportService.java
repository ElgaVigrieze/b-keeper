package lv.bkeeper.service;

import lv.bkeeper.domain.*;
import lv.bkeeper.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Computes aggregated figures for EDS report screens.
 * All amounts are rounded to 2 decimal places (EUR cents).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EdsReportService {

    private final InvoiceOutRepository    invoiceOutRepo;
    private final InvoiceInRepository     invoiceInRepo;
    private final PayrollEntryRepository  payrollRepo;
    private final EmployeeRepository      employeeRepo;

    // ── PVN (monthly) ────────────────────────────────────────────────────────

    public PvnReport pvnReport(int year, int month) {
        List<InvoiceOut> outInvoices = invoiceOutRepo.findActiveByPeriod(year, month);
        List<InvoiceIn>  inInvoices  = invoiceInRepo.findByPeriod(year, month);

        PvnReport r = new PvnReport();
        r.year  = year;
        r.month = month;

        // ── Outgoing (output VAT) ──
        for (InvoiceOut inv : outInvoices) {
            double excl = d(inv.getAmountExclVat());
            double vat  = d(inv.getVatAmount());
            int line = inv.getPvnLine() != null ? inv.getPvnLine() : 41;
            switch (line) {
                case 41 -> { r.line41Taxable += excl; r.line41Vat += vat; }
                case 43 -> r.line43 += excl;
                case 45 -> r.line45 += excl;
                case 48 -> r.line48 += excl;
                case 49 -> r.line49 += excl;
                default -> { r.line41Taxable += excl; r.line41Vat += vat; }
            }
        }

        // ── Incoming (input VAT / priekšnodoklis) ──
        for (InvoiceIn inv : inInvoices) {
            if (!inv.isDeductibleVat()) continue;
            double excl = d(inv.getAmountExclVat());
            double vat  = d(inv.getVatAmount());
            int line = inv.getPvnLine() != null ? inv.getPvnLine() : 62;
            switch (line) {
                case 62 -> { r.line62Taxable += excl; r.line62Vat += vat; }
                case 55 -> { r.line55Taxable += excl; r.line55Vat += vat; }
                case 54 -> { r.line54Taxable += excl; r.line54Vat += vat; }
                case 61 -> { r.line61Taxable += excl; r.line61Vat += vat; }
                default -> { r.line62Taxable += excl; r.line62Vat += vat; }
            }
        }

        // ── Summary ──
        r.totalOutputVat = round(r.line41Vat);
        r.totalInputVat  = round(r.line62Vat + r.line55Vat + r.line54Vat + r.line61Vat);
        r.vatPayable     = round(r.totalOutputVat - r.totalInputVat);

        // round all fields
        r.line41Taxable = round(r.line41Taxable);
        r.line41Vat     = round(r.line41Vat);
        r.line43        = round(r.line43);
        r.line45        = round(r.line45);
        r.line48        = round(r.line48);
        r.line49        = round(r.line49);
        r.line62Taxable = round(r.line62Taxable);
        r.line62Vat     = round(r.line62Vat);
        r.line55Taxable = round(r.line55Taxable);
        r.line55Vat     = round(r.line55Vat);
        r.line54Taxable = round(r.line54Taxable);
        r.line54Vat     = round(r.line54Vat);
        r.line61Taxable = round(r.line61Taxable);
        r.line61Vat     = round(r.line61Vat);

        return r;
    }

    // ── DDZ (monthly) ─────────────────────────────────────────────────────────

    public DdzReport ddzReport(int year, int month) {
        List<PayrollEntry> entries   = payrollRepo.findByPeriodYearAndPeriodMonth(year, month);
        List<Employee>     employees = employeeRepo.findAllOrderByName();

        DdzReport r = new DdzReport();
        r.year  = year;
        r.month = month;

        for (PayrollEntry e : entries) {
            DdzReport.Row row = new DdzReport.Row();
            // Find employee name
            employees.stream()
                    .filter(emp -> emp.getId().equals(e.getEmployeeId()))
                    .findFirst()
                    .ifPresent(emp -> {
                        row.personalCode = emp.getPersonalCode();
                        row.fullName     = emp.getFirstName() + " " + emp.getLastName();
                    });

            row.grossSalary    = round(d(e.getGrossSalary()));
            row.vsaoiEmployer  = round(d(e.getVsaoiEmployer()));
            row.iin            = round(d(e.getIin()));
            row.riskDuty       = round(d(e.getRiskDuty()));
            row.vsaoiEmployee  = round(d(e.getVsaoiEmployee()));
            row.solidarityTax  = round(d(e.getSolidarityTax()));
            row.netSalary      = round(d(e.getNetSalary()));

            r.rows.add(row);

            r.totalGross       += row.grossSalary;
            r.totalVsaoiEmployer += row.vsaoiEmployer;
            r.totalVsaoiEmployee += row.vsaoiEmployee;
            r.totalIin         += row.iin;
            r.totalRiskDuty    += row.riskDuty;
            r.totalNet         += row.netSalary;
        }

        r.totalGross          = round(r.totalGross);
        r.totalVsaoiEmployer  = round(r.totalVsaoiEmployer);
        r.totalVsaoiEmployee  = round(r.totalVsaoiEmployee);
        r.totalIin            = round(r.totalIin);
        r.totalRiskDuty       = round(r.totalRiskDuty);
        r.totalNet            = round(r.totalNet);
        r.totalEmployerCost   = round(r.totalGross + r.totalVsaoiEmployer + r.totalRiskDuty);

        return r;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double d(Double v) { return v != null ? v : 0.0; }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public static class PvnReport {
        public int year, month;

        // Output VAT lines (outgoing invoices)
        public double line41Taxable, line41Vat;   // domestic standard rate
        public double line43;                      // domestic 0%
        public double line45;                      // EU supply
        public double line48;                      // services abroad
        public double line49;                      // exempt

        // Input VAT lines (incoming invoices, deductible)
        public double line62Taxable, line62Vat;   // domestic purchases
        public double line55Taxable, line55Vat;   // EU goods
        public double line54Taxable, line54Vat;   // reverse charge services
        public double line61Taxable, line61Vat;   // imports

        // Summary
        public double totalOutputVat;
        public double totalInputVat;
        public double vatPayable;  // positive = pay to VID, negative = refund
    }

    public static class DdzReport {
        public int year, month;
        public java.util.List<Row> rows = new java.util.ArrayList<>();

        public double totalGross;
        public double totalVsaoiEmployer;
        public double totalVsaoiEmployee;
        public double totalIin;
        public double totalRiskDuty;
        public double totalNet;
        public double totalEmployerCost;

        public static class Row {
            public String personalCode;
            public String fullName;
            public double grossSalary;
            public double vsaoiEmployer;
            public double vsaoiEmployee;
            public double iin;
            public double riskDuty;
            public double solidarityTax;
            public double netSalary;
        }
    }
}
