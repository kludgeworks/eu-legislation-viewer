package eu.legislation.formex.ingester

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.springframework.batch.test.JobOperatorTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
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
 * NOTE: Uses JobOperatorTestUtils (Spring Batch 6.0+) instead of the deprecated
 * JobLauncherTestUtils which is scheduled for removal in 6.2.
 */
@SpringBootTest(classes = [FormexIngesterApplication::class, FormexXmlBatchJobEndToEndTest.TestConfig::class])
@SpringBatchTest
@ActiveProfiles("e2e")
// @Disabled("Manual E2E test - requires network access to EU SPARQL endpoint and download servers")
class FormexXmlBatchJobEndToEndTest {

    @Autowired
    private lateinit var jobOperatorTestUtils: JobOperatorTestUtils

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var job: Job

    @Autowired
    private lateinit var jobLauncher: JobLauncher

    @BeforeEach
    fun setup() {
        // Configure JobOperatorTestUtils (replacement for deprecated JobLauncherTestUtils)
        // JobOperatorTestUtils extends JobLauncherTestUtils and provides the same API
        // but is designed for use with JobOperator in Spring Batch 6.0+
        jobOperatorTestUtils.job = job
        jobOperatorTestUtils.jobLauncher = jobLauncher
        
        // Clear database before test
        jdbcTemplate.execute("DELETE FROM formex_documents")
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
        // JobOperatorTestUtils.launchJob() generates unique job parameters automatically
        // to allow multiple test runs (inherited from JobLauncherTestUtils)
        val jobExecution = jobOperatorTestUtils.launchJob()

        // Then: Job should complete successfully
        assertEquals(BatchStatus.COMPLETED, jobExecution.status, 
            "Job should complete successfully")
        assertEquals("COMPLETED", jobExecution.exitStatus.exitCode,
            "Job exit status should be COMPLETED")

        // Debug: Print step execution details
        jobExecution.stepExecutions.forEach { stepExecution ->
            println("Step: ${stepExecution.stepName}")
            println("  Read count: ${stepExecution.readCount}")
            println("  Write count: ${stepExecution.writeCount}")
            println("  Commit count: ${stepExecution.commitCount}")
            println("  Skip count: ${stepExecution.skipCount}")
            println("  Exit status: ${stepExecution.exitStatus}")
        }

        // Verify exactly 2 records were inserted
        val recordCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM formex_documents",
            Int::class.java
        )
        assertEquals(2, recordCount, 
            "Should have exactly 2 records from limited SPARQL query")

        // Verify all records have required metadata populated
        val recordsWithMetadata = jdbcTemplate.queryForList(
            """
            SELECT id, uri, file_size, work_id, expr_id, man_id, cs_id, 
                   version, root_element, file_path
            FROM formex_documents
            """.trimIndent()
        )

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
            assertNotNull(record["version"], 
                "Version should be extracted from XML (e.g., '4.3.xd')")
            assertNotNull(record["root_element"], 
                "Root element should be extracted from XML")
            
            // Verify file path is stored and file exists
            val filePath = record["file_path"] as String
            assertNotNull(filePath, "File path should be stored")
            val file = Paths.get(filePath)
            assertTrue(Files.exists(file), "XML file should exist at stored path")
            
