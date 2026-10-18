package lv.bkeeper.controller;

import lv.bkeeper.domain.InvoiceOut;
import lv.bkeeper.repository.AppSettingRepository;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.InvoiceOutRepository;
import lv.bkeeper.service.DriveImportService;
import lv.bkeeper.service.GoogleDriveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DriveImportController.class)
class DriveImportControllerTest {

    private static final String FOLDER_KEY = GoogleDriveService.FOLDER_SETTING_KEY;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleDriveService drive;

    @MockitoBean
    private InvoiceOutRepository repo;

    @MockitoBean
    private AppSettingRepository settings;

    @MockitoBean
    private CompanyRepository companyRepo;

    @MockitoBean
    private DriveImportService importService;

    // ── List ─────────────────────────────────────────────────────────────────

    @Test
    void list_notConfigured_shouldRenderNotConfiguredPanel() throws Exception {
        when(settings.findValue(FOLDER_KEY)).thenReturn(Optional.empty());
        when(drive.hasKeyFile()).thenReturn(false);
        when(drive.serviceAccountEmail()).thenReturn(Optional.empty());
        when(companyRepo.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/invoices/out/drive"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/out-drive"))
                .andExpect(model().attribute("configured", false))
                .andExpect(model().attribute("hasKey", false));
    }

    @Test
    void list_configured_shouldListImportableFiles() throws Exception {
        when(settings.findValue(FOLDER_KEY)).thenReturn(Optional.of("folder-1"));
        when(drive.hasKeyFile()).thenReturn(true);
        when(drive.serviceAccountEmail()).thenReturn(Optional.of("sa@proj.iam.gserviceaccount.com"));
        when(companyRepo.findById(1)).thenReturn(Optional.empty());
        when(settings.findValue(DriveImportService.LAST_CHECK_AT_KEY))
                .thenReturn(Optional.of("17.10.2026 10:00"));
        when(settings.findValue(DriveImportService.LAST_CHECK_COUNT_KEY))
                .thenReturn(Optional.of("2"));
        when(settings.findValue(DriveImportService.LAST_CHECK_OK_KEY))
                .thenReturn(Optional.of("1"));
        when(drive.listFiles("folder-1")).thenReturn(List.of(
                new GoogleDriveService.DriveFile("f1", "inv_final.pdf", "application/pdf",
                        "2026-10-01T12:00:00Z", 1024L),
                new GoogleDriveService.DriveFile("f2", "inv_draft.pdf", "application/pdf",
                        "2026-10-02T09:00:00Z", 2048L),
                new GoogleDriveService.DriveFile("f3", "inv_new.pdf", "application/pdf",
                        "2026-10-03T08:00:00Z", 4096L),
                new GoogleDriveService.DriveFile("folder", "Subfolder",
                        "application/vnd.google-apps.folder", null, null)));
        when(repo.findAllImported()).thenReturn(List.of(
                InvoiceOut.builder().driveFileId("f1").status("ISSUED").build(),
                InvoiceOut.builder().driveFileId("f2").status("DRAFT").build()));

        mockMvc.perform(get("/invoices/out/drive"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/out-drive"))
                .andExpect(model().attribute("configured", true))
                .andExpect(model().attribute("newCount", 1L));
    }

    @Test
    void list_driveFailure_shouldRenderError() throws Exception {
        when(settings.findValue(FOLDER_KEY)).thenReturn(Optional.of("folder-1"));
        when(drive.hasKeyFile()).thenReturn(true);
        when(drive.serviceAccountEmail()).thenReturn(Optional.of("sa@proj.iam.gserviceaccount.com"));
        when(companyRepo.findById(1)).thenReturn(Optional.empty());
        when(drive.listFiles("folder-1")).thenThrow(new RuntimeException("quota exceeded"));

        mockMvc.perform(get("/invoices/out/drive"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("driveError", "quota exceeded"));
    }

    @Test
    void list_withImportedParam_shouldLoadResultInvoices() throws Exception {
        when(settings.findValue(FOLDER_KEY)).thenReturn(Optional.empty());
        when(drive.hasKeyFile()).thenReturn(false);
        when(drive.serviceAccountEmail()).thenReturn(Optional.empty());
        when(companyRepo.findById(1)).thenReturn(Optional.empty());
        InvoiceOut importedInv = InvoiceOut.builder()
                .invoiceNumber("2026-001")
                .amountInclVat(123.45)
                .build();
        when(repo.findAllById(List.of(7L))).thenReturn(List.of(importedInv));

        mockMvc.perform(get("/invoices/out/drive").param("imported", "7,abc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("importedInvoices", List.of(importedInv)));
    }

    // ── Import selected ──────────────────────────────────────────────────────

    @Test
    void import_notConfigured_shouldRedirectWithError() throws Exception {
        when(importService.configured()).thenReturn(false);

        mockMvc.perform(post("/invoices/out/drive/import").param("fileId", "f1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out/drive"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void import_noFilesSelected_shouldRedirectWithError() throws Exception {
        when(importService.configured()).thenReturn(true);

        mockMvc.perform(post("/invoices/out/drive/import"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out/drive"))
                .andExpect(flash().attribute("error", "Nav izvēlēts neviens fails."));
    }

    @Test
    void import_success_shouldRedirectWithImportedIds() throws Exception {
        when(importService.configured()).thenReturn(true);
        when(importService.importFiles(List.of("f1", "f2")))
                .thenReturn(new DriveImportService.ImportSummary(List.of(7L), List.of(), 0, 0));

        mockMvc.perform(post("/invoices/out/drive/import")
                        .param("fileId", "f1", "f2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out/drive?imported=7"))
                .andExpect(flash().attribute("success", "1 rēķins importēts kā izrakstīti rēķini."));

        verify(importService).importFiles(List.of("f1", "f2"));
    }

    @Test
    void import_withErrorsOnly_shouldRedirectWithoutImportedParam() throws Exception {
        when(importService.configured()).thenReturn(true);
        when(importService.importFiles(anyList()))
                .thenReturn(new DriveImportService.ImportSummary(
                        List.of(), List.of("inv.pdf: neizdevās"), 0, 0));

        mockMvc.perform(post("/invoices/out/drive/import").param("fileId", "f1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out/drive"))
                .andExpect(flash().attribute("error", "inv.pdf: neizdevās"));
    }

    // ── Check now ────────────────────────────────────────────────────────────

    @Test
    void checkNow_notConfigured_shouldRedirectWithError() throws Exception {
        when(importService.configured()).thenReturn(false);

        mockMvc.perform(post("/invoices/out/drive/check-now"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out/drive"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void checkNow_noNewInvoices_shouldRedirectWithSuccess() throws Exception {
        when(importService.configured()).thenReturn(true);
        when(importService.runCheckNow())
                .thenReturn(new DriveImportService.ImportSummary(List.of(), List.of(), 3, 0));

        mockMvc.perform(post("/invoices/out/drive/check-now"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out/drive"))
                .andExpect(flash().attribute("success",
                        "Pārbaude pabeigta — mapē nav jaunu rēķinu."));
    }

    @Test
    void checkNow_withNewInvoices_shouldReportCount() throws Exception {
        when(importService.configured()).thenReturn(true);
        when(importService.runCheckNow())
                .thenReturn(new DriveImportService.ImportSummary(List.of(1L, 2L), List.of(), 0, 0));

        mockMvc.perform(post("/invoices/out/drive/check-now"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out/drive"))
                .andExpect(flash().attribute("success",
                        "Pārbaude pabeigta: 2 jauni rēķini importēti kā izrakstīti rēķini."));
    }

    @Test
    void checkNow_failure_shouldRedirectWithErrorMessage() throws Exception {
        when(importService.configured()).thenReturn(true);
        when(importService.runCheckNow()).thenThrow(new RuntimeException("token expired"));

        mockMvc.perform(post("/invoices/out/drive/check-now"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out/drive"))
                .andExpect(flash().attribute("error", "Pārbaude neizdevās: token expired"));
    }
}
