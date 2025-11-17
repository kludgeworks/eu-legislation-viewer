package eu.legislation.formex.ingester.domain.model

import java.io.Serializable
import java.util.UUID

/**
 * Data class representing XML metadata extracted from downloaded files
 *
 * @property correlationId Unique identifier for tracing this item through the pipeline
 * @property id Unique identifier for the document
 * @property uri Source URI of the XML document
 * @property fileSize Size of the XML file in bytes
 * @property workId Work identifier UUID
 * @property exprId Expression identifier
 * @property manId Manifestation identifier
 * @property csId Content stream identifier
 * @property filePath Path to permanent file containing XML content
 * @property version Schema version extracted from noNamespaceSchemaLocation (e.g., "4.3.xd")
 * @property rootElement Local name of the root XML element
 */
data class XmlMetadata(
    val correlationId: String,
    val id: UUID,
    val uri: String,
    val fileSize: Long,
    val workId: UUID,
    val exprId: String,
    val manId: String,
    val csId: String,
    val filePath: String,
    val version: String? = null,
    val rootElement: String? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 2L // Incremented due to schema change
    }
}
