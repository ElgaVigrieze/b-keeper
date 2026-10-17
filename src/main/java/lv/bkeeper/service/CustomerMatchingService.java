package lv.bkeeper.service;

import lv.bkeeper.domain.Customer;
import lv.bkeeper.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Matches an extracted outgoing-invoice client against the customer database.
 *
 * Matching strategy (in priority order):
 *   1. Exact VAT number match              — most reliable
 *   2. Exact registration number match     — reliable
 *   3. Exact name match (case-insensitive) — good
 *   4. Fuzzy name containment             — last resort
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerMatchingService {

    private final CustomerRepository repo;

    /**
     * Attempt to match against the customer database.
     * Returns the matched customer, or empty if no confident match found.
     */
    public Optional<Customer> findMatch(String extractedName, String extractedVat, String extractedRegNumber) {
        // 1. VAT number match — most reliable, try first
        if (extractedVat != null && !extractedVat.isBlank()) {
            String normVat = normalise(extractedVat);
            Optional<Customer> byVat = repo.findByVatNumberActive(normVat);
            if (byVat.isPresent()) {
                log.info("Customer matched by VAT '{}' → '{}'", normVat, byVat.get().getName());
                return byVat;
            }
            // Also try without country prefix: LV41203041233 → 41203041233
            if (normVat.length() > 2) {
                Optional<Customer> byVatStripped = repo.findByVatNumberActive(normVat.substring(2));
                if (byVatStripped.isPresent()) {
                    log.info("Customer matched by VAT (stripped) '{}' → '{}'",
                            normVat.substring(2), byVatStripped.get().getName());
                    return byVatStripped;
                }
            }
        }

        // 2. Registration number match
        if (extractedRegNumber != null && !extractedRegNumber.isBlank()) {
            String normReg = normalise(extractedRegNumber);
            Optional<Customer> byReg = repo.findByRegNumberActive(normReg);
            if (byReg.isPresent()) {
                log.info("Customer matched by reg number '{}' → '{}'", normReg, byReg.get().getName());
                return byReg;
            }
        }

        if (extractedName == null || extractedName.isBlank()) return Optional.empty();

        List<Customer> allCustomers = repo.findByActiveTrueOrderByName();
        String normName = extractedName.trim().toLowerCase();

        // 3. Exact name match
        for (Customer c : allCustomers) {
            if (c.getName() != null && c.getName().trim().equalsIgnoreCase(normName)) {
                log.info("Customer matched by exact name '{}' → '{}'", extractedName, c.getName());
                return Optional.of(c);
            }
        }

        // 4. Name containment — extracted name contains a known customer name (or vice versa)
        for (Customer c : allCustomers) {
            if (c.getName() == null) continue;
            String cName = c.getName().trim().toLowerCase();
            if (cName.length() < 3) continue;   // avoid matching on very short fragments
            if (normName.contains(cName) || cName.contains(normName)) {
                log.info("Customer matched by containment '{}' → '{}'", extractedName, c.getName());
                return Optional.of(c);
            }
        }

        log.info("No customer match found for '{}' / '{}' / '{}'", extractedName, extractedVat, extractedRegNumber);
        return Optional.empty();
    }

    private String normalise(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase().replaceAll("\\s+", "");
    }
}
