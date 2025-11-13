package eu.legislation.formex.ingester

import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.ItemStream
import org.springframework.batch.infrastructure.item.support.ListItemReader
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * Factory for creating SPARQL CSV readers with restart support.
 *
 * Fetches URIs from SPARQL endpoint, parses CSV with Univocity, and returns a reader
 * that implements ItemStream for restart capability.
 *
 * Uses @StepScope to defer HTTP call to step execution time (not application startup).
 * This allows tests to load the application context without requiring SPARQL endpoint availability.
 *
 * Implements ItemStream to track read position across job restarts:
 * - On restart with same job parameters, reads from the execution context to restore position
 * - Saves current index before each chunk commit
 * - Allows jobs to resume from failure point instead of reprocessing entire dataset
 *
 * @param properties SPARQL endpoint configuration
 * @param restClient Spring RestClient for HTTP communication
 */
@Component
@StepScope
class SparqlUriReader(
    private val properties: SparqlProperties,
    private val restClient: RestClient,
) {
    fun createReader(): ItemReader<CellarData> {
        val records = fetchAndParseFromSparql()
        return RestartableSparqlListItemReader(records)
    }

    private fun fetchAndParseFromSparql(): List<CellarData> {
        val query =
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

        val formBody = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)

        logger.info { "Fetching URIs from SPARQL endpoint: ${properties.endpoint}" }

        val csvContent =
            restClient
                .post()
                .uri(properties.endpoint)
                .header("Accept", "text/csv")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(formBody)
                .retrieve()
                .body(String::class.java) ?: error("Empty response from SPARQL endpoint")

        // Parse CSV with Univocity
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
 * Custom ItemReader implementation that wraps ListItemReader with ItemStream support.
 *
 * This enables Spring Batch's restart capability by tracking the current read position
 * in the ExecutionContext. When a job is restarted with the same parameters, this reader
 * will restore its position and resume reading from where it left off.
 *
 * Execution flow:
 * 1. open(ExecutionContext): Restore saved index on restart, or start from 0
 * 2. read(): Return next item, increment index
 * 3. update(ExecutionContext): Save current index before chunk commit
 * 4. close(): Cleanup (no-op for in-memory list)
 *
 * @property items The list of all items to read
 */
private class RestartableSparqlListItemReader(
    private val items: List<CellarData>,
) : ItemReader<CellarData>,
    ItemStream {
    private var currentIndex = 0
    private val logger = KotlinLogging.logger {}
    private val executionContextKey = "sparql.reader.index"

    override fun read(): CellarData? =
        if (currentIndex < items.size) {
            items[currentIndex].also { currentIndex++ }
        } else {
            null
        }

    override fun open(executionContext: ExecutionContext) {
        // On first run: currentIndex starts at 0
        // On restart: restore saved index from previous execution
        if (executionContext.containsKey(executionContextKey)) {
            val savedIndex = executionContext.getInt(executionContextKey)
            currentIndex = savedIndex
            logger.info { "SPARQL reader resuming from index $savedIndex (restarted job)" }
        } else {
            currentIndex = 0
            logger.info { "SPARQL reader starting from beginning (new job execution)" }
        }
    }

    override fun update(executionContext: ExecutionContext) {
        // Save current index before chunk commit
        // This ensures we can resume from this point if the job fails after this commit
        executionContext.putInt(executionContextKey, currentIndex)
        logger.debug { "SPARQL reader saved position at index $currentIndex" }
    }

    override fun close() {
        // No resources to cleanup for in-memory list
        logger.debug { "SPARQL reader closed at index $currentIndex" }
    }
}
