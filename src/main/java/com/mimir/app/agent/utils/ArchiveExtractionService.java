package com.mimir.app.agent.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for ZIP archive operations.
 * Provides methods for extracting files from ZIP archives.
 */
@Slf4j
@Component
public class ArchiveExtractionService {

    private static final int BUFFER_SIZE = 4096;
    private static final byte[] ZIP_MAGIC_BYTES = {0x50, 0x4B, 0x03, 0x04};

    public boolean isZipFile(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        
        for (int i = 0; i < 4; i++) {
            if (bytes[i] != ZIP_MAGIC_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    public Map<String, byte[]> extractPdfFiles(byte[] zipBytes) {
        if (!isZipFile(zipBytes)) {
            log.info("File is not a ZIP archive, treating as single PDF");
            return Map.of("extracted.pdf", zipBytes);
        }

        Map<String, byte[]> extractedFiles = new LinkedHashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && isPdfFile(entry.getName())) {
                    byte[] fileBytes = readEntryBytes(zis);
                    extractedFiles.put(entry.getName(), fileBytes);
                    log.debug("Extracted PDF: {} ({} bytes)", entry.getName(), fileBytes.length);
                }
                zis.closeEntry();
            }
            
            log.info("Successfully extracted {} PDF files from ZIP", extractedFiles.size());
            
        } catch (Exception e) {
            log.error("Failed to extract PDFs from ZIP: {}", e.getMessage(), e);
        }

        return extractedFiles;
    }

    private boolean isPdfFile(String filename) {
        return filename.toLowerCase().endsWith(".pdf");
    }

    private byte[] readEntryBytes(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        
        while ((bytesRead = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, bytesRead);
        }
        
        return baos.toByteArray();
    }
}