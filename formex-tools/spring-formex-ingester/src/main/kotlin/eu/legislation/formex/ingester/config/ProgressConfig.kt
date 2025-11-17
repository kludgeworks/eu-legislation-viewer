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

/**
 * Configuration properties for JMS queue names.
 *
 * @property cellarDataQueue Queue name for CellarData messages from SPARQL fetch
 */
@ConfigurationProperties(prefix = "formex.jms")
data class JmsProperties(
    val cellarDataQueue: String = "cellar.data.queue",
)

/**
 * Configuration properties for Spring Batch processing.
 *
 * @property skipPercentage Percentage of total URIs allowed to fail before job fails (default: 0.5%)
 */
@ConfigurationProperties(prefix = "formex.batch")
data class BatchProperties(
    val skipPercentage: Double = 0.5,
)
