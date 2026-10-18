package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pamatlīdzeklis (fixed asset).
 * Equipment, computers, furniture — cost > €430, useful life > 1 year.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("fixed_asset")
public class FixedAsset {

    @Id
    private Long id;

    private String name;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate acquisitionDate;
    private Double acquisitionCost;
    private Integer usefulLifeYears;

    /** STRAIGHT_LINE is the only method supported in simplified accounting */
    private String depreciationMethod;

    private Double annualDepreciation;       // acquisitionCost / usefulLifeYears
    private Double accumulatedDepreciation;  // sum of all posted depreciation
    private Double bookValue;               // acquisitionCost - accumulatedDepreciation

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate disposalDate;
    private Double disposalValue;

    private Long invoiceInId;   // the purchase invoice
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
