package com.kludgeworks.eu.legislation.cli.local;

import com.kludgeworks.eu.legislation.cli.domain.ContentItem;
import com.kludgeworks.eu.legislation.cli.local.filehandling2.FilePostProcessingService;
import com.kludgeworks.eu.legislation.cli.local.filehandling2.ProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Service for downloading and processing content from EU legislation portals
 */
@Service
public class ContentDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(ContentDownloadService.class);
    private final RestTemplate restTemplate;
    private final FilePostProcessingService postProcessingService;

    @Autowired
    public ContentDownloadService(FilePostProcessingService postProcessingService) {
        this.postProcessingService = postProcessingService;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Download content for a single content item
     *
     * @param item The content item to download
     * @param outputDir The output directory
     * @return DownloadResult containing information about the downloaded content
     * @throws IOException If there's an error downloading or processing content
     */
    public DownloadResult downloadContent(ContentItem item, Path outputDir) throws IOException {
        // Create output directory if it doesn't exist
        Files.createDirectories(outputDir);

        // Use identifier or generate one if null
        String identifier = item.identifier() != null ? item.identifier() : "item_" + System.currentTimeMillis();

        logger.info("Downloading: {} ({})", identifier, item.mimeType());

        try {
            // Create file path with appropriate extension
            Path downloadPath = createDownloadPath(outputDir, identifier, item.mimeType());

            // Download content directly to file
            downloadFile(item.uri(), downloadPath);

            logger.info("Content downloaded to: {}", downloadPath);

            // Process the file and get processing result
            ProcessingResult processingResult = postProcessingService.processFile(downloadPath, item.mimeType());

            // Create and return DownloadResult
            return DownloadResult.builder()
                .identifier(identifier)
                .mainFile(processingResult.processedFile())
                .isArchive(processingResult.isArchive())
                .extractedFiles(processingResult.extractedFiles())
                .build();

        } catch (Exception e) {
            logger.error("Error downloading/processing content for {}: {}", item.uri(), e.getMessage(), e);
            throw new IOException("Failed to process content: " + e.getMessage(), e);
        }
    }

    /**
     * Download a file from a URL directly to a path
     */
    private void downloadFile(String url, Path destination) throws IOException {
        try {
            ResponseEntity<Resource> response = restTemplate.exchange(
                URI.create(url),
                HttpMethod.GET,
                null,
                Resource.class
            );

            if (response.getBody() != null) {
                try (InputStream inputStream = response.getBody().getInputStream()) {
                    Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                throw new IOException("Empty response when downloading " + url);
            }
        } catch (Exception e) {
            logger.error("Error downloading from {}: {}", url, e.getMessage());
            throw new IOException("Failed to download file: " + e.getMessage(), e);
        }
    }

    /**
     * Create a file path with appropriate extension
     */
    private Path createDownloadPath(Path outputDir, String identifier, String mimeType) {
        String extension = getFileExtensionFromMimeType(mimeType);
        return outputDir.resolve(identifier + extension);
    }

    /**
     * Get file extension from MIME type
     */
    private String getFileExtensionFromMimeType(String mimeType) {
        if (mimeType == null) return ".bin";

        mimeType = mimeType.toLowerCase();

        if (mimeType.equals("application/xml") || mimeType.equals("text/xml") || mimeType.endsWith("+xml")) {
            return ".xml";
        } else if (mimeType.equals("application/json") || mimeType.endsWith("+json")) {
            return ".json";
        } else if (mimeType.equals("application/zip") || mimeType.equals("application/x-zip-compressed")) {
            return ".zip";
        } else if (mimeType.equals("text/html") || mimeType.equals("application/xhtml+xml")) {
            return ".html";
        } else if (mimeType.equals("application/pdf")) {
            return ".pdf";
        } else if (mimeType.equals("text/plain")) {
            return ".txt";
        }

        // For other types, try to extract extension from MIME type
        String[] parts = mimeType.split("/");
        if (parts.length > 1 && !parts[1].equals("octet-stream")) {
            return "." + parts[1].split(";")[0].trim();
        }

        return ".bin";
    }
}