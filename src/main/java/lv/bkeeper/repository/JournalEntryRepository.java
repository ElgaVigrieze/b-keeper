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

    // ── Annual aggregations by entry type ─────────────────────────────────────

    @Query("SELECT COALESCE(SUM(income_amount), 0) FROM journal_entry WHERE period_year = :year AND entry_type = :type")
    Double sumIncomeByYearAndType(@Param("year") int year, @Param("type") String type);

    @Query("SELECT COALESCE(SUM(expense_amount), 0) FROM journal_entry WHERE period_year = :year AND entry_type = :type")
    Double sumExpenseByYearAndType(@Param("year") int year, @Param("type") String type);

    // ── Asset depreciation tracking ───────────────────────────────────────────

    @Query("SELECT * FROM journal_entry WHERE asset_id = :assetId AND period_year = :year AND period_month = :month AND entry_type = 'ASSET_DEPRECIATION'")
    List<JournalEntry> findDepreciationByAssetAndPeriod(@Param("assetId") Long assetId, 
                                                         @Param("year") int year, 
                                                         @Param("month") int month);

    @Query("SELECT COALESCE(SUM(expense_amount), 0) FROM journal_entry WHERE asset_id = :assetId AND entry_type = 'ASSET_DEPRECIATION'")
    Double sumDepreciationByAsset(@Param("assetId") Long assetId);

    @Modifying
    @Query("DELETE FROM journal_entry WHERE asset_id = :id AND entry_type = 'ASSET_DEPRECIATION'")
    void deleteDepreciationByAssetId(@Param("id") Long assetId);
}