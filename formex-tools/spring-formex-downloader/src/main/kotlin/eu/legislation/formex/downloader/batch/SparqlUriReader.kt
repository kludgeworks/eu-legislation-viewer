package eu.legislation.formex.downloader.batch

import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import eu.legislation.formex.downloader.config.JmsProperties
import eu.legislation.formex.downloader.config.SparqlProperties
import eu.legislation.formex.downloader.domain.model.CellarData
import eu.legislation.formex.downloader.progress.TerminalProgressService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * Tasklet that fetches URIs from SPARQL endpoint and publishes them to JMS queue in parallel.
 *
 * Replaces execution context storage with JMS queue distribution to solve execution context bloat:
 * - Before: Stored 154 MB List<CellarData> in database execution context
 * - After: Publishes messages to persistent JMS queue (zero execution context storage)
 *
 * Benefits:
 * - Zero execution context storage (only stores count)
 * - Fast restart (messages remain in queue, no 3-4 minute SPARQL re-fetch)
 * - Automatic message persistence (survives application restart)
 * - Parallel publishing using coroutines (10 concurrent threads by default)
 * - Future-proof for horizontal scaling (remote chunking)
 *
 * Restart Behavior (Idempotent):
 * - Stores "sparql.publishingCompleted" flag in execution context when done
 * - On restart, checks flag and skips SPARQL fetch + publishing if already completed
 * - Prevents duplicate messages in queue on restart
 * - If publishing completes, messages remain in queue for downstream processing
 *
 * Error Handling:
 * - If SPARQL fetch fails (timeout, network error), step fails without setting flag
 * - Job status becomes FAILED
 * - On restart, SPARQL fetch re-runs (~4 minutes) and re-publishes messages
 * - If publishing fails mid-way, some duplicate messages may exist in queue (JMS handles this)
 *
 * Timeout Configuration:
 * - RestClient configured with 4-minute read timeout (see FormexXmlBatchJob.sparqlRestClient)
 * - EU SPARQL endpoint can take 3-4 minutes for large queries
 *
 * @param properties SPARQL endpoint configuration
 * @param jmsProperties JMS queue configuration
 * @param sparqlRestClient Spring RestClient for HTTP communication (with 4-minute timeout)
 * @param jmsTemplate Spring Boot auto-configured JmsTemplate for message publishing
 * @param progressService Optional terminal progress service for CLI feedback (feature-flagged)
 */
