package com.kludgeworks.eu.legislation.cli.local.filehandling2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JsonProcessor implements PostDownloadProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JsonProcessor.class);

    private final ObjectMapper objectMapper;

    public JsonProcessor() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && (mimeType.contains("application/json") || mimeType.endsWith("+json"));
    }

    @Override
    public ProcessingResult process(Path path, String mimeType) throws IOException {
        logger.info("Processing JSON file: {}", path);

        // Read the file content
        String jsonContent = Files.readString(path);

        // Parse and format the JSON
        Object jsonObject = objectMapper.readValue(jsonContent, Object.class);
        String formattedJson = objectMapper.writeValueAsString(jsonObject);

        // Write formatted JSON to the new file
        Files.writeString(path, formattedJson);

        logger.info("Successfully formatted JSON: {}", path);

        // Return processing result indicating this is not an archive
        return ProcessingResult.builder().isArchive(false).processedFile( path ).build();
    }
}