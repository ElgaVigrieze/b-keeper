package lv.bkeeper.repository;

import lv.bkeeper.domain.InvoiceOut;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceOutRepository extends CrudRepository<InvoiceOut, Long> {

    List<InvoiceOut> findByPeriodYearAndPeriodMonth(int year, int month);

    @Query("SELECT * FROM invoice_out WHERE period_year = :year AND period_month = :month AND status != 'CANCELLED'")
    List<InvoiceOut> findActiveByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(amount_excl_vat), 0) FROM invoice_out WHERE period_year = :year AND period_month = :month AND status != 'CANCELLED'")
    Double sumTurnoverByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(vat_amount), 0) FROM invoice_out WHERE period_year = :year AND period_month = :month AND status != 'CANCELLED'")
    Double sumOutputVatByPeriod(@Param("year") int year, @Param("month") int month);

    List<InvoiceOut> findByStatus(String status);
}
