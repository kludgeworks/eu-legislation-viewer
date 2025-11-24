package eu.legislation.formex.ingester.domain.table

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for formex_documents.
 *
 * This table stores metadata and file references for Formex XML documents.
 * The actual XML content is stored in the filesystem; this table stores only metadata and file paths.
 *
 * Schema mapping:
 * - uri: Cellar URI (TEXT, part of composite primary key)
 * - id: Content-based identifier (TEXT, part of composite primary key)
 * - file_path: Local filesystem path where the XML file is stored (NOT NULL)
 * - file_size: Size of the XML file in bytes (nullable INTEGER)
 * - work_id: FRBR Work identifier (NOT NULL)
 * - expr_id: FRBR Expression identifier (NOT NULL)
 * - man_id: FRBR Manifestation identifier (NOT NULL)
 * - cs_id: FRBR Component Sequence identifier (NOT NULL)
 * - version: Formex version (nullable TEXT)
 * - root_element: Root XML element name (nullable TEXT)
 *
 * Composite primary key (uri, id):
 * - Ensures each URI can only be inserted once (restart idempotency)
 * - Allows multiple URIs to reference the same content (same id)
 * - Handles upstream data quality issues where different URIs serve identical content
 *
 * Indexes:
 * - idx_formex_documents_id: On id for finding duplicate content across URIs
 * - idx_formex_documents_version: On version for quick version lookups
 * - idx_formex_documents_root_element: On root_element for filtering by element type
 * - idx_formex_documents_work_id: On work_id for Cellar identifier filtering
 * - idx_formex_documents_expr_id: On expr_id for Cellar identifier filtering
 * - idx_formex_documents_man_id: On man_id for Cellar identifier filtering
 * - idx_formex_documents_cs_id: On cs_id for Cellar identifier filtering
 */
object FormexDocuments : Table("formex_documents") {
    val uri = text("uri")
    val id = text("id")
    val filePath = text("file_path")
    val fileSize = integer("file_size").nullable()
    val workId = text("work_id")
    val exprId = text("expr_id")
    val manId = text("man_id")
    val csId = text("cs_id")
    val version = text("version").nullable()
    val rootElement = text("root_element").nullable()

    override val primaryKey = PrimaryKey(uri, id)

    init {
        index(isUnique = false, id)
        index(isUnique = false, version)
        index(isUnique = false, rootElement)
        index(isUnique = false, workId)
        index(isUnique = false, exprId)
        index(isUnique = false, manId)
        index(isUnique = false, csId)
    }
}

/**
 * Exposed table definition for processing_errors.
 *
 * This table persists processing errors that occur during batch job execution.
 * Errors are logged in a separate transaction to survive chunk rollbacks.
 *
 * Used by Spring Batch skip listeners to capture and store item processing failures.
 *
 * Schema mapping:
 * - id: Auto-incrementing primary key (INTEGER)
 * - job_execution_id: Spring Batch job execution identifier (NOT NULL)
 * - timestamp: Unix epoch milliseconds when error occurred (NOT NULL)
 * - uri: The source URI that failed processing (NOT NULL)
 * - error_message: Human-readable error message (NOT NULL)
 * - error_type: Exception class name (NOT NULL)
 * - stack_trace: Full stack trace for debugging (nullable TEXT)
 *
 * Indexes:
 * - idx_processing_errors_timestamp: On timestamp for chronological queries
 * - idx_processing_errors_uri: On uri to find all errors for a specific item
 * - idx_processing_errors_job_execution_id: On job_execution_id to find all errors for a specific job run
 */
object ProcessingErrors : Table("processing_errors") {
    val id = long("id").autoIncrement()
    // Provide a default for legacy rows so SchemaUtils can backfill column on existing SQLite DBs
    val jobExecutionId = long("job_execution_id").default(-1)
    val timestamp = long("timestamp")
    val uri = text("uri")
    val errorMessage = text("error_message")
    val errorType = text("error_type")
    val stackTrace = text("stack_trace").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, timestamp)
        index(isUnique = false, uri)
        index(isUnique = false, jobExecutionId)
    }
}
