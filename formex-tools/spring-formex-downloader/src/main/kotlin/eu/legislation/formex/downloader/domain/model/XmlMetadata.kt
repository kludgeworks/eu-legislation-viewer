package eu.legislation.formex.downloader.domain.model

import java.io.Serializable
import java.util.UUID

/**
 * Data class representing XML metadata extracted from downloaded files
 *
 * @property id Unique identifier for the document
 * @property uri Source URI of the XML document
 * @property fileSize Size of the XML file in bytes
 * @property workId Work identifier UUID
 * @property exprId Expression identifier
 * @property manId Manifestation identifier
 * @property csId Content stream identifier
 * @property filePath Path (relative to storage root) to permanent file containing XML content
 * @property version Schema version extracted from noNamespaceSchemaLocation (e.g., "4.3.xd")
 * @property rootElement Local name of the root XML element
 */
data class XmlMetadata(
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
        private const val serialVersionUID = 1L
    }
}
