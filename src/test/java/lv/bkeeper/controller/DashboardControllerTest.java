package lv.bkeeper.controller;

import lv.bkeeper.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyRepository companyRepository;

    @MockitoBean
    private InvoiceOutRepository invoiceOutRepository;

    @MockitoBean
    private InvoiceInRepository invoiceInRepository;

    @MockitoBean
    private JournalEntryRepository journalEntryRepository;

    @MockitoBean
    private PayrollEntryRepository payrollEntryRepository;

    @MockitoBean
    private EdsSubmissionRepository edsSubmissionRepository;

    @Test
    void dashboard_shouldReturnDashboardView() throws Exception {
        when(companyRepository.findById(1)).thenReturn(java.util.Optional.empty());
        when(invoiceOutRepository.sumTurnoverByPeriod(anyInt(), anyInt())).thenReturn(1000.0);
        when(invoiceOutRepository.sumOutputVatByPeriod(anyInt(), anyInt())).thenReturn(210.0);
        when(invoiceInRepository.sumDeductibleVatByPeriod(anyInt(), anyInt())).thenReturn(50.0);
        when(payrollEntryRepository.sumGrossByPeriod(anyInt(), anyInt())).thenReturn(2000.0);
        when(payrollEntryRepository.sumEmployerCostByPeriod(anyInt(), anyInt())).thenReturn(260.0);
        when(journalEntryRepository.sumIncomeByPeriod(anyInt(), anyInt())).thenReturn(1000.0);
        when(journalEntryRepository.sumExpenseByPeriod(anyInt(), anyInt())).thenReturn(300.0);
        when(journalEntryRepository.sumIncomeByYear(anyInt())).thenReturn(12000.0);
        when(journalEntryRepository.sumExpenseByYear(anyInt())).thenReturn(3600.0);
        when(edsSubmissionRepository.findByStatus("READY")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("displayYear"))
                .andExpect(model().attributeExists("displayMonth"))
                .andExpect(model().attributeExists("months"))
                .andExpect(model().attributeExists("turnover"))
                .andExpect(model().attributeExists("vatPayable"))
                .andExpect(model().attributeExists("grossSalary"))
                .andExpect(model().attributeExists("employerCost"))
                .andExpect(model().attributeExists("income"))
                .andExpect(model().attributeExists("expense"))
                .andExpect(model().attributeExists("yearIncome"))
                .andExpect(model().attributeExists("yearExpense"))
                .andExpect(model().attributeExists("pendingSubmissions"));
    }

    @Test
    void dashboard_withParameters_shouldReturnDashboardView() throws Exception {
        when(companyRepository.findById(1)).thenReturn(java.util.Optional.empty());
        when(invoiceOutRepository.sumTurnoverByPeriod(2026, 5)).thenReturn(500.0);
        when(invoiceOutRepository.sumOutputVatByPeriod(2026, 5)).thenReturn(105.0);
        when(invoiceInRepository.sumDeductibleVatByPeriod(2026, 5)).thenReturn(20.0);
        when(payrollEntryRepository.sumGrossByPeriod(2026, 5)).thenReturn(1500.0);
        when(payrollEntryRepository.sumEmployerCostByPeriod(2026, 5)).thenReturn(195.0);
        when(journalEntryRepository.sumIncomeByPeriod(2026, 5)).thenReturn(500.0);
        when(journalEntryRepository.sumExpenseByPeriod(2026, 5)).thenReturn(150.0);
        when(journalEntryRepository.sumIncomeByYear(2026)).thenReturn(6000.0);
        when(journalEntryRepository.sumExpenseByYear(2026)).thenReturn(1800.0);
        when(edsSubmissionRepository.findByStatus("READY")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/").param("year", "2026").param("month", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("displayYear", 2026))
                .andExpect(model().attribute("displayMonth", 5));
    }
}
