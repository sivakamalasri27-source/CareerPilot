package com.careerpilot.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
public class PdfExtractionService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    public static final int MIN_TEXT_LENGTH = 50;

    // PDF magic bytes: "%PDF" (0x25, 0x50, 0x44, 0x46)
    private static final byte[] PDF_MAGIC_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46};

    /**
     * Extracts text from an uploaded PDF file strictly in-memory.
     * Validates content-type, file size, and magic bytes.
     * Rejects scanned or empty PDFs. Never writes the PDF to disk.
     */
    public String extractText(MultipartFile file) {
        validateFile(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file contents", e);
        }

        validateMagicBytes(bytes);

        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text == null || text.trim().length() < MIN_TEXT_LENGTH) {
                throw new IllegalArgumentException(
                        "Extracted resume text is empty or too short. Scanned image-only PDFs without OCR text layer are not supported."
                );
            }

            return text.trim();
        } catch (IOException e) {
            log.warn("Failed to parse PDF document in memory: {}", e.getMessage());
            throw new IllegalArgumentException("Unable to read or parse PDF document: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds 5 MB limit. Size: " + file.getSize() + " bytes");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("application/pdf") && !contentType.equalsIgnoreCase("application/x-pdf"))) {
            throw new IllegalArgumentException("Invalid content type: " + contentType + ". Only PDF documents are allowed.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Invalid file extension. Only .pdf files are accepted.");
        }
    }

    private void validateMagicBytes(byte[] bytes) {
        if (bytes.length < PDF_MAGIC_BYTES.length) {
            throw new IllegalArgumentException("File is too small to be a valid PDF document");
        }

        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (bytes[i] != PDF_MAGIC_BYTES[i]) {
                throw new IllegalArgumentException("File is not a valid PDF document (magic byte signature mismatch)");
            }
        }
    }
}
