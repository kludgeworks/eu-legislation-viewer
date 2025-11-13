package eu.legislation.formex.ingester

import eu.legislation.formex.ingester.processor.StreamingXmlDownloadProcessor
import eu.legislation.formex.ingester.processor.UriFilterProcessor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor
import org.springframework.batch.test.JobOperatorTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end integration test for error logging with fault-tolerant processing.
 *
 * This test executes the entire job pipeline against the REAL EU SPARQL endpoint,
 * but injects failures into the processing pipeline to verify that:
 * 1. Errors are logged to the ProcessingErrors table
 * 2. The job continues despite failures (skip behavior)
 * 3. Successfully processed items are still written to the database
 * 4. Errors survive chunk transaction rollbacks (separate transaction isolation)
 *
 * The test uses a "poisoning" processor that fails every 3rd item processed,
 * ensuring a mix of successes and failures to validate fault tolerance.
 *
 * IMPORTANT: This test makes real HTTP calls to:
 * - https://publications.europa.eu/webapi/rdf/sparql (SPARQL query limited to 10 records)
 * - EU XML download servers (up to 10 file downloads, some may fail intentionally)
 *
 * The test is disabled by default to avoid network dependencies in CI/CD.
 * Enable manually when testing error logging behavior.
 */
@Disabled("Manual E2E test - requires network access and intentionally injects failures")
@SpringBootTest(
    classes = [
        FormexIngesterApplication::class,
        FormexXmlBatchJobErrorLoggingE2ETest.PoisonedProcessorConfig::class,
    ],
)
@SpringBatchTest
@ActiveProfiles("e2e")
@TestPropertySource(properties = ["sparql.limit=10"])
class FormexXmlBatchJobErrorLoggingE2ETest {
    @Autowired
    private lateinit var jobOperatorTestUtils: JobOperatorTestUtils

    @Autowired
    private lateinit var database: Database

    @Autowired
    private lateinit var job: Job

    @Autowired
    private lateinit var jobOperator: JobOperator

    @BeforeEach
    fun setup() {
        jobOperatorTestUtils.job = job
        jobOperatorTestUtils.setJobOperator(jobOperator)

        // Clear database before test using Exposed
        transaction(database) {
            FormexDocuments.deleteAll()
            ProcessingErrors.deleteAll()
        }

        // Reset the poison counter
        PoisonedDownloadProcessor.reset()
    }

    /**
     * End-to-end test that verifies error logging with intentionally poisoned data.
     *
     * This test:
     * - Queries real EU SPARQL endpoint (limited to 10 records)
     * - Injects failures into every 3rd item during processing
     * - Verifies that errors are logged to ProcessingErrors table
     * - Verifies that successful items are still written to FormexDocuments
     * - Verifies that the job completes with COMPLETED status (not FAILED)
     * - Verifies skip counts match expected failures
     *
     * Expected duration: 30-60 seconds (depends on network speed)
     */
    @Test
    fun `end to end test with poisoned data verifies error logging`() {
        // When: Launch the complete job with poisoned processor
        val jobExecution = jobOperatorTestUtils.startJob()

        // Then: Job should complete successfully despite errors (fault-tolerant)
        assertEquals(
            BatchStatus.COMPLETED,
            jobExecution.status,
            "Job should complete successfully despite processing errors",
        )
        assertEquals(
            "COMPLETED",
            jobExecution.exitStatus.exitCode,
            "Job exit status should be COMPLETED",
        )

        // Debug: Print step execution details
        jobExecution.stepExecutions.forEach { stepExecution ->
            println("Step: ${stepExecution.stepName}")
            println("  Read count: ${stepExecution.readCount}")
            println("  Write count: ${stepExecution.writeCount}")
            println("  Commit count: ${stepExecution.commitCount}")
            println("  Skip count: ${stepExecution.skipCount}")
            println("  Rollback count: ${stepExecution.rollbackCount}")
            println("  Exit status: ${stepExecution.exitStatus}")
        }

        val stepExecution = jobExecution.stepExecutions.first()

        // Verify that some items were skipped (poisoned)
        assertTrue(
            stepExecution.skipCount > 0,
            "Should have skipped items due to poisoned processor",
        )

        // Verify error logs were created
        val errorCount =
            transaction(database) {
                ProcessingErrors.selectAll().count()
            }
        assertTrue(
            errorCount > 0,
            "Should have logged errors to ProcessingErrors table",
        )

        // Verify errors contain expected information
        val errors =
            transaction(database) {
                ProcessingErrors.selectAll().map { row ->
                    mapOf(
                        "id" to row[ProcessingErrors.id],
                        "uri" to row[ProcessingErrors.uri],
                        "error_message" to row[ProcessingErrors.errorMessage],
                        "error_type" to row[ProcessingErrors.errorType],
                        "stack_trace" to row[ProcessingErrors.stackTrace],
                        "timestamp" to row[ProcessingErrors.timestamp],
                    )
                }
            }

        println("\nLogged errors:")
        errors.forEach { error ->
            println("  URI: ${error["uri"]}")
            println("  Error type: ${error["error_type"]}")
            println("  Message: ${error["error_message"]}")
        }

        // Verify each error has required fields
        errors.forEach { error ->
            assertNotNull(error["id"], "Error ID should not be null")
            assertNotNull(error["uri"], "Error URI should not be null")
            assertNotNull(error["error_message"], "Error message should not be null")
            assertNotNull(error["error_type"], "Error type should not be null")
            assertNotNull(error["timestamp"], "Error timestamp should not be null")
            assertTrue(
                error["error_message"].toString().contains("Intentionally poisoned"),
                "Error message should indicate poisoning",
            )
            assertEquals(
                "java.lang.IllegalStateException",
                error["error_type"],
                "Error type should be IllegalStateException",
            )
        }

        // Verify successful records were written
        val successCount =
            transaction(database) {
                FormexDocuments.selectAll().count()
            }
        assertTrue(
            successCount > 0,
            "Should have written some successful records despite errors",
        )

        // Verify the math: read = write + skip
        assertEquals(
            stepExecution.readCount,
            stepExecution.writeCount + stepExecution.skipCount,
            "Read count should equal write count plus skip count",
        )

        // Verify error count matches skip count
        assertEquals(
            stepExecution.skipCount.toLong(),
            errorCount,
            "Error log count should match skip count",
        )

        println("\nTest completed successfully!")
        println("Total read: ${stepExecution.readCount}")
        println("Successful writes: ${stepExecution.writeCount}")
        println("Skipped (errors): ${stepExecution.skipCount}")
        println("Errors logged: $errorCount")
        println("Successful records in DB: $successCount")
    }

