package lv.bkeeper.controller;

import lv.bkeeper.domain.Employee;
import lv.bkeeper.mapper.EmployeeMapper;
import lv.bkeeper.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lv.bkeeper.util.TimeUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController extends BaseCrudController <Employee, EmployeeRepository> {

    private final EmployeeRepository repo;
    private final EmployeeMapper employeeMapper;

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
    protected void saveExisting(Employee submitted) {
        repo.findById(submitted.getId()).ifPresent(existing -> {
            // Copy the form-posted fields onto the persisted row.
            employeeMapper.update(existing, submitted);
            existing.setUpdatedAt(TimeUtil.getNow());
            repo.save(existing);
        });
    }

    @Override
    protected void saveNew(Employee submitted) {
        submitted.setCreatedAt(TimeUtil.getNow());
        submitted.setUpdatedAt(TimeUtil.getNow());
        repo.save(submitted);
    }

}
