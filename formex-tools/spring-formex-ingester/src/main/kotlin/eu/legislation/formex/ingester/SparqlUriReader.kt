package eu.legislation.formex.ingester

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.support.ListItemReader
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Factory for creating thread-safe ItemReader that loads SPARQL results into memory.
 *
 * Retrieves complete metadata for XML documents including work ID, expression ID,
 * manifestation ID, content stream ID, language, and MIME type, eliminating the
 * need for URI parsing downstream.
 *
 * Uses ListItemReader for thread-safety, enabling parallel processing with taskExecutor.
 * The SPARQL query is executed once and all results are loaded into memory upfront.
 *
 * This component is meant to be used with @StepScope to ensure the HTTP call
 * happens during step execution, not during application context loading.
 */
@Component
class SparqlUriReader(
    private val restClient: RestClient,
    private val sparqlProperties: SparqlProperties,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Parses a CSV line into CellarData object.
     *
     * Expected CSV format (quoted fields):
     * "item","workId","expressionId","manifestationId","contentStreamId","language","mimeType"
     *
     * Handles commas and quotes inside quoted fields.
     *
     * @param line CSV line to parse
     * @return Parsed CellarData object
     */
    private fun parseCsvLine(line: String): CellarData {
        // Parse CSV with proper quote handling
        val fields = mutableListOf<String>()
        var currentField = StringBuilder()
        var insideQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]

            when {
                char == '"' && (i == 0 || line[i - 1] != '\\') -> {
                    insideQuotes = !insideQuotes
                }
                char == ',' && !insideQuotes -> {
                    fields.add(currentField.toString())
                    currentField = StringBuilder()
                }
                else -> {
                    currentField.append(char)
                }
            }
            i++
        }
        // Add the last field
        fields.add(currentField.toString())

        require(fields.size == 7) {
            "Expected 7 fields in CSV line, got ${fields.size}: $line"
        }

        return CellarData(
            item = fields[0],
            workId = fields[1],
            expressionId = fields[2],
            manifestationId = fields[3],
            contentStreamId = fields[4],
            language = fields[5],
            mimeType = fields[6],
        )
    }

    /**
     * Creates a thread-safe ListItemReader with all SPARQL results loaded into memory.
     *
     * This method should be called from a @StepScope bean to ensure the HTTP call
     * happens during step execution, not during bean creation.
     *
     * The reader is thread-safe and compatible with parallel processing via taskExecutor.
     *
     * @return ListItemReader containing all CellarData items from SPARQL query
     */
    fun createReader(): ItemReader<CellarData> {
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
              FILTER(?language = "${sparqlProperties.language}"^^xsd:language)
            
              ?expression cdm:expression_belongs_to_work ?work .
              ?manifestation cdm:manifestation_manifests_expression ?expression .
              ?item cdm:item_belongs_to_manifestation ?manifestation .
              ?item cmr:manifestationMimeType ?mimeType .
              FILTER(?mimeType = "application/xml;type=fmx4")
            }
            ${sparqlProperties.limit?.let { "LIMIT $it" } ?: ""}
            """.trimIndent()

        // Encode query as form-urlencoded body
        val formBody = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)

        // Execute SPARQL query and load all results into memory
        logger.info { "Executing SPARQL query to fetch URIs from endpoint: ${sparqlProperties.endpoint}" }
        val items =
            restClient
                .post()
                .uri(sparqlProperties.endpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .accept(MediaType.parseMediaType("text/csv"))
                .exchange { _, response ->
                    // Check HTTP status
                    val statusCode = response.statusCode.value()
                    if (statusCode !in 200..299) {
                        error("SPARQL endpoint returned error status $statusCode")
                    }

                    response.body.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                            // Skip header line
                            reader.readLine()

                            // Read all CSV rows and parse into CellarData objects
                            reader
                                .lineSequence()
                                .map { line -> parseCsvLine(line) }
                                .toList()
                        }
                    }
                }

        logger.info { "Loaded ${items.size} items from SPARQL endpoint into memory" }

        // Return thread-safe ListItemReader
        return ListItemReader(items)
    }
}
