package lv.bkeeper.controller;

import lv.bkeeper.domain.Supplier;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierRepository repo;
    private final CompanyRepository  companyRepo;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("suppliers",   repo.findByActiveTrueOrderByName());
        model.addAttribute("currentPage", "suppliers");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "suppliers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("supplier", Supplier.builder()
                .active(true).country("LV").defaultPvnLine(62)
                .defaultDocType("A").defaultVatRate(21.0)
                .defaultCategory("GENERAL").build());
        model.addAttribute("currentPage", "suppliers");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "suppliers/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        repo.findById(id).ifPresent(s -> model.addAttribute("supplier", s));
        model.addAttribute("currentPage", "suppliers");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "suppliers/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Supplier submitted, RedirectAttributes ra) {
        LocalDateTime now = LocalDateTime.now();
        if (submitted.getId() != null) {
            repo.findById(submitted.getId()).ifPresent(existing -> {
                existing.setName(submitted.getName());
                existing.setRegNumber(submitted.getRegNumber());
                existing.setVatNumber(submitted.getVatNumber());
                existing.setCountry(submitted.getCountry());
                existing.setDefaultPvnLine(submitted.getDefaultPvnLine());
                existing.setDefaultDocType(submitted.getDefaultDocType());
                existing.setDefaultVatRate(submitted.getDefaultVatRate());
                existing.setDefaultCategory(submitted.getDefaultCategory());
                existing.setAliases(submitted.getAliases());
                existing.setActive(submitted.isActive());
                existing.setUpdatedAt(now);
                repo.save(existing);
            });
        } else {
            submitted.setCreatedAt(now);
            submitted.setUpdatedAt(now);
            repo.save(submitted);
        }
        ra.addFlashAttribute("success", "Piegādātājs saglabāts");
        return "redirect:/suppliers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        repo.deleteById(id);
        ra.addFlashAttribute("success", "Piegādātājs dzēsts");
        return "redirect:/suppliers";
    }
}
