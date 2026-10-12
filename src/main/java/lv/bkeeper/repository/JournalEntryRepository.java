package lv.bkeeper.repository;

import lv.bkeeper.domain.JournalEntry;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JournalEntryRepository extends CrudRepository<JournalEntry, Long> {

    List<JournalEntry> findByPeriodYearAndPeriodMonthOrderByEntryDate(int year, int month);

    @Query("SELECT COALESCE(SUM(income_amount), 0) FROM journal_entry WHERE period_year = :year AND period_month = :month")
    Double sumIncomeByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(expense_amount), 0) FROM journal_entry WHERE period_year = :year AND period_month = :month")
    Double sumExpenseByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(income_amount), 0) FROM journal_entry WHERE period_year = :year")
    Double sumIncomeByYear(@Param("year") int year);

    @Query("SELECT COALESCE(SUM(expense_amount), 0) FROM journal_entry WHERE period_year = :year")
    Double sumExpenseByYear(@Param("year") int year);

    @Modifying
    @Query("DELETE FROM journal_entry WHERE invoice_out_id = :id")
    void deleteByInvoiceOutId(@Param("id") Long invoiceOutId);

    @Modifying
    @Query("DELETE FROM journal_entry WHERE invoice_in_id = :id")
    void deleteByInvoiceInId(@Param("id") Long invoiceInId);
}
