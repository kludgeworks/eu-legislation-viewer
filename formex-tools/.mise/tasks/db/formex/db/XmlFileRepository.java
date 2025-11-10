package formex.db;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import formex.cache.FileCache;
import com.github.f4b6a3.uuid.UuidCreator;
import com.github.f4b6a3.uuid.enums.UuidNamespace;
import org.apache.commons.codec.digest.DigestUtils;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import javax.xml.namespace.QName;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Repository for XML file database operations.
 * Encapsulates all database interactions with the xml_files table.
 */
public class XmlFileRepository {
    
    private final Jdbi jdbi;
    private static final int DEFAULT_BATCH_SIZE = 500;
    
    // Pattern to extract cellar identifier components from URI
    private static final Pattern CELLAR_PATTERN =
            Pattern.compile("(?i)([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\\.(\\d{4})\\.(\\d{2})/([A-Z0-9_]+)");
    
    // Namespace UUID for deterministic UUID v5 generation
    private static final UUID NAMESPACE_UUID = UuidCreator.getNameBasedSha1(
            UuidNamespace.NAMESPACE_URL, "http://publications.europa.eu/resource/cellar");
    
    // Thread-local XML factory for parsing
    private static final ThreadLocal<XMLInputFactory> XML_FACTORY = ThreadLocal.withInitial(() -> {
        var f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        f.setProperty(XMLInputFactory.IS_VALIDATING, false);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return f;
    });
    
    /**
     * Creates a new repository with connection to the specified database.
     */
    public XmlFileRepository(String databasePath) {
        this.jdbi = Jdbi.create("jdbc:duckdb:" + databasePath);
    }
    
