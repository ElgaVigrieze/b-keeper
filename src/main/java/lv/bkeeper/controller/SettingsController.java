package lv.bkeeper.controller;

import lv.bkeeper.domain.Company;
import lv.bkeeper.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final CompanyRepository repo;

    @GetMapping
    public String form(Model model) {
        Company company = repo.findById(1).orElseGet(() ->
                Company.builder()
                        .id(1)
                        .name("")
                        .regNumber("")
                        .fiscalYearStart("01-01")
                        .accountingType("SIMPLE")
                        .createdAt(LocalDateTime.now())
                        .build());
        model.addAttribute("company", company);
        model.addAttribute("currentPage", "settings");
        return "settings/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Company submitted, RedirectAttributes ra) {
        // Load the existing row to preserve fields not on the form
        Company existing = repo.findById(1).orElseGet(() ->
                Company.builder()
                        .id(1)
                        .fiscalYearStart("01-01")
                        .accountingType("SIMPLE")
                        .createdAt(LocalDateTime.now())
                        .build());

        // Apply only the fields that the form submits
        existing.setName(submitted.getName());
        existing.setRegNumber(submitted.getRegNumber());
        existing.setVatNumber(submitted.getVatNumber());
        existing.setLegalAddress(submitted.getLegalAddress());
        existing.setBankAccount(submitted.getBankAccount());
        existing.setEmail(submitted.getEmail());
        existing.setPhone(submitted.getPhone());

        repo.save(existing);
        ra.addFlashAttribute("success", "Iestatījumi saglabāti");
        return "redirect:/settings";
    }
}
