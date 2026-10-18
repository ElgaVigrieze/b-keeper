package lv.bkeeper.mapper;

import lv.bkeeper.domain.FixedAsset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Copies the fixed-asset form fields onto the persisted row when editing.
 *
 * <p>The asset form only posts {@code id, name, acquisitionDate, acquisitionCost,
 * usefulLifeYears, active}. Everything else — {@code depreciationMethod},
 * {@code accumulatedDepreciation}, {@code annualDepreciation}, {@code bookValue},
 * {@code disposalDate}, {@code disposalValue}, {@code invoiceInId},
 * {@code createdAt}, {@code updatedAt} — is deliberately {@code ignore}d so an edit
 * cannot overwrite those stored values with {@code null}.
 *
 * <p>{@code nullValuePropertyMappingStrategy = IGNORE} additionally means a posted field
 * that is empty (source {@code null}) leaves the persisted value untouched rather than
 * blanking it.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface FixedAssetMapper {

    /**
     * Copies only the form-posted fields from {@code submitted} onto the persisted
     * {@code existing}. The caller owns derived-field recomputation, {@code updatedAt}
     * and the repository save.
     */
    @Mapping(target = "depreciationMethod", ignore = true)
    @Mapping(target = "accumulatedDepreciation", ignore = true)
    @Mapping(target = "annualDepreciation", ignore = true)
    @Mapping(target = "bookValue", ignore = true)
    @Mapping(target = "disposalDate", ignore = true)
    @Mapping(target = "disposalValue", ignore = true)
    @Mapping(target = "invoiceInId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(@MappingTarget FixedAsset existing, FixedAsset submitted);
}
