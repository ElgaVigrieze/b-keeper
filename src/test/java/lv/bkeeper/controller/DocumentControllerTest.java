package lv.bkeeper.controller;

import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.service.DocumentStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentStorageService documentStorageService;

    @TempDir
    Path tempDir;

    @MockitoBean
    private CompanyRepository companyRepository;

    @Test
    void view_existingFile_shouldStreamInline() throws Exception {
        Path file = tempDir.resolve("123_invoice.pdf");
        Files.writeString(file, "%PDF-1.4 test-content");
        when(documentStorageService.resolve(DocumentStorageService.DIR_OUT, "2026/06/123_invoice.pdf"))
                .thenReturn(file);

        mockMvc.perform(get("/documents/view")
                        .param("path", "2026/06/123_invoice.pdf")
                        .param("dir", DocumentStorageService.DIR_OUT))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Disposition", containsString("123_invoice.pdf")))
                .andExpect(content().string("%PDF-1.4 test-content"));

        verify(documentStorageService).resolve(DocumentStorageService.DIR_OUT, "2026/06/123_invoice.pdf");
    }

    @Test
    void view_withoutDirParam_shouldDefaultToIncomingInvoices() throws Exception {
        Path file = tempDir.resolve("999_receipt.pdf");
        Files.writeString(file, "receipt-bytes");
        when(documentStorageService.resolve(DocumentStorageService.DIR_IN, "2026/07/999_receipt.pdf"))
                .thenReturn(file);

        mockMvc.perform(get("/documents/view").param("path", "2026/07/999_receipt.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().string("receipt-bytes"));

        verify(documentStorageService).resolve(DocumentStorageService.DIR_IN, "2026/07/999_receipt.pdf");
    }

    @Test
    void view_missingFile_shouldReturn404() throws Exception {
        when(documentStorageService.resolve(DocumentStorageService.DIR_OUT, "2026/06/gone.pdf"))
                .thenReturn(tempDir.resolve("does-not-exist.pdf"));

        mockMvc.perform(get("/documents/view")
                        .param("path", "2026/06/gone.pdf")
                        .param("dir", DocumentStorageService.DIR_OUT))
                .andExpect(status().isNotFound());
    }

    @Test
    void view_missingPathParam_shouldReturn400() throws Exception {
        mockMvc.perform(get("/documents/view"))
                .andExpect(status().isBadRequest());
    }
}
