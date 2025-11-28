package eu.legislation.formex.downloader

import eu.legislation.formex.downloader.support.TempDirsPropertySupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Spring Boot context loading test.
 *
 * Verifies that the application context starts successfully with test configuration.
 * The lazy-initialized SparqlUriReader prevents HTTP calls during context loading,
 * deferring them until step execution begins.
 */
@SpringBootTest
@ActiveProfiles("test")
class FormexDownloaderApplicationTests : TempDirsPropertySupport() {
    @Test
    fun contextLoads() {
        // Verify that the Spring context loads successfully with all beans configured.
    }
}
