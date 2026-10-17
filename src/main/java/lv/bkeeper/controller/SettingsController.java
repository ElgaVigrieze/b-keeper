package lv.bkeeper.controller;

import lv.bkeeper.domain.Company;
import lv.bkeeper.repository.AppSettingRepository;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.service.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final CompanyRepository   repo;
    private final AppSettingRepository settings;
    private final GoogleDriveService  drive;

    @GetMapping
    public String form(Model model) {
        Company company = repo.findById(1).orElseGet(() ->
                Company.builder()
                        .id(1)
                        .name("")
                        .regNumber("")
                        .fiscalYearStart("01-01")
                        .accountingType("SIMPLE")
                        .createdAt(LocalDateTime.now())
                        .build());
        model.addAttribute("company", company);
        model.addAttribute("currentPage", "settings");

        // ── Google Drive integration status ──
        model.addAttribute("driveHasKey",  drive.hasKeyFile());
        model.addAttribute("driveEmail",   drive.serviceAccountEmail().orElse(""));
        model.addAttribute("driveFolderId", settings.findValue(GoogleDriveService.FOLDER_SETTING_KEY).orElse(""));
        return "settings/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Company submitted, RedirectAttributes ra) {
        // Load the existing row to preserve fields not on the form
        Company existing = repo.findById(1).orElseGet(() ->
                Company.builder()
                        .id(1)
                        .fiscalYearStart("01-01")
                        .accountingType("SIMPLE")
                        .createdAt(LocalDateTime.now())
                        .build());

        // Apply only the fields that the form submits
        existing.setName(submitted.getName());
        existing.setRegNumber(submitted.getRegNumber());
        existing.setVatNumber(submitted.getVatNumber());
        existing.setLegalAddress(submitted.getLegalAddress());
        existing.setBankAccount(submitted.getBankAccount());
        existing.setEmail(submitted.getEmail());
        existing.setPhone(submitted.getPhone());

        repo.save(existing);
        ra.addFlashAttribute("success", "Iestatījumi saglabāti");
        return "redirect:/settings";
    }

    // ── Google Drive integration ─────────────────────────────────────────────

    /**
     * Saves the service account key (pasted JSON or uploaded file)
     * and/or the Drive folder link.
     */
    @PostMapping("/google-drive")
    public String saveGoogleDrive(@RequestParam(required = false) String folderInput,
                                  @RequestParam(required = false) String serviceAccountJson,
                                  @RequestParam(required = false) MultipartFile serviceAccountFile,
                                  RedirectAttributes ra) {
        boolean changed = false;

        // 1. Service account key
        try {
            String json = serviceAccountJson;
            if ((json == null || json.isBlank())
                    && serviceAccountFile != null && !serviceAccountFile.isEmpty()) {
                json = new String(serviceAccountFile.getBytes(), StandardCharsets.UTF_8);
            }
            if (json != null && !json.isBlank()) {
                drive.saveServiceAccountKey(json);
                changed = true;
            }
        } catch (Exception e) {
            log.error("Saving service account key failed", e);
            ra.addFlashAttribute("error", "Atslēgas saglabāšana neizdevās: " + e.getMessage());
            return "redirect:/settings";
        }

        // 2. Drive folder link / id
        if (folderInput != null && !folderInput.isBlank()) {
            String folderId = GoogleDriveService.extractFolderId(folderInput);
            if (folderId.isBlank()) {
                ra.addFlashAttribute("error",
                        "Nederīga Google Drive mapes saite vai ID. Piemērs: https://drive.google.com/drive/folders/XXXX");
                return "redirect:/settings";
            }
            settings.put(GoogleDriveService.FOLDER_SETTING_KEY, folderId);
            changed = true;
        }

        if (changed) {
            ra.addFlashAttribute("success", "Google Drive iestatījumi saglabāti");
        } else {
            ra.addFlashAttribute("error", "Nav izmaiņu — nekas netika ievadīts.");
        }
        return "redirect:/settings";
    }

    /** Tests the connection: authenticates and lists the configured folder. */
    @PostMapping("/google-drive/test")
    public String testGoogleDrive(RedirectAttributes ra) {
        if (!drive.hasKeyFile()) {
            ra.addFlashAttribute("error", "Nav pievienota servisa konta atslēga.");
            return "redirect:/settings";
        }
        String folderId = settings.findValue(GoogleDriveService.FOLDER_SETTING_KEY).orElse("");
        if (folderId.isBlank()) {
            ra.addFlashAttribute("error", "Nav norādīta Google Drive mape.");
            return "redirect:/settings";
        }
        try {
            List<GoogleDriveService.DriveFile> files = drive.listFiles(folderId);
            long importable = files.stream().filter(f -> !f.isFolder() && f.isImportable()).count();
            long already = files.size() - importable;
            ra.addFlashAttribute("success", "Savienojums darbojas! Mapē atrasti "
                    + importable + " importējami faili"
                    + (already > 0 ? " (+" + already + " citi faili, kas netiks importēti)" : "")
                    + ". Skatīt: Izejošie rēķini → Importēt no Google Drive.");
        } catch (Exception e) {
            log.error("Google Drive connection test failed", e);
            ra.addFlashAttribute("error", "Savienojuma pārbaude neizdevās: " + e.getMessage());
        }
        return "redirect:/settings";
    }

    /** Removes the stored service account key and the folder setting. */
    @PostMapping("/google-drive/remove")
    public String removeGoogleDrive(RedirectAttributes ra) {
        try {
            drive.removeServiceAccountKey();
        } catch (Exception e) {
            log.error("Removing service account key failed", e);
            ra.addFlashAttribute("error", "Atslēgas noņemšana neizdevās: " + e.getMessage());
            return "redirect:/settings";
        }
        settings.remove(GoogleDriveService.FOLDER_SETTING_KEY);
        ra.addFlashAttribute("success", "Google Drive savienojums noņemts.");
        return "redirect:/settings";
    }
}
