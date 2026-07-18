package com.careermatch.backend.resume.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Optimized PDF/DOCX text extraction service.
 *
 * Strategy (fastest-first cascade):
 *  1. PDFBox — reads from in-memory byte array (no temp file I/O for the parse step).
 *     Sorts by text position for logical reading order. Fastest for standard PDFs.
 *  2. Apache Tika — pure-Java fallback. Slightly slower but handles more formats.
 *  3. PyMuPDF Python subprocess — last resort for encrypted/complex PDFs (30s timeout).
 *
 * Text cleaning removes control chars and collapses whitespace before returning.
 */
@Service
@Slf4j
public class PdfParserService {

    /** Re-use a single Tika instance (heavy init cost); it is thread-safe. */
    private final Tika tika = new Tika();

    public String parsePdf(InputStream inputStream) throws IOException {
        long startTotal = System.currentTimeMillis();

        // Read the full stream into memory once — avoids repeated disk I/O across fallbacks.
        byte[] pdfBytes = inputStream.readAllBytes();
        log.info("[PDF] Read {} bytes from stream in {} ms",
                pdfBytes.length, System.currentTimeMillis() - startTotal);

        String extractedText = null;

        // ── Strategy 1: Apache PDFBox (in-memory byte array, no temp file) ─────────
        long t1 = System.currentTimeMillis();
        try {
            log.info("[PDF] Attempting PDFBox extraction from in-memory bytes...");
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                // sortByPosition gives logical reading order, which improves LLM accuracy.
                stripper.setSortByPosition(true);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    log.info("[TIMING][PDF] PDFBox extraction took {} ms ({} chars)",
                            System.currentTimeMillis() - t1, text.length());
                    extractedText = text;
                } else {
                    log.warn("[PDF] PDFBox returned empty text — trying Tika.");
                }
            }
        } catch (Exception e) {
            log.warn("[PDF] PDFBox failed ({} ms): {} — falling back to Tika.",
                    System.currentTimeMillis() - t1, e.getMessage());
        }

        // ── Strategy 2: Apache Tika (still in-memory via ByteArrayInputStream) ────
        if (extractedText == null) {
            long t2 = System.currentTimeMillis();
            try {
                log.info("[PDF] Attempting Apache Tika extraction...");
                String text = tika.parseToString(new ByteArrayInputStream(pdfBytes));
                if (text != null && !text.isBlank()) {
                    log.info("[TIMING][PDF] Tika extraction took {} ms ({} chars)",
                            System.currentTimeMillis() - t2, text.length());
                    extractedText = text;
                } else {
                    log.warn("[PDF] Tika returned empty text — falling back to PyMuPDF.");
                }
            } catch (Exception e) {
                log.warn("[PDF] Tika failed ({} ms): {} — falling back to PyMuPDF.",
                        System.currentTimeMillis() - t2, e.getMessage());
            }
        }

        // ── Strategy 3: PyMuPDF Python subprocess (last resort, writes a temp file) ─
        if (extractedText == null) {
            long t3 = System.currentTimeMillis();
            Path tempFile = Files.createTempFile("resume-pymupdf-", ".pdf");
            try {
                Files.write(tempFile, pdfBytes);
                log.info("[PDF] Attempting PyMuPDF (Python) extraction...");
                String text = runPythonParser(tempFile.toAbsolutePath().toString());
                if (text != null && !text.isBlank()) {
                    log.info("[TIMING][PDF] PyMuPDF extraction took {} ms ({} chars)",
                            System.currentTimeMillis() - t3, text.length());
                    extractedText = text;
                }
            } catch (Exception e) {
                log.error("[PDF] PyMuPDF fallback failed ({} ms): {}",
                        System.currentTimeMillis() - t3, e.getMessage());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        if (extractedText == null || extractedText.isBlank()) {
            throw new RuntimeException(
                    "All PDF parsers (PDFBox, Tika, PyMuPDF) failed to extract text from the resume. " +
                    "The file may be a scanned image-only PDF with no embedded text.");
        }

        // ── Text cleaning ─────────────────────────────────────────────────────────
        long cleanStart = System.currentTimeMillis();
        String cleaned = cleanText(extractedText);
        log.info("[TIMING][PDF] Text cleaning took {} ms. Chars: {} → {}",
                System.currentTimeMillis() - cleanStart, extractedText.length(), cleaned.length());

        log.info("[TIMING][PDF] Total parsePdf pipeline took {} ms",
                System.currentTimeMillis() - startTotal);

        return cleaned;
    }

    /**
     * Removes non-printable control characters, collapses excessive whitespace,
     * and trims leading/trailing space. Keeps newlines for structural context.
     */
    public String cleanText(String text) {
        if (text == null) return "";
        // Remove non-printable control chars (except tab \t and newline \n)
        String cleaned = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        // Collapse multiple spaces (not newlines) to single space
        cleaned = cleaned.replaceAll("[ \t]+", " ");
        // Collapse 3+ consecutive newlines to 2 (preserve paragraph breaks)
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        // Trim lines of only whitespace
        cleaned = cleaned.replaceAll("(?m)^[ \t]+$", "");
        return cleaned.trim();
    }

    private String runPythonParser(String absolutePath) throws Exception {
        Path tempScript = Files.createTempFile("pdf_parser-", ".py");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("scripts/pdf_parser.py");
             OutputStream os = Files.newOutputStream(tempScript)) {
            if (is == null) {
                throw new FileNotFoundException("Classpath resource scripts/pdf_parser.py not found");
            }
            is.transferTo(os);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("python", tempScript.toAbsolutePath().toString(), absolutePath);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Read stdout and stderr concurrently to prevent pipe buffer blocking
            StringBuilder output = new StringBuilder();
            StringBuilder errors = new StringBuilder();

            Thread stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            });
            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errors.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            });
            stdoutReader.start();
            stderrReader.start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            stdoutReader.join(2000);
            stderrReader.join(2000);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("PyMuPDF Python parser timed out after 30 seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("Python script exited with code " + exitCode + ". Stderr: " + errors);
            }
            return output.toString();
        } finally {
            Files.deleteIfExists(tempScript);
        }
    }
}
