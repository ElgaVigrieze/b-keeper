package lv.bkeeper.controller;

import lv.bkeeper.domain.FixedAsset;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.FixedAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final FixedAssetRepository repo;
    private final CompanyRepository companyRepo;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("assets",      repo.findAll());
        model.addAttribute("currentPage", "assets");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "assets/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("asset", FixedAsset.builder()
                .usefulLifeYears(5)
                .depreciationMethod("STRAIGHT_LINE")
                .active(true)
                .accumulatedDepreciation(0.0)
                .build());
        model.addAttribute("currentPage", "assets");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "assets/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        repo.findById(id).ifPresent(a -> model.addAttribute("asset", a));
        model.addAttribute("currentPage", "assets");
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));
        return "assets/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute FixedAsset asset, RedirectAttributes ra) {
        if (asset.getAcquisitionCost() != null && asset.getUsefulLifeYears() != null && asset.getUsefulLifeYears() > 0) {
            asset.setAnnualDepreciation(Math.round(asset.getAcquisitionCost() / asset.getUsefulLifeYears() * 100) / 100.0);
            if (asset.getAccumulatedDepreciation() == null) asset.setAccumulatedDepreciation(0.0);
            asset.setBookValue(asset.getAcquisitionCost() - asset.getAccumulatedDepreciation());
        }
        LocalDateTime now = LocalDateTime.now();
        if (asset.getId() == null) asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        repo.save(asset);
        ra.addFlashAttribute("success", "Pamatlīdzeklis saglabāts");
        return "redirect:/assets";
    }
}
