package eu.legislation.formex.ingester

import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemReader
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
 * Factory for creating SPARQL CSV readers.
 *
 * Fetches URIs from SPARQL endpoint, parses CSV with Univocity, and returns ListItemReader.
 *
 * Uses @StepScope to defer HTTP call to step execution time (not application startup).
 * This allows tests to load the application context without requiring SPARQL endpoint availability.
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
        return ListItemReader(records)
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