    /**
     * Creates a new repository with a custom Jdbi instance.
     */
    public XmlFileRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }
    
    /**
     * Computes new URIs by comparing SPARQL results against existing database URIs.
     * Uses a temporary table for efficient database-level comparison.
     */
    public List<String> computeNewUris(List<String> sparqlUris) throws Exception {
        return jdbi.withHandle(handle -> {
            // Create temporary table
            handle.execute("CREATE TEMP TABLE sparql_results (uri VARCHAR)");
            
            try {
                // Insert SPARQL URIs into temp table in batches
                int batchSize = 1000;
                for (int i = 0; i < sparqlUris.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, sparqlUris.size());
                    List<String> batch = sparqlUris.subList(i, end);
                    
                    var preparedBatch = handle.prepareBatch("INSERT INTO sparql_results VALUES (?)");
                    for (String uri : batch) {
                        preparedBatch.bind(0, uri).add();
                    }
                    preparedBatch.execute();
                }
                
                // Query for URIs that don't exist in xml_files
                List<String> newUris = handle.createQuery(
                    "SELECT uri FROM sparql_results WHERE uri NOT IN (SELECT uri FROM xml_files)"
                ).mapTo(String.class).list();
                
                return newUris;
                
            } finally {
                // Clean up temp table
                handle.execute("DROP TABLE IF EXISTS sparql_results");
            }
        });
    }
    


    /**
     * Inserts files with explicit URIs into the database.
     * Useful when URIs come from external sources (e.g., SPARQL queries).
     * 
     * @param pathUriMap Map of file paths to their URIs
     * @return Number of files successfully ingested
     */
    public long insertFilesWithUris(Map<Path, String> pathUriMap) throws Exception {
        return insertFilesWithUris(pathUriMap, DEFAULT_BATCH_SIZE);
    }
    
    /**
     * Inserts files with explicit URIs and custom batch size.
     */
    public long insertFilesWithUris(Map<Path, String> pathUriMap, int batchSize) throws Exception {
        if (pathUriMap.isEmpty()) {
            return 0;
        }
        // Delegate to internal implementation with provided URIs
        return insertFilesInternal(pathUriMap.entrySet().iterator(), batchSize);
    }
    
    /**
     * Inserts files from filesystem paths into the database.
     * Reads content from files, parses metadata, and derives URIs from file paths.
     * 
     * @param paths List of file paths to process
     * @return Number of files successfully ingested
     */
    public long insertFiles(List<Path> paths) throws Exception {
        return insertFiles(paths, DEFAULT_BATCH_SIZE);
    }
    
    /**
     * Inserts files from filesystem paths with custom batch size.
     * URIs are derived from the file paths.
     */
    public long insertFiles(List<Path> paths, int batchSize) throws Exception {
        if (paths.isEmpty()) {
            return 0;
        }
        
        // Convert paths to (Path, URI) entries with derived URIs
        Iterator<Map.Entry<Path, String>> entries = paths.stream()
            .map(path -> Map.entry(path, deriveUri(path.toString())))
            .iterator();
        
        // Delegate to internal implementation
        return insertFilesInternal(entries, batchSize);
    }
    
    /**
     * Internal implementation of file insertion with streaming I/O.
     * Processes an iterator of (Path, URI) entries and inserts them in batches.
     * Uses streaming to avoid loading entire files into memory.
     * 
     * @param entries Iterator of (Path, URI) entries to process
     * @param batchSize Number of records per batch commit
     * @return Number of files successfully ingested
     */
    private long insertFilesInternal(Iterator<Map.Entry<Path, String>> entries, int batchSize) throws Exception {
        String sql = """
                INSERT INTO xml_files
                    (id, xml_content, version, root_element, file_size, uri, work_id, expr_id, man_id, cs_id)
                SELECT :id, content, :version, :root_element, :file_size, :uri, :work_id, :expr_id, :man_id, :cs_id
                FROM read_text(:path)
                ON CONFLICT (id) DO NOTHING
                """;
        
        return jdbi.withHandle(handle -> {
            long count = 0;
            var batch = handle.prepareBatch(sql);
            AtomicInteger errors = new AtomicInteger(0);
            
            try {
                handle.begin();
                
                while (entries.hasNext()) {
                    Map.Entry<Path, String> entry = entries.next();
                    Path path = entry.getKey();
                    String uri = entry.getValue();
                    
                    try {
                        // Get file size without reading entire file
                        long size = Files.size(path);
                        
                        // Parse XML metadata using streaming
                        XmlMetadata xmlInfo = parseXmlMetadata(path);
                        
                        // Extract cellar identifiers from URI
                        CellarIdentifiers cellar = extractCellarIds(uri);
                        
                        // Generate deterministic UUID based on content using streaming
                        UUID contentId = generateFileId(path);
                        
                        // Add to batch
                        batch
                            .bind("id", contentId)
                            .bind("version", xmlInfo.version() == null ? "" : xmlInfo.version())
                            .bind("root_element", xmlInfo.rootElement())
                            .bind("file_size", size)
                            .bind("path", path.toString())
                            .bind("uri", uri)
                            .bind("work_id", cellar.workId())
                            .bind("expr_id", cellar.exprId())
                            .bind("man_id", cellar.manId())
                            .bind("cs_id", cellar.csId())
                            .add();
                        
                        count++;
                        
                        // Execute batch periodically
                        if (count % batchSize == 0) {
                            batch.execute();
                            handle.commit();
                            handle.begin();
                            batch = handle.prepareBatch(sql);
                            System.out.printf("💾 Committed %,d files%n", count);
                        }
                        
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        System.err.printf("⚠️  Error ingesting %s: %s%n", path, e.getMessage());
                    }
                }
                
                // Execute remaining batch
                if (batch.size() > 0) {
                    batch.execute();
                }
                handle.commit();
                
                if (errors.get() > 0) {
                    System.err.printf("⚠️  %,d files failed during ingestion%n", errors.get());
                }
                
            } catch (Exception e) {
                handle.rollback();
                throw e;
            }
            
            return count;
        });
    }

    // ==================== Private Helper Methods ====================
    
    /**
     * Parses XML metadata from file using streaming.
     * Avoids loading entire file into memory.
     */
    private XmlMetadata parseXmlMetadata(Path path) throws Exception {
        try (InputStream is = Files.newInputStream(path)) {
            XMLEventReader reader = XML_FACTORY.get().createXMLEventReader(is);
            try {
                while (reader.hasNext()) {
                    XMLEvent ev = reader.nextEvent();
                    if (ev.isStartElement()) {
                        StartElement se = ev.asStartElement();
                        String root = se.getName().getLocalPart();
                        
                        // Try with prefix first
                        Attribute attr = se.getAttributeByName(new QName("xsi:noNamespaceSchemaLocation"));
                        if (attr == null) {
                            // Fallback without prefix
                            attr = se.getAttributeByName(new QName("noNamespaceSchemaLocation"));
                        }
                        
                        String version = extractVersion(attr);
                        return new XmlMetadata(root, version);
                    }
                }
            } finally {
                reader.close();
            }
        }
        return new XmlMetadata("unknown", null);
    }
    
    /**
     * Extracts version string from schema location attribute.
     */
    private String extractVersion(Attribute attr) {
        if (attr == null) return null;
        String value = attr.getValue();
        if (value == null || value.isEmpty()) return null;
        int idx = value.lastIndexOf('/');
        return idx >= 0 ? value.substring(idx + 1) : value;
    }
    
    /**
     * Generates a deterministic UUID v5 based on content hash.
     * Uses streaming to avoid loading entire file into memory.
     */
    private UUID generateFileId(Path path) throws Exception {
        String contentHash = hashContent(path);
        return UuidCreator.getNameBasedSha1(NAMESPACE_UUID, contentHash);
    }
    
    /**
     * Computes SHA-256 hash of file content as hex string using streaming.
     * Avoids loading entire file into memory.
     */
    private String hashContent(Path path) throws Exception {
        try (InputStream is = Files.newInputStream(path)) {
            return DigestUtils.sha256Hex(is);  // Handles streaming + hex conversion
        }
    }
    
    /**
     * Extracts cellar identifier components from URI or file path.
     * Format: {work-id}.{expr-id}.{man-id}/{cs-id}
     */
    private CellarIdentifiers extractCellarIds(String uriOrPath) {
        var matcher = CELLAR_PATTERN.matcher(uriOrPath);
        if (matcher.find()) {
            return new CellarIdentifiers(
                matcher.group(1),  // work-id (UUID)
                matcher.group(2),  // expr-id (4-char)
                matcher.group(3),  // man-id (2-char)
                matcher.group(4)   // cs-id (alphanumeric)
            );
        }
        // Return nulls if pattern not found
        return new CellarIdentifiers(null, null, null, null);
    }
    
    /**
     * Derives HTTP URI from file path.
     * Example: downloads/publications.europa.eu/resource/cellar/16783d19.../DOC_3.xml
     * becomes: http://publications.europa.eu/resource/cellar/16783d19.../DOC_3
     */
    private String deriveUri(String filePath) {
        // Normalize path separators to forward slashes
        String path = filePath.replace('\\', '/');
        
        // Remove leading 'downloads/' directory
        path = path.replaceAll("^downloads/", "");
        
        // Remove .xml extension if present
        if (path.toLowerCase().endsWith(".xml")) {
            path = path.substring(0, path.length() - 4);
        }
        
        // Find first path separator to extract domain
        int firstSeparator = path.indexOf('/');
        
        if (firstSeparator > 0) {
            String domain = path.substring(0, firstSeparator);
            String rest = path.substring(firstSeparator + 1);
            return "http://" + domain + "/" + rest;
        }
        
        return "http://" + path;
    }
    
    // ==================== Data Classes ====================
    
    /**
     * XML metadata parsed from document.
     */
    private record XmlMetadata(String rootElement, String version) {}
    
    /**
     * Cellar identifier components extracted from URI.
     */
    private record CellarIdentifiers(String workId, String exprId, String manId, String csId) {}
}
