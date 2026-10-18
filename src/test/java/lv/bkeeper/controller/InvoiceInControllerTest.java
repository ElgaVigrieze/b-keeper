package lv.bkeeper.controller;

import lv.bkeeper.domain.InvoiceIn;
import lv.bkeeper.domain.Supplier;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.InvoiceInRepository;
import lv.bkeeper.repository.SupplierRepository;
import lv.bkeeper.service.DocumentStorageService;
import lv.bkeeper.service.InvoiceOcrService;
import lv.bkeeper.service.JournalService;
import lv.bkeeper.service.SupplierMatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvoiceInController.class)
class InvoiceInControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceInRepository invoiceInRepository;

    @MockitoBean
    private CompanyRepository companyRepository;

    @MockitoBean
    private InvoiceOcrService ocrService;

    @MockitoBean
    private JournalService journalService;

    @MockitoBean
    private DocumentStorageService documentStorageService;

    @MockitoBean
    private SupplierMatchingService supplierMatchingService;

    @MockitoBean
    private SupplierRepository supplierRepository;

    @Test
    void list_shouldReturnInvoicesInList() throws Exception {
        when(invoiceInRepository.findByPeriod(anyInt(), anyInt())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/invoices/in"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/in-list"))
                .andExpect(model().attributeExists("invoices"));
    }

    @Test
    void list_withParams_shouldReturnInvoicesInList() throws Exception {
        when(invoiceInRepository.findByPeriod(2026, 3)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/invoices/in").param("year", "2026").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/in-list"));
    }

    @Test
    void newForm_shouldReturnInvoicesInForm() throws Exception {
        when(supplierRepository.findByActiveTrueOrderByName()).thenReturn(Collections.emptyList());
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/invoices/in/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/in-form"))
                .andExpect(model().attributeExists("invoice"));
    }

    @Test
    void editForm_shouldReturnInvoicesInForm() throws Exception {
        InvoiceIn invoice = InvoiceIn.builder().id(1L).invoiceNumber("IN-1").build();
        when(invoiceInRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(supplierRepository.findByActiveTrueOrderByName()).thenReturn(Collections.emptyList());
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/invoices/in/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/in-form"))
                .andExpect(model().attributeExists("invoice"));
    }

    @Test
    void uploadAndExtract_shouldProcessFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf",
                "application/pdf", "pdf content".getBytes());
        when(documentStorageService.store(any())).thenReturn("path/invoice.pdf");
        when(ocrService.extractFromFile(any())).thenReturn(Map.of("invoiceNumber", "123"));
        when(supplierMatchingService.findMatch(anyString(), anyString())).thenReturn(Optional.empty());
        when(supplierRepository.findByActiveTrueOrderByName()).thenReturn(Collections.emptyList());
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/invoices/in/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/in-form"))
                .andExpect(model().attributeExists("invoice"));
    }

    @Test
    void uploadAndExtract_withMatch_shouldApplySupplier() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf",
                "application/pdf", "pdf content".getBytes());
        Supplier supplier = Supplier.builder()
                .id(1L)
                .name("Supplier")
                .vatNumber("LV123")
                .defaultVatRate(21.0)
                .defaultCategory("GENERAL")
                .build();
        when(documentStorageService.store(any())).thenReturn("path/invoice.pdf");
        when(ocrService.extractFromFile(any())).thenReturn(Map.of("supplierName", "Sup"));
        when(supplierMatchingService.findMatch(anyString(), anyString())).thenReturn(Optional.of(supplier));
        when(supplierRepository.findByActiveTrueOrderByName()).thenReturn(Collections.emptyList());
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/invoices/in/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/in-form"))
                .andExpect(model().attributeExists("matchedSupplier"));
    }

    @Test
    void applySupplier_shouldReturnForm() throws Exception {
        Supplier supplier = Supplier.builder().id(1L).name("Test").defaultVatRate(21.0).build();
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.findByActiveTrueOrderByName()).thenReturn(Collections.emptyList());
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(post("/invoices/in/apply-supplier")
                        .param("supplierId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/in-form"))
                .andExpect(model().attributeExists("invoice"));
    }

    @Test
    void saveNew_shouldSaveAndRedirect() throws Exception {
        when(invoiceInRepository.save(any(InvoiceIn.class))).thenAnswer(a -> a.getArgument(0));

        mockMvc.perform(post("/invoices/in/save")
                        .param("invoiceNumber", "IN-001")
                        .param("invoiceDate", "2026-04-01")
                        .param("amountExclVat", "200")
                        .param("vatRate", "21"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/in?year=2026&month=4"));

        verify(invoiceInRepository).save(any(InvoiceIn.class));
        verify(journalService).recordInvoiceIn(any(InvoiceIn.class));
    }

    @Test
    void saveExisting_shouldUpdateAndRedirect() throws Exception {
        InvoiceIn existing = InvoiceIn.builder().id(1L).periodYear(2026).periodMonth(5).build();
        when(invoiceInRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/invoices/in/save")
                        .param("id", "1")
                        .param("invoiceNumber", "IN-002")
                        .param("invoiceDate", "2026-05-10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/in?year=2026&month=5"));

        verify(invoiceInRepository).save(any(InvoiceIn.class));
        verify(journalService).recordInvoiceIn(any(InvoiceIn.class));
    }

    @Test
    void delete_shouldDeleteAndRedirect() throws Exception {
        InvoiceIn existing = InvoiceIn.builder().id(1L).pdfFilename("2026/04/x.pdf").build();
        when(invoiceInRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/invoices/in/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/in"));

        verify(journalService).deleteByInvoiceInId(1L);
        verify(documentStorageService).delete("2026/04/x.pdf");
        verify(invoiceInRepository).deleteById(1L);
    }

    @Test
    void delete_unknownId_shouldDoNothing() throws Exception {
        when(invoiceInRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/invoices/in/99/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/in"));

        verify(invoiceInRepository, never()).deleteById(anyLong());
        verify(journalService, never()).deleteByInvoiceInId(anyLong());
    }
}
