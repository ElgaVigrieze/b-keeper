package lv.bkeeper.repository;

import lv.bkeeper.domain.FixedAsset;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface FixedAssetRepository extends CrudRepository<FixedAsset, Long> {
    List<FixedAsset> findByActiveTrue();
}
