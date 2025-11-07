package eu.legislation.formex.ingester.processor;

import com.github.f4b6a3.uuid.UuidCreator;
import eu.legislation.formex.ingester.model.SparqlUri;
import eu.legislation.formex.ingester.model.XmlMetadata;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StreamingXmlDownloadProcessor implements ItemProcessor<SparqlUri, XmlMetadata> {

    private final RestTemplate restTemplate;
    private final Path tempDir;
    private static final String CELLAR_NAMESPACE = "http://publications.europa.eu/resource/cellar";

    public StreamingXmlDownloadProcessor(
            RestTemplate restTemplate,
            @Value("${temp.dir}") String tempDirPath) throws IOException {
        this.restTemplate = restTemplate;
        this.tempDir = Files.createDirectories(Paths.get(tempDirPath));
    }

    @Override
    public XmlMetadata process(SparqlUri item) throws Exception {
        String uri = item.uri();

        // Extract ID components from URI
        IdComponents components = extractIdComponents(uri);

        // Download and hash in single pass
        Path tempFile = tempDir.resolve( UUID.randomUUID() + ".xml");
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

    private FileMetrics downloadWithMetrics(String uri, Path destination) {
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
