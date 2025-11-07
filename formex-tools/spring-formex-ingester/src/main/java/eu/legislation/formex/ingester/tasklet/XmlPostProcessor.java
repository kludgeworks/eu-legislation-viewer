package eu.legislation.formex.ingester.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-processing tasklet that parses XML documents to extract metadata
 * (version and root_element) from stored XML documents.
 * 
 * Runs after the download step completes to populate version and root_element
 * columns for all newly inserted records.
 */
@Component
public class XmlPostProcessor implements Tasklet {
    
    private static final Logger log = LoggerFactory.getLogger(XmlPostProcessor.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final Pattern versionPattern = Pattern.compile("([^/]+\\.xd)$");
    
    public XmlPostProcessor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
    }
    
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // Fetch all records that need post-processing
        String selectSql = "SELECT id, xml_content FROM formex_documents WHERE version IS NULL";
        List<Map<String, Object>> records = jdbcTemplate.queryForList(selectSql);
        
        int updated = 0;
        for (Map<String, Object> record : records) {
            UUID id = (UUID) record.get("id");
            String xmlContent = (String) record.get("xml_content");
            
            try {
                // Parse XML to extract version and root element
                DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));
                Element rootElement = doc.getDocumentElement();
                
                String rootElementName = rootElement.getLocalName() != null 
                    ? rootElement.getLocalName() 
                    : rootElement.getNodeName();
                
                // Extract version from noNamespaceSchemaLocation attribute
                String schemaLocation = rootElement.getAttribute("noNamespaceSchemaLocation");
                String version = null;
                if (schemaLocation != null && !schemaLocation.isEmpty()) {
                    Matcher matcher = versionPattern.matcher(schemaLocation);
                    if (matcher.find()) {
                        version = matcher.group(1);
                    }
                }
                
                // Update the record
                String updateSql = "UPDATE formex_documents SET version = ?, root_element = ? WHERE id = ?";
                jdbcTemplate.update(updateSql, version, rootElementName, id);
                updated++;
                
            } catch (Exception e) {
                log.warn("Failed to parse XML for record {}: {}", id, e.getMessage());
                // Continue processing other records
            }
        }
        
        contribution.incrementWriteCount(updated);
        log.info("Post-processing complete: {} records updated with version and root_element", updated);
        
        return RepeatStatus.FINISHED;
    }
}
