#!/usr/bin/env nu
#MISE description="Get metadata for a work and related resources"
#MISE alias="i"
#USAGE arg "[identifier]" help="Identifier in the form uuid[.expr-id[.man-id]][/CS_ID[.xml]]"
#USAGE flag "--id <identifier>" help="Identifier in the form uuid[.expr-id[.man-id]][/CS_ID[.xml]]"
#USAGE flag "--uuid <uuid>" help="Deprecated: bare UUID input (legacy compatibility)"
#USAGE flag "--expr-id <id>" help="Optional expression identifier (e.g. 0006)"
#USAGE flag "--man-id <id>" help="Optional manifestation identifier (e.g. 02)"
#USAGE flag "--cs-id <id>" help="Optional content stream identifier (e.g. DOC_3)"
#USAGE flag "-e --endpoint <url>" help="SPARQL endpoint URL" default="https://publications.europa.eu/webapi/rdf/sparql"

# Decode minimal HTML entities (titles sometimes arrive encoded)
def decode-html [text: string] {
    if $text == null {
        return null
    }

    try {
        $text
        | ^python3 -c "import sys,html; sys.stdout.write(html.unescape(sys.stdin.read()))"
    } catch {
        $text
    }
}

# Extract MISE arguments
let identifier_positional = ($env.usage_identifier? | default "")
let identifier_arg = ($env.usage_id? | default "")
let legacy_uuid_arg = ($env.usage_uuid? | default "")
let endpoint = ($env.usage_endpoint? | default "https://publications.europa.eu/webapi/rdf/sparql")
let expr_override_raw = ($env.usage_expr_id? | default "")
let man_override_raw = ($env.usage_man_id? | default "")
let cs_override_raw = ($env.usage_cs_id? | default "")

let identifier_source = (
    if $identifier_positional != "" {
        $identifier_positional
    } else if $identifier_arg != "" {
        $identifier_arg
    } else {
        $legacy_uuid_arg
    }
)
if $identifier_source == "" {
    error make {
        msg: "Provide an identifier argument, --id <uuid[.expr-id[.man-id]][/CS_ID]>, or legacy --uuid <uuid>."
    }
}

let identifier_clean = ($identifier_source | str trim)
let parsed_identifier = (
    $identifier_clean
    | parse --regex "(?P<prefix>.*?)(?P<work>[0-9a-fA-F-]{36})(?:\\.(?P<expr>[0-9A-Za-z]+))?(?:\\.(?P<man>[0-9A-Za-z]+))?(?:/(?P<cs>[^/]+?))?(?:\\.xml)?$"
)

if ($parsed_identifier | length) == 0 {
    error make {
        msg: $"Could not parse identifier: ($identifier_clean)"
    }
}

let identifier_row = $parsed_identifier.0
let uuid = $identifier_row.work

if $uuid == "" {
    error make {
        msg: $"Missing work identifier in: ($identifier_clean)"
    }
}

let expr_from_id_raw = ($identifier_row.expr? | default "")
let man_from_id_raw = ($identifier_row.man? | default "")
let cs_from_id_raw = ($identifier_row.cs? | default "")

let expr_from_id = (if $expr_from_id_raw == "" { null } else { $expr_from_id_raw })
let man_from_id = (if $man_from_id_raw == "" { null } else { $man_from_id_raw })
let cs_from_id = (if $cs_from_id_raw == "" { null } else { $cs_from_id_raw })

# Determine filters (defaults come from identifier, overrides take precedence)
let expr_filter_candidate = (if $expr_override_raw == "" { $expr_from_id } else { $expr_override_raw })
let man_filter_candidate = (if $man_override_raw == "" { $man_from_id } else { $man_override_raw })
let cs_filter_candidate = (if $cs_override_raw == "" { $cs_from_id } else { $cs_override_raw })

