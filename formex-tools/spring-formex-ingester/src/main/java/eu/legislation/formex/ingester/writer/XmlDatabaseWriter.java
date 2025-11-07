package eu.legislation.formex.ingester.writer;

import eu.legislation.formex.ingester.listener.TempFileCleanupListener;
import eu.legislation.formex.ingester.model.XmlMetadata;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Batch inserts XML metadata and content into DuckDB database.
 * Reads XML content from temporary files and tracks them for later cleanup.
 */
@Component
public class XmlDatabaseWriter implements ItemWriter<XmlMetadata> {
    
    private final JdbcTemplate jdbcTemplate;
    private final TempFileCleanupListener cleanupListener;
    
    public XmlDatabaseWriter(JdbcTemplate jdbcTemplate, TempFileCleanupListener cleanupListener) {
        this.jdbcTemplate = jdbcTemplate;
        this.cleanupListener = cleanupListener;
    }
    
    @Override
    public void write(Chunk<? extends XmlMetadata> chunk) throws Exception {
        String sql = """
            INSERT INTO formex_documents 
            (id, xml_content, file_size, uri, work_id, expr_id, man_id, cs_id, version, root_element)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
            """;
        
        jdbcTemplate.batchUpdate(sql, chunk.getItems(), chunk.size(),
            (ps, metadata) -> {
                try {
                    ps.setObject(1, metadata.id());
                    
                    // Read XML content from temp file
                    String xmlContent = Files.readString(Paths.get(metadata.tempFilePath()));
                    ps.setString(2, xmlContent);
                    
                    ps.setLong(3, metadata.fileSize());
                    ps.setString(4, metadata.uri());
                    ps.setObject(5, metadata.workId());
                    ps.setString(6, metadata.exprId());
                    ps.setString(7, metadata.manId());
                    ps.setString(8, metadata.csId());
                    
                    // Track temp file for cleanup after successful step completion
                    cleanupListener.addTempFile(metadata.tempFilePath());
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException("Failed to read XML file: " + metadata.tempFilePath(), e);
                }
            });
    }
}
