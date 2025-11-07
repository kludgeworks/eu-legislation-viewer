package eu.legislation.formex.ingester.model

import java.util.UUID

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