let expr_filter_norm = (
    if $expr_filter_candidate == null {
        null
    } else {
        let trimmed = ($expr_filter_candidate | str trim)
        if $trimmed == "" { null } else { $trimmed | str upcase }
    }
)

let man_filter_norm = (
    if $man_filter_candidate == null {
        null
    } else {
        let trimmed = ($man_filter_candidate | str trim)
        if $trimmed == "" { null } else { $trimmed | str upcase }
    }
)

let cs_filter_norm = (
    if $cs_filter_candidate == null {
        null
    } else {
        let trimmed = ($cs_filter_candidate | str trim | str replace --regex '(?i)\.xml$' "")
        if $trimmed == "" { null } else { $trimmed | str upcase }
    }
)

# Construct URIs and query
let work_uri = $"http://publications.europa.eu/resource/cellar/($uuid)"
let work_filter = (["  FILTER(?work = <" $work_uri ">)"] | str join "")
let sparql_query = ([
  "PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>"
  "PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>"
  "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>"
  ""
  "SELECT ?title ?celex ?expression ?manifestation ?item"
  "WHERE {"
  "  ?work cdm:resource_legal_id_celex ?celex ."
  "  ?expression cdm:expression_belongs_to_work ?work ;"
  "              cmr:lang \"eng\"^^xsd:language ;"
  "              cdm:expression_title ?title ."
  ""
  "  ?manifestation cdm:manifestation_manifests_expression ?expression ."
  ""
  "  ?item cdm:item_belongs_to_manifestation ?manifestation ."
  ""
  $work_filter
  "}"
] | str join "\n")

# Build URL with encoded query parameter
let url = $"($endpoint)?query=($sparql_query | url encode --all)"

# Fetch SPARQL results and parse JSON
let response = (
    http get --headers [Accept "application/sparql-results+json"] $url
    | from json
)

let results = (
    $response.results.bindings
    | each { |binding|
        let title = (decode-html $binding.title.value)
        let celex = $binding.celex.value
        let expression_uri = $binding.expression.value
        let manifestation_uri = $binding.manifestation.value
        let item_uri = $binding.item.value

        let expression_parts = ($expression_uri | split row ".")
        let manifestation_parts = ($manifestation_uri | split row ".")
        let item_parts = ($item_uri | split row "/")

        let expression_id = (if ($expression_parts | length) > 1 { $expression_parts | last | str upcase } else { null })
        let manifestation_id = (if ($manifestation_parts | length) > 1 { $manifestation_parts | last | str upcase } else { null })
        let content_stream_id = (if ($item_parts | length) > 0 { $item_parts | last | str upcase } else { null })

        {
            title: $title,
            celex: $celex,
            work_uri: $work_uri,
            expression_uri: $expression_uri,
            manifestation_uri: $manifestation_uri,
            item_uri: $item_uri,
            expression_id: $expression_id,
            manifestation_id: $manifestation_id,
            content_stream_id: $content_stream_id
        }
    }
)

let has_results = ($results | length) > 0
if not $has_results {
    let message = (gum style --foreground 196 --bold "No metadata found")
    let work_label = (gum style --foreground 244 --bold "Work:")
    let work_value = (gum style --faint $"($work_uri)")
    let work_line = ([$work_label $work_value] | str join " ")
    gum style --border-foreground 196 --border double --align center --width 60 --margin "1 2" --padding "1 4" $message $work_line
    exit 1
}

let has_filters = (
    ($expr_filter_norm != null) or ($man_filter_norm != null) or ($cs_filter_norm != null)
)

let filtered_results = (
    $results
    | where { |res|
        let expr_ok = (if $expr_filter_norm != null { $res.expression_id == $expr_filter_norm } else { true })
        let man_ok = (if $man_filter_norm != null { $res.manifestation_id == $man_filter_norm } else { true })
        let cs_ok = (if $cs_filter_norm != null { $res.content_stream_id == $cs_filter_norm } else { true })
        ($expr_ok and $man_ok) and $cs_ok
    }
)

