#!/usr/bin/env nu
#MISE description="Get title for a document by UUID"
#USAGE arg "<uuid>" help="UUID of the document (e.g., 649266ad-6b87-4a28-80ea-8f8c1aae32c4.0001.01/DOC_1)"
#USAGE flag "-e --endpoint <url>" help="SPARQL endpoint URL" default="https://publications.europa.eu/webapi/rdf/sparql"

# Get UUID from mise argument
let uuid = $env.usage_uuid

# Get endpoint (with default fallback)
let endpoint = ($env.usage_endpoint? | default "https://publications.europa.eu/webapi/rdf/sparql")

# The manifestation URI
let manifestation_uri = $"http://publications.europa.eu/resource/cellar/($uuid)"

# SPARQL query to get title
let sparql_query = ([
  "PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>"
  ""
  "SELECT ?title"
  "WHERE {"
  ("  <" + $manifestation_uri + ">")
  "    cdm:manifestation_manifests_expression ?expression ."
  "  "
  "  ?expression cdm:expression_belongs_to_work ?work ."
  "  "
  "  OPTIONAL { ?expression cdm:expression_title ?title }"
  "  OPTIONAL { ?work cdm:work_title ?title }"
  "  "
  "  FILTER(lang(?title) = \"en\" || lang(?title) = \"\")"
  "}"
] | str join "\n")

# Build URL with encoded query parameter
let url = $"($endpoint)?query=($sparql_query | url encode --all)"

# Fetch SPARQL results and parse XML
let response = (
    http get --headers [Accept "application/sparql-results+xml"] $url
    | from xml
)

# Navigate XML structure to extract results
let results = (
    $response.content
    | where tag == "results"
    | get content.0
    | each { |result|
        let bindings = $result.content

        let title = (
            $bindings
            | where tag == "binding" and attributes.name == "title"
            | get content.0.content.0.content
            | first
        )

        { title: $title }
    }
)

# Display results
if ($results | length) > 0 {
    print $"Manifestation: ($manifestation_uri)"
    print $"Title: ($results.0.title)"
} else {
    print $"No title found for: ($manifestation_uri)"
}
