package com.kludgeworks.eu.legislation.cli.local.filehandling2;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import lombok.Builder;

/**
 * Represents the result of file processing operations
 */
@Builder
public record ProcessingResult(
    boolean isArchive,
    List<Path> extractedFiles,
    Path processedFile
) {
    public ProcessingResult {
        extractedFiles = List.copyOf( Objects.requireNonNullElse( extractedFiles, List.of() ) );
    }
}