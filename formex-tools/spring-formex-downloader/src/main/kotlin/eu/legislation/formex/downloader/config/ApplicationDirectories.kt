package eu.legislation.formex.downloader.config

import dev.dirs.ProjectDirectories
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment
import org.springframework.core.env.MapPropertySource
import java.nio.file.Path
import kotlin.io.path.createDirectories

private val logger = KotlinLogging.logger {}

/**
 * Configuration for platform-appropriate application directories.
 *
 * Uses the XDG Base Directory specification on Linux/macOS and equivalent
 * Windows paths (AppData) to store application data in standard locations.
 *
 * Directory structure:
 * - Data directory: Persistent application data (database, XML files)
 *   - macOS/Linux: ~/.local/share/formex-downloader
 *   - Windows: C:\Users\{username}\AppData\Local\Foo Corp\Bar App\data
 *
 * - State directory: Application state files (logs, JMS data)
 *   - macOS/Linux: ~/.local/share/formex-downloader/state (or ~/.local/state/formex-downloader if XDG_STATE_HOME is set)
 *   - Windows: C:\Users\{username}\AppData\Local\Foo Corp\Bar App\data\state
 *
 * Directory paths are resolved by DirectoriesEnvironmentPostProcessor and exposed as
 * Spring properties (app.dataDir, app.stateDir). This configuration class provides
 * typed Path beans for dependency injection.
 */
@Configuration
class ApplicationDirectories(
    private val environment: Environment,
) {
    /**
     * Provides the data directory path for persistent application data.
     *
     * Used for:
     * - SQLite database (formex.db)
     * - Downloaded XML files
     *
     * @return Path to the application data directory
     */
    @Bean
    fun dataDirectory(): Path {
        val dataDir = environment.getRequiredProperty("app.dataDir")
        val path = Path.of(dataDir)
        logger.info { "Application data directory: $path" }
        return path
    }

    /**
     * Provides the state directory path for application state files.
     *
     * Used for:
     * - Log files
     * - Artemis JMS persistent queue data
     *
     * @return Path to the application state directory
     */
    @Bean
    fun stateDirectory(): Path {
        val stateDir = environment.getRequiredProperty("app.stateDir")
        val path = Path.of(stateDir)
        logger.info { "Application state directory: $path" }
        return path
    }
}

/**
 * Environment post-processor that resolves platform-appropriate directories
 * and injects them as properties very early in the Spring Boot lifecycle.
 *
 * This runs before application.yaml is loaded, ensuring directory paths
 * are available for property placeholder resolution (e.g., ${app.dataDir}).
 *
 * Uses the directories-jvm library (https://codeberg.org/dirs/directories-jvm) which follows:
 * - XDG Base Directory specification on Linux/macOS
 * - Windows AppData conventions
 *
 * Implementation details:
 * - Creates directories immediately to prevent initialization failures
 * - Adds paths as Spring PropertySource (Spring-idiomatic, scoped to Environment)
 * - Spring Boot automatically propagates logging.file.path to LOG_PATH for Logback
 * - No system properties needed - Spring Boot handles property resolution for all components
 */
class DirectoriesEnvironmentPostProcessor : EnvironmentPostProcessor {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        // Resolve platform-appropriate directories using directories-jvm library
        val projectDirs = ProjectDirectories.from("eu", "legislation", "formex-downloader")
        val dataDir = projectDirs.dataDir
        val stateDir = projectDirs.dataLocalDir?.let { "$it/state" } ?: "${projectDirs.dataDir}/state"

        // Create directories immediately (prevents database/logging failures at startup)
        Path.of(dataDir).createDirectories()
        Path.of(stateDir).createDirectories()

        val properties =
            mapOf(
                "app.dataDir" to dataDir,
                "app.stateDir" to stateDir,
            )
        environment.propertySources.addFirst(
            MapPropertySource("applicationDirectories", properties),
        )
    }
}

/**
 * ApplicationListener that activates the cli-progress profile when
 * formex.features.cli-progress is set to true (default).
 */
