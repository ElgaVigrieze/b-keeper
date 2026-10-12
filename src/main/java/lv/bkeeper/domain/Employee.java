package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An employee of the company.
 */
@Data
@Builder
@Table("employee")
public class Employee {

    @Id
    private Long id;

    /** Personas kods, e.g. "030284-11631" */
    private String personalCode;

    private String firstName;
    private String lastName;
    private String position;

    /** Monthly gross salary in EUR */
    private Double monthlyGross;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate employmentStart;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate employmentEnd;     // null = currently employed

    /** True = algas nodokļu grāmatiņa iesniegta – entitles to non-taxable minimum */
    private boolean taxBookSubmitted;

    /** Monthly non-taxable minimum in EUR (0 if no tax book) */
    private Double nonTaxableMin;

    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
