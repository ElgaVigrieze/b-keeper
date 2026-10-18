package lv.bkeeper.controller;

import lv.bkeeper.domain.Supplier;
import lv.bkeeper.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController extends BaseCrudController<Supplier, SupplierRepository> {

    private final SupplierRepository repo;

    @Override
    protected SupplierRepository getRepo() {
        return repo;
    }

    @Override
    protected String getEntityName() {
        return "supplier";
    }

    @Override
    protected Supplier createDefaultEntityForm() {
        return Supplier.builder()
                .active(true).country("LV").defaultPvnLine(62)
                .defaultDocType("A").defaultVatRate(21.0)
                .defaultCategory("GENERAL").build();
    }

    @Override
    protected void saveEditedEntity(Supplier submitted) {
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
    }
}
