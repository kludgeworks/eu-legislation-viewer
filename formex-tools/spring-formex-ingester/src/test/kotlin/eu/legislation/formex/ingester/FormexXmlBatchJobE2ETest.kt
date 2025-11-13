package eu.legislation.formex.ingester

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
import org.springframework.batch.test.JobOperatorTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Paths

/**
 * End-to-end integration test for the complete Formex XML batch job.
 *
 * This test executes the entire job pipeline against the REAL EU SPARQL endpoint:
 * 1. Queries SPARQL endpoint for FORMEX documents (limited to 2 records)
 * 2. Filters existing URIs
 * 3. Downloads XML files from EU servers to permanent storage
 * 4. Extracts metadata (version, root_element)
 * 5. Inserts records into SQLite with file paths
 *
 * Test uses in-memory SQLite for isolation and limits query to 2 records for speed.
 *
 * IMPORTANT: This test makes real HTTP calls to:
 * - https://publications.europa.eu/webapi/rdf/sparql (SPARQL query)
 * - EU XML download servers (2 file downloads)
 *
 * The test is disabled by default to avoid network dependencies in CI/CD.
 * Enable manually when testing against real infrastructure.
 *
 * NOTE: Uses JobOperatorTestUtils and JobOperator (Spring Batch 6.0+) for job testing.
 * This is the modern approach that replaces the deprecated JobLauncher and JobLauncherTestUtils.
 * The test requires a JobOperator bean configured in the application context.
 *
 */
@Disabled("Manual E2E test - requires network access to EU SPARQL endpoint and download servers")
@SpringBootTest(classes = [FormexIngesterApplication::class])
@SpringBatchTest
@ActiveProfiles("e2e")
class FormexXmlBatchJobE2ETest {
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
        }
    }

    /**
     * End-to-end test that verifies the complete job flow with real EU data.
     *
     * This test:
     * - Queries real EU SPARQL endpoint (limited to 2 records via LIMIT clause)
     * - Downloads 2 real XML files from EU servers to permanent storage
     * - Parses XML to extract version and root element
     * - Stores metadata records with file paths in database
     * - Verifies files exist at stored paths
     *
     * Expected duration: 10-30 seconds (depends on network speed)
     */
    @Test
    fun `end to end test with real EU datastore limited to 2 records`() {
        // When: Launch the complete job
        // JobOperatorTestUtils.startJob() generates unique job parameters automatically
        // to allow multiple test runs, using the JobOperator to execute the batch job
        val jobExecution = jobOperatorTestUtils.startJob()

        // Then: Job should complete successfully
        assertEquals(
            BatchStatus.COMPLETED,
            jobExecution.status,
            "Job should complete successfully",
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
            println("  Exit status: ${stepExecution.exitStatus}")
        }

        // Verify exactly 2 records were inserted using Exposed
        val recordCount =
            transaction(database) {
                FormexDocuments.selectAll().count()
            }
        assertEquals(
            2,
            recordCount,
            "Should have exactly 2 records from limited SPARQL query",
        )

        // Verify all records have required metadata populated using Exposed
        val recordsWithMetadata =
            transaction(database) {
                FormexDocuments.selectAll().map { row ->
                    mapOf(
                        "id" to row[FormexDocuments.id],
                        "uri" to row[FormexDocuments.uri],
                        "file_size" to row[FormexDocuments.fileSize],
                        "work_id" to row[FormexDocuments.workId],
                        "expr_id" to row[FormexDocuments.exprId],
                        "man_id" to row[FormexDocuments.manId],
                        "cs_id" to row[FormexDocuments.csId],
                        "version" to row[FormexDocuments.version],
                        "root_element" to row[FormexDocuments.rootElement],
                        "file_path" to row[FormexDocuments.filePath],
                    )
                }
            }

        assertEquals(2, recordsWithMetadata.size, "Should retrieve 2 records")

        recordsWithMetadata.forEach { record ->
            // Verify basic fields
            assertNotNull(record["id"], "ID should not be null")
            assertNotNull(record["uri"], "URI should not be null")
            assertNotNull(record["file_size"], "File size should not be null")
            assertNotNull(record["work_id"], "Work ID should not be null")
            assertNotNull(record["expr_id"], "Expression ID should not be null")
            assertNotNull(record["man_id"], "Manifestation ID should not be null")
            assertNotNull(record["cs_id"], "Content Stream ID should not be null")

            // Verify metadata extraction worked (version and root_element populated)
            assertNotNull(
                record["version"],
                "Version should be extracted from XML (e.g., '4.3.xd')",
            )
            assertNotNull(
                record["root_element"],
                "Root element should be extracted from XML",
            )

            // Verify file path is stored and file exists
            val filePath = record["file_path"] as String
            assertNotNull(filePath, "File path should be stored")
            val file = Paths.get(filePath)
            assertTrue(Files.exists(file), "XML file should exist at stored path")

            // Verify file size matches actual file size
            val actualFileSize = Files.size(file)
            val storedFileSize = (record["file_size"] as Int).toLong()
            assertEquals(
                storedFileSize,
                actualFileSize,
                "Stored file size should match actual file size",
            )
        }

        // Verify URIs are from EU SPARQL endpoint (should contain europa.eu) using Exposed
        val uris =
            transaction(database) {
                FormexDocuments.selectAll().map { it[FormexDocuments.uri] }
            }
        assertTrue(
            uris.all { it?.contains("europa.eu") == true || it?.contains("cellar") == true },
            "URIs should be from EU SPARQL endpoint",
        )

        // Verify step execution metrics
        val stepExecution = jobExecution.stepExecutions.first()
        assertEquals(
            "downloadXml",
            stepExecution.stepName,
            "Should have downloadXml step",
        )
        assertTrue(
            stepExecution.readCount >= 2,
            "Should have read at least 2 items from SPARQL",
        )
        assertEquals(
            2,
            stepExecution.writeCount,
            "Should have written exactly 2 items to database",
        )
        assertEquals(
            0,
            stepExecution.skipCount,
            "Should have no skips in successful run",
        )
        assertEquals(
            0,
            stepExecution.rollbackCount,
            "Should have no rollbacks in successful run",
        )

        println("Test completed successfully!")
        println("Downloaded and processed $recordCount records from EU SPARQL endpoint")
        println("Sample URIs: ${uris.take(2)}")
    }
}
