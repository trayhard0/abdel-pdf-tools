package com.example.pdfConverter;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    // ==========================================
    // 1. MERGE PDFs
    // ==========================================
    @PostMapping("/merge")
    public ResponseEntity<?> mergePdfs(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length < 2) {
            return ResponseEntity.badRequest().body("Please provide at least two PDF files to merge.");
        }

        // Security: Verify EVERY file in the array is actually a PDF
        for (MultipartFile file : files) {
            if (file.isEmpty() || !FileSecurityUtils.isValidFile(file, "application/pdf")) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .body("One or more files are invalid. All files must be PDFs.");
            }
        }

        try {
            PDFMergerUtility pdfMerger = new PDFMergerUtility();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            pdfMerger.setDestinationStream(outputStream);

            for(MultipartFile file : files) {
                InputStream inputStream = file.getInputStream();
                pdfMerger.addSource(inputStream);
            }

            pdfMerger.mergeDocuments(null);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment","merged.pdf");

            return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);
        } catch(Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Merge failed.");
        }
    }

    // ==========================================
    // 2. HEIC TO PDF
    // ==========================================
    @PostMapping("/heic-to-pdf")
    public ResponseEntity<?> convertHeicToPdf(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty.");
        }

        // Security: Verify it is actually an HEIC/HEIF image
        if (!FileSecurityUtils.isValidFile(file, "image/heic") && !FileSecurityUtils.isValidFile(file, "image/heif")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Invalid file type. Must be an HEIC file.");
        }

        // Declare temp files outside the try block so the finally block can access them
        File tempHeic = null;
        File tempPdf = null;

        try {
            tempHeic = File.createTempFile("upload_", ".heic");
            tempPdf = File.createTempFile("output_", ".pdf");

            file.transferTo(tempHeic);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "magick",
                    tempHeic.getAbsolutePath(),
                    "-resize", "612x792",
                    "-background", "white",
                    "-gravity", "center",
                    "-extent", "612x792",
                    "-compress", "jpeg",
                    "-quality", "80",
                    tempPdf.getAbsolutePath()
            );
            Process process = processBuilder.start();

            process.waitFor();

            byte[] pdfBytes = Files.readAllBytes(tempPdf.toPath());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment","converted_heic.pdf");

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("HEIC Conversion failed.");
        } finally {
            // Security: Guaranteed cleanup of server hard drive
            if (tempHeic != null && tempHeic.exists()) {
                tempHeic.delete();
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }

    // ==========================================
    // 3. TXT TO PDF
    // ==========================================
    @PostMapping("/txt-to-pdf")
    public ResponseEntity<?> convertTxtToPdf(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty.");
        }

        // Security: Verify it is actually a text file
        if (!FileSecurityUtils.isValidFile(file, "text/plain")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Invalid file type. Must be a TXT file.");
        }

        // The try-with-resources statement automatically closes the PDDocument to prevent memory leaks
        try (PDDocument document = new PDDocument()) {

            PDPage page = new PDPage();
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            // Define our font, sizing, and margins
            PDType1Font font = PDType1Font.COURIER;
            float fontSize = 12f;
            float leading = 14.5f;
            float startX = 25f;
            float startY = 750f;
            float bottomMargin = 50f;
            float currentY = startY;

            // Calculate the maximum width allowed
            float maxWidth = page.getMediaBox().getWidth() - (startX * 2);

            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.setLeading(leading);
            contentStream.newLineAtOffset(startX, startY);

            String text = new String(file.getBytes());
            String[] paragraphs = text.split("\\r?\\n");

            List<String> wrappedLines = new ArrayList<>();

            for (String paragraph : paragraphs) {
                // Replace tabs with 4 spaces
                paragraph = paragraph.replace("\t", "    ");

                if (paragraph.trim().isEmpty()) {
                    wrappedLines.add("");
                    continue;
                }

                // --- INDENTATION SAVING LOGIC ---
                // Count how many spaces are at the start of the line
                String leadingSpaces = "";
                int spaceIndex = 0;
                while (spaceIndex < paragraph.length() && paragraph.charAt(spaceIndex) == ' ') {
                    leadingSpaces += " ";
                    spaceIndex++;
                }

                // Cut those spaces off before we chop the sentence into words
                String textToWrap = paragraph.substring(spaceIndex);
                String[] words = textToWrap.split(" ");

                // Start our new line with the saved indentation!
                StringBuilder currentLine = new StringBuilder(leadingSpaces);

                for (String word : words) {
                    if (word.isEmpty()) continue; // Skip extra accidental spaces between words

                    // If the line ONLY has our indent so far, just add the word without an extra space
                    if (currentLine.toString().trim().isEmpty()) {
                        currentLine.append(word);
                    } else {
                        String testLine = currentLine.toString() + " " + word;
                        float testWidth = (font.getStringWidth(testLine) / 1000.0f) * fontSize;

                        if (testWidth > maxWidth) {
                            wrappedLines.add(currentLine.toString());
                            // If we wrap to a new line, start that new line with the SAME indentation!
                            currentLine = new StringBuilder(leadingSpaces).append(word);
                        } else {
                            currentLine.append(" ").append(word);
                        }
                    }
                }

                if (currentLine.length() > 0) {
                    wrappedLines.add(currentLine.toString());
                }
                // -------------------------------------
            }

            // Loop through our perfectly wrapped and indented lines and draw them
            for (String line : wrappedLines) {

                if (currentY <= bottomMargin) {
                    contentStream.endText();
                    contentStream.close();

                    page = new PDPage();
                    document.addPage(page);

                    contentStream = new PDPageContentStream(document, page);
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.setLeading(leading);

                    currentY = startY;
                    contentStream.newLineAtOffset(startX, currentY);
                }

                contentStream.showText(line);
                contentStream.newLine();

                currentY -= leading;
            }

            contentStream.endText();
            contentStream.close();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "converted_wrapped_text.pdf");

            return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("TXT Conversion failed.");
        }
    }
}