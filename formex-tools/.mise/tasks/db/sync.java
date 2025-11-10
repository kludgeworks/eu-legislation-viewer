///usr/bin/env jbang -Djava.rmi.server.hostname=127.0.0.1 "$0" "$@" ; exit $?
//JAVA 25+
//DEPS org.duckdb:duckdb_jdbc:1.4.1.0
//DEPS org.jdbi:jdbi3-core:3.49.6
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//DEPS info.picocli:picocli:4.7.7
//DEPS me.tongfei:progressbar:0.10.1
//DEPS com.fasterxml.woodstox:woodstox-core:7.1.1
//DEPS com.github.f4b6a3:uuid-creator:6.1.1
//SOURCES formex/cache/FileCache.java
//SOURCES formex/db/XmlFileRepository.java
//MISE description="Sync XML files from Europa Publications SPARQL endpoint into DuckDB"

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import formex.cache.FileCache;
import formex.db.XmlFileRepository;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Command(name = "sync", mixinStandardHelpOptions = true,
        description = "Sync XML files from Europa Publications SPARQL endpoint into DuckDB")
public class sync implements Callable<Integer> {

    @Option(names = {"-d", "--database"}, defaultValue = "formex.db",
            description = "DuckDB database file")
    String databaseFile;

    @Option(names = {"-e", "--endpoint"}, 
            defaultValue = "https://publications.europa.eu/webapi/rdf/sparql",
            description = "SPARQL endpoint URL")
    String sparqlEndpoint;

    @Option(names = {"-l", "--limit"},
            description = "Limit number of results (for testing)")
    Integer limit;

    private static final String SPARQL_QUERY = """
        PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
        PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
        
        SELECT DISTINCT ?item
        WHERE {
          ?expression cmr:lang "eng"^^xsd:language .
          ?manifestation cdm:manifestation_manifests_expression ?expression .
          ?item cdm:item_belongs_to_manifestation ?manifestation .
          ?item cmr:manifestationMimeType "application/xml;type=fmx4" .
        }
        %s
        """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    private static final int DOWNLOAD_THREADS = 12;
    private static final int HTTP_TIMEOUT_SECONDS = 120;

