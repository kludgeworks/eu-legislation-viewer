# Spring Batch Formex XML Processing Plan

## Overview
Process 600k XML URIs from SPARQL endpoint, filter existing records, download XML files, calculate metadata, and store in DuckDB.

## Architecture Flow
1. Fetch URIs from SPARQL endpoint (single HTTP request)
2. Filter URIs against existing database records (batch 1000 at a time)
3. Download XML files in parallel (streaming, no memory loading)
4. Calculate UUIDv5 during download (single pass)
5. Insert records into DuckDB
6. Post-process with webbed extension (extract version/root_element)
7. Cleanup temp files

## Dependencies
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>
    <dependency>
        <groupId>org.duckdb</groupId>
        <artifactId>duckdb_jdbc</artifactId>
        <version>1.1.3</version>
    </dependency>
    <dependency>
        <groupId>com.jayway.jsonpath</groupId>
        <artifactId>json-path</artifactId>
    </dependency>
    <dependency>
        <groupId>com.github.f4b6a3</groupId>
        <artifactId>uuid-creator</artifactId>
        <version>5.3.7</version>
    </dependency>
    <dependency>
        <groupId>commons-codec</groupId>
        <artifactId>commons-codec</artifactId>
    </dependency>
</dependencies>
```

## Domain Models
```java
// Record for URI from SPARQL response
public record SparqlUri(String uri) {}

// Record for XML metadata
public record XmlMetadata(
    UUID id,
    String uri,
    long fileSize,
    UUID workId,
    String exprId,
    String manId,
    String csId,
    String tempFilePath  // Path to downloaded file
) {}
```

## Component 1: SPARQL URI Reader

**Purpose**: Fetch all URIs from SPARQL endpoint in a single HTTP request

**Implementation**:
```java
@Component
public class SparqlUriReader extends AbstractItemStreamItemReader<SparqlUri> {
    
    private final RestTemplate restTemplate;
    private final String sparqlEndpoint;
    private Iterator<String> uriIterator;
    
    public SparqlUriReader(
            RestTemplate restTemplate,
            @Value("${sparql.endpoint}") String sparqlEndpoint) {
        this.restTemplate = restTemplate;
        this.sparqlEndpoint = sparqlEndpoint;
    }
    
    @Override
    public void open(ExecutionContext executionContext) {
        String query = """
            SELECT ?item
            WHERE {
              # Your SPARQL query here
            }
            """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.parseMediaType("application/sparql-results+json")));
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("query", query);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        String jsonResponse = restTemplate.postForObject(
            sparqlEndpoint,
            request,
            String.class
        );
        
        // Extract URIs using JsonPath: $.results.bindings[*].item.value
        List<String> uris = JsonPath.read(jsonResponse, "$.results.bindings[*].item.value");
        this.uriIterator = uris.iterator();
        
        // Save count to execution context for monitoring
        executionContext.putLong("totalUris", uris.size());
    }
    
    @Override
    public SparqlUri read() {
        if (uriIterator != null && uriIterator.hasNext()) {
            return new SparqlUri(uriIterator.next());
        }
        return null;
    }
}
```

**Key Points**:
- Single HTTP POST to SPARQL endpoint
- JsonPath extraction: `$.results.bindings[*].item.value`
- Returns iterator for Spring Batch processing
- Tracks total count in ExecutionContext

## Component 2: URI Filter Processor

**Purpose**: Check if URIs already exist in database (batch 1000 at a time)

**Implementation**:
```java
@Component
public class UriFilterProcessor implements ItemProcessor<SparqlUri, SparqlUri> {
    
    private final JdbcTemplate jdbcTemplate;
    private final Set<String> existingUris = new HashSet<>();
    private final List<String> batchBuffer = new ArrayList<>();
    private static final int BATCH_SIZE = 1000;
    
    public UriFilterProcessor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public SparqlUri process(SparqlUri item) {
        batchBuffer.add(item.uri());
        
        // When buffer reaches batch size, check database
        if (batchBuffer.size() >= BATCH_SIZE) {
            checkBatchInDatabase();
        }
        
        // Return null if URI already exists (filters it out)
        return existingUris.contains(item.uri()) ? null : item;
    }
    
    private void checkBatchInDatabase() {
        if (batchBuffer.isEmpty()) return;
        
        // Create parameterized query with IN clause
        String placeholders = String.join(",", Collections.nCopies(batchBuffer.size(), "?"));
        String sql = "SELECT uri FROM formex_documents WHERE uri IN (" + placeholders + ")";
        
        List<String> found = jdbcTemplate.queryForList(sql, String.class, batchBuffer.toArray());
        existingUris.addAll(found);
        
        batchBuffer.clear();
    }
    
