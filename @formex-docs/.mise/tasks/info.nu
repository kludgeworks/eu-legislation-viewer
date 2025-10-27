#!/usr/bin/env nu
#MISE description="Get metadata for a work and related resources"
#MISE alias="i"
#USAGE arg "[identifier]" help="Identifier in the form uuid[.expr-id[.man-id]][/CS_ID[.xml]]"

# Only keep truly reusable utilities
def decode-html [] {
    if $in == null {
        null
    } else {
        try {
            $"<div>($in)</div>" | ^htmlq --text div | str trim
        } catch {
            $in
        }
    }
}

# Format successful document details panel
def format-details [selection: record, has_filters: bool, results: list<record>] {
    let base_lines = (
        [
            [213 "Title:" 120 $selection.title]
            [213 "CELEX:" 208 $selection.celex]
            [244 "Work:" "" $selection.work_uri]
        ]
        | each {|row|
            [
                (^gum style --foreground $row.0 --bold $"($row.1)")
                (if $row.2 != "" {
                    ^gum style --foreground $row.2 --bold $"($row.3)"
                } else {
                    ^gum style --faint $"($row.3)"
                })
            ]
            | str join " "
        }
    )

    let resource_lines = (
        if $has_filters {
            [
                ["Expression:" $selection.expression_id $selection.expression_uri]
                ["Manifestation:" $selection.manifestation_id $selection.manifestation_uri]
                ["Content Stream:" $selection.content_stream_id $selection.item_uri]
            ]
            | each {|row|
                [
                    (^gum style --foreground 213 --bold $row.0)
                    (
                        if $row.1 == null {
                            ^gum style --faint "(unknown)"
                        } else {
                            ^gum style --foreground 111 --bold $"($row.1)"
                        }
                    )
                    (^gum style --faint $"($row.2)")
                ]
                | str join " "
            }
        } else {
            []
        }
    )

    let note = (
        if (not $has_filters) and (($results | length) > 1) {
            $results
            | each {|r| $"($r.expression_id) / ($r.manifestation_id) / ($r.content_stream_id)" }
            | uniq
            | take 6
            | str join ", "
            | $"Available expr/man/cs: ($in)"
            | ^gum style --faint $in
            | [$in]
        } else {
            []
        }
    )

    [$base_lines $resource_lines $note]
    | flatten
    | prepend (^gum style --foreground 212 --bold --underline "Document Details")
    | ^gum style --border-foreground 212 --border double --align center --width 90 --margin "1 2" --padding "1 4" ...$in
}

# Format error panel when no results found
def format-no-results [work_uri: string] {
    [
        (^gum style --foreground 196 --bold "No metadata found")
        ([(^gum style --foreground 244 --bold "Work:") (^gum style --faint $work_uri)] | str join " ")
    ]
    | ^gum style --border-foreground 196 --border double --align center --width 60 --margin "1 2" --padding "1 4" ...$in
}

# Format error panel when filters don't match
def format-no-matches [expr_filter, man_filter, cs_filter, results: list<record>] {
    let available = (
        $results
        | each {|r| $"($r.expression_id) / ($r.manifestation_id) / ($r.content_stream_id)" }
        | uniq
        | take 6
        | str join "\n"
    )

    [
        (^gum style --foreground 196 --bold "No matching resources")
        (^gum style --foreground 244 --bold "Requested")
        (
            [
                (if $expr_filter != null { $"Expression: ($expr_filter)" } else { "" })
                (if $man_filter != null { $"Manifestation: ($man_filter)" } else { "" })
                (if $cs_filter != null { $"Content Stream: ($cs_filter)" } else { "" })
            ]
            | where {|it| $it != "" }
            | str join " • "
            | if $in != "" { ^gum style --faint $in } else { "" }
        )
        (^gum style --foreground 244 --bold "Available expr/man/cs")
        (^gum style --faint $available)
    ]
    | where {|it| $it != "" }
    | ^gum style --border-foreground 196 --border double --align center --width 80 --margin "1 2" --padding "1 4" ...$in
}


# Main - everything is a pipeline
def main [identifier?: string] {
    # Parse and normalize identifier in one pipeline
    let id = (
        $identifier
        | default ($env.usage_identifier? | default "")
        | if $in == "" {
            error make { msg: "Provide an identifier argument in the form uuid[.expr-id[.man-id]][/CS_ID]." }
        } else { $in }
        | str trim
        | str replace --regex '\.xml$' ''
        | split row '/'
        | {
            parts: ($in.0 | split row '.')
            work: ($in.0 | split row '.' | first)
            expr: ($in.0 | split row '.' | get --optional 1 | if $in != null { str upcase } else { null })
            man: ($in.0 | split row '.' | get --optional 2 | if $in != null { str upcase } else { null })
            cs: ($in | get --optional 1 | if $in != null { str upcase } else { null })
        }
    )

    # Fetch and transform metadata in one pipeline
    let results = (
        [
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
            $"  FILTER\(?work = <http://publications.europa.eu/resource/cellar/($id.work)>\)"
            "}"
        ]
        | str join "\n"
        | url encode --all
        | $"https://publications.europa.eu/webapi/rdf/sparql?query=($in)"
        | http get --headers [Accept "application/sparql-results+json"] $in
        | from json
        | get results.bindings
        | each {|b|
            {
                title: ($b.title.value | decode-html)
                celex: $b.celex.value
                work_uri: $"http://publications.europa.eu/resource/cellar/($id.work)"
                expression_uri: $b.expression.value
                manifestation_uri: $b.manifestation.value
                item_uri: $b.item.value
                expression_id: ($b.expression.value | split row '.' | last | str upcase)
                manifestation_id: ($b.manifestation.value | split row '.' | last | str upcase)
                content_stream_id: ($b.item.value | split row '/' | last | str upcase)
            }
        }
    )

    # Early exit if no results
    if ($results | is-empty) {
        format-no-results $"http://publications.europa.eu/resource/cellar/($id.work)"
        exit 1
    }

    # Pure data transformation: find the selection or determine failure
    let has_filters = ($id.expr != null) or ($id.man != null) or ($id.cs != null)

    let selection_info = (
        $results
        | if $has_filters {
            where {|r|
                ((if $id.expr != null { $r.expression_id == $id.expr } else { true })
                and (if $id.man != null { $r.manifestation_id == $id.man } else { true })
                and (if $id.cs != null { $r.content_stream_id == $id.cs } else { true }))
            }
            | if ($in | is-empty) {
                { status: "no_match" }
            } else {
                { status: "ok", item: ($in | first) }
            }
        } else {
            { status: "ok", item: ($in | first) }
        }
    )

    # Handle error case: no matching resources
    if $selection_info.status == "no_match" {
        format-no-matches $id.expr $id.man $id.cs $results
        exit 1
    }

    # Success path: display formatted output
    format-details $selection_info.item $has_filters $results
}
