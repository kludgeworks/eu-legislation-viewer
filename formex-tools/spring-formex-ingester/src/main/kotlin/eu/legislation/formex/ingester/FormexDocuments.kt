package eu.legislation.formex.ingester

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for formex_documents.
 *
 * This table stores metadata and file references for Formex XML documents.
 * The actual XML content is stored in the filesystem; this table stores only metadata and file paths.
 *
 * Schema mapping:
 * - id: Primary key (TEXT), unique identifier for the document
 * - file_path: Local filesystem path where the XML file is stored (NOT NULL)
 * - file_size: Size of the XML file in bytes (nullable INTEGER)
 * - uri: Unique URI from Cellar (nullable TEXT with UNIQUE constraint)
 * - work_id: FRBR Work identifier (NOT NULL)
 * - expr_id: FRBR Expression identifier (NOT NULL)
 * - man_id: FRBR Manifestation identifier (NOT NULL)
 * - cs_id: FRBR Component Sequence identifier (NOT NULL)
 * - version: Formex version (nullable TEXT)
 * - root_element: Root XML element name (nullable TEXT)
 *
 * Indexes:
 * - idx_formex_documents_version: On version for quick version lookups
 * - idx_formex_documents_root_element: On root_element for filtering by element type
 * - idx_formex_documents_work_id: On work_id for Cellar identifier filtering
 * - idx_formex_documents_expr_id: On expr_id for Cellar identifier filtering
 * - idx_formex_documents_man_id: On man_id for Cellar identifier filtering
 * - idx_formex_documents_cs_id: On cs_id for Cellar identifier filtering
 */
object FormexDocuments : Table("formex_documents") {
    val id = text("id")
    val filePath = text("file_path")
    val fileSize = integer("file_size").nullable()
    val uri = text("uri").nullable().uniqueIndex()
    val workId = text("work_id")
    val exprId = text("expr_id")
    val manId = text("man_id")
    val csId = text("cs_id")
    val version = text("version").nullable()
    val rootElement = text("root_element").nullable()

    override val primaryKey = PrimaryKey(id)

    // Indexes for performance optimization
    init {
        index(isUnique = false, version)              // idx_formex_documents_version
        index(isUnique = false, rootElement)          // idx_formex_documents_root_element
        index(isUnique = false, workId)               // idx_formex_documents_work_id
        index(isUnique = false, exprId)               // idx_formex_documents_expr_id
        index(isUnique = false, manId)                // idx_formex_documents_man_id
        index(isUnique = false, csId)                 // idx_formex_documents_cs_id
    }
}
