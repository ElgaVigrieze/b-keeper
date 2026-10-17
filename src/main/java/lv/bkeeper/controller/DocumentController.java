package lv.bkeeper.controller;

import lv.bkeeper.service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Controller
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentStorageService storageService;

    /**
     * Serves a stored invoice document for download/viewing.
     * Usage: /documents/view?path=2026/06/1234567890_invoice.pdf
     *        /documents/view?path=2026/06/1234567890_invoice.pdf&dir=invoices-out
     */
    @GetMapping("/view")
    public ResponseEntity<Resource> view(@RequestParam String path,
                                         @RequestParam(defaultValue = DocumentStorageService.DIR_IN) String dir)
            throws IOException {
        Path file = storageService.resolve(dir, path);

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(file);
        if (contentType == null) contentType = "application/octet-stream";

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                // inline = opens in browser; attachment = forces download
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.getFileName() + "\"")
                .body(resource);
    }
}
