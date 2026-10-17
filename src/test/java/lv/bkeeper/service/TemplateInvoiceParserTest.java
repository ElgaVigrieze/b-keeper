package lv.bkeeper.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the deterministic outgoing-invoice template parser against the
 * user's real sheet-export layout (as produced by Google Sheets → PDF).
 */
class TemplateInvoiceParserTest {

    /** Real layout (client name/reg swapped for a neutral example). */
    private static final String REAL_INVOICE = """
            SIA „VentSystem.lv” VAJAG SŪTĪT UZ olimps@ventspils.lv
            tel.+371 25911891, ventsystem@inbox.lv
            Rēķins Nr. VS26/01
            05.01.2026
            Izpildītājs: SIA „VentSystem.lv”
            Adrese: Lielais prospekts 18 – 11, Ventspils, LV-3601
            Reģistrācijas Nr. LV41203041233
            Norēķinu rekvizīti: A/S „Swedbank”
            Konts: LV51HABA0551031251238
            Pasūtītājs: SIA Paraugs
            Adrese: Sporta iela 7/9, Ventspils, LV-3601
            Reģistrācijas Nr. 40000000000
            Norēķinu rekvizīti: AS Swedbank
            Konts: LV29HABA0001408060044
            Apmaksas veids: pārskaitījums
            Apmaksas termiņš: līdz 23.01.2026
            Maksājuma pamatojums
            Nosaukums Daudz. Cena Summa
            Mobilā LED video ekrāna nogādāšana un montāža /
            demontāža Lielais laukums, Ventspils, 31.12.2025. 1 €500,00 €500,00
            PVN 21% €105,00
            Kopā €605,00
            Rēķins ir sagatavots elektroniski un ir derīgs bez paraksta
            """;

    @Test
    void extractsAllFieldsFromRealLayout() {
        Map<String, String> m = TemplateInvoiceParser.parseOutgoing(REAL_INVOICE);

        assertEquals("VS26/01", m.get("invoiceNumber"));
        assertEquals("2026-01-05", m.get("issueDate"));
        assertEquals("2026-01-23", m.get("dueDate"));
        assertEquals("SIA Paraugs", m.get("clientName"));
        assertEquals("40000000000", m.get("clientRegNumber"));
        assertEquals("40000000000", m.get("clientVatNumber"));
        assertEquals("21", m.get("vatRate"));
        assertEquals("105.00", m.get("vatAmount"));
        assertEquals("500.00", m.get("amountExclVat"));
        assertEquals("605.00", m.get("amountInclVat"));
        assertNotNull(m.get("description"));
        assertTrue(m.get("description").startsWith("Mobilā LED video ekrāna"));
        assertTrue(m.get("description").endsWith("31.12.2025."));
        assertTrue(TemplateInvoiceParser.hasCoreFields(m));
    }

    @Test
    void handlesThousandsSeparatorsAndThreeDigitNumbers() {
        String invoice = """
                Rēķins Nr. VS26/115
                16.10.2026
                Pasūtītājs: SIA Liels Klients
                Reģistrācijas Nr. 41234567890
                Apmaksas termiņš: līdz 30.10.2026
                Nosaukums Daudz. Cena Summa
                Konsultācijas 2 €1 234,56 €2 469,12
                PVN 21% €518,52
                Kopā €2 987,64
                """;

        Map<String, String> m = TemplateInvoiceParser.parseOutgoing(invoice);

        assertEquals("VS26/115", m.get("invoiceNumber"));
        assertEquals("2026-10-16", m.get("issueDate"));
        assertEquals("2026-10-30", m.get("dueDate"));
        assertEquals("SIA Liels Klients", m.get("clientName"));
        assertEquals("2469.12", m.get("amountExclVat"));
        assertEquals("518.52", m.get("vatAmount"));
        assertEquals("2987.64", m.get("amountInclVat"));
        assertEquals("Konsultācijas", m.get("description"));
        assertTrue(TemplateInvoiceParser.hasCoreFields(m));
    }

    @Test
    void derivesVatWhenAmountMissing() {
        String invoice = """
                Rēķins Nr. VS26/77
                10.06.2026
                Pasūtītājs: SIA Klients
                Reģistrācijas Nr. 41111111111
                Nosaukums Daudz. Cena Summa
                Pakalpojums 1 €100,00 €100,00
                PVN 21%
                Kopā €121,00
                """;

        Map<String, String> m = TemplateInvoiceParser.parseOutgoing(invoice);

        assertEquals("100.00", m.get("amountExclVat"));
        assertEquals("21.00", m.get("vatAmount"));
        assertEquals("121.00", m.get("amountInclVat"));
    }

    @Test
    void returnsNoCoreFieldsForForeignDocuments() {
        Map<String, String> m = TemplateInvoiceParser.parseOutgoing(
                "Pavisam cits dokuments\nSaraksts 2026\n1 2 3\nCita summa 10,00");

        assertFalse(TemplateInvoiceParser.hasCoreFields(m));
    }

    /** VS26/15-18 variant: "Rēķins - pavadzīme Nr. VS26 / 16", date with trailing dot. */
    @Test
    void readsPavadzimeVariantWithSpacedNumber() {
        String invoice = """
                SIA „VentSystem.lv”
                tel.+371 25911891, ventsystem@inbox.lv
                Rēķins - pavadzīme Nr. VS26 / 16
                14.04.2026.
                Izpildītājs: SIA „VentSystem.lv”
                Reģistrācijas Nr. LV41203041233
                Pasūtītājs: Ventspils valstspilsētas pašvaldības iestāde "Ventspils Izglītības pārvalde"
                Reģistrācijas Nr. 90000052497
                Apmaksas termiņš: līdz 21.04.2026
                Nosaukums Daudz. Cena Summa
                Bateriju komplekts radiomikrofoniem. 2 €123,96 €247,92
                , €247,92
                PVN 21% €52,06
                Kopā €299,98
                """;

        Map<String, String> m = TemplateInvoiceParser.parseOutgoing(invoice);

        assertEquals("VS26/16", m.get("invoiceNumber"));
        assertEquals("2026-04-14", m.get("issueDate"));
        assertEquals("2026-04-21", m.get("dueDate"));
        assertEquals("90000052497", m.get("clientRegNumber"));
        assertEquals("247.92", m.get("amountExclVat"));
        assertEquals("52.06", m.get("vatAmount"));
        assertEquals("299.98", m.get("amountInclVat"));
        assertNotNull(m.get("description"));
        assertTrue(m.get("description").startsWith("Bateriju komplekts"));
        assertTrue(TemplateInvoiceParser.hasCoreFields(m));
    }
}
