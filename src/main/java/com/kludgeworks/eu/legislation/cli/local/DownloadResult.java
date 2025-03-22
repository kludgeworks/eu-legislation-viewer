package com.kludgeworks.eu.legislation.cli.local;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import lombok.With;
import lombok.Builder;

/**
 * Result of a content download operation
 */
@Builder
public record DownloadResult(
    String identifier,
    Path mainFile,
    @With boolean isArchive,
    @With List<Path> extractedFiles
) {
    public DownloadResult {
        extractedFiles = List.copyOf( Objects.requireNonNullElse( extractedFiles, List.of() ) );
    }
}