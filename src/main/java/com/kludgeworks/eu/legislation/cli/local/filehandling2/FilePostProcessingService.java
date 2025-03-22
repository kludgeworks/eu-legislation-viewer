package com.kludgeworks.eu.legislation.cli.local.filehandling2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FilePostProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(FilePostProcessingService.class);
    private final List<PostDownloadProcessor> processors;

    @Autowired
    public FilePostProcessingService(List<PostDownloadProcessor> processors) {
        this.processors = processors;
    }

    public ProcessingResult processFile(Path path, String mimeType) throws IOException {
        for ( PostDownloadProcessor processor : processors ) {
            if ( processor.supports(mimeType) ) {
                return processor.process(path, mimeType);
            }
        }

     logger.info("No processor found for mime type {}", mimeType);
        return ProcessingResult.builder().processedFile( path ).build();
    }
}