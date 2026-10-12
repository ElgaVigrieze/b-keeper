package lv.bkeeper.controller;

import lv.bkeeper.domain.Employee;
import lv.bkeeper.domain.PayrollEntry;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.EmployeeRepository;
import lv.bkeeper.repository.PayrollEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollEntryRepository payrollRepo;
    private final EmployeeRepository     employeeRepo;
    private final CompanyRepository      companyRepo;

    // Latvian tax rates 2026
    private static final double VSAOI_EMPLOYER = 0.2359;
    private static final double VSAOI_EMPLOYEE = 0.1050;
    private static final double RISK_DUTY      = 0.0046;
    private static final double IIN_RATE       = 0.23;

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public String list(@RequestParam(required = false) Integer year,
                       @RequestParam(required = false) Integer month,
                       Model model) {
        LocalDate now = LocalDate.now();
        int y = year  != null ? year  : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        List<PayrollEntry> entries = payrollRepo.findByPeriodYearAndPeriodMonth(y, m);

        // Calculate totals in controller — Thymeleaf can't handle stream lambdas
        double totalGross       = entries.stream().mapToDouble(e -> e.getGrossSalary()    != null ? e.getGrossSalary()    : 0).sum();
        double totalVsaoiEmp    = entries.stream().mapToDouble(e -> e.getVsaoiEmployer()  != null ? e.getVsaoiEmployer()  : 0).sum();
        double totalRisk        = entries.stream().mapToDouble(e -> e.getRiskDuty()       != null ? e.getRiskDuty()       : 0).sum();
        double totalNet         = entries.stream().mapToDouble(e -> e.getNetSalary()      != null ? e.getNetSalary()      : 0).sum();
        double totalEmployerCost = totalGross + totalVsaoiEmp + totalRisk;

        model.addAttribute("entries",           entries);
        model.addAttribute("employees",         employeeRepo.findAll());
        model.addAttribute("totalGross",         totalGross);
        model.addAttribute("totalVsaoiEmployer", totalVsaoiEmp);
        model.addAttribute("totalEmployerCost",  totalEmployerCost);
        model.addAttribute("totalNet",           totalNet);
        model.addAttribute("displayYear",  y);
        model.addAttribute("displayMonth", m);
        model.addAttribute("months",       DashboardController.MONTHS);
        model.addAttribute("currentPage",  "payroll");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "payroll/list";
    }

    // ── Calculate all active employees for a period ───────────────────────────

    @PostMapping("/calculate")
    public String calculate(@RequestParam int year,
                            @RequestParam int month,
                            RedirectAttributes ra) {
        List<Employee> employees = employeeRepo.findByActiveTrue();
        int created = 0;
        int skipped = 0;

        for (Employee emp : employees) {
            if (payrollRepo.findByEmployeeIdAndPeriodYearAndPeriodMonth(emp.getId(), year, month).isPresent()) {
                skipped++;
                continue;
            }
            payrollRepo.save(buildEntry(emp, year, month));
            created++;
        }

        ra.addFlashAttribute("success",
                "Aprēķināts " + created + " darbiniekiem" +
                (skipped > 0 ? " (" + skipped + " jau bija)" : ""));
        return "redirect:/payroll?year=" + year + "&month=" + month;
    }

    // ── Recalculate one entry (delete + recreate) ─────────────────────────────

    @PostMapping("/{id}/recalculate")
    public String recalculate(@PathVariable Long id, RedirectAttributes ra) {
        payrollRepo.findById(id).ifPresent(existing -> {
            employeeRepo.findById(existing.getEmployeeId()).ifPresent(emp -> {
                payrollRepo.deleteById(id);
                payrollRepo.save(buildEntry(emp, existing.getPeriodYear(), existing.getPeriodMonth()));
            });
        });
        ra.addFlashAttribute("success", "Alga pāraprēķināta");
        return "redirect:/payroll";
    }

    // ── Recalculate ALL entries for a period ──────────────────────────────────

    @PostMapping("/recalculate-all")
    public String recalculateAll(@RequestParam int year,
                                 @RequestParam int month,
                                 RedirectAttributes ra) {
        List<PayrollEntry> existing = payrollRepo.findByPeriodYearAndPeriodMonth(year, month);
        for (PayrollEntry entry : existing) {
            employeeRepo.findById(entry.getEmployeeId()).ifPresent(emp -> {
                payrollRepo.deleteById(entry.getId());
                payrollRepo.save(buildEntry(emp, year, month));
            });
        }
        ra.addFlashAttribute("success", "Visas algas pāraprēķinātas");
        return "redirect:/payroll?year=" + year + "&month=" + month;
    }

    // ── Delete one entry ──────────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        payrollRepo.deleteById(id);
        ra.addFlashAttribute("success", "Ieraksts dzēsts");
        return "redirect:/payroll";
    }

    // ── Save (manual override) ────────────────────────────────────────────────

    @PostMapping("/save")
    public String save(@ModelAttribute PayrollEntry entry, RedirectAttributes ra) {
        LocalDateTime now = LocalDateTime.now();
        if (entry.getId() != null) {
            payrollRepo.findById(entry.getId()).ifPresent(existing -> {
                existing.setGrossSalary(entry.getGrossSalary());
                existing.setVsaoiEmployer(entry.getVsaoiEmployer());
                existing.setVsaoiEmployee(entry.getVsaoiEmployee());
                existing.setIin(entry.getIin());
                existing.setRiskDuty(entry.getRiskDuty());
                existing.setNetSalary(entry.getNetSalary());
                existing.setHoursWorked(entry.getHoursWorked());
                existing.setUpdatedAt(now);
                payrollRepo.save(existing);
            });
        } else {
            entry.setCreatedAt(now);
            entry.setUpdatedAt(now);
            payrollRepo.save(entry);
        }
        ra.addFlashAttribute("success", "Ieraksts saglabāts");
        return "redirect:/payroll?year=" + entry.getPeriodYear() + "&month=" + entry.getPeriodMonth();
    }

    // ── Calculation logic ─────────────────────────────────────────────────────

    private PayrollEntry buildEntry(Employee emp, int year, int month) {
        double gross       = emp.getMonthlyGross();
        double vsaoiEmp    = round(gross * VSAOI_EMPLOYER);
        double vsaoiWorker = round(gross * VSAOI_EMPLOYEE);
        double risk        = round(gross * RISK_DUTY);
        double nonTaxable  = emp.getNonTaxableMin() != null ? emp.getNonTaxableMin() : 0.0;
        double taxBase     = Math.max(0, gross - vsaoiWorker - nonTaxable);
        double iin         = round(taxBase * IIN_RATE);
        double net         = round(gross - vsaoiWorker - iin - risk);

        LocalDateTime now = LocalDateTime.now();
        return PayrollEntry.builder()
                .employeeId(emp.getId())
                .periodYear(year)
                .periodMonth(month)
                .grossSalary(gross)
                .vsaoiEmployer(vsaoiEmp)
                .vsaoiEmployee(vsaoiWorker)
                .riskDuty(risk)
                .iin(iin)
                .solidarityTax(0.0)
                .netSalary(net)
                .salaryCorrection(0.0)
                .vsaoiCorrection(0.0)
                .pensionCorrection(0.0)
                .solidarityCorrection(0.0)
                .hoursWorked(0)
                .paidInCash(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
