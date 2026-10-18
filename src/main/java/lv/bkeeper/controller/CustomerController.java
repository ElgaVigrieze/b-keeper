package lv.bkeeper.controller;

import lv.bkeeper.domain.Customer;
import lv.bkeeper.mapper.CustomerMapper;
import lv.bkeeper.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lv.bkeeper.util.TimeUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController extends BaseCrudController<Customer, CustomerRepository> {

    private final CustomerRepository repo;
    private final CustomerMapper customerMapper;

    @Override
    protected CustomerRepository getRepo() {
        return repo;
    }

    @Override
    protected String getEntityName() {
        return "customer";
    }

    @Override
    protected Customer createDefaultEntityForm() {
        return Customer.builder()
                .active(true)
                .country("LV")
                .defaultPvnLine(41)
                .defaultDocType("1")
                .defaultVatRate(21.0)
                .build();
    }

    @Override
    protected void saveExisting(Customer submitted) {
        repo.findById(submitted.getId()).ifPresent(existing -> {
            // Copy the form-posted fields onto the persisted row.
            customerMapper.update(existing, submitted);
            existing.setUpdatedAt(TimeUtil.getNow());
            repo.save(existing);
        });
    }

    @Override
    protected void saveNew(Customer submitted) {
        submitted.setCreatedAt(TimeUtil.getNow());
        submitted.setUpdatedAt(TimeUtil.getNow());
        repo.save(submitted);

    }
}
