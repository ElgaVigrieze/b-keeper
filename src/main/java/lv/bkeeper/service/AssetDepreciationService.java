package lv.bkeeper.service;

import lv.bkeeper.domain.FixedAsset;
import lv.bkeeper.domain.JournalEntry;
import lv.bkeeper.repository.FixedAssetRepository;
import lv.bkeeper.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Automatically posts monthly depreciation entries for fixed assets.
 * 
 * Called lazily when reports or journals are accessed — no manual triggers needed.
 * Depreciation is idempotent: calling it multiple times for the same period
 * won't create duplicate entries.
 * 
 * Uses straight-line depreciation: annual_depreciation / 12 per month.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetDepreciationService {

    private final FixedAssetRepository assetRepo;
    private final JournalEntryRepository journalRepo;

    /**
     * Ensures depreciation is posted for all active assets for the given period.
     * Safe to call multiple times — skips assets that already have entries.
     * 
     * @return number of new depreciation entries created
     */
    @Transactional
    public int ensureDepreciationPosted(int year, int month) {
        YearMonth period = YearMonth.of(year, month);
        String periodStart = period.atDay(1).toString();
        String periodEnd = period.atEndOfMonth().toString();

        List<FixedAsset> assets = assetRepo.findActiveInPeriod(periodStart, periodEnd);
        int created = 0;

        for (FixedAsset asset : assets) {
            if (postDepreciationIfNeeded(asset, year, month)) {
                created++;
            }
        }

        if (created > 0) {
            log.info("Posted depreciation for {} assets in {}-{:02d}", created, year, month);
        }

        return created;
    }

    /**
     * Ensures depreciation is posted for all months in a year up to the current month.
     * Useful when generating annual reports.
     */
    @Transactional
    public int ensureDepreciationPostedForYear(int year) {
        int currentYear = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();
        
        // For past years, post all 12 months; for current year, up to current month
        int maxMonth = (year < currentYear) ? 12 : Math.min(12, currentMonth);
        
        int totalCreated = 0;
        for (int month = 1; month <= maxMonth; month++) {
            totalCreated += ensureDepreciationPosted(year, month);
        }
        
        return totalCreated;
    }

    /**
     * Posts depreciation for a single asset if not already posted.
     * Also updates the asset's accumulated_depreciation and book_value.
     */
    private boolean postDepreciationIfNeeded(FixedAsset asset, int year, int month) {
        // Skip if already posted
        List<JournalEntry> existing = journalRepo.findDepreciationByAssetAndPeriod(
                asset.getId(), year, month);
        if (!existing.isEmpty()) {
            return false;
        }

        // Skip if asset has no depreciation configured
        if (asset.getAnnualDepreciation() == null || asset.getAnnualDepreciation() <= 0) {
            return false;
        }

        // Skip if asset is fully depreciated
        double bookValue = asset.getBookValue() != null ? asset.getBookValue() : 0;
        if (bookValue <= 0) {
            return false;
        }

        // Calculate monthly depreciation (don't exceed remaining book value)
        double monthlyDepreciation = round(asset.getAnnualDepreciation() / 12.0);
        monthlyDepreciation = Math.min(monthlyDepreciation, bookValue);

        // Pro-rate for partial months (acquisition or disposal mid-month)
        monthlyDepreciation = proRateDepreciation(asset, year, month, monthlyDepreciation);
        
        if (monthlyDepreciation <= 0) {
            return false;
        }

        // Create journal entry
        YearMonth period = YearMonth.of(year, month);
        LocalDate entryDate = period.atEndOfMonth();

        JournalEntry entry = JournalEntry.builder()
                .entryDate(entryDate.toString())
                .description("Nolietojums: " + asset.getName())
                .reference("DEPR-" + asset.getId() + "-" + year + "-" + String.format("%02d", month))
                .entryType("ASSET_DEPRECIATION")
                .incomeAmount(0.0)
                .expenseAmount(monthlyDepreciation)
                .periodYear(year)
                .periodMonth(month)
                .assetId(asset.getId())
                .createdAt(LocalDateTime.now())
                .build();

        journalRepo.save(entry);

        // Update asset accumulated depreciation and book value
        double newAccumulated = (asset.getAccumulatedDepreciation() != null ? 
                asset.getAccumulatedDepreciation() : 0) + monthlyDepreciation;
        double newBookValue = asset.getAcquisitionCost() - newAccumulated;

        asset.setAccumulatedDepreciation(round(newAccumulated));
        asset.setBookValue(round(Math.max(0, newBookValue)));
        asset.setUpdatedAt(LocalDateTime.now());
        assetRepo.save(asset);

        log.debug("Posted depreciation EUR {} for asset '{}' ({}-{:02d})", 
                monthlyDepreciation, asset.getName(), year, month);

        return true;
    }

    /**
     * Pro-rate depreciation for partial months.
     * If asset was acquired mid-month, depreciate only for days owned.
     * If asset was disposed mid-month, depreciate only until disposal.
     */
    private double proRateDepreciation(FixedAsset asset, int year, int month, double fullMonthAmount) {
        YearMonth period = YearMonth.of(year, month);
        LocalDate monthStart = period.atDay(1);
        LocalDate monthEnd = period.atEndOfMonth();
        int daysInMonth = period.lengthOfMonth();

        LocalDate acqDate = asset.getAcquisitionDate();
        LocalDate dispDate = asset.getDisposalDate();

        // Determine effective start and end within this month
        LocalDate effectiveStart = monthStart;
        LocalDate effectiveEnd = monthEnd;

        if (acqDate != null && acqDate.isAfter(monthStart)) {
            effectiveStart = acqDate;
        }
        if (dispDate != null && dispDate.isBefore(monthEnd)) {
            effectiveEnd = dispDate;
        }

        // If effective period is invalid (disposed before acquired in this month), no depreciation
        if (effectiveStart.isAfter(effectiveEnd)) {
            return 0;
        }

        // Calculate days of ownership
        int daysOwned = (int) (effectiveEnd.toEpochDay() - effectiveStart.toEpochDay()) + 1;

        // If full month owned, return full amount
        if (daysOwned >= daysInMonth) {
            return fullMonthAmount;
        }

        // Pro-rate
        return round(fullMonthAmount * daysOwned / daysInMonth);
    }

    /**
     * Recalculates all depreciation for an asset.
     * Useful after editing asset details.
     */
    @Transactional
    public void recalculateAssetDepreciation(Long assetId) {
        // Delete existing depreciation entries
        journalRepo.deleteDepreciationByAssetId(assetId);

        // Reset accumulated depreciation
        assetRepo.findById(assetId).ifPresent(asset -> {
            asset.setAccumulatedDepreciation(0.0);
            asset.setBookValue(asset.getAcquisitionCost());
            asset.setUpdatedAt(LocalDateTime.now());
            assetRepo.save(asset);

            // Re-post depreciation from acquisition to now
            LocalDate acqDate = asset.getAcquisitionDate();
            LocalDate endDate = asset.getDisposalDate() != null ? 
                    asset.getDisposalDate() : LocalDate.now();

            YearMonth start = YearMonth.from(acqDate);
            YearMonth end = YearMonth.from(endDate);

            for (YearMonth period = start; !period.isAfter(end); period = period.plusMonths(1)) {
                postDepreciationIfNeeded(asset, period.getYear(), period.getMonthValue());
            }

            log.info("Recalculated depreciation for asset '{}'", asset.getName());
        });
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
