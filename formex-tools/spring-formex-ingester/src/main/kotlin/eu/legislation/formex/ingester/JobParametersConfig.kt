package eu.legislation.formex.ingester

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.ConfigurableEnvironment
import java.time.Instant
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Configuration for handling job execution state and run IDs.
 *
 * Ensures every job execution has a unique `run.id` derived from the
 * current timestamp (milliseconds since epoch). This prevents the
 * "job already running" error by cleaning up stale job executions
 * on application startup.
 *
 * Approach: On startup, find any job executions in STARTED state,
 * mark them as FAILED, and delete the corresponding job instances,
 * allowing a fresh execution to proceed.
 *
 * Each run will have its own unique execution record.
 */
@Configuration
class JobParametersConfig {
    /**
     * ApplicationListener that cleans up stale jobs early in the boot process.
     *
     * Listens for ApplicationStartedEvent which fires after the application context
     * is fully initialized but before any ApplicationRunner beans are executed.
     * This timing ensures the datasource is ready and executes before JobLauncherApplicationRunner.
     *
     * @param dataSource The application datasource
     * @return ApplicationListener for cleanup
     */
    @Bean
    fun staleJobCleanupListener(dataSource: DataSource): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { event ->
            val runId = Instant.now().toEpochMilli().toString()
            System.setProperty("run.id", runId)
            logger.info { "Auto-generated run.id: $runId" }

            // Clean up any stale job executions (STARTED status from previous crashes)
            try {
                dataSource.connection.use { connection ->
                    connection.createStatement().use { stmt ->
                        // First, find job instances that have STARTED executions
                        val findStaleQuery = "SELECT DISTINCT JOB_INSTANCE_ID FROM BATCH_JOB_EXECUTION WHERE STATUS = 'STARTED'"
                        val staleInstanceIds = mutableListOf<Long>()
                        stmt.executeQuery(findStaleQuery).use { rs ->
                            while (rs.next()) {
                                staleInstanceIds.add(rs.getLong("JOB_INSTANCE_ID"))
                            }
                        }

                        if (staleInstanceIds.isNotEmpty()) {
                            logger.info { "Found ${staleInstanceIds.size} stale STARTED job instance(s)" }

                            // Delete related records in order (respecting foreign keys)
                            for (instanceId in staleInstanceIds) {
                                // Delete job execution context
                                stmt.executeUpdate(
                                    "DELETE FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID IN " +
                                        "(SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = $instanceId)",
                                )
                                // Delete job execution step context
                                stmt.executeUpdate(
                                    "DELETE FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID IN " +
                                        "(SELECT STEP_EXECUTION_ID FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN " +
                                        "(SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = $instanceId))",
                                )
                                // Delete step executions
                                stmt.executeUpdate(
                                    "DELETE FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN " +
                                        "(SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = $instanceId)",
                                )
                                // Delete job execution parameters
                                stmt.executeUpdate(
                                    "DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID IN " +
                                        "(SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = $instanceId)",
                                )
                                // Delete job executions
                                stmt.executeUpdate(
                                    "DELETE FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID = $instanceId",
                                )
                                // Delete job instance
                                stmt.executeUpdate(
                                    "DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID = $instanceId",
                                )
                            }

                            logger.info { "Cleaned up ${staleInstanceIds.size} stale job execution(s)" }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Could not clean up stale job executions - this may cause 'already running' errors" }
                // Don't fail startup - the job will run and might encounter the error
            }
        }
}

/**
 * EnvironmentPostProcessor to set up run.id early in the Spring Boot lifecycle.
 *
 * This runs very early (before most beans are created) to ensure run.id is
 * available throughout the application lifecycle.
 */
class RunIdEnvironmentPostProcessor : EnvironmentPostProcessor {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val runId = Instant.now().toEpochMilli().toString()
        System.setProperty("run.id", runId)
    }
}
