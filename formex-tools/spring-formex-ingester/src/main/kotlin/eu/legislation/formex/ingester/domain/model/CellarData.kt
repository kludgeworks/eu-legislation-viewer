package eu.legislation.formex.ingester.domain.model

import java.io.Serializable

/**
 * Data class representing structured metadata from SPARQL endpoint response
 *
 * @property correlationId Unique identifier for tracing this item through the pipeline
 * @property item The full URI for the XML content stream
 * @property workId Work identifier (e.g., "JOC_2023_433_R_ENG")
 * @property expressionId Expression identifier
 * @property manifestationId Manifestation identifier
 * @property contentStreamId Content stream identifier
 * @property language ISO language code (e.g., "eng")
 * @property mimeType MIME type of the content (e.g., "application/xml")
 */
data class CellarData(
    val correlationId: String,
    val item: String,
    val workId: String,
    val expressionId: String,
    val manifestationId: String,
    val contentStreamId: String,
    val language: String,
    val mimeType: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 2L // Incremented due to schema change
    }
}
