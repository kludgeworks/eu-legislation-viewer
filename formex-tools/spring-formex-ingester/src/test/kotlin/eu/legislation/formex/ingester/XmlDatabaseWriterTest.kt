package eu.legislation.formex.ingester

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class XmlDatabaseWriterTest {

    @Autowired
    private lateinit var xmlDatabaseWriter: XmlDatabaseWriter

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @TempDir
    lateinit var tempDir: Path

    @AfterEach
    fun cleanup() {
        // Clear test data from database
        jdbcTemplate.execute("DELETE FROM formex_documents")
    }

    @Test
    fun `should write multiple items in batch and verify all records`() {
        // Given: Create 3 test items with temp XML files
        val metadata1 = createTestXmlMetadata(
            xmlContent = "<doc1>Content 1</doc1>",
            uri = "http://example.org/doc1",
            fileSize = 100L,
            workId = UUID.randomUUID(),
            exprId = "expr-1",
            manId = "man-1",
            csId = "cs-1"
        )

        val metadata2 = createTestXmlMetadata(
            xmlContent = "<doc2>Content 2</doc2>",
            uri = "http://example.org/doc2",
            fileSize = 200L,
            workId = UUID.randomUUID(),
            exprId = "expr-2",
            manId = "man-2",
            csId = "cs-2"
        )

        val metadata3 = createTestXmlMetadata(
            xmlContent = "<doc3>Content 3</doc3>",
            uri = "http://example.org/doc3",
            fileSize = 300L,
            workId = null,
            exprId = null,
            manId = null,
            csId = null
        )

        val chunk = Chunk(listOf(metadata1, metadata2, metadata3))

        // When: Write batch to database
        xmlDatabaseWriter.write(chunk)

        // Then: Verify all 3 records exist in database
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM formex_documents", Int::class.java)
        Assertions.assertEquals(3, count, "Should have 3 records in database")

        // Verify first record
        val record1 = jdbcTemplate.queryForMap(
            "SELECT * FROM formex_documents WHERE id = ?",
            metadata1.id
        )
        Assertions.assertEquals(metadata1.id.toString(), record1["id"].toString())
        Assertions.assertEquals("<doc1>Content 1</doc1>", record1["xml_content"])
        Assertions.assertEquals(100L, record1["file_size"])
        Assertions.assertEquals("http://example.org/doc1", record1["uri"])
        Assertions.assertEquals(metadata1.workId.toString(), record1["work_id"].toString())
        Assertions.assertEquals("expr-1", record1["expr_id"])
        Assertions.assertEquals("man-1", record1["man_id"])
        Assertions.assertEquals("cs-1", record1["cs_id"])

        // Verify second record
        val record2 = jdbcTemplate.queryForMap(
            "SELECT * FROM formex_documents WHERE id = ?",
            metadata2.id
        )
        Assertions.assertEquals(metadata2.id.toString(), record2["id"].toString())
        Assertions.assertEquals("<doc2>Content 2</doc2>", record2["xml_content"])

        // Verify third record with null fields
        val record3 = jdbcTemplate.queryForMap(
            "SELECT * FROM formex_documents WHERE id = ?",
            metadata3.id
        )
        Assertions.assertEquals(metadata3.id.toString(), record3["id"].toString())
        Assertions.assertNull(record3["work_id"])
        Assertions.assertNull(record3["expr_id"])
        Assertions.assertNull(record3["man_id"])
        Assertions.assertNull(record3["cs_id"])
    }

    @Test
    fun `should read XML content from temp file and store in database`() {
        // Given: Create temp file with specific XML content
        val expectedXmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FORMEX_PLUS>
                <HEADER>Test Header</HEADER>
                <BODY>
                    <SECTION>Test Section Content</SECTION>
                </BODY>
            </FORMEX_PLUS>
        """.trimIndent()

        val metadata = createTestXmlMetadata(
            xmlContent = expectedXmlContent,
            uri = "http://example.org/test-xml",
            fileSize = expectedXmlContent.length.toLong(),
            workId = UUID.randomUUID(),
            exprId = "test-expr",
            manId = "test-man",
            csId = "test-cs"
        )

        val chunk = Chunk(listOf(metadata))

        // When: Write to database
        xmlDatabaseWriter.write(chunk)

        // Then: Verify XML content is correctly stored in database
        val actualXmlContent = jdbcTemplate.queryForObject(
            "SELECT xml_content FROM formex_documents WHERE id = ?",
            String::class.java,
            metadata.id
        )

        Assertions.assertEquals(expectedXmlContent, actualXmlContent, "XML content should match exactly")

        // Verify file size matches
        val actualFileSize = jdbcTemplate.queryForObject(
            "SELECT file_size FROM formex_documents WHERE id = ?",
            Long::class.java,
            metadata.id
        )
        Assertions.assertEquals(
            expectedXmlContent.length.toLong(),
            actualFileSize,
            "File size should match XML content length"
        )
    }

    /**
     * Helper method to create test XmlMetadata with a temporary XML file.
     * Uses JUnit's @TempDir for automatic cleanup.
     *
     * @param xmlContent The XML content to write to the temp file
     * @param uri The URI for the metadata
     * @param fileSize The file size
     * @param workId Work UUID (nullable)
     * @param exprId Expression ID (nullable)
     * @param manId Manifestation ID (nullable)
     * @param csId Content Stream ID (nullable)
     * @return XmlMetadata with tempFilePath pointing to created temp file
     */
    private fun createTestXmlMetadata(
        xmlContent: String,
        uri: String,
        fileSize: Long,
        workId: UUID?,
        exprId: String?,
        manId: String?,
        csId: String?
    ): XmlMetadata {
        // Create temp file with XML content in JUnit's managed temp directory
        val tempFile = Files.createTempFile(tempDir, "test-xml-", ".xml")
        Files.writeString(tempFile, xmlContent)

        return XmlMetadata(
            id = UUID.randomUUID(),
            uri = uri,
            fileSize = fileSize,
            workId = workId,
            exprId = exprId,
            manId = manId,
            csId = csId,
            tempFilePath = tempFile.toString()
        )
    }
}