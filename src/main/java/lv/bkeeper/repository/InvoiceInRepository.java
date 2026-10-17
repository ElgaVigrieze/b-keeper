package lv.bkeeper.repository;

import lv.bkeeper.domain.InvoiceIn;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceInRepository extends CrudRepository<InvoiceIn, Long> {

    List<InvoiceIn> findByPeriodYearAndPeriodMonth(int year, int month);

    @Query("SELECT * FROM invoice_in WHERE period_year = :year AND period_month = :month ORDER BY invoice_date")
    List<InvoiceIn> findByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("SELECT * FROM invoice_in WHERE period_year = :year AND period_month = :month AND pvn_line IN (62) ORDER BY invoice_date")
    List<InvoiceIn> findDomesticByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("SELECT * FROM invoice_in WHERE period_year = :year AND period_month = :month AND pvn_line IN (55) ORDER BY invoice_date")
    List<InvoiceIn> findEuByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(vat_amount), 0) FROM invoice_in WHERE period_year = :year AND period_month = :month AND deductible_vat = 1")
    Double sumDeductibleVatByPeriod(@Param("year") int year, @Param("month") int month);
}
