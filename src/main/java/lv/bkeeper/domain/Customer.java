package lv.bkeeper.domain;

import lv.bkeeper.controller.Identifiable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * A known customer in the customer database.
 * Used to auto-fill outgoing invoice fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("customer")
public class Customer implements Identifiable {

    @Id
    private Long id;

    private String name;        // Official company name
    private String regNumber;   // Reģistrācijas numurs
    private String vatNumber;   // PVN reģ. nr. e.g. LV41203041233
    private String country;     // ISO 2-letter
    private String address;
    private String email;
    private String phone;

    // Defaults applied when creating outgoing invoices
    private Integer defaultPvnLine;  // 41 = domestic, 45 = EU goods
    private String  defaultDocType;  // 1 = rēķins, X = kases čeks
    private Double  defaultVatRate;  // 21.0, 12.0, 0.0

    private String notes;
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
