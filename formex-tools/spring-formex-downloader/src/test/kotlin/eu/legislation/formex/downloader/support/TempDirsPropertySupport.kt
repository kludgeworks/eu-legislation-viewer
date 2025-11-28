package eu.legislation.formex.downloader.support

import org.junit.jupiter.api.io.TempDir
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Path

/**
 * Supplies temporary directories for app data/state/logging during Spring Boot tests.
 * Prevents polluting user-level application directories.
 */
abstract class TempDirsPropertySupport {
    companion object {
        @JvmStatic
        @TempDir
        lateinit var tempDir: Path

        @JvmStatic
        @DynamicPropertySource
        fun registerTempDirs(registry: DynamicPropertyRegistry) {
            val base = tempDir.resolve("formex-downloader-test")
            registry.add("app.dataDir") { base.resolve("data").toString() }
            registry.add("app.stateDir") { base.resolve("state").toString() }
            registry.add("logging.file.path") { base.resolve("logs").toString() }
        }
    }
}