    public static void main(String[] args) {
        System.exit(new CommandLine(new sync()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("🔄 Starting sync process...");
        
        // Phase 1: Fetch URIs from SPARQL endpoint
        List<String> sparqlUris = fetchSparqlUris();
        if (sparqlUris.isEmpty()) {
            System.err.println("❌ No URIs returned from SPARQL query");
            return 1;
        }
        System.out.printf("✅ Found %,d content streams from SPARQL%n", sparqlUris.size());

        // Phase 2: Use database to compute diff (efficient for large datasets)
        XmlFileRepository repository = new XmlFileRepository(databaseFile);
        List<String> newUris = repository.computeNewUris(sparqlUris);
        
        if (newUris.isEmpty()) {
            System.out.println("✅ Database is up to date - no new files to download");
            return 0;
        }

        System.out.printf("📊 Diff: %,d new files to download%n", newUris.size());
        
        // Phase 2: Download files to persistent cache
        Map<String, DownloadResult> downloadResults = downloadFiles(newUris);
        
        // Count successes and failures
        long successCount = downloadResults.values().stream()
                .filter(r -> r.success).count();
        long failureCount = downloadResults.size() - successCount;
        
        System.out.printf("\n📦 Download complete: %,d succeeded, %,d failed%n", 
                successCount, failureCount);
        
        if (successCount == 0) {
            System.err.println("❌ No files downloaded successfully");
            return 1;
        }
        
        // Phase 4: Ingest downloaded files into database
        System.out.println("\n📥 Starting database ingestion...");
        
        // Filter successful downloads and build path-to-URI map
        FileCache cache = new FileCache(Path.of(".cache/formex-sync"));
        List<String> successfulUris = downloadResults.values().stream()
                .filter(r -> r.success)
                .map(r -> r.uri)
                .toList();
        
        Map<Path, String> pathUriMap = successfulUris.stream()
                .collect(java.util.stream.Collectors.toMap(
                    cache::getCachePath,
                    uri -> uri
                ));
        
        long ingestedCount = repository.insertFilesWithUris(pathUriMap);
        
        System.out.printf("✅ Sync complete: %,d files ingested%n", ingestedCount);
        
        return failureCount > 0 ? 1 : 0;
    }

    /**
     * Fetches content stream URIs from the Europa Publications SPARQL endpoint.
     */
    private List<String> fetchSparqlUris() throws Exception {
        System.out.println("🔍 Querying SPARQL endpoint...");
        
        // Add LIMIT clause if specified
        String limitClause = (limit != null) ? "LIMIT " + limit : "";
        String query = String.format(SPARQL_QUERY, limitClause);
        
        // Build SPARQL query URL
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = sparqlEndpoint + "?query=" + encodedQuery;
        
        // Execute HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/sparql-results+json")
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("SPARQL query failed with status: " + response.statusCode());
        }

        // Parse JSON response
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode bindings = root.path("results").path("bindings");

        List<String> uris = new ArrayList<>();
        for (JsonNode binding : bindings) {
            String uri = binding.path("item").path("value").asText();
            if (!uri.isEmpty()) {
                uris.add(uri);
            }
        }

        return uris;
    }


    
    /**
     * Downloads files in parallel to persistent cache with progress tracking.
     * Uses FileCache for permanent storage with SHA-256 hashed filenames.
     */
    private Map<String, DownloadResult> downloadFiles(List<String> uris) throws Exception {
        // Initialize file-based cache
        FileCache cache = new FileCache(Path.of(".cache/formex-sync"));
        Map<String, DownloadResult> results = new ConcurrentHashMap<>();
        AtomicInteger cacheHits = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        
        // Create progress bar with 2-second update interval
        try (ProgressBar pb = new ProgressBarBuilder()
                .setTaskName("Downloading")
                .setInitialMax(uris.size())
                .setUnit(" files", 1)
                .setUpdateIntervalMillis(2000)
                .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BLOCK)
                .showSpeed()
                .build()) {
            
            // Download in parallel
            ExecutorService executor = Executors.newFixedThreadPool(DOWNLOAD_THREADS);
            
            try {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                
                for (String uri : uris) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            // Check cache first, or download and cache
                            boolean wasCached = cache.contains(uri);
                            byte[] content = cache.get(uri).orElseGet(() -> {
                                try {
                                    byte[] downloaded = downloadFile(uri);
                                    cache.put(uri, downloaded);
                                    return downloaded;
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            
                            if (wasCached) {
                                cacheHits.incrementAndGet();
                            }
                            
                            // Success - content is in cache
                            results.put(uri, new DownloadResult(true, null, uri));
                            
                            // Update extra message with stats
                            int errorCount = errors.get();
                            int cached = cacheHits.get();
                            if (errorCount > 0 || cached > 0) {
                                pb.setExtraMessage(String.format("cached: %d | errors: %d", cached, errorCount));
                            }
                            
                        } catch (Exception e) {
                            results.put(uri, new DownloadResult(false, e.getMessage(), null));
                            int errorCount = errors.incrementAndGet();
                            System.err.printf("⚠️  [ERROR] %s: %s%n", uri, e.getMessage());
                            
                            // Update extra message to show error count
                            pb.setExtraMessage(String.format("cached: %d | errors: %d", cacheHits.get(), errorCount));
                            
                        } finally {
                            pb.step();  // Increment progress (thread-safe)
                        }
                    }, executor);
                    
                    futures.add(future);
                }
                
                // Wait for all downloads to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
            } finally {
                executor.shutdown();
            }
        }
        
        return results;
    }
    
    /**
     * Downloads a single file from the network.
     */
    private byte[] downloadFile(String uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .GET()
                .build();
        
        HttpResponse<byte[]> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        
        return response.body();
    }
    
    /**
     * Result of a download operation.
     * URI is stored so Phase 4 can retrieve content from cache.
     */
    record DownloadResult(boolean success, String error, String uri) {}
}
