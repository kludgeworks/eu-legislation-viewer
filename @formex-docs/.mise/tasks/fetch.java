///usr/bin/env jbang --quiet "$0" "$@" ; exit $?
//JAVA 25
//PREVIEW
//MISE description="Fetch English FMX4 manifestations from Europa Publications RDF endpoint"

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class fetch {
    public static void main(String[] args) throws Exception {
    String sparqlQuery = """
        PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
        PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

        SELECT DISTINCT ?item
        WHERE {
          # Find expressions in English
          ?expression cmr:lang "eng"^^xsd:language .

          # Find manifestations of those expressions
          ?manifestation cdm:manifestation_manifests_expression ?expression .

          # Find items (content streams) belonging to those manifestations
          ?item cdm:item_belongs_to_manifestation ?manifestation .

          # Filter for fmx4 format only
          ?item cmr:manifestationMimeType "application/xml;type=fmx4" .
        }
        """;

    // Default endpoint - can be overridden via command line argument
    String endpoint = args.length > 0 ? args[0] : "https://publications.europa.eu/webapi/rdf/sparql";

    // Create HTTP client
    HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    // Encode query for URL
    String encodedQuery = URLEncoder.encode(sparqlQuery, StandardCharsets.UTF_8);

    // Build request URL with query parameter
    String requestUrl = endpoint + "?query=" + encodedQuery;

    // Create HTTP request
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(requestUrl))
        .header("Accept", "application/sparql-results+xml")
        .GET()
        .build();

    // Send request and get response
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
        System.err.println("Error: HTTP " + response.statusCode());
        System.err.println(response.body());
        System.exit(1);
    }

    // Parse XML response manually (no external libraries)
    String xmlResponse = response.body();

    // Extract all "item" URI bindings using regex
    // Looking for: <binding name="item"><uri>...</uri></binding>
    Pattern pattern = Pattern.compile("<binding\\s+name=\"item\">\\s*<uri>([^<]+)</uri>");
    Matcher matcher = pattern.matcher(xmlResponse);

    // Output each item URI on a new line
    while (matcher.find()) {
        System.out.println(matcher.group(1));
    }
    }
}
