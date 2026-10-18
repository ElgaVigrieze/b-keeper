package lv.bkeeper.controller;

import lv.bkeeper.domain.Customer;
import lv.bkeeper.domain.InvoiceOut;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.CustomerRepository;
import lv.bkeeper.repository.InvoiceOutRepository;
import lv.bkeeper.service.JournalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvoiceOutController.class)
class InvoiceOutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceOutRepository invoiceOutRepository;

    @MockitoBean
    private CompanyRepository companyRepository;

    @MockitoBean
    private JournalService journalService;

    @MockitoBean
    private CustomerRepository customerRepository;

    @Test
    void list_shouldReturnInvoicesOutList() throws Exception {
        when(invoiceOutRepository.findActiveByPeriod(anyInt(), anyInt())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/invoices/out"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/out-list"))
                .andExpect(model().attributeExists("invoices"));
    }

    @Test
    void list_withParams_shouldReturnInvoicesOutList() throws Exception {
        when(invoiceOutRepository.findActiveByPeriod(2026, 1)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/invoices/out").param("year", "2026").param("month", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/out-list"));
    }

    @Test
    void newForm_shouldReturnInvoicesOutForm() throws Exception {
        when(customerRepository.findByActiveTrueOrderByName()).thenReturn(Collections.emptyList());
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/invoices/out/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/out-form"))
                .andExpect(model().attributeExists("invoice"));
    }

    @Test
    void editForm_shouldReturnInvoicesOutForm() throws Exception {
        InvoiceOut invoice = InvoiceOut.builder().id(1L).invoiceNumber("INV-1").build();
        when(invoiceOutRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(customerRepository.findByActiveTrueOrderByName()).thenReturn(Collections.emptyList());
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/invoices/out/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/out-form"))
                .andExpect(model().attributeExists("invoice"));
    }

    @Test
    void applyCustomer_shouldReturnFormWithCustomerData() throws Exception {
        Customer customer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .regNumber("123456")
                .defaultVatRate(21.0)
                .defaultPvnLine(41)
                .defaultDocType("1")
                .build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.findByActiveTrueOrderByName()).thenReturn(Collections.emptyList());
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(post("/invoices/out/apply-customer")
                        .param("customerId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("invoices/out-form"))
                .andExpect(model().attributeExists("invoice"));
    }

    @Test
    void saveNew_shouldSaveAndRedirect() throws Exception {
        InvoiceOut saved = InvoiceOut.builder().id(1L).periodYear(2026).periodMonth(1).build();
        when(invoiceOutRepository.save(any(InvoiceOut.class))).thenReturn(saved);

        mockMvc.perform(post("/invoices/out/save")
                        .param("invoiceNumber", "INV-001")
                        .param("issueDate", "2026-01-15")
                        .param("amountExclVat", "1000")
                        .param("vatRate", "21"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out?year=2026&month=1"));

        verify(invoiceOutRepository).save(any(InvoiceOut.class));
        verify(journalService).recordInvoiceOut(any(InvoiceOut.class));
    }

    @Test
    void saveExisting_shouldUpdateAndRedirect() throws Exception {
        InvoiceOut existing = InvoiceOut.builder().id(1L).periodYear(2026).periodMonth(2).build();
        when(invoiceOutRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/invoices/out/save")
                        .param("id", "1")
                        .param("invoiceNumber", "INV-002")
                        .param("issueDate", "2026-02-01")
                        .param("amountExclVat", "500"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out?year=2026&month=2"));

        verify(invoiceOutRepository).save(any(InvoiceOut.class));
        verify(journalService).recordInvoiceOut(any(InvoiceOut.class));
    }

    @Test
    void delete_shouldDeleteAndRedirect() throws Exception {
        mockMvc.perform(post("/invoices/out/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out"));

        verify(journalService).deleteByInvoiceOutId(1L);
        verify(invoiceOutRepository).deleteById(1L);
    }

    @Test
    void updateStatus_shouldUpdateAndRedirect() throws Exception {
        InvoiceOut existing = InvoiceOut.builder().id(1L).status("DRAFT").build();
        when(invoiceOutRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/invoices/out/1/status")
                        .param("status", "PAID")
                        .param("paidDate", "2026-02-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invoices/out"));

        verify(invoiceOutRepository).save(any(InvoiceOut.class));
        verify(journalService).recordInvoiceOut(any(InvoiceOut.class));
    }
}
