package dev.hrai.jobposting.resume;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Extracts plain text from an uploaded resume (PDF or plain text). */
public final class ResumeTextExtractor {

    private ResumeTextExtractor() {
    }

    public static String extract(InputStream content, String contentType, String filename) throws IOException {
        boolean looksLikePdf = (contentType != null && contentType.contains("pdf"))
                || (filename != null && filename.toLowerCase().endsWith(".pdf"));

        if (looksLikePdf) {
            byte[] bytes = content.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                String text = new PDFTextStripper().getText(document);
                if (text == null || text.isBlank()) {
                    throw new IOException("no extractable text found in PDF (it may be a scanned image)");
                }
                return text;
            }
        }

        // Plain text resume, or anything else we can at least attempt to read as text
        String text = new String(content.readAllBytes(), StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new IOException("resume file is empty or unreadable");
        }
        return text;
    }
}
