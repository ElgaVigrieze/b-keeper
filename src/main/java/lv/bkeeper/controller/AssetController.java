package lv.bkeeper.controller;

import lv.bkeeper.domain.FixedAsset;
import lv.bkeeper.repository.FixedAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController extends BaseCrudController<FixedAsset, FixedAssetRepository> {

    private final FixedAssetRepository repo;

    @Override
    protected FixedAssetRepository getRepo() {
        return repo;
    }

    @Override
    protected String getEntityName() {
        return "asset";
    }

    @Override
    protected FixedAsset createDefaultEntityForm() {
        return FixedAsset.builder()
                .usefulLifeYears(5)
                .depreciationMethod("STRAIGHT_LINE")
                .active(true)
                .accumulatedDepreciation(0.0)
                .build();
    }

    @Override
    protected void saveEditedEntity(FixedAsset submitted) {
        LocalDateTime now = LocalDateTime.now();
        if (submitted.getAcquisitionCost() != null && submitted.getUsefulLifeYears() != null && submitted.getUsefulLifeYears() > 0) {
            submitted.setAnnualDepreciation(Math.round(submitted.getAcquisitionCost() / submitted.getUsefulLifeYears() * 100) / 100.0);
            if (submitted.getAccumulatedDepreciation() == null) submitted.setAccumulatedDepreciation(0.0);
            submitted.setBookValue(submitted.getAcquisitionCost() - submitted.getAccumulatedDepreciation());
        }
        if (submitted.getId() == null) submitted.setCreatedAt(now);
        submitted.setUpdatedAt(now);
        repo.save(submitted);
    }

}
