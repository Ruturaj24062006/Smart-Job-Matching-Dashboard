package com.careermatch.backend.resume.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class PdfParserService {

    private final Tika tika = new Tika();

    public String parsePdf(InputStream inputStream) throws IOException {
        // Copy stream to a temp file for PyMuPDF script
        Path tempFile = Files.createTempFile("resume-", ".pdf");
        try (OutputStream out = Files.newOutputStream(tempFile)) {
            inputStream.transferTo(out);
        }

        try {
            log.info("Attempting PDF text extraction via PyMuPDF (fitz) script...");
            String text = runPythonParser(tempFile.toAbsolutePath().toString());
            if (text != null && !text.isBlank()) {
                log.info("PyMuPDF PDF extraction succeeded.");
                // Delete temp file after successful extraction
                Files.deleteIfExists(tempFile);
                return text;
            }
        } catch (Exception e) {
            log.warn("PyMuPDF extraction failed: {}. Falling back to Apache Tika.", e.getMessage());
        }

        // Fallback to Apache Tika using the same temp file, then clean up
        log.info("Attempting PDF text extraction via Apache Tika fallback...");
        return parseWithTika(tempFile);
    }

    private String runPythonParser(String absolutePath) throws Exception {
        String scriptPath = "d:/job matching/backend/src/main/resources/scripts/pdf_parser.py";
        ProcessBuilder pb = new ProcessBuilder("python", scriptPath, absolutePath);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            StringBuilder error = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }
            throw new RuntimeException("Python script exited with code " + exitCode + ". Error: " + error);
        }
        return output.toString();
    }

    private String parseWithTika(Path filePath) {
        try (InputStream is = Files.newInputStream(filePath)) {
            return tika.parseToString(is);
        } catch (Exception e) {
            log.error("Apache Tika fallback parsing failed: {}", e.getMessage());
            throw new RuntimeException("All PDF parsers failed to extract text: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(filePath);
            } catch (Exception ex) {
                log.warn("Failed to delete temp file: {}", ex.getMessage());
            }
        }
    }
}