    // Call at end of processing to check remaining items
    @PreDestroy
    public void cleanup() {
        checkBatchInDatabase();
    }
}
```

**Key Points**:
- Buffers URIs until 1000 collected
- Single database query per batch (IN clause)
- Returns null to filter out existing URIs
- Caches results to avoid re-checking

## Component 3: XML Download Processor

**Purpose**: Download XML files, calculate UUIDv5, extract metadata from URI

**Implementation**:
```java
@Component 
public class StreamingXmlDownloadProcessor implements ItemProcessor<SparqlUri, XmlMetadata> {
    
    private final RestTemplate restTemplate;
    private final Path tempDir;
    private static final String CELLAR_NAMESPACE = "http://publications.europa.eu/resource/cellar";
    
    public StreamingXmlDownloadProcessor(
            RestTemplate restTemplate,
            @Value("${temp.dir:./temp}") String tempDirPath) throws IOException {
        this.restTemplate = restTemplate;
        this.tempDir = Files.createDirectories(Paths.get(tempDirPath));
    }
    
    @Override
    public XmlMetadata process(SparqlUri item) throws Exception {
        String uri = item.uri();
        
        // Extract ID components from URI
        IdComponents components = extractIdComponents(uri);
        
        // Download and hash in single pass
        Path tempFile = tempDir.resolve(UUID.randomUUID().toString() + ".xml");
        FileMetrics metrics = downloadWithMetrics(uri, tempFile);
        
        return new XmlMetadata(
            metrics.id(),
            uri,
            metrics.size(),
            components.workId(),
            components.exprId(),
            components.manId(),
            components.csId(),
            tempFile.toString()
        );
    }
    
    private IdComponents extractIdComponents(String uri) {
        // Pattern: .../work_id.expr_id.man_id/cs_id
        Pattern pattern = Pattern.compile(".*/([^/]+)\\.([^.]+)\\.([^/]+)/(.+)$");
        Matcher matcher = pattern.matcher(uri);
        
        if (matcher.find()) {
            return new IdComponents(
                UUID.fromString(matcher.group(1)),
                matcher.group(2),
                matcher.group(3),
                matcher.group(4)
            );
        }
        
        return new IdComponents(null, null, null, null);
    }
    
    private FileMetrics downloadWithMetrics(String uri, Path destination) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        
        long[] size = {0};
        
        restTemplate.execute(uri, HttpMethod.GET, null, response -> {
            try (InputStream input = response.getBody();
                 DigestInputStream digestInput = new DigestInputStream(input, digest);
                 OutputStream output = Files.newOutputStream(destination)) {
                
                byte[] buffer = new byte[65536]; // 64KB buffer for large files
                int bytesRead;
                while ((bytesRead = digestInput.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    size[0] += bytesRead;
                }
            }
            return null;
        });
        
        // Generate UUIDv5 from file content hash
        byte[] hash = digest.digest();
        UUID id = UuidCreator.getNameBasedSha1(CELLAR_NAMESPACE, hash);
        
        return new FileMetrics(id, size[0]);
    }
    
    private record IdComponents(UUID workId, String exprId, String manId, String csId) {}
    private record FileMetrics(UUID id, long size) {}
}
```

**Key Points**:
- Streaming download with DigestInputStream (no memory loading)
- Hash calculated during download (single pass)
- UUIDv5 generated using uuid-creator library
- Extracts work_id, expr_id, man_id, cs_id from URI pattern
- Writes to temp file for later database insertion

## Component 4: Database Writer

**Purpose**: Insert XML metadata and content into DuckDB

**Implementation**:
```java
@Component
public class XmlDatabaseWriter implements ItemWriter<XmlMetadata> {
    
    private final JdbcTemplate jdbcTemplate;
    private final TempFileCleanupListener cleanupListener;
    
    public XmlDatabaseWriter(JdbcTemplate jdbcTemplate, TempFileCleanupListener cleanupListener) {
        this.jdbcTemplate = jdbcTemplate;
        this.cleanupListener = cleanupListener;
    }
    
