package com.message.approval.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.springframework.stereotype.Service;

@Service
public class DocumentPreviewService {
    public String render(Path file, String fileName) throws IOException {
        String extension = extension(fileName);
        String text;
        if (extension.equals("csv")) {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } else if (extension.matches("doc|docx|xls|xlsx")) {
            try (POITextExtractor extractor = ExtractorFactory.createExtractor(file.toFile())) {
                text = extractor.getText();
            }
        } else {
            throw new IllegalArgumentException("Preview is not supported for this document type");
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<style>body{font-family:Segoe UI,Arial,sans-serif;margin:0;padding:24px;background:#fff;color:#172033}" +
                "h2{margin-top:0}.document-content{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.55}" +
                "</style></head><body><h2>" + escape(fileName) + "</h2><div class=\"document-content\">" + escape(text) + "</div></body></html>";
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
