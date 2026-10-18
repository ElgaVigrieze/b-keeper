package lv.bkeeper.controller;

import lv.bkeeper.domain.FixedAsset;
import lv.bkeeper.mapper.FixedAssetMapper;
import lv.bkeeper.repository.FixedAssetRepository;
import lombok.RequiredArgsConstructor;
import lv.bkeeper.util.TimeUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController extends BaseCrudController<FixedAsset, FixedAssetRepository> {

    private final FixedAssetRepository repo;
    private final FixedAssetMapper assetMapper;

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
    protected void saveExisting(FixedAsset submitted) {
        repo.findById(submitted.getId()).ifPresent(existing -> {
            // Copy the editable form fields onto the persisted row.
            assetMapper.update(existing, submitted);
            // Recompute derived fields — an edit can change cost / useful life.
            recomputeDerivedFields(existing);
            existing.setUpdatedAt(TimeUtil.getNow());
            repo.save(existing);
        });
    }

    @Override
    protected void saveNew(FixedAsset submitted) {
        recomputeDerivedFields(submitted);
        if (submitted.getId() == null) {
            submitted.setCreatedAt(TimeUtil.getNow());
        }
        submitted.setUpdatedAt(TimeUtil.getNow());
        repo.save(submitted);
    }

    /**
     * annualDepreciation = acquisitionCost / usefulLifeYears (2 dp);
     * bookValue          = acquisitionCost - accumulatedDepreciation.
     * Only applied when the inputs are usable; otherwise the values are left as-is.
     */
    private void recomputeDerivedFields(FixedAsset asset) {
        Double cost = asset.getAcquisitionCost();
        Integer years = asset.getUsefulLifeYears();
        if (cost == null || years == null || years <= 0) {
            return;
        }
        asset.setAnnualDepreciation(Math.round(cost / years * 100) / 100.0);
        if (asset.getAccumulatedDepreciation() == null) {
            asset.setAccumulatedDepreciation(0.0);
        }
        asset.setBookValue(cost - asset.getAccumulatedDepreciation());
    }

}
