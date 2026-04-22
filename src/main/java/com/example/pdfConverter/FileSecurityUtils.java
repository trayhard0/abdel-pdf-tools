package com.example.pdfConverter;

import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Paths;

public class FileSecurityUtils {

    private static final Tika tika = new Tika();

    /**
     * Checks the true internal MIME type of the file, ignoring the extension.
     */
    public static boolean isValidFile(MultipartFile file, String expectedMimeType) {
        try {
            // Tika reads the first few bytes of the file to determine its actual type
            String detectedType = tika.detect(file.getInputStream());
            return detectedType.startsWith(expectedMimeType);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Prevents "Directory Traversal" attacks (e.g., uploading a file named "../../etc/passwd")
     */
    public static String sanitizeFileName(String originalFilename) {
        if (originalFilename == null) return "unknown_file";
        // This strips away any path information and just leaves the pure file name
        return Paths.get(originalFilename).getFileName().toString();
    }
}
