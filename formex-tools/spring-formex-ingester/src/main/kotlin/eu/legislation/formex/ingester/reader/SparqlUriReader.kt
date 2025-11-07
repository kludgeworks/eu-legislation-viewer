package eu.legislation.formex.ingester.reader

import com.jayway.jsonpath.JsonPath
import eu.legislation.formex.ingester.model.SparqlUri
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamException
import org.springframework.batch.item.support.AbstractItemStreamItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

@Component
class SparqlUriReader(
    private val restTemplate: RestTemplate,
    @Value("\${sparql.endpoint}") private val sparqlEndpoint: String
) : AbstractItemStreamItemReader<SparqlUri>() {

    private var uriIterator: Iterator<String>? = null

    init {
        setName("sparqlUriReader")
    }

    override fun open(executionContext: ExecutionContext) {
        val query = """
            SELECT ?item
            WHERE {
              # Your SPARQL query here
            }
            """.trimIndent()

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            accept = listOf(MediaType.parseMediaType("application/sparql-results+json"))
        }

        val body = LinkedMultiValueMap<String, String>().apply {
            add("query", query)
        }

        val request = HttpEntity(body, headers)

        val jsonResponse = restTemplate.postForObject(
            sparqlEndpoint,
            request,
            String::class.java
        )

        // Extract URIs using JsonPath: $.results.bindings[*].item.value
        val uris: List<String> = JsonPath.read(jsonResponse, "$.results.bindings[*].item.value")
        this.uriIterator = uris.iterator()

        // Save count to execution context for monitoring
        executionContext.putLong("totalUris", uris.size.toLong())
    }

    override fun read(): SparqlUri? {
        return uriIterator?.takeIf { it.hasNext() }?.let { 
            SparqlUri(it.next()) 
        }
    }
}
