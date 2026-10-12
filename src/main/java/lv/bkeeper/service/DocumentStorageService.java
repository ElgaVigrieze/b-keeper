package lv.bkeeper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * Stores uploaded invoice documents on disk.
 * Location: ~/bkeeper/documents/invoices-in/YYYY/MM/
 *
 * Required by Latvian Grāmatvedības likums:
 * attaisnojuma dokumenti must be kept for 5 years.
 */
@Slf4j
@Service
public class DocumentStorageService {

    private static final Path BASE_DIR = Paths.get(
            System.getProperty("user.home"), "bkeeper", "documents", "invoices-in");

    /**
     * Saves the uploaded file and returns the relative path stored in the DB.
     * Format: YYYY/MM/originalFilename  (with timestamp prefix to avoid collisions)
     */
    public String store(MultipartFile file) throws IOException {
        LocalDate now = LocalDate.now();
        Path monthDir = BASE_DIR.resolve(
                String.valueOf(now.getYear()))
                .resolve(String.format("%02d", now.getMonthValue()));

        Files.createDirectories(monthDir);

        // Prefix with timestamp to avoid filename collisions
        String safeName = System.currentTimeMillis() + "_" + sanitise(file.getOriginalFilename());
        Path target = monthDir.resolve(safeName);

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored document: {}", target);

        // Return relative path for DB storage
        return now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/" + safeName;
    }

    /**
     * Resolves a stored relative path back to the full file path.
     */
    public Path resolve(String relativePath) {
        return BASE_DIR.resolve(relativePath);
    }

    /**
     * Deletes the stored file. Silent if file doesn't exist.
     */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Files.deleteIfExists(BASE_DIR.resolve(relativePath));
        } catch (IOException e) {
            log.warn("Could not delete document {}: {}", relativePath, e.getMessage());
        }
    }

    private String sanitise(String name) {
        if (name == null) return "document.pdf";
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
