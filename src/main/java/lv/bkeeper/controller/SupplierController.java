package lv.bkeeper.controller;

import lv.bkeeper.domain.Supplier;
import lv.bkeeper.mapper.SupplierMapper;
import lv.bkeeper.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lv.bkeeper.util.TimeUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController extends BaseCrudController<Supplier, SupplierRepository> {

    private final SupplierRepository repo;
    private final SupplierMapper supplierMapper;

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
    protected void saveExisting(Supplier submitted) {
        repo.findById(submitted.getId()).ifPresent(existing -> {
            // Copy the form-posted fields onto the persisted row.
            supplierMapper.update(existing, submitted);
            existing.setUpdatedAt(TimeUtil.getNow());
            repo.save(existing);
        });
    }

    @Override
    protected void saveNew(Supplier submitted) {
        submitted.setCreatedAt(TimeUtil.getNow());
        submitted.setUpdatedAt(TimeUtil.getNow());
        repo.save(submitted);
    }
}
