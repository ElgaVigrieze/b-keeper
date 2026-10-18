package lv.bkeeper.controller;

import lombok.RequiredArgsConstructor;
import lv.bkeeper.domain.Company;
import lv.bkeeper.repository.CompanyRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class CompanyAdvice {
    private final CompanyRepository repo;

    @ModelAttribute("company")
    public Company company() {
        return repo.findById(1).orElse(null);
    }
}
