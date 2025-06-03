package com.kludgeworks.eu.legislation.cellarclient.sparql

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Service

/**
 * SPARQL API client with automatic type resolution
 */
@Service
class SparqlApiClient(
    private val httpClient: HttpClient
) {
    private val url = "https://publications.europa.eu/webapi/rdf/sparql"

    /**
     * Fetches amendments for a given CELEX number using a SPARQL query
     *
     * @param celexNumber The CELEX number of the legislation to find amendments for
     * @return List of SparqlAmendment objects representing the amendments
     */
    suspend fun fetchAmendments(celexNumber: String): List<AmendmentInfo> {
        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
            
            SELECT DISTINCT ?amendingWorkUri ?amendedWorkUri ?amendmentCelex ?documentDate 
                   (GROUP_CONCAT(DISTINCT ?entryForceDate; separator=", ") AS ?entryForceDates) 
                   (SAMPLE(?title) AS ?amendmentTitle)
            WHERE {
              {
                # Early amendments (2015-2021): Get titles from expression level
                ?amendedWorkUri cdm:resource_legal_id_celex "$celexNumber"^^xsd:string .
                ?amendingWorkUri cdm:resource_legal_amends_resource_legal ?amendedWorkUri .
                ?amendingWorkUri cdm:resource_legal_id_celex ?amendmentCelex .
                ?amendingWorkUri cdm:work_date_document ?documentDate .
                OPTIONAL { ?amendingWorkUri cdm:resource_legal_date_entry-into-force ?entryForceDate }
                
                # Get English expression titles
                ?expression cdm:expression_belongs_to_work ?amendingWorkUri .
                ?expression cmr:lang "eng"^^xsd:language .
                ?expression cdm:expression_title ?title .
                
                FILTER(?documentDate < "2022-01-01"^^xsd:date)
              }
              UNION
              {
                # Later amendments (2022+): Get titles from work level  
                ?amendedWorkUri cdm:resource_legal_id_celex "$celexNumber"^^xsd:string .
                ?amendingWorkUri cdm:resource_legal_amends_resource_legal ?amendedWorkUri .
                ?amendingWorkUri cdm:resource_legal_id_celex ?amendmentCelex .
                ?amendingWorkUri cdm:work_date_document ?documentDate .
                OPTIONAL { ?amendingWorkUri cdm:resource_legal_date_entry-into-force ?entryForceDate }
                
                # Get work titles
                ?amendingWorkUri cdm:work_title ?title .
                FILTER(lang(?title) = "en" || lang(?title) = "")
                
                FILTER(?documentDate >= "2022-01-01"^^xsd:date)
              }
            }
            GROUP BY ?amendingWorkUri ?amendedWorkUri ?amendmentCelex ?documentDate
            ORDER BY ?documentDate
        """.trimIndent()

        return httpClient.sparql(url, query, AmendmentConverter)
    }

    suspend fun fetchConsolidations(celexNumber: String): List<ConsolidationInfo> {
        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            
            SELECT DISTINCT 
              ?consolidation as ?uri
              ?consolidationId 
              ?celex 
              ?consolidationDate 
              ?basedOnWork 
              (GROUP_CONCAT(DISTINCT ?amendment; separator=",") as ?amendments)
              ?layer 
              ?version
            WHERE {
              # Get the original work by CELEX number
              ?basedOnWork cdm:resource_legal_id_celex "$celexNumber"^^xsd:string .
              
              # Find all consolidations of this work
              ?consolidation cdm:act_consolidated_based_on_resource_legal ?basedOnWork ;
                             cdm:work_id_document ?consolidationId ;
                             cdm:resource_legal_date_entry-into-force ?consolidationDate .
              
              # Optional CELEX (only parent consolidations have this)
              OPTIONAL { ?consolidation cdm:resource_legal_id_celex ?celex }
              
              # Optional layer and version
              OPTIONAL { ?consolidation cdm:act_consolidated_layer ?layer }
              OPTIONAL { ?consolidation cdm:act_consolidated_version ?version }
              
              # Find all amendments incorporated into this consolidation
              # This is done by finding amendments that:
              # 1. Amend the base work
              # 2. Have entry into force date <= consolidation date
              OPTIONAL {
                ?amendment cdm:resource_legal_amends_resource_legal ?basedOnWork ;
                           cdm:resource_legal_date_entry-into-force ?amendmentDate .
                FILTER(?amendmentDate <= ?consolidationDate)
              }
            }
            GROUP BY ?consolidation ?consolidationId ?celex ?consolidationDate ?basedOnWork ?layer ?version
            ORDER BY DESC(?consolidationDate) ?layer ?version
        """.trimIndent()

        return httpClient.sparql(url, query, ConsolidationConverter)
    }
}

suspend inline fun <reified T> HttpClient.sparql(
    url: String,
    query: String,
    converter: SparqlBindingConverter<T>,
    crossinline block: HttpRequestBuilder.() -> Unit = {}
): List<T> {
    val response = submitForm(
        url=url,
        formParameters = Parameters.build {
            append("query", query)
        }
    ) {
        headers {
            append(HttpHeaders.Accept, "application/sparql-results+json")
        }
        block()
    }.body<SparqlResponse<JsonObject>>()

    return response.results.bindings.mapNotNull { converter.convert(it) }
}