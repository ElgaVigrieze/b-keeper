package lv.bkeeper.controller;

import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/journal")
@RequiredArgsConstructor
public class JournalController {

    private final JournalEntryRepository repo;
    private final CompanyRepository companyRepo;

    @GetMapping
    public String list(@RequestParam(required = false) Integer year,
                       @RequestParam(required = false) Integer month,
                       Model model) {
        LocalDate now = LocalDate.now();
        int y = year  != null ? year  : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        var entries = repo.findByPeriodYearAndPeriodMonthOrderByEntryDate(y, m);
        double totalIncome  = entries.stream().mapToDouble(e -> e.getIncomeAmount()  != null ? e.getIncomeAmount()  : 0).sum();
        double totalExpense = entries.stream().mapToDouble(e -> e.getExpenseAmount() != null ? e.getExpenseAmount() : 0).sum();

        model.addAttribute("entries",       entries);
        model.addAttribute("totalIncome",   totalIncome);
        model.addAttribute("totalExpense",  totalExpense);
        model.addAttribute("displayYear",   y);
        model.addAttribute("displayMonth",  m);
        model.addAttribute("months",        DashboardController.MONTHS);
        model.addAttribute("currentPage",   "journal");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "journal/list";
    }
}
