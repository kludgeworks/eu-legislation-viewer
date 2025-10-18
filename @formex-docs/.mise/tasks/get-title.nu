#!/usr/bin/env nu
#MISE description="Get title for a document by UUID"
#USAGE arg "<uuid>" help="UUID of the document (e.g., 649266ad-6b87-4a28-80ea-8f8c1aae32c4.0001.01/DOC_1)"
#USAGE flag "-e --endpoint <url>" help="SPARQL endpoint URL" default="https://publications.europa.eu/webapi/rdf/sparql"

# Get UUID from mise argument
let uuid = $env.usage_uuid

# Get endpoint (with default fallback)
let endpoint = ($env.usage_endpoint? | default "https://publications.europa.eu/webapi/rdf/sparql")

# The work URI
let work_uri = $"http://publications.europa.eu/resource/cellar/($uuid)"

# SPARQL query to get title
let sparql_query = ([
  "PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>"
  "PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>"
  "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>"
  ""
  "SELECT ?title"
  "WHERE {"
  ("  ?expression cdm:expression_belongs_to_work <" + $work_uri + "> ;")
  "              cmr:lang "eng"^^xsd:language ;"
  "              cdm:expression_title ?title ."
  "}"
] | str join "
")

# Build URL with encoded query parameter
let url = $"($endpoint)?query=($sparql_query | url encode --all)"

# Fetch SPARQL results and parse JSON
let response = (
    http get --headers [Accept "application/sparql-results+json"] $url
    | from json
)

# Navigate JSON structure to extract results
let results = (
    $response.results.bindings
    | where { |binding| $binding.title."xml:lang" == "en" }
    | each { |binding| { title: $binding.title.value } }
)

# Display results using gum
if ($results | length) > 0 {
    gum style --foreground 212 --bold --margin "1 0" "Document Title"
    gum style --foreground 240 $"Work: ($work_uri)"
    gum style --foreground 86 --bold --margin "1 0" $results.0.title
} else {
    gum style --foreground 196 --bold "No title found"
    gum style --foreground 240 $"Work: ($work_uri)"
}