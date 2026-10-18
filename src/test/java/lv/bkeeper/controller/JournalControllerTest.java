package lv.bkeeper.controller;

import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.JournalEntryRepository;
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

@WebMvcTest(JournalController.class)
class JournalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JournalEntryRepository journalEntryRepository;

    @MockitoBean
    private CompanyRepository companyRepository;

    @Test
    void list_shouldReturnJournalList() throws Exception {
        when(journalEntryRepository.findByPeriodYearAndPeriodMonthOrderByEntryDate(anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/journal"))
                .andExpect(status().isOk())
                .andExpect(view().name("journal/list"))
                .andExpect(model().attributeExists("entries"))
                .andExpect(model().attributeExists("totalIncome"))
                .andExpect(model().attributeExists("totalExpense"));
    }

    @Test
    void list_withParams_shouldReturnJournalList() throws Exception {
        when(journalEntryRepository.findByPeriodYearAndPeriodMonthOrderByEntryDate(2026, 6))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/journal").param("year", "2026").param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(view().name("journal/list"))
                .andExpect(model().attribute("displayYear", 2026))
                .andExpect(model().attribute("displayMonth", 6));
    }
}
