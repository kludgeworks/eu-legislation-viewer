package eu.legislation.formex.ingester.reader;

import com.jayway.jsonpath.JsonPath;
import eu.legislation.formex.ingester.model.SparqlUri;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Iterator;
import java.util.List;

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
        setName("sparqlUriReader");
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
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
