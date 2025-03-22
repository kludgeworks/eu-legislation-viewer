package com.kludgeworks.eu.legislation.cli.local;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.kludgeworks.eu.legislation.cli.Legislation_32015R1998.DOC1_CONTENT_ITEM;

@SpringBootTest
class ContentDownloadServiceTest {

    @Autowired
    private ContentDownloadService contentDownloadService;

    @TempDir
    Path tempDir;

    @Test
    void downloadContent() throws IOException {
        final DownloadResult downloadResult = contentDownloadService.downloadContent( DOC1_CONTENT_ITEM, tempDir );

        System.out.println( "downloadResult = " + downloadResult );
        Assertions.assertEquals( DOC1_CONTENT_ITEM.identifier(), downloadResult.identifier() );
        Assertions.assertTrue( () -> downloadResult.mainFile().endsWith( DOC1_CONTENT_ITEM.identifier() + ".xml") );
    }
}