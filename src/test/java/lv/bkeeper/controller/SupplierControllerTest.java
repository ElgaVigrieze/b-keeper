package lv.bkeeper.controller;

import lv.bkeeper.domain.Supplier;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.SupplierRepository;
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

@WebMvcTest(SupplierController.class)
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierRepository supplierRepository;

    @MockitoBean
    private CompanyRepository companyRepository;

    @Test
    void list_shouldReturnSuppliersList() throws Exception {
        when(supplierRepository.findByActiveTrueOrderByName()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/suppliers"))
                .andExpect(status().isOk())
                .andExpect(view().name("suppliers/list"))
                .andExpect(model().attributeExists("suppliers"));
    }

    @Test
    void newForm_shouldReturnSuppliersForm() throws Exception {
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/suppliers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("suppliers/form"))
                .andExpect(model().attributeExists("supplier"));
    }

    @Test
    void editForm_shouldReturnSuppliersForm() throws Exception {
        Supplier supplier = Supplier.builder().id(1L).name("Test").build();
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/suppliers/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("suppliers/form"))
                .andExpect(model().attributeExists("supplier"));
    }

    @Test
    void saveNew_shouldSaveAndRedirect() throws Exception {
        mockMvc.perform(post("/suppliers/save")
                        .param("name", "New Supplier"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/suppliers"));

        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    void saveExisting_shouldUpdateAndRedirect() throws Exception {
        Supplier existing = Supplier.builder().id(1L).name("Old").build();
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/suppliers/save")
                        .param("id", "1")
                        .param("name", "Updated"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/suppliers"));

        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    void delete_shouldDeleteAndRedirect() throws Exception {
        mockMvc.perform(post("/suppliers/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/suppliers"));

        verify(supplierRepository).deleteById(1L);
    }
}