            // Verify file size matches actual file size
            val actualFileSize = Files.size(file)
            val storedFileSize = (record["file_size"] as Number).toLong()
            assertEquals(storedFileSize, actualFileSize, 
                "Stored file size should match actual file size")
        }

        // Verify URIs are from EU SPARQL endpoint (should contain europa.eu)
        val uris = jdbcTemplate.queryForList(
            "SELECT uri FROM formex_documents",
            String::class.java
        )
        assertTrue(uris.all { it.contains("europa.eu") || it.contains("cellar") },
            "URIs should be from EU SPARQL endpoint")

        // Verify step execution metrics
        val stepExecution = jobExecution.stepExecutions.first()
        assertEquals("downloadXml", stepExecution.stepName, 
            "Should have downloadXml step")
        assertTrue(stepExecution.readCount >= 2, 
            "Should have read at least 2 items from SPARQL")
        assertEquals(2, stepExecution.writeCount, 
            "Should have written exactly 2 items to database")
        assertEquals(0, stepExecution.skipCount, 
            "Should have no skips in successful run")
        assertEquals(0, stepExecution.rollbackCount, 
            "Should have no rollbacks in successful run")

        println("Test completed successfully!")
        println("Downloaded and processed ${recordCount} records from EU SPARQL endpoint")
        println("Sample URIs: ${uris.take(2)}")
    }

    /**
     * Test configuration that overrides SparqlUriReader to add LIMIT 2 to the query.
     *
     * This ensures the E2E test only downloads 2 records instead of all available records,
     * keeping the test fast and reducing load on EU servers.
     */
    @TestConfiguration
    class TestConfig {

        /**
         * Overrides the production SparqlUriReader with a version that limits results to 2.
         *
         * Uses @Primary to ensure this bean is used instead of the production bean.
         * The query is identical to production except for the added LIMIT 2 clause.
         */
        @Bean
        @Primary
        fun testSparqlUriReader(
            restClient: RestClient,
            sparqlProperties: SparqlProperties
        ): SparqlUriReader {
            return object : SparqlUriReader(restClient, sparqlProperties) {
                override fun createReader(): FlatFileItemReader<CellarData> {
                    // Same query as production, but with LIMIT 2 added
                    val query = """
                        PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
                        PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
                        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                        
                        SELECT DISTINCT
                          ?item
                          (STRAFTER(STR(?work), "cellar/") AS ?workId)
                          (STRAFTER(STR(?expression), CONCAT(STR(?work), ".")) AS ?expressionId)
                          (STRAFTER(STR(?manifestation), CONCAT(STR(?expression), ".")) AS ?manifestationId)
                          (STRAFTER(STR(?item), CONCAT(STR(?manifestation), "/")) AS ?contentStreamId)
                          ?language
                          ?mimeType
                        WHERE {
                          ?expression cmr:lang ?language .
                          FILTER(?language = "${sparqlProperties.language}"^^xsd:language)
                        
                          ?expression cdm:expression_belongs_to_work ?work .
                          ?manifestation cdm:manifestation_manifests_expression ?expression .
                          ?item cdm:item_belongs_to_manifestation ?manifestation .
                          ?item cmr:manifestationMimeType ?mimeType .
                          FILTER(?mimeType = "application/xml;type=fmx4")
                        }
                        LIMIT 2
                        """.trimIndent()

                    // Fetch CSV stream from SPARQL endpoint using POST with form-encoded body
                    val encodedQuery = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    val csvResource = restClient.post()
                        .uri(sparqlProperties.endpoint)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(encodedQuery)
                        .accept(MediaType.parseMediaType("text/csv"))
                        .retrieve()
                        .body(Resource::class.java)
                            ?: error("SPARQL endpoint returned null CSV stream")

                    return FlatFileItemReaderBuilder<CellarData>()
                        .name("sparqlCsvReader")
                        .resource(csvResource)
                        .delimited()
                        .names("item", "workId", "expressionId", "manifestationId",
                               "contentStreamId", "language", "mimeType")
                        .linesToSkip(1)
                        .fieldSetMapper { fieldSet ->
                            CellarData(
                                item = fieldSet.readString("item")
                                    ?: throw IllegalArgumentException("Missing required field: item"),
                                workId = fieldSet.readString("workId")
                                    ?: throw IllegalArgumentException("Missing required field: workId"),
                                expressionId = fieldSet.readString("expressionId")
                                    ?: throw IllegalArgumentException("Missing required field: expressionId"),
                                manifestationId = fieldSet.readString("manifestationId")
                                    ?: throw IllegalArgumentException("Missing required field: manifestationId"),
                                contentStreamId = fieldSet.readString("contentStreamId")
                                    ?: throw IllegalArgumentException("Missing required field: contentStreamId"),
                                language = fieldSet.readString("language")
                                    ?: throw IllegalArgumentException("Missing required field: language"),
                                mimeType = fieldSet.readString("mimeType")
                                    ?: throw IllegalArgumentException("Missing required field: mimeType")
                            )
                        }
                        .build()
                }
            }
        }
    }
}