    @Override
    public void write(Chunk<? extends XmlMetadata> chunk) {
        String sql = """
            INSERT INTO formex_documents 
            (id, xml_content, file_size, uri, work_id, expr_id, man_id, cs_id, version, root_element)
            VALUES (?, ?::XML, ?, ?, ?, ?, ?, ?, NULL, NULL)
            """;
        
        jdbcTemplate.batchUpdate(sql, chunk.getItems(), chunk.size(),
            (ps, metadata) -> {
                ps.setObject(1, metadata.id());
                
                String xmlContent = Files.readString(Paths.get(metadata.tempFilePath()));
                ps.setString(2, xmlContent);
                
                ps.setLong(3, metadata.fileSize());
                ps.setString(4, metadata.uri());
                ps.setObject(5, metadata.workId());
                ps.setString(6, metadata.exprId());
                ps.setString(7, metadata.manId());
                ps.setString(8, metadata.csId());
                
                // Track for later cleanup instead of deleting now
                cleanupListener.addTempFile(metadata.tempFilePath());
            });
    }
}
```

**Key Points**:
- Batch insert for efficiency
- Reads XML from temp file (not kept in memory)
- Casts to XML type for DuckDB
- Tracks temp files for later cleanup (does NOT delete here)

## Component 5: Temp File Cleanup Listener

**Purpose**: Delete temp files after step completes successfully

**Implementation**:
```java
@Component
public class TempFileCleanupListener implements StepExecutionListener {
    
    private final Set<String> tempFiles = ConcurrentHashMap.newKeySet();
    
    public void addTempFile(String path) {
        tempFiles.add(path);
    }
    
    @Override
    public void afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
            // Only cleanup on successful completion
            tempFiles.forEach(path -> {
                try {
                    Files.deleteIfExists(Paths.get(path));
                } catch (IOException e) {
                    // Log but don't fail the step
                    System.err.println("Failed to delete temp file: " + path);
                }
            });
            tempFiles.clear();
        } else {
            // On failure, keep files for debugging/retry
            System.out.println("Step failed. Temp files retained at: " + tempFiles);
        }
    }
}
```

**Key Points**:
- Thread-safe file tracking (ConcurrentHashMap.newKeySet())
- Only deletes on successful step completion
- Retains files on failure for debugging/retry
- Clean separation from writer logic

## Component 6: Post-Process Tasklet

**Purpose**: Extract version and root_element using DuckDB webbed extension

**Implementation**:
```java
@Component
public class XmlPostProcessor implements Tasklet {
    
    private final JdbcTemplate jdbcTemplate;
    
    public XmlPostProcessor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // Load webbed extension
        jdbcTemplate.execute("INSTALL webbed FROM community");
        jdbcTemplate.execute("LOAD webbed");
        
        // Update version and root_element for records that don't have them yet
        String updateSql = """
            UPDATE formex_documents
            SET 
                version = regexp_extract(
                    list_filter(
                        xml_extract_attributes(xml_content, '/*'),
                        x -> x.attribute_name = 'noNamespaceSchemaLocation'
                    )[1].attribute_value,
                    '([^/]+\\.xd)$',
                    1
                ),
                root_element = xml_extract_attributes(xml_content, '/*')[1].element_name
            WHERE version IS NULL
            """;
        
        int updated = jdbcTemplate.update(updateSql);
        contribution.incrementWriteCount(updated);
        
        return RepeatStatus.FINISHED;
    }
}
```

**Key Points**:
- Installs and loads DuckDB webbed extension
- Extracts `noNamespaceSchemaLocation` attribute as version
- Extracts root element name
- Only updates records where version is NULL (new records)

## Job Configuration

**Purpose**: Wire all components together into Spring Batch job

**Implementation**:
```java
@Configuration
public class FormexXmlBatchJob {
    
    @Bean
    public Job processFormexXml(
            JobRepository jobRepository,
            Step downloadXmlStep,
            Step postProcessStep) {
        return new JobBuilder("processFormexXml", jobRepository)
            .start(downloadXmlStep)
            .next(postProcessStep)
            .build();
    }
    
