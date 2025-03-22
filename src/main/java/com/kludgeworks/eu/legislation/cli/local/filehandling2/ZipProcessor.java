package com.kludgeworks.eu.legislation.cli.local.filehandling2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ZipProcessor implements PostDownloadProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ZipProcessor.class);
    private final List<PostDownloadProcessor> processors;

    @Autowired
    public ZipProcessor(List<PostDownloadProcessor> processors) {
        // Avoid circular dependency by filtering out self
        this.processors = processors.stream()
            .filter(p -> !(p instanceof ZipProcessor))
            .toList();
    }

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && (mimeType.contains("application/zip") ||
            mimeType.equals("application/x-zip-compressed"));
    }

    @Override
    public ProcessingResult process(Path path, String mimeType) throws IOException {
        logger.info("Processing ZIP file: {}", path);

        // Create extraction directory
        Path extractDir = path.resolveSibling(path.getFileName().toString() + "_extracted");
        Files.createDirectories(extractDir);

        List<Path> extractedFiles = new ArrayList<>();

        // Extract and process ZIP contents
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(extractDir.resolve(entry.getName()));
                } else {
                    // Extract the file
                    Path outputFile = extractDir.resolve(entry.getName());
                    Files.createDirectories(outputFile.getParent());
                    Files.copy(zis, outputFile, StandardCopyOption.REPLACE_EXISTING);
                    extractedFiles.add(outputFile);
                    logger.debug("Extracted: {}", outputFile.getFileName());

                    // Process extracted file if it matches a processor
                    String entryMimeType = guessMimeType(outputFile.getFileName().toString());
                    processExtractedFile(outputFile, entryMimeType);
                }
            }
            logger.info("Successfully extracted {} files to {}", extractedFiles.size(), extractDir);
        }

        // Return processing result with archive flag and extracted files
        return ProcessingResult.builder().isArchive(true).processedFile(path).extractedFiles(extractedFiles).build();
    }

    /**
     * Process an extracted file with an appropriate processor
     */
    private void processExtractedFile(Path file, String mimeType) {
        if (mimeType == null) return;

        for (PostDownloadProcessor processor : processors) {
            if (processor.supports(mimeType)) {
                try {
                    processor.process(file, mimeType);
                    break; // Only use the first matching processor
                } catch (IOException e) {
                    logger.warn("Failed to process extracted file {}: {}", file, e.getMessage());
                }
            }
        }
    }

    /**
     * Guess MIME type from file extension
     */
    private String guessMimeType(String fileName) {
        String lowerCaseFileName = fileName.toLowerCase();

        if (lowerCaseFileName.endsWith(".xml")) {
            return "application/xml";
        } else if (lowerCaseFileName.endsWith(".json")) {
            return "application/json";
        } else if (lowerCaseFileName.endsWith(".html") || lowerCaseFileName.endsWith(".htm")) {
            return "text/html";
        } else if (lowerCaseFileName.endsWith(".txt")) {
            return "text/plain";
        } else if (lowerCaseFileName.endsWith(".zip")) {
            return "application/zip";
        } else if (lowerCaseFileName.endsWith(".pdf")) {
            return "application/pdf";
        }

        return null;
    }
}