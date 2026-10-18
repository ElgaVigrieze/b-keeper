package lv.bkeeper.controller;

import lv.bkeeper.domain.Employee;
import lv.bkeeper.domain.PayrollEntry;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.EmployeeRepository;
import lv.bkeeper.repository.PayrollEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PayrollController.class)
class PayrollControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayrollEntryRepository payrollEntryRepository;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private CompanyRepository companyRepository;

    @Test
    void list_shouldReturnPayrollList() throws Exception {
        when(payrollEntryRepository.findByPeriodYearAndPeriodMonth(anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(employeeRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/payroll"))
                .andExpect(status().isOk())
                .andExpect(view().name("payroll/list"))
                .andExpect(model().attributeExists("entries"))
                .andExpect(model().attributeExists("employees"))
                .andExpect(model().attributeExists("totalGross"))
                .andExpect(model().attributeExists("totalNet"));
    }

    @Test
    void list_withParams_shouldReturnPayrollList() throws Exception {
        when(payrollEntryRepository.findByPeriodYearAndPeriodMonth(2026, 7))
                .thenReturn(Collections.emptyList());
        when(employeeRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/payroll").param("year", "2026").param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(view().name("payroll/list"))
                .andExpect(model().attribute("displayYear", 2026))
                .andExpect(model().attribute("displayMonth", 7));
    }

    @Test
    void calculate_shouldCreateEntriesAndRedirect() throws Exception {
        Employee emp = Employee.builder().id(1L).monthlyGross(1000.0).nonTaxableMin(0.0).build();
        when(employeeRepository.findByActiveTrue()).thenReturn(List.of(emp));
        when(payrollEntryRepository.findByEmployeeIdAndPeriodYearAndPeriodMonth(1L, 2026, 8))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/payroll/calculate")
                        .param("year", "2026")
                        .param("month", "8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payroll?year=2026&month=8"));

        verify(payrollEntryRepository).save(any(PayrollEntry.class));
    }

    @Test
    void calculate_whenEntryExists_shouldSkip() throws Exception {
        Employee emp = Employee.builder().id(1L).monthlyGross(1000.0).nonTaxableMin(0.0).build();
        when(employeeRepository.findByActiveTrue()).thenReturn(List.of(emp));
        when(payrollEntryRepository.findByEmployeeIdAndPeriodYearAndPeriodMonth(1L, 2026, 8))
                .thenReturn(Optional.of(PayrollEntry.builder().id(9L).build()));

        mockMvc.perform(post("/payroll/calculate")
                        .param("year", "2026")
                        .param("month", "8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payroll?year=2026&month=8"));

        verify(payrollEntryRepository, never()).save(any(PayrollEntry.class));
    }

    @Test
    void recalculate_shouldDeleteAndSave() throws Exception {
        PayrollEntry existing = PayrollEntry.builder().id(1L).employeeId(2L).periodYear(2026).periodMonth(8).build();
        Employee emp = Employee.builder().id(2L).monthlyGross(1500.0).nonTaxableMin(0.0).build();
        when(payrollEntryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(emp));

        mockMvc.perform(post("/payroll/1/recalculate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payroll"));

        verify(payrollEntryRepository).deleteById(1L);
        verify(payrollEntryRepository).save(any(PayrollEntry.class));
    }

    @Test
    void delete_shouldDeleteAndRedirect() throws Exception {
        mockMvc.perform(post("/payroll/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payroll"));

        verify(payrollEntryRepository).deleteById(1L);
    }

    @Test
    void saveNew_shouldSaveAndRedirect() throws Exception {
        mockMvc.perform(post("/payroll/save")
                        .param("periodYear", "2026")
                        .param("periodMonth", "9")
                        .param("grossSalary", "1000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payroll?year=2026&month=9"));

        verify(payrollEntryRepository).save(any(PayrollEntry.class));
    }

    @Test
    void saveExisting_shouldUpdateAndRedirect() throws Exception {
        PayrollEntry existing = PayrollEntry.builder().id(1L).periodYear(2026).periodMonth(9).build();
        when(payrollEntryRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/payroll/save")
                        .param("id", "1")
                        .param("periodYear", "2026")
                        .param("periodMonth", "9")
                        .param("grossSalary", "1200"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payroll?year=2026&month=9"));

        verify(payrollEntryRepository).save(any(PayrollEntry.class));
    }
}
