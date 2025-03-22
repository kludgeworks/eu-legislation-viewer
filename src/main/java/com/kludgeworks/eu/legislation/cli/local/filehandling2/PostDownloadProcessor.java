package com.kludgeworks.eu.legislation.cli.local.filehandling2;

import java.io.IOException;
import java.nio.file.Path;

public interface PostDownloadProcessor {
    boolean supports(String mimeType);
    ProcessingResult process(Path path, String mimeType) throws IOException;
}