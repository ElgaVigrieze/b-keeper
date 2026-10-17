package lv.bkeeper.repository;

import lv.bkeeper.domain.FixedAsset;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FixedAssetRepository extends CrudRepository<FixedAsset, Long> {
    List<FixedAsset> findByActiveTrue();

    /**
     * Find assets that were active during a specific month.
     * An asset is active in a month if:
     *   - acquisition_date <= last day of month
     *   - AND (disposal_date IS NULL OR disposal_date >= first day of month)
     */
    @Query("""
        SELECT * FROM fixed_asset 
        WHERE acquisition_date <= :periodEnd
          AND (disposal_date IS NULL OR disposal_date >= :periodStart)
          AND active = 1
        """)
    List<FixedAsset> findActiveInPeriod(@Param("periodStart") String periodStart, 
                                         @Param("periodEnd") String periodEnd);

    @Query("SELECT COALESCE(SUM(acquisition_cost), 0) FROM fixed_asset WHERE active = 1")
    Double sumAcquisitionCostActive();

    @Query("SELECT COALESCE(SUM(accumulated_depreciation), 0) FROM fixed_asset WHERE active = 1")
    Double sumAccumulatedDepreciationActive();

    @Query("SELECT COALESCE(SUM(book_value), 0) FROM fixed_asset WHERE active = 1")
    Double sumBookValueActive();

    @Query("SELECT COALESCE(SUM(annual_depreciation), 0) FROM fixed_asset WHERE active = 1")
    Double sumAnnualDepreciationActive();
}