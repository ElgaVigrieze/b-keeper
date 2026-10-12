package lv.bkeeper.controller;

import lv.bkeeper.domain.Employee;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeRepository repo;
    private final CompanyRepository companyRepo;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("employees",   repo.findAll());
        model.addAttribute("currentPage", "employees");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "employees/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("employee", Employee.builder()
                .active(true)
                .nonTaxableMin(0.0)
                .monthlyGross(0.0)
                .build());
        model.addAttribute("currentPage", "employees");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "employees/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        repo.findById(id).ifPresent(e -> model.addAttribute("employee", e));
        model.addAttribute("currentPage", "employees");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "employees/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Employee submitted, RedirectAttributes ra) {
        LocalDateTime now = LocalDateTime.now();

        if (submitted.getId() != null) {
            // Update: load existing to preserve createdAt
            repo.findById(submitted.getId()).ifPresent(existing -> {
                existing.setPersonalCode(submitted.getPersonalCode());
                existing.setFirstName(submitted.getFirstName());
                existing.setLastName(submitted.getLastName());
                existing.setPosition(submitted.getPosition());
                existing.setMonthlyGross(submitted.getMonthlyGross());
                existing.setNonTaxableMin(submitted.getNonTaxableMin());
                existing.setEmploymentStart(submitted.getEmploymentStart());
                existing.setEmploymentEnd(submitted.getEmploymentEnd());
                existing.setTaxBookSubmitted(submitted.isTaxBookSubmitted());
                existing.setActive(submitted.isActive());
                existing.setUpdatedAt(now);
                repo.save(existing);
            });
        } else {
            // Insert: set both timestamps
            submitted.setCreatedAt(now);
            submitted.setUpdatedAt(now);
            repo.save(submitted);
        }

        ra.addFlashAttribute("success", "Darbinieks saglabāts");
        return "redirect:/employees";
    }
}
