package com.kludgeworks.eu.legislation.formex.downloader

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.annotation.DirtiesContext.ClassMode
import org.springframework.test.context.ActiveProfiles

/**
 * Spring Boot context loading test.
 *
 * Verifies that the application context starts successfully with test configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class FormexDownloaderApplicationTests {
    @Test
    fun contextLoads() {
        // Verify that the Spring context loads successfully with all beans configured.
    }
}
