///usr/bin/env jbang -Djava.rmi.server.hostname=127.0.0.1 "$0" "$@" ; exit $?
//JAVA 21+
//DEPS org.duckdb:duckdb_jdbc:1.4.1.0
//DEPS info.picocli:picocli:4.7.7
//DEPS com.fasterxml.woodstox:woodstox-core:7.1.1
//DEPS org.jdbi:jdbi3-core:3.49.6
//DEPS com.github.f4b6a3:uuid-creator:6.1.1
//MISE description="Ingest XML files from downloads directory into DuckDB"


import com.github.f4b6a3.uuid.enums.UuidNamespace;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import com.github.f4b6a3.uuid.UuidCreator;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import javax.xml.namespace.QName;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Command(name = "ingest", mixinStandardHelpOptions = true,
        description = "Ingest XML files into DuckDB (expects existing xml_files table)")
public class ingest implements Callable<Integer> {

    @Option(names = {"-d", "--database"}, defaultValue = "formex.db")
    String databaseFile;

    @Option(names = {"-p", "--parallelism"}, defaultValue = "100",
            description = "Max number of files processed concurrently")
    int parallelism;

    @Option(names = {"-b", "--batch-size"}, defaultValue = "500",
            description = "Number of rows per JDBC batch")
    int batchSize;

    @Parameters(index = "0", defaultValue = "downloads",
            description = "Directory to scan for XML files")
    String directory;

    // Pattern to extract cellar identifier components: {work-id}.{expr-id}.{man-id}/{cs-id}
    private static final Pattern CELLAR_PATTERN =
            Pattern.compile("(?i)([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\\.(\\d{4})\\.(\\d{2})/([A-Z0-9_]+)");
    
    // Namespace UUID for deterministic UUID v5 generation based on the cellar resource URL
    private static final UUID NAMESPACE_UUID = UuidCreator.getNameBasedSha1(UuidNamespace.NAMESPACE_URL, "http://publications.europa.eu/resource/cellar");

    private static final ThreadLocal<XMLInputFactory> XML_FACTORY = ThreadLocal.withInitial(() -> {
        var f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        f.setProperty(XMLInputFactory.IS_VALIDATING, false);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return f;
    });

    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public static void main(String[] args) {
        System.exit(new CommandLine(new ingest()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        Path root = Paths.get(directory);
        if (!Files.exists(root)) {
            System.err.println("Directory does not exist: " + root);
            return 1;
        }

        System.out.println("📥 Starting XML ingestion (no table creation)…");

        Jdbi jdbi = Jdbi.create("jdbc:duckdb:" + databaseFile);

        String sql = """
                INSERT INTO xml_files
                    (id, xml_content, version, root_element, file_size, uri, work_id, expr_id, man_id, cs_id)
                SELECT :id, content, :version, :root_element, :file_size, :uri, :work_id, :expr_id, :man_id, :cs_id
                FROM read_text(:path)
                ON CONFLICT (id) DO NOTHING
                """;

        // Use a blocking queue for producer-consumer pattern
        BlockingQueue<Optional<FileMeta>> queue = new LinkedBlockingQueue<>(1000);

        // Start consumer thread that will process items from the queue
        ExecutorService consumerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<Long> consumerFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return jdbi.withHandle(handle -> {
                    long count = 0;
                    PreparedBatch batch = handle.prepareBatch(sql);
                    
                    try {
                        handle.begin();
                        
                        while (true) {
                            Optional<FileMeta> item = queue.take();

                            // The only stop condition is the sentinel
                            if (item.isEmpty()) {
                                break;
                            }

                            FileMeta meta = item.get();
                            try {
                                batch
                                    .bind("id", meta.id())
                                    .bind("version", meta.version() == null ? "" : meta.version())
                                    .bind("root_element", meta.rootElement())
                                    .bind("file_size", meta.fileSize())
                                    .bind("path", meta.filePath())
                                    .bind("uri", meta.uri())
                                    .bind("work_id", meta.workId())
                                    .bind("expr_id", meta.exprId())
                                    .bind("man_id", meta.manId())
                                    .bind("cs_id", meta.csId())
                                    .add();

                                count++;
                                if (count % batchSize == 0) {
                                    batch.execute();
                                    handle.commit();
                                    handle.begin();
                                    batch = handle.prepareBatch(sql);
                                    System.out.printf("💾 committed %d files%n", count);
                                }
                            } catch (Exception e) {
                                errors.incrementAndGet();
                                System.err.printf("⚠️ error processing %s: %s%n",
                                        meta.filePath(), e.getMessage());
                            }
                        }

                        // Execute any remaining batch items
                        if (batch.size() > 0) {
                            batch.execute();
                        }
                        handle.commit();
                        
                    } catch (InterruptedException e) {
                        // Handle interruption properly
                        Thread.currentThread().interrupt();
                        handle.rollback();
                        throw new RuntimeException("Consumer thread interrupted", e);
                    } catch (Exception e) {
                        // Rollback on any other exception
                        handle.rollback();
                        throw e;
                    }
                    
                    return count;
                });
            } catch (Exception e) {
                System.err.printf("❌ consumer thread error: %s%n", e.getMessage());
                e.printStackTrace();
                return 0L;
            }
        }, consumerExecutor);

