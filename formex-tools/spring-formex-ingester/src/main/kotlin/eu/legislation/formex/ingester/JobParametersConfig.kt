package eu.legislation.formex.ingester

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Configuration for auto-generating job run IDs.
 *
 * If no `run.id` is provided as a command-line argument, one is automatically generated
 * from the current timestamp (milliseconds since epoch).
 *
 * This allows the application to be started with simply:
 *   ./mvnw spring-boot:run
 *
 * Instead of requiring:
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="run.id=$(date +%s)"
 */
@Configuration
class JobParametersConfig {
    /**
     * ApplicationRunner that auto-generates a run.id if one isn't provided.
     *
     * Runs after Spring Boot application is fully initialized but before the
     * automatic job launch. If the run.id property isn't set, this generates
     * one from the current timestamp.
     *
     * @return ApplicationRunner bean for parameter initialization
     */
    @Bean
    fun jobParametersInitializer(): ApplicationRunner =
        ApplicationRunner {
            val runId = System.getProperty("run.id")
            if (runId == null) {
                val generatedRunId = Instant.now().toEpochMilli().toString()
                System.setProperty("run.id", generatedRunId)
                logger.info { "Auto-generated run.id: $generatedRunId" }
            } else {
                logger.info { "Using provided run.id: $runId" }
            }
        }
}