    @Bean
    public Step downloadXmlStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SparqlUriReader reader,
            UriFilterProcessor filterProcessor,
            StreamingXmlDownloadProcessor downloadProcessor,
            XmlDatabaseWriter writer,
            TempFileCleanupListener cleanupListener) {
        
        CompositeItemProcessor<SparqlUri, XmlMetadata> compositeProcessor = 
            new CompositeItemProcessor<>();
        compositeProcessor.setDelegates(List.of(filterProcessor, downloadProcessor));
        
        return new StepBuilder("downloadXml", jobRepository)
            .<SparqlUri, XmlMetadata>chunk(10, transactionManager) // Smaller chunks for large files
            .reader(reader)
            .processor(compositeProcessor)
            .writer(writer)
            .listener(cleanupListener)  // Cleanup temp files after step completes
            .taskExecutor(taskExecutor()) // Parallel processing
            .throttleLimit(10) // Max 10 concurrent downloads
            .build();
    }
    
    @Bean
    public Step postProcessStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            XmlPostProcessor postProcessor) {
        return new StepBuilder("postProcess", jobRepository)
            .tasklet(postProcessor, transactionManager)
            .build();
    }
    
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("xml-download-");
        executor.initialize();
        return executor;
    }
    
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate();
        
        // Configure for large file downloads
        template.setRequestFactory(new HttpComponentsClientHttpRequestFactory() {{
            setConnectTimeout(30000);
            setReadTimeout(300000); // 5 minutes for large files
        }});
        
        return template;
    }
}
```

**Key Points**:
- Two-step job: download + post-process
- CompositeItemProcessor chains filter → download
- Chunk size of 10 (small for large files)
- Parallel execution: 10 concurrent threads, throttled at 10
- RestTemplate configured for large files (5 min timeout)

## Application Configuration
```yaml
spring:
  datasource:
    url: jdbc:duckdb:./formex.db
    driver-class-name: org.duckdb.DuckDBDriver
    hikari:
      maximum-pool-size: 1  # DuckDB works best with single connection
  
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false  # Manual trigger via REST endpoint

sparql:
  endpoint: https://your-sparql-endpoint.com/query

temp:
  dir: ./temp/xml
```

## Database Schema
```sql
CREATE TABLE IF NOT EXISTS formex_documents (
    id UUID PRIMARY KEY,
    xml_content XML,
    version VARCHAR,
    root_element VARCHAR,
    file_size BIGINT,
    uri VARCHAR UNIQUE,
    work_id UUID,
    expr_id VARCHAR,
    man_id VARCHAR,
    cs_id VARCHAR
);

CREATE INDEX IF NOT EXISTS idx_uri ON formex_documents(uri);
CREATE INDEX IF NOT EXISTS idx_version ON formex_documents(version);
```

## Execution Flow

1. **SPARQL Fetch** (SparqlUriReader.open())
   - Single HTTP POST to SPARQL endpoint
   - Extract ~600k URIs using JsonPath
   - Store in memory iterator

2. **URI Filtering** (UriFilterProcessor)
   - Buffer URIs in batches of 1000
   - Check database for existing records
   - Filter out duplicates

3. **XML Download** (StreamingXmlDownloadProcessor) - **PARALLEL**
   - Download XML file to temp directory
   - Calculate SHA-1 hash during download
   - Generate UUIDv5 from hash
   - Extract work_id, expr_id, man_id, cs_id from URI
   - Return metadata record

4. **Database Insert** (XmlDatabaseWriter)
   - Read XML content from temp file
   - Batch insert into DuckDB
   - Track temp file paths for cleanup

5. **Temp File Cleanup** (TempFileCleanupListener.afterStep())
   - Delete temp files on successful completion
   - Retain files on failure for debugging

6. **Post-Processing** (XmlPostProcessor)
   - Load DuckDB webbed extension
   - Extract version from `noNamespaceSchemaLocation` attribute
   - Extract root element name
   - Update all new records

## Performance Characteristics

- **Memory Efficiency**: Streaming downloads, no files held in memory
- **Parallelism**: 10 concurrent downloads
- **Database Batching**: URI checks in 1000s, inserts in 10s
- **Single-Pass Hashing**: Hash calculated during download
- **Failure Resilience**: Temp files retained on error for retry

## Key Design Decisions

1. **Why StepExecutionListener for cleanup?**
   - Ensures temp files only deleted after successful commit
   - Retains files on failure for debugging
   - Clean separation of concerns

2. **Why chunk size of 10?**
   - Large XML files (200MB+) need smaller chunks
   - Balances throughput with memory pressure

3. **Why separate post-processing step?**
   - DuckDB webbed extension can process all records efficiently
   - Avoids parsing XML twice (once for insert, once for attributes)
   - Cleaner separation of concerns

4. **Why parallel processing?**
   - Downloads are I/O bound
   - 10 concurrent threads maximize throughput
   - URI order doesn't matter

## Monitoring and Observability

- ExecutionContext tracks total URI count
- Step contribution tracks write counts
- StepExecution shows success/failure status
- Temp file paths logged on failure

## Error Handling

- **Download failures**: Processor returns null, item skipped
- **Database errors**: Transaction rolls back chunk
- **Cleanup errors**: Logged but don't fail step
- **Retry**: Manual restart with existing URIs filtered out