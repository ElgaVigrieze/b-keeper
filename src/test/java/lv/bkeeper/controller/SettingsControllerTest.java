package lv.bkeeper.controller;

import lv.bkeeper.domain.Company;
import lv.bkeeper.repository.AppSettingRepository;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.service.GoogleDriveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyRepository companyRepository;

    @MockitoBean
    private AppSettingRepository appSettingRepository;

    @MockitoBean
    private GoogleDriveService googleDriveService;

    @Test
    void form_shouldReturnSettingsForm() throws Exception {
        when(companyRepository.findById(1)).thenReturn(Optional.empty());
        when(googleDriveService.hasKeyFile()).thenReturn(false);
        when(googleDriveService.serviceAccountEmail()).thenReturn(Optional.empty());
        when(appSettingRepository.findValue(GoogleDriveService.FOLDER_SETTING_KEY)).thenReturn(Optional.empty());

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/form"))
                .andExpect(model().attributeExists("company"))
                .andExpect(model().attributeExists("driveHasKey"))
                .andExpect(model().attributeExists("driveEmail"))
                .andExpect(model().attributeExists("driveFolderId"));
    }

    @Test
    void save_shouldSaveCompanyAndRedirect() throws Exception {
        Company existing = Company.builder().id(1).name("Old").build();
        when(companyRepository.findById(1)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/settings/save")
                        .param("name", "New Company")
                        .param("regNumber", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        verify(companyRepository).save(any(Company.class));
    }

    @Test
    void saveGoogleDrive_withJson_shouldSaveKey() throws Exception {
        String json = "{\"type\":\"service_account\"}";

        mockMvc.perform(post("/settings/google-drive")
                        .param("serviceAccountJson", json))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("success"));

        verify(googleDriveService).saveServiceAccountKey(json);
    }

    @Test
    void saveGoogleDrive_withFile_shouldSaveKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile("serviceAccountFile", "key.json",
                "application/json", "{\"type\":\"service_account\"}".getBytes());

        mockMvc.perform(multipart("/settings/google-drive")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("success"));

        verify(googleDriveService).saveServiceAccountKey(anyString());
    }

    @Test
    void saveGoogleDrive_withFolder_shouldSaveFolder() throws Exception {
        // extractFolderId is a static pure helper — exercised for real
        mockMvc.perform(post("/settings/google-drive")
                        .param("folderInput", "https://drive.google.com/drive/folders/ABC123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("success"));

        verify(appSettingRepository).put(GoogleDriveService.FOLDER_SETTING_KEY, "ABC123");
        verify(googleDriveService, never()).saveServiceAccountKey(anyString());
    }

    @Test
    void saveGoogleDrive_withInvalidFolder_shouldShowError() throws Exception {
        mockMvc.perform(post("/settings/google-drive")
                        .param("folderInput", "https://example.com/not-a-drive-link/abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("error"));

        verify(appSettingRepository, never()).put(anyString(), anyString());
    }

    @Test
    void testGoogleDrive_whenConfigured_shouldTestConnection() throws Exception {
        when(googleDriveService.hasKeyFile()).thenReturn(true);
        when(appSettingRepository.findValue(GoogleDriveService.FOLDER_SETTING_KEY)).thenReturn(Optional.of("folder123"));
        when(googleDriveService.listFiles("folder123")).thenReturn(java.util.List.of(
                new GoogleDriveService.DriveFile("1", "inv.pdf", "application/pdf",
                        "2026-10-01T12:00:00Z", 1024L)
        ));

        mockMvc.perform(post("/settings/google-drive/test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testGoogleDrive_whenNoKey_shouldShowError() throws Exception {
        when(googleDriveService.hasKeyFile()).thenReturn(false);

        mockMvc.perform(post("/settings/google-drive/test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void removeGoogleDrive_shouldRemoveKeyAndFolder() throws Exception {
        mockMvc.perform(post("/settings/google-drive/remove"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("success"));

        verify(googleDriveService).removeServiceAccountKey();
        verify(appSettingRepository).remove(GoogleDriveService.FOLDER_SETTING_KEY);
    }
}
