package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * A known supplier in the supplier database.
 * Used to auto-fill incoming invoice fields and avoid OCR mistakes on supplier data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("supplier")
public class Supplier {

    @Id
    private Long id;

    private String name;           // Official company name
    private String regNumber;      // Reģistrācijas numurs
    private String vatNumber;      // PVN reģ. nr. e.g. LV41203041233
    private String country;        // ISO 2-letter

    // Defaults used when creating invoices from this supplier
    private Integer defaultPvnLine;   // 62 = domestic, 55 = EU goods
    private String  defaultDocType;   // A, G, T, N
    private Double  defaultVatRate;   // 21.0, 12.0, 0.0
    private String  defaultCategory;  // GENERAL, IT, FUEL, RENT, SERVICES, MATERIALS

    /**
     * Comma-separated name fragments for fuzzy matching.
     * Ollama may return "Neste" or "Neste Latvia" or "Neste Latvija SIA" —
     * all should match the same supplier.
     * Example: "Neste,Neste Latvia,Neste Latvija"
     */
    private String aliases;

    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
