package lv.bkeeper.controller;

import lv.bkeeper.domain.Customer;
import lv.bkeeper.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController extends BaseCrudController<Customer, CustomerRepository> {

    private final CustomerRepository repo;

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
    protected void saveEditedEntity(Customer submitted) {
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
    }
}
