package lv.bkeeper.controller;

import lv.bkeeper.domain.Employee;
import lv.bkeeper.mapper.EmployeeMapper;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.EmployeeRepository;
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

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private CompanyRepository companyRepository;

    @MockitoBean
    private EmployeeMapper employeeMapper;

    @Test
    void list_shouldReturnEmployeesList() throws Exception {
        when(employeeRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(view().name("employees/list"))
                .andExpect(model().attributeExists("employees"));
    }

    @Test
    void newForm_shouldReturnEmployeesForm() throws Exception {
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/employees/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("employees/form"))
                .andExpect(model().attributeExists("employee"));
    }

    @Test
    void editForm_shouldReturnEmployeesForm() throws Exception {
        Employee employee = Employee.builder().id(1L).build();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/employees/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("employees/form"))
                .andExpect(model().attributeExists("employee"));
    }

    @Test
    void saveNew_shouldSaveAndRedirect() throws Exception {
        mockMvc.perform(post("/employees/save")
                        .param("firstName", "John"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"));

        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void saveExisting_shouldUpdateAndRedirect() throws Exception {
        Employee existing = Employee.builder().id(1L).firstName("Old").build();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/employees/save")
                        .param("id", "1")
                        .param("firstName", "Updated"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"));

        verify(employeeRepository).save(any(Employee.class));
    }
}
