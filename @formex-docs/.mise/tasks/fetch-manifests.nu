#!/usr/bin/env nu
#MISE description="Fetch English FMX4 manifestations from Europa Publications RDF endpoint"
#USAGE flag "--endpoint <url>" help="SPARQL endpoint URL" default="https://publications.europa.eu/webapi/rdf/sparql"

# SPARQL query to find English FMX4 manifestations
let sparql_query = "
PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT DISTINCT ?item
WHERE {
  # Find expressions in English
  ?expression cmr:lang \"eng\"^^xsd:language .

  # Find manifestations of those expressions
  ?manifestation cdm:manifestation_manifests_expression ?expression .

  # Find items (content streams) belonging to those manifestations
  ?item cdm:item_belongs_to_manifestation ?manifestation .

  # Filter for fmx4 format only
  ?item cmr:manifestationMimeType \"application/xml;type=fmx4\" .
}
"

# Get endpoint from mise argument (with default fallback)
let endpoint = ($env.usage_endpoint? | default "https://publications.europa.eu/webapi/rdf/sparql")

# Build URL with encoded query parameter
let url = $"($endpoint)?query=($sparql_query | url encode --all)"

# Fetch SPARQL results and parse JSON
let response = (
    http get --headers [
        Accept
        "application/sparql-results+json"
    ] $url
    | from json
)

# Navigate JSON structure
$response.results.bindings
| get item.value
| str join "\n"
