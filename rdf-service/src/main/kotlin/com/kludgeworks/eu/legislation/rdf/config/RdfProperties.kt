package com.kludgeworks.eu.legislation.rdf.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rdf")
data class RdfProperties(
    val endpoint: EndpointProperties = EndpointProperties(),
    val connection: ConnectionProperties = ConnectionProperties(),
    val query: QueryProperties = QueryProperties()
) {
    data class EndpointProperties(
        val url: String = "https://publications.europa.eu/webapi/rdf/sparql"
    )

    data class ConnectionProperties(
        val pool: PoolProperties = PoolProperties(),
        val timeout: Int = 5000,
        val socketTimeout: Int = 30000
    )

    data class PoolProperties(
        val maxTotal: Int = 50,
        val maxPerRoute: Int = 20
    )

    data class QueryProperties(
        val defaultTimeout: Long = 30000,
        val maxResults: Int = 1000
    )
}
