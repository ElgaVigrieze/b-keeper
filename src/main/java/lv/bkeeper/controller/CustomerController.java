package lv.bkeeper.controller;

import lv.bkeeper.domain.Customer;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository repo;
    private final CompanyRepository  companyRepo;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("customers",   repo.findByActiveTrueOrderByName());
        model.addAttribute("currentPage", "customers");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "customers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("customer", Customer.builder()
                .active(true).country("LV")
                .defaultPvnLine(41).defaultDocType("1")
                .defaultVatRate(21.0).build());
        model.addAttribute("currentPage", "customers");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "customers/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        repo.findById(id).ifPresent(c -> model.addAttribute("customer", c));
        model.addAttribute("currentPage", "customers");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "customers/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Customer submitted, RedirectAttributes ra) {
        LocalDateTime now = LocalDateTime.now();
        if (submitted.getId() != null) {
            repo.findById(submitted.getId()).ifPresent(existing -> {
                existing.setName(submitted.getName());
                existing.setRegNumber(submitted.getRegNumber());
                existing.setVatNumber(submitted.getVatNumber());
                existing.setCountry(submitted.getCountry());
                existing.setAddress(submitted.getAddress());
                existing.setEmail(submitted.getEmail());
                existing.setPhone(submitted.getPhone());
                existing.setDefaultPvnLine(submitted.getDefaultPvnLine());
                existing.setDefaultDocType(submitted.getDefaultDocType());
                existing.setDefaultVatRate(submitted.getDefaultVatRate());
                existing.setNotes(submitted.getNotes());
                existing.setActive(submitted.isActive());
                existing.setUpdatedAt(now);
                repo.save(existing);
            });
        } else {
            submitted.setCreatedAt(now);
            submitted.setUpdatedAt(now);
            repo.save(submitted);
        }
        ra.addFlashAttribute("success", "Klients saglabāts");
        return "redirect:/customers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        repo.deleteById(id);
        ra.addFlashAttribute("success", "Klients dzēsts");
        return "redirect:/customers";
    }
}
