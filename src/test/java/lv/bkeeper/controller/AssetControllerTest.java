package lv.bkeeper.controller;

import lv.bkeeper.domain.FixedAsset;
import lv.bkeeper.mapper.FixedAssetMapper;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.FixedAssetRepository;
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

@WebMvcTest(AssetController.class)
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FixedAssetRepository fixedAssetRepository;

    @MockitoBean
    private CompanyRepository companyRepository;

    @MockitoBean
    private FixedAssetMapper assetMapper;

    @Test
    void list_shouldReturnAssetsList() throws Exception {
        when(fixedAssetRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/assets"))
                .andExpect(status().isOk())
                .andExpect(view().name("assets/list"))
                .andExpect(model().attributeExists("assets"));
    }

    @Test
    void newForm_shouldReturnAssetsForm() throws Exception {
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/assets/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("assets/form"))
                .andExpect(model().attributeExists("asset"));
    }

    @Test
    void editForm_shouldReturnAssetsForm() throws Exception {
        FixedAsset asset = FixedAsset.builder().id(1L).name("Asset").build();
        when(fixedAssetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(companyRepository.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/assets/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("assets/form"))
                .andExpect(model().attributeExists("asset"));
    }

    @Test
    void saveNew_shouldSaveAndRedirect() throws Exception {
        mockMvc.perform(post("/assets/save")
                        .param("name", "New Asset")
                        .param("acquisitionCost", "1000")
                        .param("usefulLifeYears", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/assets"));

        verify(fixedAssetRepository).save(any(FixedAsset.class));
    }

    @Test
    void saveExisting_shouldSaveAndRedirect() throws Exception {
        FixedAsset existing = FixedAsset.builder().id(1L).name("Old").build();
        when(fixedAssetRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/assets/save")
                        .param("id", "1")
                        .param("name", "Asset")
                        .param("acquisitionCost", "2000")
                        .param("usefulLifeYears", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/assets"));

        verify(fixedAssetRepository).save(any(FixedAsset.class));
    }
}
