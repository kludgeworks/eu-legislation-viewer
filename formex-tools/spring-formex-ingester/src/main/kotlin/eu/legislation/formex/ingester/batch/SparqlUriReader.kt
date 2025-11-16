package eu.legislation.formex.ingester.batch

import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import eu.legislation.formex.ingester.config.SparqlProperties
import eu.legislation.formex.ingester.domain.model.CellarData
import eu.legislation.formex.ingester.progress.MordantProgressService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.ItemStream
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * Tasklet that fetches URIs from SPARQL endpoint and stores them in StepExecutionContext.
 *
 * This is the idiomatic Spring Batch approach for expensive data fetching:
 * - Runs once at the beginning of the job
 * - Stores fetched data list in StepExecutionContext
 * - Downstream readers access data via ExecutionContext
 *
 * Error Handling:
 * - If SPARQL fetch fails (timeout, network error), step fails
 * - Job status becomes FAILED
 * - On restart, SPARQL fetch re-runs (~4 minutes)
 *
 * Timeout Configuration:
 * - RestClient configured with 4-minute read timeout (see FormexXmlBatchJob.restClient)
 * - EU SPARQL endpoint can take 3-4 minutes for large queries
 *
 * @param properties SPARQL endpoint configuration
 * @param restClient Spring RestClient for HTTP communication (with 4-minute timeout)
 * @param progressService Optional Mordant progress service for CLI feedback (feature-flagged)
 */
@Component
class SparqlUriFetchTasklet(
    private val properties: SparqlProperties,
    private val restClient: RestClient,
    private val progressService: MordantProgressService?,
) : Tasklet {
    override fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext,
    ): RepeatStatus {
        logger.info { "Fetching URIs from SPARQL endpoint: ${properties.endpoint}" }

        // Show waiting message for long SPARQL fetch
        progressService?.printTaskletWaiting("Fetching URIs from SPARQL endpoint (this may take 3-4 minutes)")

        val records = fetchAndParseFromSparql()

        // Show completion message
        progressService?.printTaskletComplete("Fetched ${records.size} URIs from SPARQL endpoint")

        // Store in step execution context for downstream reader
        chunkContext.stepContext.stepExecution.executionContext
            .put(SPARQL_DATA_KEY, records)

        // Also store count in job execution context for progress tracking (if available)
        chunkContext.stepContext.stepExecution.jobExecution
            ?.executionContext
            ?.putLong("sparql.uriCount", records.size.toLong())

        logger.info { "Stored ${records.size} URIs in ExecutionContext for downstream processing" }

        return RepeatStatus.FINISHED
    }

    companion object {
        const val SPARQL_DATA_KEY = "sparql.data"
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

        return restClient
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

/**
 * ItemReader that reads from SPARQL data stored in JobExecutionContext with restart support.
 *
 * Reads data from JobExecutionContext (populated by SparqlUriFetchTasklet and promoted by ExecutionContextPromotionListener).
 *
 * Implements ItemStream to track read position across job restarts:
 * - On restart, restores position from ExecutionContext
 * - Saves current index before each chunk commit
 * - Allows jobs to resume from failure point
 *
 * Note: Requires @StepScope for proper Spring Batch lifecycle management.
 */
@Component
@StepScope
class CachedSparqlUriReader :
    ItemReader<CellarData>,
    ItemStream {
    private var currentIndex = 0
    private val logger = KotlinLogging.logger {}
    private val executionContextKey = "sparql.reader.index"
    private lateinit var cachedData: List<CellarData>
    private var jobExecutionContext: ExecutionContext? = null

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        // Get job execution context which contains the promoted SPARQL data
        jobExecutionContext = stepExecution.jobExecution.executionContext
    }

    override fun read(): CellarData? =
        if (currentIndex < cachedData.size) {
            cachedData[currentIndex].also {
                logger.debug { "Reader: Read index=$currentIndex URI=${it.item}" }
                currentIndex++
            }
        } else {
            logger.debug { "Reader: Exhausted at index=$currentIndex (total=${cachedData.size})" }
            null
        }

    override fun open(executionContext: ExecutionContext) {
        // Get data from job execution context (promoted from step 1)
        // Using Spring Batch 5.1+ type-safe API with defensive null/type checking
        cachedData =
            try {
                // Suppression justified: Java type erasure prevents List<CellarData>::class from existing.
                // ExecutionContext.get() returns List (raw type), requiring unchecked cast to List<CellarData>.
                // Runtime safety ensured via ClassCastException handling and safe cast operator.
                @Suppress("UNCHECKED_CAST")
                jobExecutionContext
                    ?.get(SparqlUriFetchTasklet.SPARQL_DATA_KEY, List::class.java)
                    ?.let { it as? List<CellarData> }
                    ?: emptyList()
            } catch (e: ClassCastException) {
                logger.warn { "Unexpected type in ExecutionContext for key ${SparqlUriFetchTasklet.SPARQL_DATA_KEY}" }
                emptyList()
            }

        if (cachedData.isEmpty()) {
            logger.warn { "No SPARQL data found in JobExecutionContext - step 1 may have failed" }
            return
        }

        // On first run: currentIndex starts at 0
        // On restart: restore saved index from previous execution
        if (executionContext.containsKey(executionContextKey)) {
            val savedIndex = executionContext.getInt(executionContextKey)
            currentIndex = savedIndex
            logger.info { "SPARQL reader resuming from index $savedIndex of ${cachedData.size} URIs (restarted job)" }
        } else {
            currentIndex = 0
            logger.info { "SPARQL reader starting from beginning with ${cachedData.size} URIs (new job execution)" }
        }
    }

    override fun update(executionContext: ExecutionContext) {
        // Save current index before chunk commit
        // This ensures we can resume from this point if the job fails after this commit
        executionContext.putInt(executionContextKey, currentIndex)
        logger.info { "Reader: Saved checkpoint at index=$currentIndex (will resume from here if job restarts)" }
    }

    override fun close() {
        // No resources to cleanup for in-memory list
        logger.debug { "SPARQL reader closed at index $currentIndex of ${cachedData.size}" }
    }
}