    /**
     * Test configuration that provides a poisoned processor for testing error logging.
     *
     * This configuration replaces the normal downloadXmlStep with one that uses
     * a poisoned processor that fails every 3rd item.
     */
    @Suppress("DEPRECATION") // See: https://github.com/spring-projects/spring-batch/issues/5084
    @TestConfiguration
    class PoisonedProcessorConfig {
        @Bean
        @Primary
        fun downloadXmlStep(
            jobRepository: JobRepository,
            transactionManager: PlatformTransactionManager,
            @Autowired reader: org.springframework.batch.infrastructure.item.ItemReader<CellarData>,
            @Autowired filterProcessor: UriFilterProcessor,
            @Autowired downloadProcessor: StreamingXmlDownloadProcessor,
            @Autowired postProcessor: XmlPostProcessor,
            @Autowired writer: XmlDatabaseWriter,
            @Autowired errorLoggingSkipListener: ErrorLoggingSkipListener,
        ): Step {
            // Create poisoned wrapper for download processor
            val poisonedDownloadProcessor = PoisonedDownloadProcessor(downloadProcessor)

            // Chain filter, poisoned download, and post-processing processors
            val compositeProcessor =
                CompositeItemProcessor<CellarData, XmlMetadata>().apply {
                    setDelegates(listOf(filterProcessor, poisonedDownloadProcessor, postProcessor))
                }

            return StepBuilder("downloadXml", jobRepository)
                .chunk<CellarData, XmlMetadata>(10, transactionManager)
                .reader(reader)
                .processor(compositeProcessor)
                .writer(writer)
                .faultTolerant()
                .skip(Exception::class.java)
                .skipLimit(1000)
                .listener(errorLoggingSkipListener)
                .build()
        }
    }

    /**
     * Processor wrapper that intentionally fails every 3rd item to test error logging.
     *
     * Implements ItemProcessor to delegate to the real processor but inject failures.
     */
    class PoisonedDownloadProcessor(
        private val realProcessor: StreamingXmlDownloadProcessor,
    ) : ItemProcessor<CellarData, XmlMetadata> {
        companion object {
            private val counter = AtomicInteger(0)

            fun reset() {
                counter.set(0)
            }
        }

        override fun process(item: CellarData): XmlMetadata? {
            val count = counter.incrementAndGet()
            // Fail every 3rd item
            if (count % 3 == 0) {
                throw IllegalStateException("Intentionally poisoned item #$count: ${item.item}")
            }
            // Process normally for other items
            return realProcessor.process(item)
        }
    }
}
