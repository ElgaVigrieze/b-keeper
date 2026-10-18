package lv.bkeeper.controller;

import lv.bkeeper.domain.Company;
import lv.bkeeper.repository.CompanyRepository;
import lv.bkeeper.repository.EdsSubmissionRepository;
import lv.bkeeper.repository.JournalEntryRepository;
import lv.bkeeper.service.EdsReportService;
import lv.bkeeper.service.EdsXmlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(EdsController.class)
class EdsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EdsSubmissionRepository repo;

    @MockitoBean
    private CompanyRepository companyRepo;

    @MockitoBean
    private EdsReportService reportService;

    @MockitoBean
    private EdsXmlService xmlService;

    @MockitoBean
    private JournalEntryRepository journalRepo;

    // ── PVN declaration ───────────────────────────────────────────────────────

    @Test
    void pvn_shouldRenderReportForGivenPeriod() throws Exception {
        EdsReportService.PvnReport report = new EdsReportService.PvnReport();
        report.year = 2025;
        report.month = 3;
        report.vatPayable = 150.0;
        when(reportService.pvnReport(2025, 3)).thenReturn(report);
        when(companyRepo.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/eds/pvn").param("year", "2025").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("eds/pvn"))
                .andExpect(model().attribute("pvn", report))
                .andExpect(model().attribute("displayYear", 2025))
                .andExpect(model().attribute("displayMonth", 3))
                .andExpect(model().attribute("periodStart", "01.03.2025"))
                .andExpect(model().attribute("periodEnd", "31.03.2025"))
                .andExpect(model().attribute("currentPage", "eds-pvn"));

        verify(reportService).pvnReport(2025, 3);
    }

    @Test
    void pvn_withoutParams_shouldUseCurrentPeriod() throws Exception {
        when(reportService.pvnReport(anyInt(), anyInt())).thenReturn(new EdsReportService.PvnReport());
        when(companyRepo.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/eds/pvn"))
                .andExpect(status().isOk())
                .andExpect(view().name("eds/pvn"));
    }

    // ── PVN XML ──────────────────────────────────────────────────────────────

    @Test
    void pvnXml_withoutCompany_shouldReturn404() throws Exception {
        when(companyRepo.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/eds/pvn/xml").param("year", "2025").param("month", "3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pvnXml_withCompany_shouldReturnXmlAttachment() throws Exception {
        Company company = Company.builder().id(1).name("SIA Testa Uzņēmums").build();
        when(companyRepo.findById(1)).thenReturn(Optional.of(company));
        when(xmlService.generatePvnXml(company, 2025, 3)).thenReturn("<pvn-declaration/>");

        mockMvc.perform(get("/eds/pvn/xml").param("year", "2025").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"PVN_2025_03.xml\""))
                .andExpect(content().string("<pvn-declaration/>"));
    }

    // ── DDZ report ───────────────────────────────────────────────────────────

    @Test
    void ddz_shouldRenderReport() throws Exception {
        EdsReportService.DdzReport report = new EdsReportService.DdzReport();
        report.year = 2025;
        report.month = 6;
        report.totalGross = 5000.0;
        when(reportService.ddzReport(2025, 6)).thenReturn(report);
        when(companyRepo.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/eds/ddz").param("year", "2025").param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(view().name("eds/ddz"))
                .andExpect(model().attribute("ddz", report))
                .andExpect(model().attribute("displayYear", 2025))
                .andExpect(model().attribute("displayMonth", 6))
                .andExpect(model().attribute("currentPage", "eds-ddz"));
    }

    @Test
    void ddzXml_withoutCompany_shouldReturn404() throws Exception {
        when(companyRepo.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/eds/ddz/xml").param("year", "2025").param("month", "6"))
                .andExpect(status().isNotFound());
    }

    // ── UIN ──────────────────────────────────────────────────────────────────

    @Test
    void uin_shouldComputeProfitFromJournalSums() throws Exception {
        when(journalRepo.sumIncomeByYear(2025)).thenReturn(10000.0);
        when(journalRepo.sumExpenseByYear(2025)).thenReturn(4000.0);
        when(companyRepo.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/eds/uin").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(view().name("eds/uin"))
                .andExpect(model().attribute("displayYear", 2025))
                .andExpect(model().attribute("yearIncome", 10000.0))
                .andExpect(model().attribute("yearExpense", 4000.0))
                .andExpect(model().attribute("profit", 6000.0))
                .andExpect(model().attribute("currentPage", "eds-uin"));
    }

    @Test
    void uin_withoutYear_shouldDefaultToPreviousYear() throws Exception {
        int previousYear = java.time.LocalDate.now().getYear() - 1;
        when(journalRepo.sumIncomeByYear(previousYear)).thenReturn(1000.0);
        when(journalRepo.sumExpenseByYear(previousYear)).thenReturn(400.0);
        when(companyRepo.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/eds/uin"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("displayYear", previousYear));
    }

    // ── Annual report ────────────────────────────────────────────────────────

    @Test
    void annual_shouldComputeMonthlyBreakdown() throws Exception {
        when(journalRepo.sumIncomeByYear(2025)).thenReturn(12000.0);
        when(journalRepo.sumExpenseByYear(2025)).thenReturn(7200.0);
        when(journalRepo.sumIncomeByPeriod(eq(2025), anyInt())).thenReturn(1000.0);
        when(journalRepo.sumExpenseByPeriod(eq(2025), anyInt())).thenReturn(600.0);
        when(companyRepo.findById(1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/eds/annual").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(view().name("eds/annual"))
                .andExpect(model().attribute("displayYear", 2025))
                .andExpect(model().attribute("yearIncome", 12000.0))
                .andExpect(model().attribute("yearExpense", 7200.0))
                .andExpect(model().attribute("profit", 4800.0))
                .andExpect(model().attributeExists("monthlyIncome"))
                .andExpect(model().attributeExists("monthlyExpense"))
                .andExpect(model().attribute("currentPage", "eds-annual"));

        verify(journalRepo).sumIncomeByPeriod(2025, 1);
        verify(journalRepo).sumExpenseByPeriod(2025, 12);
    }
}
