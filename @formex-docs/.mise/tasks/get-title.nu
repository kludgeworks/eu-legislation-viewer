#!/usr/bin/env nu
#MISE description="Get title for a document by UUID"
#USAGE flag "--uuid <uuid>" help="UUID of the document to fetch the title for"
#USAGE flag "-e --endpoint <url>" help="SPARQL endpoint URL" default="https://publications.europa.eu/webapi/rdf/sparql"

# Get UUID from mise argument
let uuid = $env.usage_uuid

# Get endpoint (with default fallback)
let endpoint = ($env.usage_endpoint? | default "https://publications.europa.eu/webapi/rdf/sparql")

# The work URI
let work_uri = $"http://publications.europa.eu/resource/cellar/($uuid)"

# SPARQL query to get title and CELEX identifier
let sparql_query = ([
  "PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>"
  "PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>"
  "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>"
  ""
  "SELECT ?title ?celex"
  "WHERE {"
  ("  ?work cdm:resource_legal_id_celex ?celex .")
  ("  ?expression cdm:expression_belongs_to_work ?work ;")
  "              cmr:lang "eng"^^xsd:language ;"
  "              cdm:expression_title ?title ."
  ("  FILTER(?work = <" + $work_uri + ">)")
  "}"
] | str join "\n")

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
    | each { |binding| { title: $binding.title.value celex: $binding.celex.value } }
)

# Display results using gum
if ($results | length) > 0 {
    let title = $results.0.title
    let celex = $results.0.celex
    let header = (gum style --foreground 212 --bold --underline "Document Details")
    let title_label = (gum style --foreground 213 --bold "Title:")
    let celex_label = (gum style --foreground 213 --bold "CELEX:")
    let work_label = (gum style --foreground 244 --bold "Work:")
    let title_value = (gum style --foreground 120 --bold $"($title)")
    let celex_value = (gum style --foreground 208 --bold $"($celex)")
    let work_value = (gum style --faint $"($work_uri)")
    let title_line = ([$title_label $title_value] | str join " ")
    let celex_line = ([$celex_label $celex_value] | str join " ")
    let work_line = ([$work_label $work_value] | str join " ")
    gum style --border-foreground 212 --border double --align center --width 80 --margin "1 2" --padding "1 4" $header $title_line $celex_line $work_line
} else {
    let message = (gum style --foreground 196 --bold "No title found")
    let work_label = (gum style --foreground 244 --bold "Work:")
    let work_value = (gum style --faint $"($work_uri)")
    let work_line = ([$work_label $work_value] | str join " ")
    gum style --border-foreground 196 --border double --align center --width 60 --margin "1 2" --padding "1 4" $message $work_line
}
