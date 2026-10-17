package lv.bkeeper.service;

import lv.bkeeper.domain.Supplier;
import lv.bkeeper.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Matches an extracted invoice supplier name/VAT against the supplier database.
 *
 * Matching strategy (in priority order):
 *   1. Exact VAT number match              — most reliable
 *   2. Exact registration number match     — reliable
 *   3. Exact name match (case-insensitive) — good
 *   4. Alias match (any alias fragment)    — catches OCR variants
 *   5. Fuzzy name containment             — last resort
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierMatchingService {

    private final SupplierRepository repo;

    /**
     * Attempt to match against the supplier database.
     * Returns the matched supplier, or empty if no confident match found.
     */
    public Optional<Supplier> findMatch(String extractedName, String extractedVat) {
        // 1. VAT number match — most reliable, try first
        if (extractedVat != null && !extractedVat.isBlank()) {
            String normVat = normaliseVat(extractedVat);
            Optional<Supplier> byVat = repo.findByVatNumberActive(normVat);
            if (byVat.isPresent()) {
                log.info("Supplier matched by VAT '{}' → '{}'", normVat, byVat.get().getName());
                return byVat;
            }
            // Also try without country prefix: LV41203041233 → 41203041233
            if (normVat.length() > 2) {
                Optional<Supplier> byVatStripped = repo.findByVatNumberActive(normVat.substring(2));
                if (byVatStripped.isPresent()) {
                    log.info("Supplier matched by VAT (stripped) '{}' → '{}'",
                            normVat.substring(2), byVatStripped.get().getName());
                    return byVatStripped;
                }
            }
        }

        // 2–5. Name-based matching
        if (extractedName == null || extractedName.isBlank()) return Optional.empty();

        List<Supplier> allSuppliers = repo.findByActiveTrueOrderByName();
        String normName = extractedName.trim().toLowerCase();

        // 2. Exact name match
        for (Supplier s : allSuppliers) {
            if (s.getName().trim().equalsIgnoreCase(normName)) {
                log.info("Supplier matched by exact name '{}' → '{}'", extractedName, s.getName());
                return Optional.of(s);
            }
        }

        // 3. Alias match
        for (Supplier s : allSuppliers) {
            if (s.getAliases() != null && !s.getAliases().isBlank()) {
                for (String alias : s.getAliases().split(",")) {
                    if (alias.trim().equalsIgnoreCase(normName)) {
                        log.info("Supplier matched by alias '{}' → '{}'", extractedName, s.getName());
                        return Optional.of(s);
                    }
                }
            }
        }

        // 4. Name containment — extracted name contains a known supplier name
        for (Supplier s : allSuppliers) {
            String sName = s.getName().trim().toLowerCase();
            if (normName.contains(sName) || sName.contains(normName)) {
                log.info("Supplier matched by containment '{}' → '{}'", extractedName, s.getName());
                return Optional.of(s);
            }
            // Also check aliases for containment
            if (s.getAliases() != null) {
                for (String alias : s.getAliases().split(",")) {
                    String a = alias.trim().toLowerCase();
                    if (!a.isBlank() && (normName.contains(a) || a.contains(normName))) {
                        log.info("Supplier matched by alias containment '{}' → '{}'",
                                extractedName, s.getName());
                        return Optional.of(s);
                    }
                }
            }
        }

        log.info("No supplier match found for '{}' / '{}'", extractedName, extractedVat);
        return Optional.empty();
    }

    private String normaliseVat(String vat) {
        if (vat == null) return "";
        return vat.trim().toUpperCase().replaceAll("\\s+", "");
    }
}
