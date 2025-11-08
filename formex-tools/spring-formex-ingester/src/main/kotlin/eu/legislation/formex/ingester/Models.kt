package eu.legislation.formex.ingester

import java.util.UUID

/**
 * Data class representing structured metadata from SPARQL endpoint response
 *
 * @property item The full URI for the XML content stream
 * @property workId Work identifier (e.g., "JOC_2023_433_R_ENG")
 * @property expressionId Expression identifier
 * @property manifestationId Manifestation identifier
 * @property contentStreamId Content stream identifier
 * @property language ISO language code (e.g., "eng")
 * @property mimeType MIME type of the content (e.g., "application/xml")
 */
data class CellarData(
    val item: String,
    val workId: String,
    val expressionId: String,
    val manifestationId: String,
    val contentStreamId: String,
    val language: String,
    val mimeType: String
)

/**
 * Data class representing XML metadata extracted from downloaded files
 */
data class XmlMetadata(
    val id: UUID,
    val uri: String,
    val fileSize: Long,
    val workId: UUID?,
    val exprId: String?,
    val manId: String?,
    val csId: String?,
    val tempFilePath: String
)
