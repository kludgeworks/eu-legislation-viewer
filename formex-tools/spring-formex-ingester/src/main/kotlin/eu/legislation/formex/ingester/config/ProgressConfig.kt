package eu.legislation.formex.ingester.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for SPARQL endpoint.
 *
 * @property endpoint SPARQL endpoint URL
 * @property language ISO language code for filtering (default: eng)
 * @property limit Optional LIMIT clause for SPARQL query (null = no limit)
 */
@ConfigurationProperties(prefix = "sparql")
data class SparqlProperties(
    val endpoint: String,
    val language: String = "eng",
    val limit: Int? = null,
)

/**
 * Configuration properties for XML file storage.
 *
 * @property directory Directory path for permanent XML file storage
 */
@ConfigurationProperties(prefix = "formex.storage")
data class StorageProperties(
    val directory: String = "./formex-xml-files",
)
