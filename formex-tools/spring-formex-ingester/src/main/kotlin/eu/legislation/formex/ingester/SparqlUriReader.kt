package eu.legislation.formex.ingester

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Factory for creating FlatFileItemReader that streams CSV from SPARQL endpoint.
 *
 * Retrieves complete metadata for XML documents including work ID, expression ID,
 * manifestation ID, content stream ID, language, and MIME type, eliminating the
 * need for URI parsing downstream.
 *
 * This component is meant to be used with @StepScope to ensure the HTTP call
 * happens during step execution, not during application context loading.
 */
@Component
class SparqlUriReader(
    private val restClient: RestClient,
    private val sparqlProperties: SparqlProperties
) {

    /**
     * Creates a FlatFileItemReader that streams CSV results from SPARQL endpoint.
     *
     * This method should be called from a @StepScope bean to ensure the HTTP call
     * happens during step execution, not during bean creation.
     *
     * @return FlatFileItemReader configured to parse CSV into CellarData objects
     */
    fun createReader(): FlatFileItemReader<CellarData> {
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
            """.trimIndent()

        // Encode query as form-urlencoded body
        val formBody = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)

        val csvResource = restClient.post()
            .uri(sparqlProperties.endpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formBody)
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