package lv.bkeeper.service;

import lv.bkeeper.domain.*;
import lv.bkeeper.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes aggregated figures for EDS report screens.
 * All amounts are rounded to 2 decimal places (EUR cents).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EdsReportService {

    private final InvoiceOutRepository       invoiceOutRepo;
    private final InvoiceInRepository        invoiceInRepo;
    private final PayrollEntryRepository     payrollRepo;
    private final EmployeeRepository         employeeRepo;
    private final JournalEntryRepository     journalRepo;
    private final FixedAssetRepository       assetRepo;
    private final AssetDepreciationService   depreciationService;

    // ── PVN (monthly) ────────────────────────────────────────────────────────

    public PvnReport pvnReport(int year, int month) {
        // Ensure depreciation is posted for this period before calculating
        depreciationService.ensureDepreciationPosted(year, month);

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
        // Ensure depreciation is posted for this period before calculating
        depreciationService.ensureDepreciationPosted(year, month);

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

    // ── UIN (annual corporate income tax) ─────────────────────────────────────

    /**
     * UIN deklarācija data for a given year.
     * Under the new Latvian model, CIT is only on distributed profits (dividends).
     * Most small companies have UIN = 0.
     */
    public UinReport uinReport(int year) {
        // Ensure depreciation is posted for the entire year before calculating
        depreciationService.ensureDepreciationPostedForYear(year);

        UinReport r = new UinReport();
        r.year = year;

        // Financial results from journal
        r.totalIncome  = round(d(journalRepo.sumIncomeByYear(year)));
        r.totalExpense = round(d(journalRepo.sumExpenseByYear(year)));
        r.profitBeforeTax = round(r.totalIncome - r.totalExpense);

        // Payroll totals (for info)
        r.totalGrossSalaries = round(d(payrollRepo.sumGrossByYear(year)));
        r.totalVsaoiEmployer = round(d(payrollRepo.sumVsaoiEmployerByYear(year)));
        r.totalRiskDuty      = round(d(payrollRepo.sumRiskDutyByYear(year)));

        // UIN calculation (simplified — assumes no dividends distributed)
        // Under LV law 4.pants 2.daļa, UIN base = dividends / 0.8, tax = 20%
        r.dividendsDistributed = 0.0;   // TODO: add dividend tracking if needed
        r.nonBusinessExpenses  = 0.0;   // TODO: track if needed
        r.loansToRelatedParties = 0.0;  // TODO: track if needed

        r.line5DeemedDividends = r.dividendsDistributed;
        r.line6DeemedProfit    = r.nonBusinessExpenses + r.loansToRelatedParties;
        r.line7TaxBase         = round((r.line5DeemedDividends + r.line6DeemedProfit) / 0.8);
        r.line22AdjustedBase   = r.line7TaxBase;  // no deductions in simple case
        r.line23Tax            = round(r.line22AdjustedBase * 0.20);
        r.line29TotalTax       = r.line23Tax;
        r.line32AnnualResult   = r.line29TotalTax;

        return r;
    }

    // ── Gada Pārskats (annual financial report) ───────────────────────────────

    /**
     * Simplified annual report (vienkāršots gada pārskats) for small companies.
     * Includes income statement and simplified balance sheet.
     */
    public AnnualReport annualReport(int year) {
        // Ensure depreciation is posted for the entire year before calculating
        depreciationService.ensureDepreciationPostedForYear(year);

        AnnualReport r = new AnnualReport();
        r.year = year;

        // ── Income Statement (Peļņas vai zaudējumu aprēķins) ──

        // Revenue = income from invoices
        r.netTurnover = round(d(journalRepo.sumIncomeByYearAndType(year, "INCOME_INVOICE")));

        // Cost of goods/services (simplified: all invoice expenses)
        r.costOfSales = round(d(journalRepo.sumExpenseByYearAndType(year, "EXPENSE_INVOICE")));

        r.grossProfit = round(r.netTurnover - r.costOfSales);

        // Personnel costs from payroll
        r.personnelCostsGross   = round(d(payrollRepo.sumGrossByYear(year)));
        r.personnelCostsSocial  = round(d(payrollRepo.sumVsaoiEmployerByYear(year)) + d(payrollRepo.sumRiskDutyByYear(year)));
        r.personnelCostsTotal   = round(r.personnelCostsGross + r.personnelCostsSocial);

        // Depreciation - use actual posted depreciation from journal
        r.depreciationExpense = round(d(journalRepo.sumExpenseByYearAndType(year, "ASSET_DEPRECIATION")));

        // Other operating expenses (could be expanded)
        r.otherOperatingExpense = 0.0;

        // Operating profit
        r.operatingProfit = round(r.grossProfit - r.personnelCostsTotal - r.depreciationExpense - r.otherOperatingExpense);

        // Financial income/expense (simplified: none)
        r.financialIncome  = 0.0;
        r.financialExpense = 0.0;

        // Profit before tax
        r.profitBeforeTax = round(r.operatingProfit + r.financialIncome - r.financialExpense);

        // Corporate income tax (UIN)
        UinReport uin = uinReport(year);
        r.corporateIncomeTax = uin.line32AnnualResult;

        // Net profit
        r.netProfit = round(r.profitBeforeTax - r.corporateIncomeTax);

        // ── Balance Sheet (simplified) ──

        // Fixed assets
        r.fixedAssetsGross       = round(d(assetRepo.sumAcquisitionCostActive()));
        r.fixedAssetsDepreciation = round(d(assetRepo.sumAccumulatedDepreciationActive()));
        r.fixedAssetsNet         = round(d(assetRepo.sumBookValueActive()));

        // Current assets (simplified — would need bank/cash tracking for accuracy)
        r.receivables = 0.0;  // TODO: sum unpaid outgoing invoices
        r.cashAndBank = 0.0;  // TODO: needs bank account tracking
        r.currentAssetsTotal = round(r.receivables + r.cashAndBank);

        r.totalAssets = round(r.fixedAssetsNet + r.currentAssetsTotal);

        // Equity (simplified)
        r.shareCapital   = 2800.0;  // default SIA minimum, should come from company settings
        r.retainedEarnings = r.netProfit;
        r.equityTotal    = round(r.shareCapital + r.retainedEarnings);

        // Liabilities
        r.payables = 0.0;  // TODO: sum unpaid incoming invoices
        r.taxLiabilities = 0.0;  // could compute from DDZ
        r.liabilitiesTotal = round(r.payables + r.taxLiabilities);

        r.totalEquityAndLiabilities = round(r.equityTotal + r.liabilitiesTotal);

        // Monthly breakdown for display
        r.monthlyIncome  = new double[13];
        r.monthlyExpense = new double[13];
        for (int m = 1; m <= 12; m++) {
            r.monthlyIncome[m]  = round(d(journalRepo.sumIncomeByPeriod(year, m)));
            r.monthlyExpense[m] = round(d(journalRepo.sumExpenseByPeriod(year, m)));
        }

        return r;
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

    // ── UIN Report ────────────────────────────────────────────────────────────

    public static class UinReport {
        public int year;

        // Financial results from journal
        public double totalIncome;
        public double totalExpense;
        public double profitBeforeTax;

        // Payroll summary
        public double totalGrossSalaries;
        public double totalVsaoiEmployer;
        public double totalRiskDuty;

        // UIN calculation inputs
        public double dividendsDistributed;
        public double nonBusinessExpenses;
        public double loansToRelatedParties;

        // UIN form lines
        public double line5DeemedDividends;    // 4.p. 2.d. 1.p.
        public double line6DeemedProfit;       // nosacīti sadalītā peļņa
        public double line7TaxBase;            // (5+6)/0.8
        public double line22AdjustedBase;      // after deductions
        public double line23Tax;               // 22 × 20%
        public double line29TotalTax;          // nodokļa kopsumma
        public double line32AnnualResult;      // gada rezultāts
    }

    // ── Annual Report ─────────────────────────────────────────────────────────

    public static class AnnualReport {
        public int year;

        // Income Statement (Peļņas vai zaudējumu aprēķins)
        public double netTurnover;            // neto apgrozījums
        public double costOfSales;            // pārdotās produkcijas ražošanas izmaksas
        public double grossProfit;            // bruto peļņa

        public double personnelCostsGross;    // personāla izmaksas - algas
        public double personnelCostsSocial;   // personāla izmaksas - soc. apdr.
        public double personnelCostsTotal;    // personāla izmaksas kopā

        public double depreciationExpense;    // nolietojums
        public double otherOperatingExpense;  // pārējās saimnieciskās darbības izmaksas

        public double operatingProfit;        // peļņa no pamatdarbības
        public double financialIncome;        // finanšu ieņēmumi
        public double financialExpense;       // finanšu izmaksas

        public double profitBeforeTax;        // peļņa pirms nodokļiem
        public double corporateIncomeTax;     // UIN
        public double netProfit;              // tīrā peļņa

        // Balance Sheet (Bilance) - Assets
        public double fixedAssetsGross;       // pamatlīdzekļi sākotnējā vērtība
        public double fixedAssetsDepreciation; // uzkrātais nolietojums
        public double fixedAssetsNet;         // pamatlīdzekļi atlikusī vērtība

        public double receivables;            // debitori
        public double cashAndBank;            // nauda un bankas konti
        public double currentAssetsTotal;     // apgrozāmie līdzekļi kopā

        public double totalAssets;            // aktīvi kopā

        // Balance Sheet - Equity & Liabilities
        public double shareCapital;           // pamatkapitāls
        public double retainedEarnings;       // nesadalītā peļņa
        public double equityTotal;            // pašu kapitāls kopā

        public double payables;               // kreditori
        public double taxLiabilities;         // nodokļu saistības
        public double liabilitiesTotal;       // saistības kopā

        public double totalEquityAndLiabilities;  // pasīvi kopā

        // Monthly breakdown
        public double[] monthlyIncome;
        public double[] monthlyExpense;
    }
}
