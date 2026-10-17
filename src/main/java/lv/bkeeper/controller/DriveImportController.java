package lv.bkeeper.controller;

import lv.bkeeper.domain.InvoiceOut;
import lv.bkeeper.repository.AppSettingRepository;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.InvoiceOutRepository;
import lv.bkeeper.service.DriveImportService;
import lv.bkeeper.service.DriveImportService.ImportSummary;
import lv.bkeeper.service.GoogleDriveService;
import lv.bkeeper.service.GoogleDriveService.DriveFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports outgoing invoices from a Google Drive folder.
 *
 * The heavy lifting happens in {@link DriveImportService}; this controller
 * only renders the page (list of Drive files with their import status),
 * starts manual imports and shows the results.
 */
@Slf4j
@Controller
@RequestMapping("/invoices/out/drive")
@RequiredArgsConstructor
public class DriveImportController {

    /** app_setting key holding the Google Drive folder id. */
    private static final String FOLDER_SETTING = GoogleDriveService.FOLDER_SETTING_KEY;

    private final GoogleDriveService   drive;
    private final InvoiceOutRepository repo;
    private final AppSettingRepository settings;
    private final CompanyRepository    companyRepo;
    private final DriveImportService   importService;

    // ── List ─────────────────────────────────────────────────────────────────

    @GetMapping
    public String list(@RequestParam(required = false) String imported, Model model) {
        String folderId = settings.findValue(FOLDER_SETTING).orElse("");

        model.addAttribute("currentPage",        "invoices-out");
        model.addAttribute("hasKey",             drive.hasKeyFile());
        model.addAttribute("serviceAccountEmail", drive.serviceAccountEmail().orElse(""));
        model.addAttribute("folderId",           folderId);
        model.addAttribute("configured",         drive.hasKeyFile() && !folderId.isBlank());
        companyRepo.findById(1).ifPresent(c -> model.addAttribute("company", c));

        // Last automatic / manual folder check
        settings.findValue(DriveImportService.LAST_CHECK_AT_KEY)
                .ifPresent(v -> model.addAttribute("lastCheckAt", v));
        model.addAttribute("lastCheckCount",
                settings.findValue(DriveImportService.LAST_CHECK_COUNT_KEY).orElse("0"));
        model.addAttribute("lastCheckError",
                "0".equals(settings.findValue(DriveImportService.LAST_CHECK_OK_KEY).orElse("1")));

        if (drive.hasKeyFile() && !folderId.isBlank()) {
            try {
                List<DriveFile> files = drive.listFiles(folderId);
                Map<String, InvoiceOut> byDriveId = new HashMap<>();
                repo.findAllImported().forEach(i -> byDriveId.put(i.getDriveFileId(), i));

                List<FileRow> rows = files.stream()
                        .filter(f -> !f.isFolder() && f.isImportable())
                        .map(f -> {
                            InvoiceOut inv = byDriveId.get(f.id());
                            boolean finalized   = inv != null && !"DRAFT".equals(inv.getStatus());
                            boolean refreshable = inv != null && "DRAFT".equals(inv.getStatus());
                            return new FileRow(f, finalized, refreshable);
                        })
                        .toList();
                model.addAttribute("files", rows);
                model.addAttribute("newCount",
                        rows.stream().filter(r -> !r.imported() && !r.refreshable()).count());
            } catch (Exception e) {
                log.error("Google Drive listing failed", e);
                model.addAttribute("driveError", e.getMessage());
            }
        }

        // Invoices imported in the last import (PRG result panel)
        if (imported != null && !imported.isBlank()) {
            List<Long> ids = new ArrayList<>();
            for (String part : imported.split(",")) {
                try { ids.add(Long.parseLong(part.trim())); } catch (NumberFormatException ignored) {}
            }
            if (!ids.isEmpty()) {
                List<InvoiceOut> importedInvoices = new ArrayList<>();
                repo.findAllById(ids).forEach(importedInvoices::add);
                model.addAttribute("importedInvoices", importedInvoices);
            }
        }
        return "invoices/out-drive";
    }