@Component
class SparqlUriJmsPublisher(
    private val properties: SparqlProperties,
    private val jmsProperties: JmsProperties,
    private val sparqlRestClient: RestClient,
    private val jmsTemplate: JmsTemplate,
    private val progressService: TerminalProgressService?,
) : Tasklet {
    override fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext,
    ): RepeatStatus {
        val executionContext =
            chunkContext.stepContext.stepExecution
                ?.jobExecution
                ?.executionContext

        // Check if publishing already completed (restart scenario)
        val alreadyPublished = executionContext?.containsKey("sparql.publishingCompleted") == true
        if (alreadyPublished) {
            val uriCount = executionContext?.getLong("sparql.uriCount") ?: 0
            logger.info { "Publishing already completed in previous run ($uriCount URIs) - skipping SPARQL fetch and publishing" }
            progressService?.printTaskletComplete("Publishing already completed in previous run ($uriCount URIs) - resuming job")
            return RepeatStatus.FINISHED
        }

        logger.info { "Fetching URIs from SPARQL endpoint: ${properties.endpoint}" }

        // TODO it feels odd to have the progress service this tightly coupled

        // Show waiting message for long SPARQL fetch
        progressService?.printTaskletWaiting("Fetching URIs from SPARQL endpoint (this will take a few minutes)")

        val records = fetchAndParseFromSparql()

        // Show completion message
        progressService?.printTaskletComplete("Fetched ${records.size} URIs from SPARQL endpoint")

        // TODO extract these to config
        val parallelism = 10
        val txBatchSize = 1_000
        logger.info { "Publishing ${records.size} URIs to queue" }
        progressService?.printTaskletWaiting("Publishing ${records.size} URIs to JMS queue with $parallelism parallel threads and transactional batching...")

        // Hide cursor for progress bar updates
        progressService?.hideCursor()

        // Publish messages in parallel using coroutines with limited parallelism and transactional batching
        val publishedCount = AtomicInteger(0)
        // TODO extract these to config
        val progressUpdateInterval = 1_000
        val batchSize = 1_000

        runBlocking(Dispatchers.IO.limitedParallelism(parallelism)) {
            records
                .chunked(batchSize)
                .map { batch ->
                    async {
                        // Use provided JMS session for transactional batching; avoid nested template calls
                        jmsTemplate.execute { session, _ ->
                            val destination = session.createQueue(jmsProperties.cellarDataQueue)
                            val producer = session.createProducer(destination)
                            batch.forEachIndexed { index, cellarData ->
                                val message = session.createObjectMessage(cellarData)
                                producer.send(message)
                                val count = publishedCount.incrementAndGet()

                                // Commit transaction every txBatchSize messages to reduce network round trips
                                if ((index + 1) % txBatchSize == 0) {
                                    session.commit()
                                }

                                // Update progress bar every 1000 messages (thread-safe)
                                if (count % progressUpdateInterval == 0) {
                                    logger.debug { "Published $count / ${records.size} messages..." }
                                    progressService?.printChunkProgress(count.toLong(), records.size.toLong())
                                }
                            }
                            // Final commit for any remaining messages in this batch
                            session.commit()
                            producer.close()
                            null // Return null to satisfy execute() signature
                        }
                    }
                }.awaitAll()
        }

        // Final progress update to show 100% completion
        progressService?.printChunkProgress(records.size.toLong(), records.size.toLong())

        // Show cursor again and move to next line
        progressService?.showCursor()
        progressService?.printNewline()

        logger.info { "Successfully published ${records.size} messages using $parallelism parallel threads" }

        // Store completion flag and count (prevents re-publishing on restart)
        executionContext?.putLong("sparql.uriCount", records.size.toLong())
        executionContext?.putString("sparql.publishingCompleted", "true")

        logger.info { "Successfully published ${records.size} URIs to JMS queue" }
        progressService?.printTaskletComplete("Successfully published ${records.size} URIs to JMS queue")

        return RepeatStatus.FINISHED
    }

    private fun fetchAndParseFromSparql(): List<CellarData> {
        val query = buildSparqlQuery()
        val csvContent = fetchCsvFromEndpoint(query)
        return parseCsvToCellarData(csvContent)
    }

    /**
     * Builds the SPARQL query for fetching Formex XML document metadata.
     *
     * Query retrieves:
     * - Document URI and IDs (work, expression, manifestation, content stream)
     * - Language and MIME type metadata
     *
     * Filters applied:
     * - Language matches configured language (e.g., "eng")
     * - MIME type is "application/xml;type=fmx4" (Formex 4 XML)
     */
    private fun buildSparqlQuery(): String =
        """
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
          FILTER(?language = "${properties.language}"^^xsd:language)

          ?expression cdm:expression_belongs_to_work ?work .
          ?manifestation cdm:manifestation_manifests_expression ?expression .
          ?item cdm:item_belongs_to_manifestation ?manifestation .
          ?item cmr:manifestationMimeType ?mimeType .
          FILTER(?mimeType = "application/xml;type=fmx4")
        }
        ${properties.limit?.let { "LIMIT $it" } ?: ""}
        """.trimIndent()

    /**
     * Executes SPARQL query against configured endpoint and returns CSV response.
     *
     * @param query The SPARQL query to execute
     * @return CSV content as string
     * @throws IllegalStateException if response is empty
     */
    private fun fetchCsvFromEndpoint(query: String): String {
        val formBody = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)

        return sparqlRestClient
            .post()
            .uri(properties.endpoint)
            .header("Accept", "text/csv")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(formBody)
            .retrieve()
            .body(String::class.java) ?: error("Empty response from SPARQL endpoint")
    }

    /**
     * Parses CSV content into list of CellarData objects.
     *
     * Uses Univocity CSV parser with following settings:
     * - Header extraction enabled (first row contains column names)
     * - Quote character: " (double quote)
     * - Delimiter: , (comma)
     *
     * @param csvContent CSV string content to parse
     * @return List of parsed CellarData records
     */
    private fun parseCsvToCellarData(csvContent: String): List<CellarData> {
        val bufferedInputStream = ByteArrayInputStream(csvContent.toByteArray(StandardCharsets.UTF_8))
        val settings =
            CsvParserSettings().apply {
                isHeaderExtractionEnabled = true
                format.quote = '"'
                format.delimiter = ','
            }
        val parser = CsvParser(settings)
        val reader = BufferedReader(InputStreamReader(bufferedInputStream, StandardCharsets.UTF_8))

        val records = mutableListOf<CellarData>()
        parser.beginParsing(reader)
        while (true) {
            val row = parser.parseNextRecord() ?: break
            records.add(
                CellarData(
                    item = row.getString("item"),
                    workId = row.getString("workId"),
                    expressionId = row.getString("expressionId"),
                    manifestationId = row.getString("manifestationId"),
                    contentStreamId = row.getString("contentStreamId"),
                    language = row.getString("language"),
                    mimeType = row.getString("mimeType"),
                ),
            )
        }
        parser.stopParsing()
        reader.close()

        logger.info { "Parsed ${records.size} records from SPARQL endpoint" }
        return records
    }
}
