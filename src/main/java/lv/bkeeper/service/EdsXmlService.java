package lv.bkeeper.service;

import lv.bkeeper.domain.Company;
import lv.bkeeper.domain.Employee;
import lv.bkeeper.domain.InvoiceIn;
import lv.bkeeper.domain.InvoiceOut;
import lv.bkeeper.repository.EmployeeRepository;
import lv.bkeeper.repository.InvoiceInRepository;
import lv.bkeeper.repository.InvoiceOutRepository;
import lv.bkeeper.repository.PayrollEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Generates EDS-compatible XML files for PVN declaration and DDZ report.
 *
 * These XML files can be uploaded directly to eds.vid.gov.lv via
 * File → Upload XML.
 *
 * Format references:
 *   PVN1  — PVN deklarācija (monthly VAT return)
 *   DDZ   — Darba devēja ziņojums (monthly payroll report)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EdsXmlService {

    private final InvoiceOutRepository   invoiceOutRepo;
    private final InvoiceInRepository    invoiceInRepo;
    private final PayrollEntryRepository payrollRepo;
    private final EmployeeRepository     employeeRepo;
    private final EdsReportService       reportService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ── PVN XML ───────────────────────────────────────────────────────────────

    public String generatePvnXml(Company company, int year, int month) {
        EdsReportService.PvnReport r = reportService.pvnReport(year, month);
        String periodStart = LocalDate.of(year, month, 1).format(DATE_FMT);
        String periodEnd   = LocalDate.of(year, month, 1).withDayOfMonth(
                LocalDate.of(year, month, 1).lengthOfMonth()).format(DATE_FMT);
        String submDate    = LocalDate.now().format(DATE_FMT);
        String regNr       = safe(company.getRegNumber());
        String pvnNr       = safe(company.getVatNumber());
        String name        = safe(company.getName());

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<PVN1 xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");
        sb.append("  <Galvene>\n");
        sb.append("    <Maks_kods>").append(regNr).append("</Maks_kods>\n");
        sb.append("    <PVN_numurs>").append(pvnNr).append("</PVN_numurs>\n");
        sb.append("    <Nosauk>").append(escXml(name)).append("</Nosauk>\n");
        sb.append("    <Taks_periods_no>").append(periodStart).append("</Taks_periods_no>\n");
        sb.append("    <Taks_periods_lidz>").append(periodEnd).append("</Taks_periods_lidz>\n");
        sb.append("    <Iesn_dat>").append(submDate).append("</Iesn_dat>\n");
        sb.append("  </Galvene>\n");
        sb.append("  <Dati>\n");

        // Output VAT section
        appendLine(sb, "41", r.line41Taxable, r.line41Vat, true);
        appendLineAmount(sb, "43", r.line43);
        appendLineAmount(sb, "45", r.line45);
        appendLineAmount(sb, "48", r.line48);
        appendLineAmount(sb, "49", r.line49);

        // Summary output
        double totalTaxable = r.line41Taxable + r.line43 + r.line45 + r.line48 + r.line49;
        appendLine(sb, "50", totalTaxable, r.totalOutputVat, true);

        // Input VAT section
        appendLine(sb, "62", r.line62Taxable, r.line62Vat, false);
        appendLine(sb, "55", r.line55Taxable, r.line55Vat, false);
        appendLine(sb, "54", r.line54Taxable, r.line54Vat, false);
        appendLine(sb, "61", r.line61Taxable, r.line61Vat, false);
        appendLineAmount(sb, "64", r.totalInputVat);

        // Net payable
        if (r.vatPayable >= 0) {
            appendLineAmount(sb, "65", r.vatPayable);
        } else {
            appendLineAmount(sb, "66", Math.abs(r.vatPayable));
        }

        sb.append("  </Dati>\n");

        // Annexes (PVN 1-I domestic, PVN 1-II EU goods)
        appendPvnAnnex1(sb, company, year, month);
        appendPvnAnnex2(sb, company, year, month);
        appendPvnAnnex3(sb, company, year, month);

        sb.append("</PVN1>\n");
        return sb.toString();
    }

    private void appendLine(StringBuilder sb, String nr, double taxable, double vat, boolean isOutput) {
        sb.append("    <R").append(nr).append(">\n");
        sb.append("      <Summa>").append(fmt(taxable)).append("</Summa>\n");
        sb.append("      <PVN>").append(fmt(vat)).append("</PVN>\n");
        sb.append("    </R").append(nr).append(">\n");
    }

    private void appendLineAmount(StringBuilder sb, String nr, double amount) {
        sb.append("    <R").append(nr).append(">").append(fmt(amount)).append("</R").append(nr).append(">\n");
    }

    private void appendPvnAnnex1(StringBuilder sb, Company company, int year, int month) {
        // PVN 1-I: Domestic purchases annex
        List<InvoiceIn> domestic = invoiceInRepo.findDomesticByPeriod(year, month);
        if (domestic.isEmpty()) return;
        sb.append("  <PVN1_I>\n");
        for (InvoiceIn inv : domestic) {
            sb.append("    <Ieraksts>\n");
            sb.append("      <Dok_nr>").append(escXml(safe(inv.getInvoiceNumber()))).append("</Dok_nr>\n");
            sb.append("      <Datums>").append(fmtDate(inv.getInvoiceDate())).append("</Datums>\n");
            sb.append("      <Pvn_nr>").append(safe(inv.getSupplierVatNumber())).append("</Pvn_nr>\n");
            sb.append("      <Nosauk>").append(escXml(safe(inv.getSupplierName()))).append("</Nosauk>\n");
            sb.append("      <Summa>").append(fmt(d(inv.getAmountExclVat()))).append("</Summa>\n");
            sb.append("      <PVN>").append(fmt(d(inv.getVatAmount()))).append("</PVN>\n");
            sb.append("      <Rinda>62</Rinda>\n");
            sb.append("      <Dok_veids>").append(safe(inv.getDocumentTypeCode())).append("</Dok_veids>\n");
            sb.append("    </Ieraksts>\n");
        }
        sb.append("  </PVN1_I>\n");
    }

    private void appendPvnAnnex2(StringBuilder sb, Company company, int year, int month) {
        // PVN 1-II: EU goods annex
        List<InvoiceIn> eu = invoiceInRepo.findEuByPeriod(year, month);
        if (eu.isEmpty()) return;
        sb.append("  <PVN1_II>\n");
        for (InvoiceIn inv : eu) {
            sb.append("    <Ieraksts>\n");
            sb.append("      <Dok_nr>").append(escXml(safe(inv.getInvoiceNumber()))).append("</Dok_nr>\n");
            sb.append("      <Datums>").append(fmtDate(inv.getInvoiceDate())).append("</Datums>\n");
            sb.append("      <Pvn_nr>").append(safe(inv.getSupplierVatNumber())).append("</Pvn_nr>\n");
            sb.append("      <Nosauk>").append(escXml(safe(inv.getSupplierName()))).append("</Nosauk>\n");
            sb.append("      <Valsts>").append(safe(inv.getSupplierCountry())).append("</Valsts>\n");
            sb.append("      <Summa_eur>").append(fmt(d(inv.getAmountExclVat()))).append("</Summa_eur>\n");
            sb.append("      <PVN>").append(fmt(d(inv.getVatAmount()))).append("</PVN>\n");
            sb.append("      <Rinda>55</Rinda>\n");
            sb.append("      <Dok_veids>").append(safe(inv.getDocumentTypeCode())).append("</Dok_veids>\n");
            sb.append("    </Ieraksts>\n");
        }
        sb.append("  </PVN1_II>\n");
    }

    private void appendPvnAnnex3(StringBuilder sb, Company company, int year, int month) {
        // PVN 1-III: Outgoing invoices annex
        List<InvoiceOut> out = invoiceOutRepo.findActiveByPeriod(year, month);
        if (out.isEmpty()) return;
        sb.append("  <PVN1_III>\n");
        for (InvoiceOut inv : out) {
            sb.append("    <Ieraksts>\n");
            sb.append("      <Dok_nr>").append(escXml(safe(inv.getInvoiceNumber()))).append("</Dok_nr>\n");
            sb.append("      <Datums>").append(fmtDate(inv.getIssueDate())).append("</Datums>\n");
            sb.append("      <Pvn_nr>").append(safe(inv.getClientVatNumber())).append("</Pvn_nr>\n");
            sb.append("      <Nosauk>").append(escXml(safe(inv.getClientName()))).append("</Nosauk>\n");
            sb.append("      <Summa>").append(fmt(d(inv.getAmountExclVat()))).append("</Summa>\n");
            sb.append("      <PVN>").append(fmt(d(inv.getVatAmount()))).append("</PVN>\n");
            sb.append("      <Rinda>").append(inv.getPvnLine() != null ? inv.getPvnLine() : 41).append("</Rinda>\n");
            sb.append("      <Dok_veids>").append(safe(inv.getDocumentTypeCode())).append("</Dok_veids>\n");
            sb.append("    </Ieraksts>\n");
        }
        sb.append("  </PVN1_III>\n");
    }

    // ── DDZ XML ───────────────────────────────────────────────────────────────

    public String generateDdzXml(Company company, int year, int month) {
        EdsReportService.DdzReport r = reportService.ddzReport(year, month);
        String submDate = LocalDate.now().format(DATE_FMT);
        String regNr    = safe(company.getRegNumber());
        String name     = safe(company.getName());

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<DDZ xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");
        sb.append("  <Galvene>\n");
        sb.append("    <Maks_kods>").append(regNr).append("</Maks_kods>\n");
        sb.append("    <Nosauk>").append(escXml(name)).append("</Nosauk>\n");
        sb.append("    <Gads>").append(year).append("</Gads>\n");
        sb.append("    <Menesis>").append(String.format("%02d", month)).append("</Menesis>\n");
        sb.append("    <Iesn_dat>").append(submDate).append("</Iesn_dat>\n");
        sb.append("  </Galvene>\n");
        sb.append("  <Darbinieki>\n");

        for (EdsReportService.DdzReport.Row row : r.rows) {
            sb.append("    <Darbinieks>\n");
            sb.append("      <Pers_kods>").append(safe(row.personalCode)).append("</Pers_kods>\n");
            sb.append("      <Vards_Uzvards>").append(escXml(safe(row.fullName))).append("</Vards_Uzvards>\n");
            sb.append("      <Ien_darba>").append(fmt(row.grossSalary)).append("</Ien_darba>\n");
            sb.append("      <VSAOI_DD>").append(fmt(row.vsaoiEmployer)).append("</VSAOI_DD>\n");
            sb.append("      <VSAOI_DN>").append(fmt(row.vsaoiEmployee)).append("</VSAOI_DN>\n");
            sb.append("      <IIN>").append(fmt(row.iin)).append("</IIN>\n");
            sb.append("      <Risk_nodeva>").append(fmt(row.riskDuty)).append("</Risk_nodeva>\n");
            sb.append("      <Sol_nodoklis>").append(fmt(row.solidarityTax)).append("</Sol_nodoklis>\n");
            sb.append("    </Darbinieks>\n");
        }

        sb.append("  </Darbinieki>\n");
        sb.append("  <Kopsavilkums>\n");
        sb.append("    <Kopa_ien>").append(fmt(r.totalGross)).append("</Kopa_ien>\n");
        sb.append("    <Kopa_VSAOI_DD>").append(fmt(r.totalVsaoiEmployer)).append("</Kopa_VSAOI_DD>\n");
        sb.append("    <Kopa_VSAOI_DN>").append(fmt(r.totalVsaoiEmployee)).append("</Kopa_VSAOI_DN>\n");
        sb.append("    <Kopa_IIN>").append(fmt(r.totalIin)).append("</Kopa_IIN>\n");
        sb.append("    <Kopa_risk>").append(fmt(r.totalRiskDuty)).append("</Kopa_risk>\n");
        sb.append("  </Kopsavilkums>\n");
        sb.append("</DDZ>\n");
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fmt(double v) {
        return String.format("%.2f", v);
    }

    private String fmtDate(LocalDate d) {
        if (d == null) return "";
        return d.format(DATE_FMT);
    }

    private String safe(String s) {
        return s != null ? s.trim() : "";
    }

    private double d(Double v) {
        return v != null ? v : 0.0;
    }

    private String escXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
