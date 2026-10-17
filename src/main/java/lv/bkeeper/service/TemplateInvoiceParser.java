package lv.bkeeper.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic field extractor for the user's outgoing invoice template
 * ("Rēķins Nr. VS26/xx" Google-Sheets export layout).
 *
 * The PDF text layer is parsed with label-based regexes. When all core
 * fields are found the AI (Ollama) step can be skipped entirely — this is
 * instant and 100% repeatable. If the layout is not recognised, the caller
 * falls back to the LLM extraction.
 *
 * Reads: invoice number + issue date (below the number), "Pasūtītājs"
 * (client) block, client "Reģistrācijas Nr." (the one AFTER the client
 * label — the first belongs to the issuer), "Apmaksas termiņš" due date,
 * "PVN xx%" / "PVN amount" / "Kopā total" amounts and the item table as
 * the description.
 */
public final class TemplateInvoiceParser {

    private static final int UNICODE =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern INVOICE_NR =
            Pattern.compile("(?<!\\w)Rēķin\\w*[^\\n]{0,25}(?:Nr\\.?|numurs)\\s*:?\\s*([A-Za-z0-9][A-Za-z0-9/_.\\- ]*)", UNICODE);
    private static final Pattern DATE_DMY =
            Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern DUE_DATE =
            Pattern.compile("Apmaksas\\s+termiņš[^\\d\\n]{0,25}(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})", UNICODE);
    private static final Pattern CLIENT =
            Pattern.compile("Pasūtītājs\\s*:?[ \\t]*([^\\n]*)", UNICODE);
    private static final Pattern CLIENT_NEXT_LINE =
            Pattern.compile("Pasūtītājs\\s*:?[ \\t]*\\n[ \\t]*([^\\n]+)", UNICODE);
    private static final Pattern REG_NR =
            Pattern.compile("Reģistrācijas\\s+Nr\\.?\\s*:?\\s*([A-Z]{0,2}\\d[\\dA-Z]*)", UNICODE);
    private static final Pattern VAT_RATE =
            Pattern.compile("PVN\\s+(\\d{1,2}(?:[.,]\\d+)?)\\s*%", UNICODE);
    private static final Pattern VAT_AMOUNT =
            Pattern.compile("PVN\\s+\\d{1,2}(?:[.,]\\d+)?\\s*%\\s*€?\\s*([0-9][0-9 .,]*)", UNICODE);
    private static final Pattern TOTAL =
            Pattern.compile("Kopā\\s*:?\\s*€?\\s*([0-9][0-9 .,]*)", UNICODE);
    private static final Pattern ITEMS_HEADER =
            Pattern.compile("Nosaukums\\s+Daudz\\.?\\s+Cena\\s+Summa", UNICODE);
    /** Row tail: quantity + two prices, e.g. " 1 €500,00 €500,00" (thousands may contain spaces). */
    private static final Pattern QTY_TAIL =
            Pattern.compile("\\s+\\d+(?:[.,]\\d+)?\\s+€\\s*[0-9][0-9 .,]*\\s+€\\s*[0-9][0-9 .,]*\\s*$");
    /** Row tail with only one price, e.g. " 1 €500,00". */
    private static final Pattern PRICE_TAIL =
            Pattern.compile("\\s+\\d+(?:[.,]\\d+)?\\s+€\\s*[0-9][0-9 .,]*\\s*$");

    private TemplateInvoiceParser() {
    }

