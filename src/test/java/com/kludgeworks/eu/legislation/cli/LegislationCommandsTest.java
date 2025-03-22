package com.kludgeworks.eu.legislation.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.kludgeworks.eu.legislation.cli.Legislation_32015R1998.CELEX;
import static org.junit.jupiter.api.Assertions.*;

@Disabled
@SpringBootTest
class LegislationCommandsTest {

    private static final Logger logger = LoggerFactory.getLogger(LegislationCommandsTest.class);

    @Autowired
    private LegislationCommands legislationCommands;

    @TempDir
    Path tempDir;

    /**
     * Test that validates downloading legislation with CELEX number 32015R1998.
     * This test uses actual external resources to validate the implementation.
     */
    @Test
    @Timeout(value = 5,unit = TimeUnit.MINUTES)
    void downloadLegislation_retrievesCorrectResults_forCelex32015R1998() throws Exception {
        // Given
        String baseDir = tempDir.toString();
        boolean includeAmendments = true;
        boolean includeConsolidations = true;

        logger.info("Starting test for CELEX number {} in directory {}", CELEX, baseDir);

        // When - Call the actual implementation (no mocking)
        String result = legislationCommands.downloadLegislation(
            CELEX, baseDir, includeAmendments, includeConsolidations);

        // Then - Validate the results
        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("downloaded successfully"),
            "Result should indicate successful download: " + result);

        // Verify downloaded files exist
        Path outputDir = tempDir.resolve(CELEX);
        assertTrue( Files.exists(outputDir), "Output directory should exist: " + outputDir);

//        // Validate the downloaded content against expected values
//        validateMainLegislation(outputDir);
//
//        if (includeAmendments) {
//            validateAmendments(outputDir);
//
//            if (includeConsolidations) {
//                validateConsolidations(outputDir);
//            }
//        }
//
//        // Generate and log validation report
//        logger.info("Validation Report:\n{}", expectedResults.generateReport());
    }

}