    // ── Import selected ───────────────────────────────────────────────────────

    @PostMapping("/import")
    public String importSelected(@RequestParam(name = "fileId", required = false) List<String> fileIds,
                                 RedirectAttributes ra) {
        if (!importService.configured()) {
            ra.addFlashAttribute("error", "Google Drive nav konfigurēts. Pievienojiet savienojumu sadaļā Iestatījumi.");
            return "redirect:/invoices/out/drive";
        }
        if (fileIds == null || fileIds.isEmpty()) {
            ra.addFlashAttribute("error", "Nav izvēlēts neviens fails.");
            return "redirect:/invoices/out/drive";
        }

        ImportSummary summary = importService.importFiles(fileIds);
        addImportMessages(ra, summary);

        return summary.createdIds().isEmpty()
                ? "redirect:/invoices/out/drive"
                : "redirect:/invoices/out/drive?imported=" + joinIds(summary.createdIds());
    }

    // ── Check now (force the automatic check) ─────────────────────────────────

    @PostMapping("/check-now")
    public String checkNow(RedirectAttributes ra) {
        if (!importService.configured()) {
            ra.addFlashAttribute("error", "Google Drive nav konfigurēts. Pievienojiet savienojumu sadaļā Iestatījumi.");
            return "redirect:/invoices/out/drive";
        }
        try {
            ImportSummary summary = importService.runCheckNow();
            if (summary.createdIds().isEmpty()) {
                ra.addFlashAttribute("success", "Pārbaude pabeigta — mapē nav jaunu rēķinu.");
            } else {
                ra.addFlashAttribute("success", "Pārbaude pabeigta: " + summary.createdIds().size()
                        + " jauni rēķini importēti kā izrakstīti rēķini.");
            }
            if (!summary.errors().isEmpty()) {
                ra.addFlashAttribute("error", joinErrors(summary.errors()));
            }
        } catch (Exception e) {
            log.error("Manual Drive check failed", e);
            ra.addFlashAttribute("error", "Pārbaude neizdevās: " + shorten(e.getMessage()));
        }
        return "redirect:/invoices/out/drive";
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    private void addImportMessages(RedirectAttributes ra, ImportSummary s) {
        if (!s.createdIds().isEmpty()) {
            String message = s.createdIds().size()
                    + (s.createdIds().size() == 1 ? " rēķins importēts" : " rēķini importēti")
                    + " kā izrakstīti rēķini.";
            if (s.refreshed() > 0) {
                message += " " + s.refreshed()
                        + (s.refreshed() == 1 ? " agrākais melnraksts tika aizvietots ar svaigiem datiem."
                                              : " agrākie melnraksti tika aizvietoti ar svaigiem datiem.");
            }
            ra.addFlashAttribute("success", message);
        }
        if (s.skipped() > 0) {
            ra.addFlashAttribute("warning", s.skipped() + (s.skipped() == 1 ? " fails" : " faili")
                    + " jau bija importēti un tika izlaisti.");
        }
        if (!s.errors().isEmpty()) {
            ra.addFlashAttribute("error", joinErrors(s.errors()));
        }
    }

    private String joinErrors(List<String> errors) {
        String message = String.join("; ", errors.subList(0, Math.min(3, errors.size())));
        if (errors.size() > 3) message += "; …";
        return message;
    }

    private String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    private String shorten(String message) {
        if (message == null) return "nezināma kļūda";
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }

    // ── Model ────────────────────────────────────────────────────────────────

    /**
     * A Drive file row with its import status:
     *  - imported    = imported earlier and already reviewed/finalised (locked)
     *  - refreshable = imported earlier but still a draft (can be re-imported)
     */
    public record FileRow(DriveFile file, boolean imported, boolean refreshable) {
    }
}
