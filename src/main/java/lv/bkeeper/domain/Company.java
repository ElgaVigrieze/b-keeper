package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The company using the app. Single row enforced at DB level.
 */
@Data
@Builder
@Table("company")
public class Company {

    @Id
    private Integer id;

    private String name;
    private String regNumber;
    private String vatNumber;
    private String legalAddress;
    private String bankAccount;
    private String email;
    private String phone;

    /** Fiscal year start as MM-DD, e.g. "01-01" */
    private String fiscalYearStart;

    /** SIMPLE = vienkāršais ieraksts | DOUBLE = divkāršais ieraksts */
    private String accountingType;

    private LocalDateTime createdAt;
}
