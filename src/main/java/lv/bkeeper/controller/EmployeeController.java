package lv.bkeeper.controller;

import lv.bkeeper.domain.Employee;
import lv.bkeeper.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController extends BaseCrudController <Employee, EmployeeRepository> {

    private final EmployeeRepository repo;

    @Override
    protected EmployeeRepository getRepo() {
        return repo;
    }

    @Override
    protected String getEntityName() {
        return "employee";
    }

    @Override
    protected Employee createDefaultEntityForm() {
        return Employee.builder()
                .active(true)
                .nonTaxableMin(0.0)
                .monthlyGross(0.0)
                .build();
    }

    @Override
    protected void saveEditedEntity(Employee submitted) {
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
    }

}
