package eu.legislation.formex.ingester.reader

import eu.legislation.formex.ingester.config.properties.SparqlProperties
import eu.legislation.formex.ingester.model.CellarData
import eu.legislation.formex.ingester.model.SparqlResponse
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.support.IteratorItemReader
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/**
 * Factory for creating ItemReader that fetches structured XML metadata from SPARQL endpoint.
 *
 * Queries the EU Publications Office SPARQL endpoint to retrieve complete metadata
 * for XML documents including work ID, expression ID, manifestation ID, content stream ID,
 * language, and MIME type. This eliminates the need for URI parsing downstream.
 *
 * Uses IteratorItemReader since the HTTP endpoint doesn't support streaming and returns
 * all results in a single response.
 */
@Component
class SparqlUriReader(
    private val restClient: RestClient,
    private val sparqlProperties: SparqlProperties
) {

    /**
     * Creates an ItemReader with all SPARQL results fetched and loaded into memory.
     *
     * @return IteratorItemReader containing all fetched CellarData items
     */
    fun createReader(): ItemReader<CellarData> {
        val items = fetchSparqlData()
        return IteratorItemReader(items)
    }

    private fun fetchSparqlData(): List<CellarData> {
        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX annot: <http://publications.europa.eu/ontology/annotation#>
            
            SELECT ?item ?workId ?expressionId ?manifestationId ?contentStreamId ?language ?mimeType
            WHERE {
              ?work cdm:work_id_document ?workId .
              ?expression cdm:expression_belongs_to_work ?work ;
                          cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/${sparqlProperties.language}> ;
                          cdm:expression_id_document ?expressionId .
              ?manifestation cdm:manifestation_manifests_expression ?expression ;
                             cdm:manifestation_id_document ?manifestationId .
              ?item cdm:manifestation_manifests_expression ?expression ;
                    cdm:content_stream_id ?contentStreamId ;
                    cdm:content_stream_type <http://publications.europa.eu/resource/authority/content-stream-type/FORMEX> ;
                    cdm:content_stream_mime_type ?mimeType .
              BIND(LCASE(STR(?language)) AS ?language)
            }
            """.trimIndent()

        val params = LinkedMultiValueMap<String, String>()
        params.add("query", query)
        
        val sparqlResponse = restClient.post()
            .uri(sparqlProperties.endpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.parseMediaType("application/sparql-results+json"))
            .body(params)
            .retrieve()
            .body(SparqlResponse::class.java) ?: throw IllegalStateException("SPARQL endpoint returned null response")

        return sparqlResponse.results.bindings.map { it.toCellarData() }
    }
}