package eu.legislation.formex.ingester.batch

import eu.legislation.formex.ingester.config.BatchProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.step.skip.SkipLimitExceededException
import org.springframework.batch.core.step.skip.SkipPolicy
import org.springframework.stereotype.Component
import kotlin.math.ceil
import kotlin.math.max

private val logger = KotlinLogging.logger {}

/**
 * Custom skip policy that calculates skip limit as a percentage of total URIs.
 *
 * The skip limit is calculated dynamically based on:
 * - Total URI count from Step 1 (stored in job execution context as "sparql.uriCount")
 * - Configured skip percentage (from application.yaml: formex.batch.skip-percentage)
 * - Minimum skip limit of 10 to prevent overly aggressive failure on small datasets
 *
 * This policy implements both SkipPolicy and StepExecutionListener:
 * - StepExecutionListener: Retrieves total URI count before step starts
 * - SkipPolicy: Checks if skip count exceeds calculated percentage limit
 *
 * Example calculation with 0.5% and 586,627 URIs:
 * - Skip limit = max(ceil(586627 * 0.005), 10) = 2,934 failures allowed
 *
 * @param batchProperties Configuration properties containing skip percentage
 */
@Component
class PercentageBasedSkipPolicy(
    private val batchProperties: BatchProperties,
) : SkipPolicy,
    StepExecutionListener {
    companion object {
        private const val MINIMUM_SKIP_LIMIT = 10
        private const val DEFAULT_TOTAL_URIS = 1000L // Fallback if URI count not found
    }

    @Volatile
    private var totalUriCount: Long = DEFAULT_TOTAL_URIS

    @Volatile
    private var calculatedSkipLimit: Int = MINIMUM_SKIP_LIMIT

    /**
     * Gets the currently calculated skip limit.
     * Exposed for progress display purposes.
     */
    fun getSkipLimit(): Long = calculatedSkipLimit.toLong()

    /**
     * Called before step starts. Retrieves total URI count from job execution context.
     */
    override fun beforeStep(stepExecution: StepExecution) {
        val jobExecutionContext = stepExecution.jobExecution.executionContext

        // Retrieve total URI count stored by SparqlUriJmsPublisher in Step 1
        totalUriCount =
            if (jobExecutionContext.containsKey("sparql.uriCount")) {
                jobExecutionContext.getLong("sparql.uriCount")
            } else {
                logger.warn { "Total URI count not found in execution context - using default value $DEFAULT_TOTAL_URIS" }
                DEFAULT_TOTAL_URIS
            }

        // Calculate skip limit based on percentage
        val calculated = ceil(totalUriCount * (batchProperties.skipPercentage / 100.0)).toInt()
        calculatedSkipLimit = max(calculated, MINIMUM_SKIP_LIMIT)

        logger.info {
            "Configured skip policy: allow up to $calculatedSkipLimit failures " +
                "(${batchProperties.skipPercentage}% of $totalUriCount URIs, minimum $MINIMUM_SKIP_LIMIT)"
        }
    }

    /**
     * Determines whether an exception should be skipped based on skip count.
     *
     * @param throwable The exception that occurred
     * @param skipCount Number of items already skipped in this step
     * @return true if item should be skipped, false if step should fail
     * @throws SkipLimitExceededException if skip count exceeds calculated limit
     */
    override fun shouldSkip(
        throwable: Throwable,
        skipCount: Long,
    ): Boolean {
        // Skip all exceptions up to the calculated limit
        if (skipCount >= calculatedSkipLimit) {
            logger.error {
                "Skip limit exceeded: $skipCount skips >= $calculatedSkipLimit limit " +
                    "(${batchProperties.skipPercentage}% of $totalUriCount URIs)"
            }
            throw SkipLimitExceededException(
                calculatedSkipLimit.toLong(),
                throwable,
            )
        }

        // Allow skip - log every 100 skips for visibility
        if (skipCount % 100 == 0L) {
            logger.warn { "Skip count: $skipCount / $calculatedSkipLimit (${batchProperties.skipPercentage}% of $totalUriCount)" }
        }

        return true
    }
}
