package lv.bkeeper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * Stores invoice documents on disk.
 * Location: ~/bkeeper/documents/{invoices-in|invoices-out}/YYYY/MM/
 *
 * Required by Latvian Grāmatvedības likums:
 * attaisnojuma dokumenti must be kept for 5 years.
 */
@Slf4j
@Service
public class DocumentStorageService {

    /** Sub-directory for incoming invoices (supplier documents). */
    public static final String DIR_IN  = "invoices-in";
    /** Sub-directory for outgoing invoices (documents imported from Google Drive, etc.). */
    public static final String DIR_OUT = "invoices-out";

    private static final Path BASE_DIR = Paths.get(
            System.getProperty("user.home"), "bkeeper", "documents");

    /**
     * Saves an uploaded incoming-invoice file and returns the relative path
     * stored in the DB. Format: YYYY/MM/originalFilename (timestamp-prefixed).
     */
    public String store(MultipartFile file) throws IOException {
        return store(file.getBytes(), file.getOriginalFilename(), DIR_IN);
    }

    /**
     * Saves a file and returns the relative path stored in the DB.
     * Format: YYYY/MM/originalFilename (with timestamp prefix to avoid collisions)
     */
    public String store(byte[] data, String originalFilename, String subDir) throws IOException {
        String dir = (subDir == null || subDir.isBlank()) ? DIR_IN : subDir;
        LocalDate now = LocalDate.now();
        Path monthDir = BASE_DIR.resolve(dir)
                .resolve(String.valueOf(now.getYear()))
                .resolve(String.format("%02d", now.getMonthValue()));

        Files.createDirectories(monthDir);

        // Prefix with timestamp to avoid filename collisions
        String safeName = System.currentTimeMillis() + "_" + sanitise(originalFilename);
        Path target = monthDir.resolve(safeName);

        Files.write(target, data);
        log.info("Stored document: {}", target);

        // Return relative path for DB storage
        return now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/" + safeName;
    }

    /**
     * Resolves a stored relative path back to the full file path
     * within the given sub-directory.
     */
    public Path resolve(String subDir, String relativePath) {
        String dir = (subDir == null || subDir.isBlank()) ? DIR_IN : subDir;
        return BASE_DIR.resolve(dir).resolve(relativePath);
    }

    /** Resolves a stored incoming-invoice relative path (legacy signature). */
    public Path resolve(String relativePath) {
        return resolve(DIR_IN, relativePath);
    }

    /**
     * Deletes a stored file from the given sub-directory. Silent if it doesn't exist.
     */
    public void delete(String subDir, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(subDir, relativePath));
        } catch (IOException e) {
            log.warn("Could not delete document {}: {}", relativePath, e.getMessage());
        }
    }

    /** Deletes a stored incoming-invoice file (legacy signature). */
    public void delete(String relativePath) {
        delete(DIR_IN, relativePath);
    }

    private String sanitise(String name) {
        if (name == null) return "document.pdf";
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