if $has_filters and ($filtered_results | length) == 0 {
    let message = (gum style --foreground 196 --bold "No matching resources" )
    let details = (
        [
            (if $expr_filter_norm != null { $"Expression: ($expr_filter_norm)" } else { "" })
            (if $man_filter_norm != null { $"Manifestation: ($man_filter_norm)" } else { "" })
            (if $cs_filter_norm != null { $"Content Stream: ($cs_filter_norm)" } else { "" })
        ]
        | where {|it| $it != "" }
        | str join " • "
    )

    let available = (
        $results
        | each { |res| $"($res.expression_id) / ($res.manifestation_id) / ($res.content_stream_id)" }
        | uniq
        | take 6
        | str join "\n"
    )

    let header = (gum style --foreground 244 --bold "Requested")
    let detail_line = (if $details == "" { "" } else { gum style --faint $details })
    let available_header = (gum style --foreground 244 --bold "Available expr/man/cs")
    gum style --border-foreground 196 --border double --align center --width 80 --margin "1 2" --padding "1 4" $message $header $detail_line $available_header (gum style --faint $available)
    exit 1
}

let selected_results = (if $has_filters and ($filtered_results | length) > 0 { $filtered_results } else { $results })
let selection = $selected_results.0

let title = $selection.title
let celex = $selection.celex
let expression_id = $selection.expression_id
let manifestation_id = $selection.manifestation_id
let content_stream_id = $selection.content_stream_id
let expression_uri = $selection.expression_uri
let manifestation_uri = $selection.manifestation_uri
let item_uri = $selection.item_uri

let header = (gum style --foreground 212 --bold --underline "Document Details")
let title_label = (gum style --foreground 213 --bold "Title:")
let celex_label = (gum style --foreground 213 --bold "CELEX:")
let work_label = (gum style --foreground 244 --bold "Work:")
let title_value = (gum style --foreground 120 --bold $"($title)")
let celex_value = (gum style --foreground 208 --bold $"($celex)")
let work_value = (gum style --faint $"($work_uri)")

let lines = [
    ([$title_label $title_value] | str join " "),
    ([$celex_label $celex_value] | str join " "),
    ([$work_label $work_value] | str join " ")
]

let expression_label = (gum style --foreground 213 --bold "Expression:")
let manifestation_label = (gum style --foreground 213 --bold "Manifestation:")
let item_label = (gum style --foreground 213 --bold "Content Stream:")

let expression_value = (if $expression_id == null { gum style --faint "(unknown)" } else { gum style --foreground 111 --bold $"($expression_id)" })
let manifestation_value = (if $manifestation_id == null { gum style --faint "(unknown)" } else { gum style --foreground 111 --bold $"($manifestation_id)" })
let item_value = (if $content_stream_id == null { gum style --faint "(unknown)" } else { gum style --foreground 111 --bold $"($content_stream_id)" })

let expression_line = ([$expression_label $expression_value (gum style --faint $"($expression_uri)")] | str join " ")
let manifestation_line = ([$manifestation_label $manifestation_value (gum style --faint $"($manifestation_uri)")] | str join " ")
let item_line = ([$item_label $item_value (gum style --faint $"($item_uri)")] | str join " ")

let enriched_lines = (if $has_filters { $lines | append $expression_line | append $manifestation_line | append $item_line } else { $lines })

let note = (
    if (not $has_filters) and (($results | length) > 1) {
        let variants = (
            $results
            | each { |res| $"($res.expression_id) / ($res.manifestation_id) / ($res.content_stream_id)" }
            | uniq
            | take 6
            | str join ", "
        )
        (gum style --faint $"Available expr/man/cs: ($variants)")
    } else { null }
)

let panel_args = (if $note == null { $enriched_lines } else { $enriched_lines | append $note })

gum style --border-foreground 212 --border double --align center --width 90 --margin "1 2" --padding "1 4" $header ...$panel_args
