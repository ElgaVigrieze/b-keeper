package lv.bkeeper.controller;

import lv.bkeeper.domain.Company;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.EdsSubmissionRepository;
import lv.bkeeper.repository.JournalEntryRepository;
import lv.bkeeper.service.EdsReportService;
import lv.bkeeper.service.EdsXmlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/eds")
@RequiredArgsConstructor
public class EdsController {

    private final EdsSubmissionRepository repo;
    private final CompanyRepository       companyRepo;
    private final EdsReportService        reportService;
    private final EdsXmlService           xmlService;
    private final JournalEntryRepository  journalRepo;

    // ── PVN declaration ───────────────────────────────────────────────────────

    @GetMapping("/pvn")
    public String pvn(@RequestParam(required = false) Integer year,
                      @RequestParam(required = false) Integer month,
                      Model model) {
        LocalDate now = LocalDate.now();
        int y = year  != null ? year  : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        EdsReportService.PvnReport pvn = reportService.pvnReport(y, m);
        LocalDate periodEnd = LocalDate.of(y, m, 1)
                .withDayOfMonth(LocalDate.of(y, m, 1).lengthOfMonth());

        model.addAttribute("pvn",          pvn);
        model.addAttribute("displayYear",  y);
        model.addAttribute("displayMonth", m);
        model.addAttribute("periodStart",  String.format("01.%02d.%d", m, y));
        model.addAttribute("periodEnd",    String.format("%02d.%02d.%d", periodEnd.getDayOfMonth(), m, y));
        model.addAttribute("monthPadded",  String.format("%02d", m));
        model.addAttribute("months",       DashboardController.MONTHS);
        model.addAttribute("monthName",    DashboardController.MONTHS.get(m));
        model.addAttribute("submissions",  repo.findByPeriodYearOrderByPeriodMonth(y));
        model.addAttribute("currentPage",  "eds-pvn");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "eds/pvn";
    }

    @GetMapping("/pvn/xml")
    public ResponseEntity<byte[]> pvnXml(@RequestParam int year, @RequestParam int month) {
        Optional<Company> co = companyRepo.findById(1);
        if (co.isEmpty()) return ResponseEntity.notFound().build();
        String xml   = xmlService.generatePvnXml(co.get(), year, month);
        String fname = String.format("PVN_%d_%02d.xml", year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.getBytes(StandardCharsets.UTF_8));
    }

    // ── DDZ report ────────────────────────────────────────────────────────────

    @GetMapping("/ddz")
    public String ddz(@RequestParam(required = false) Integer year,
                      @RequestParam(required = false) Integer month,
                      Model model) {
        LocalDate now = LocalDate.now();
        int y = year  != null ? year  : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        EdsReportService.DdzReport ddz = reportService.ddzReport(y, m);

        model.addAttribute("ddz",          ddz);
        model.addAttribute("displayYear",  y);
        model.addAttribute("displayMonth", m);
        model.addAttribute("monthPadded",  String.format("%02d", m));
        model.addAttribute("months",       DashboardController.MONTHS);
        model.addAttribute("monthName",    DashboardController.MONTHS.get(m));
        model.addAttribute("submissions",  repo.findByPeriodYearOrderByPeriodMonth(y));
        model.addAttribute("currentPage",  "eds-ddz");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "eds/ddz";
    }

    @GetMapping("/ddz/xml")
    public ResponseEntity<byte[]> ddzXml(@RequestParam int year, @RequestParam int month) {
        Optional<Company> co = companyRepo.findById(1);
        if (co.isEmpty()) return ResponseEntity.notFound().build();
        String xml   = xmlService.generateDdzXml(co.get(), year, month);
        String fname = String.format("DDZ_%d_%02d.xml", year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.getBytes(StandardCharsets.UTF_8));
    }

    // ── UIN ───────────────────────────────────────────────────────────────────

    @GetMapping("/uin")
    public String uin(@RequestParam(required = false) Integer year, Model model) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear() - 1;

        double yearIncome  = journalRepo.sumIncomeByYear(y);
        double yearExpense = journalRepo.sumExpenseByYear(y);
        double profit      = round(yearIncome - yearExpense);

        model.addAttribute("displayYear",  y);
        model.addAttribute("yearIncome",   round(yearIncome));
        model.addAttribute("yearExpense",  round(yearExpense));
        model.addAttribute("profit",       profit);
        model.addAttribute("submissions",  repo.findByPeriodYearOrderByPeriodMonth(y));
        model.addAttribute("currentPage",  "eds-uin");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "eds/uin";
    }

    // ── Annual report ─────────────────────────────────────────────────────────

    @GetMapping("/annual")
    public String annual(@RequestParam(required = false) Integer year, Model model) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear() - 1;

        double yearIncome  = journalRepo.sumIncomeByYear(y);
        double yearExpense = journalRepo.sumExpenseByYear(y);

        double[] monthlyIncome  = new double[13];
        double[] monthlyExpense = new double[13];
        for (int m = 1; m <= 12; m++) {
            monthlyIncome[m]  = round(journalRepo.sumIncomeByPeriod(y, m));
            monthlyExpense[m] = round(journalRepo.sumExpenseByPeriod(y, m));
        }

        model.addAttribute("displayYear",    y);
        model.addAttribute("yearIncome",     round(yearIncome));
        model.addAttribute("yearExpense",    round(yearExpense));
        model.addAttribute("profit",         round(yearIncome - yearExpense));
        model.addAttribute("monthlyIncome",  monthlyIncome);
        model.addAttribute("monthlyExpense", monthlyExpense);
        model.addAttribute("months",         DashboardController.MONTHS);
        model.addAttribute("submissions",    repo.findByPeriodYearOrderByPeriodMonth(y));
        model.addAttribute("currentPage",    "eds-annual");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "eds/annual";
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
