package lv.bkeeper.repository;

import lv.bkeeper.domain.PayrollEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollEntryRepository extends CrudRepository<PayrollEntry, Long> {

    List<PayrollEntry> findByPeriodYearAndPeriodMonth(int year, int month);

    Optional<PayrollEntry> findByEmployeeIdAndPeriodYearAndPeriodMonth(
            long employeeId, int year, int month);

    @Query("SELECT COALESCE(SUM(gross_salary), 0) FROM payroll_entry WHERE period_year = :year AND period_month = :month")
    Double sumGrossByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(vsaoi_employer + risk_duty), 0) FROM payroll_entry WHERE period_year = :year AND period_month = :month")
    Double sumEmployerCostByPeriod(@Param("year") int year, @Param("month") int month);

    // ── Annual aggregations for UIN / Gada Pārskats ───────────────────────────

    List<PayrollEntry> findByPeriodYear(int year);

    @Query("SELECT COALESCE(SUM(gross_salary), 0) FROM payroll_entry WHERE period_year = :year")
    Double sumGrossByYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(vsaoi_employer), 0) FROM payroll_entry WHERE period_year = :year")
    Double sumVsaoiEmployerByYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(vsaoi_employee), 0) FROM payroll_entry WHERE period_year = :year")
    Double sumVsaoiEmployeeByYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(iin), 0) FROM payroll_entry WHERE period_year = :year")
    Double sumIinByYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(risk_duty), 0) FROM payroll_entry WHERE period_year = :year")
    Double sumRiskDutyByYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(net_salary), 0) FROM payroll_entry WHERE period_year = :year")
    Double sumNetSalaryByYear(@Param("year") int year);
}