    /**
     * Extracts invoice fields from the PDF text layer.
     * Fields that cannot be found are simply absent from the returned map.
     */
    public static Map<String, String> parseOutgoing(String rawText) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rawText == null || rawText.isBlank()) {
            return out;
        }
        String text = rawText.replace('\u00A0', ' ');

        // ── Invoice number + issue date (date sits right below the number) ──
        Matcher nr = INVOICE_NR.matcher(text);
        if (nr.find()) {
            out.put("invoiceNumber", nr.group(1).replaceAll("\\s+", ""));
            String window = text.substring(nr.end(), Math.min(text.length(), nr.end() + 300));
            Matcher d = DATE_DMY.matcher(window);
            if (d.find()) {
                out.put("issueDate", toIso(d.group(1), d.group(2), d.group(3)));
            }
        }

        // ── Due date ──
        Matcher due = DUE_DATE.matcher(text);
        if (due.find()) {
            out.put("dueDate", toIso(due.group(1), due.group(2), due.group(3)));
        }

        // ── Client (Pasūtītājs) ──
        int clientEnd = -1;
        Matcher cl = CLIENT.matcher(text);
        if (cl.find()) {
            String name = clean(cl.group(1));
            if (name.isBlank()) {                        // name moved to the next line
                Matcher nxt = CLIENT_NEXT_LINE.matcher(text);
                if (nxt.find()) name = clean(nxt.group(1));
            }
            if (!name.isBlank()) {
                out.put("clientName", name);
            }
            clientEnd = cl.end();
        }

        // ── Client registration number: the "Reģistrācijas Nr." AFTER the client label ──
        if (clientEnd >= 0) {
            Matcher reg = REG_NR.matcher(text);
            while (reg.find()) {
                if (reg.start() > clientEnd) {
                    String value = reg.group(1).trim();
                    out.put("clientRegNumber", value);
                    out.put("clientVatNumber", value);   // template shows one number for the client
                    break;
                }
            }
        }

        // ── VAT rate ──
        Matcher rate = VAT_RATE.matcher(text);
        if (rate.find()) {
            out.put("vatRate", clean(rate.group(1)).replace(',', '.'));
        }

        // ── VAT amount ──
        Double vatAmount = null;
        Matcher vat = VAT_AMOUNT.matcher(text);
        if (vat.find()) {
            vatAmount = parseAmount(vat.group(1));
            if (vatAmount != null) {
                out.put("vatAmount", two(vatAmount));
            }
        }

        // ── Total (Kopā) and subtotal (derived: total − VAT) ──
        Matcher total = TOTAL.matcher(text);
        if (total.find()) {
            Double incl = parseAmount(total.group(1));
            if (incl != null) {
                out.put("amountInclVat", two(incl));
                if (vatAmount == null) {                 // derive VAT from the rate
                    Double r = parseAmount(out.get("vatRate"));
                    if (r != null && r > 0) {
                        vatAmount = round2(incl * r / (100.0 + r));
                        out.put("vatAmount", two(vatAmount));
                    }
                }
                if (vatAmount != null) {
                    out.put("amountExclVat", two(incl - vatAmount));
                }
            }
        }

        // ── Description: item table rows between the header and "PVN"/"Kopā" ──
        Matcher header = ITEMS_HEADER.matcher(text);
        if (header.find()) {
            int from = header.end();
            int to = Math.min(firstStartAfter(text, from, VAT_RATE, VAT_AMOUNT, TOTAL),
                    Math.min(text.length(), from + 600));
            String desc = describeItems(text.substring(from, to));
            if (!desc.isBlank()) {
                out.put("description", desc);
            }
        }

        return out;
    }

    /** True when the fields required for a usable draft were all found. */
    public static boolean hasCoreFields(Map<String, String> m) {
        return nonBlank(m.get("invoiceNumber"))
                && nonBlank(m.get("issueDate"))
                && nonBlank(m.get("clientName"))
                && nonBlank(m.get("amountInclVat"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int firstStartAfter(String text, int from, Pattern... patterns) {
        int end = text.length();
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find(from)) {
                end = Math.min(end, m.start());
            }
        }
        return end;
    }

    /** Joins item-table lines and strips the trailing quantity/price columns. */
    private static String describeItems(String block) {
        StringBuilder sb = new StringBuilder();
        for (String line : block.split("\\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.matches("(?i)^PVN\\b.*") || l.matches("^[€\\s\\d.,]+$")) {
                continue;
            }
            l = QTY_TAIL.matcher(l).replaceFirst("");
            l = PRICE_TAIL.matcher(l).replaceFirst("");
            l = l.trim();
            if (!l.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(l);
            }
        }
        return sb.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private static String toIso(String d, String m, String y) {
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d)).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static Double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.replace('\u00A0', ' ').replaceAll("\\s+", "").trim();
        s = s.replaceAll("[.,]+$", "");
        if (s.isEmpty()) return null;
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            s = lastDot > lastComma ? s.replace(",", "") : s.replace(".", "").replace(",", ".");
        } else if (lastComma >= 0) {
            s = s.replace(",", ".");
        }
        try {
            return new BigDecimal(s).doubleValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String two(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
