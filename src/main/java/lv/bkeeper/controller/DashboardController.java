package lv.bkeeper.controller;

import lv.bkeeper.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final CompanyRepository companyRepository;
    private final InvoiceOutRepository invoiceOutRepository;
    private final InvoiceInRepository invoiceInRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PayrollEntryRepository payrollEntryRepository;
    private final EdsSubmissionRepository edsSubmissionRepository;

    public static final Map<Integer, String> MONTHS = new LinkedHashMap<>();

    static {
        MONTHS.put(1,  "Janvāris");
        MONTHS.put(2,  "Februāris");
        MONTHS.put(3,  "Marts");
        MONTHS.put(4,  "Aprīlis");
        MONTHS.put(5,  "Maijs");
        MONTHS.put(6,  "Jūnijs");
        MONTHS.put(7,  "Jūlijs");
        MONTHS.put(8,  "Augusts");
        MONTHS.put(9,  "Septembris");
        MONTHS.put(10, "Oktobris");
        MONTHS.put(11, "Novembris");
        MONTHS.put(12, "Decembris");
    }

    @GetMapping("/")
    public String dashboard(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        LocalDate now = LocalDate.now();
        int displayYear  = year  != null ? year  : now.getYear();
        int displayMonth = month != null ? month : now.getMonthValue();

        companyRepository.findById(1).ifPresent(c -> model.addAttribute("company", c));

        double turnover     = invoiceOutRepository.sumTurnoverByPeriod(displayYear, displayMonth);
        double outputVat    = invoiceOutRepository.sumOutputVatByPeriod(displayYear, displayMonth);
        double inputVat     = invoiceInRepository.sumDeductibleVatByPeriod(displayYear, displayMonth);
        double vatPayable   = outputVat - inputVat;
        double grossSalary  = payrollEntryRepository.sumGrossByPeriod(displayYear, displayMonth);
        double employerCost = payrollEntryRepository.sumEmployerCostByPeriod(displayYear, displayMonth);
        double income       = journalEntryRepository.sumIncomeByPeriod(displayYear, displayMonth);
        double expense      = journalEntryRepository.sumExpenseByPeriod(displayYear, displayMonth);
        double yearIncome   = journalEntryRepository.sumIncomeByYear(displayYear);
        double yearExpense  = journalEntryRepository.sumExpenseByYear(displayYear);

        var pendingSubmissions = edsSubmissionRepository.findByStatus("READY");

        model.addAttribute("displayYear",        displayYear);
        model.addAttribute("displayMonth",       displayMonth);
        model.addAttribute("months",             MONTHS);
        model.addAttribute("turnover",           turnover);
        model.addAttribute("outputVat",          outputVat);
        model.addAttribute("inputVat",           inputVat);
        model.addAttribute("vatPayable",         vatPayable);
        model.addAttribute("grossSalary",        grossSalary);
        model.addAttribute("employerCost",       employerCost);
        model.addAttribute("income",             income);
        model.addAttribute("expense",            expense);
        model.addAttribute("yearIncome",         yearIncome);
        model.addAttribute("yearExpense",        yearExpense);
        model.addAttribute("pendingSubmissions", pendingSubmissions);

        return "dashboard";
    }
}
