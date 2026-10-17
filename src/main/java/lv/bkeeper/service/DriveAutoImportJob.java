package lv.bkeeper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checks the Google Drive invoice folder automatically while the app runs
 * (every hour by default) and imports any new invoice sheets as issued invoices.
 *
 * The interval can be tuned without a rebuild:
 *   --bkeeper.drive.check-interval-ms=3600000
 *   --bkeeper.drive.check-initial-delay-ms=120000
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriveAutoImportJob {

    private final DriveImportService importService;

    @Scheduled(initialDelayString = "${bkeeper.drive.check-initial-delay-ms:120000}",
               fixedDelayString   = "${bkeeper.drive.check-interval-ms:3600000}")
    public void hourlyCheck() {
        if (!importService.configured()) {
            log.debug("Google Drive not configured — automatic check skipped");
            return;
        }
        try {
            DriveImportService.ImportSummary s = importService.runCheckNow();
            log.info("Automatic Drive check: {} new invoice(s) imported, {} error(s)",
                    s.createdIds().size(), s.errors().size());
        } catch (Exception e) {
            log.error("Automatic Drive check failed: {}", e.getMessage());
        }
    }
}
