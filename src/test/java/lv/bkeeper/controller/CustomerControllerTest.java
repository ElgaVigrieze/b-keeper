package lv.bkeeper.controller;

import lv.bkeeper.domain.Customer;
import lv.bkeeper.mapper.CustomerMapper;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.CustomerRepository;
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

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerRepository customerRepository;

    @MockitoBean
    private CompanyRepository companyRepository;

    @MockitoBean
    private CustomerMapper customerMapper;

    @Test
    void list_shouldReturnCustomersList() throws Exception {
        when(customerRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers/list"))
                .andExpect(model().attributeExists("customers"));
    }

    @Test
    void newForm_shouldReturnCustomersForm() throws Exception {
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/customers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers/form"))
                .andExpect(model().attributeExists("customer"));
    }

    @Test
    void editForm_shouldReturnCustomersForm() throws Exception {
        Customer customer = Customer.builder().id(1L).name("Test").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/customers/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("customers/form"))
                .andExpect(model().attributeExists("customer"));
    }

    @Test
    void saveNew_shouldSaveAndRedirect() throws Exception {
        mockMvc.perform(post("/customers/save")
                        .param("name", "New Customer"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers"));

        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void saveExisting_shouldUpdateAndRedirect() throws Exception {
        Customer existing = Customer.builder().id(1L).name("Old").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/customers/save")
                        .param("id", "1")
                        .param("name", "Updated"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers"));

        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void delete_shouldDeleteAndRedirect() throws Exception {
        mockMvc.perform(post("/customers/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customers"));

        verify(customerRepository).deleteById(1L);
    }
}