        // Producer side: virtual threads, bounded by semaphore
        try (var vtExecutor = Executors.newVirtualThreadPerTaskExecutor();
             Stream<Path> files = Files.walk(root).filter(Files::isRegularFile)
                     .filter(p -> p.toString().toLowerCase().endsWith(".xml"))) {

            Semaphore gate = new Semaphore(parallelism);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            files.forEach(path -> {
                try {
                    gate.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                var fut = CompletableFuture.runAsync(() -> {
                    try {
                        processFile(path).ifPresentOrElse(
                            meta -> {
                                try {
                                    queue.put(Optional.of(meta));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    errors.incrementAndGet();
                                }
                            },
                            () -> {} // error already logged in processFile
                        );
                    } finally {
                        gate.release();
                    }
                }, vtExecutor);

                futures.add(fut);
            });

            // Wait for all producers to finish
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            queue.put(Optional.empty()); // sentinel to signal consumer to stop
        }

        // Wait for consumer to finish and get the count
        long inserted = consumerFuture.join();

        System.out.printf("✅ done. inserted: %d, errors: %d%n", inserted, errors.get());
        return 0;
    }

    private Optional<FileMeta> processFile(Path file) {
        try {
            String filePath = file.toString();
            long size = Files.size(file);
            XmlInfo info = parseXmlInfo(file);

            // Extract URI and cellar identifiers
            String uri = deriveUri(filePath);
            CellarIdentifiers cellar = extractCellarIdentifiers(filePath);
            
            // Generate UUID v5 based on file content using uuid-creator library
            byte[] fileContent = Files.readAllBytes(file);
            UUID contentBasedId = generateContentBasedId(fileContent);

            long c = processed.incrementAndGet();
            if (c % 1000 == 0) {
                System.out.printf("📊 processed %d files…%n", c);
            }

            return Optional.of(new FileMeta(
                contentBasedId,
                filePath, 
                info.version(), 
                info.rootElement(), 
                size,
                uri,
                cellar.workId(),
                cellar.exprId(),
                cellar.manId(),
                cellar.csId()
            ));
        } catch (Exception e) {
            errors.incrementAndGet();
            System.err.printf("⚠️ error processing %s: %s%n", file, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Generates a UUID v5 based on the SHA-256 hash of the provided name.
     * Uses the uuid-creator library for standards-compliant UUID v5 generation.
     * UUID v5 ensures that the same content always produces the same UUID (deterministic).
     * Uses http://publications.europa.eu/resource/cellar as the namespace URI.
     */
    private UUID generateContentBasedId(byte[] content) throws Exception {
        String contentHash = hashFileContent(content);
        return UuidCreator.getNameBasedSha1(NAMESPACE_UUID, contentHash);
    }

    /**
     * Computes SHA-256 hash of file content as a hex string.
     * This hash is then used to generate a deterministic UUID v5.
     */
    private String hashFileContent(byte[] content) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(content);
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        
        return hexString.toString();
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

    /**
     * Extracts cellar identifier components from file path.
     * Format: {work-id}.{expr-id}.{man-id}/{cs-id}
     * Example: 16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_3
     */
    private CellarIdentifiers extractCellarIdentifiers(String filePath) {
        var matcher = CELLAR_PATTERN.matcher(filePath);
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

    // this is ugly, but at least we don't load the whole file into memory
    private XmlInfo parseXmlInfo(Path file) throws Exception {
        try (InputStream is = Files.newInputStream(file)) {
            XMLEventReader reader = XML_FACTORY.get().createXMLEventReader(is);
            try {
                while (reader.hasNext()) {
                    XMLEvent ev = reader.nextEvent();
                    if (ev.isStartElement()) {
                        StartElement se = ev.asStartElement();
                        String root = se.getName().getLocalPart();
                        
                        // When namespace-aware is false, attributes keep their prefix
                        Attribute attr = se.getAttributeByName(new QName("xsi:noNamespaceSchemaLocation"));
                        if (attr == null) {
                            // Fallback without prefix
                            attr = se.getAttributeByName(new QName("noNamespaceSchemaLocation"));
                        }
                        
                        String version = extractVersion(attr);
                        return new XmlInfo(root, version);
                    }
                }
            } finally {
                reader.close();  // Important!
            }
        }
        return new XmlInfo("unknown", null);
    }

    private String extractVersion(Attribute attr) {
        if (attr == null) return null;
        String value = attr.getValue();
        if (value == null || value.isEmpty()) return null;
        int idx = value.lastIndexOf('/');
        return idx >= 0 ? value.substring(idx + 1) : value;
    }

    record FileMeta(
        UUID id,
        String filePath, 
        String version, 
        String rootElement, 
        long fileSize,
        String uri,
        String workId,
        String exprId,
        String manId,
        String csId
    ) {}
    
    record CellarIdentifiers(
        String workId,
        String exprId,
        String manId,
        String csId
    ) {}
    
    record XmlInfo(String rootElement, String version) {}
}
