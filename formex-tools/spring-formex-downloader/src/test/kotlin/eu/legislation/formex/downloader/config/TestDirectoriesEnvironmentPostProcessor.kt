package eu.legislation.formex.downloader.config

import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import java.nio.file.Paths
import kotlin.io.path.createDirectories

/**
 * Test-only post processor that redirects data/state/log paths to the build directory
 * so tests do not write under the user's home directory.
 */
class TestDirectoriesEnvironmentPostProcessor : EnvironmentPostProcessor {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        // Only activate for test/e2e profiles
        val isTestProfile = environment.activeProfiles.any { it.equals("test", true) || it.equals("e2e", true) }
        if (!isTestProfile) return

        val buildDir = environment.getProperty("maven.build.directory") ?: System.getProperty("java.io.tmpdir")
        val base = Paths.get(buildDir, "formex-downloader-test")
        val dataDir = base.resolve("data")
        val stateDir = base.resolve("state")
        val logDir = base.resolve("logs")

        // Ensure directories exist
        listOf(dataDir, stateDir, logDir).forEach { it.createDirectories() }

        environment.propertySources.addFirst(
            MapPropertySource(
                "testApplicationDirectories",
                mapOf(
                    "app.dataDir" to dataDir.toString(),
                    "app.stateDir" to stateDir.toString(),
                    "logging.file.path" to logDir.toString(),
                ),
            ),
        )
    }
}
