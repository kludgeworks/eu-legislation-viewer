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


# Main - everything is a pipeline
def main [identifier?: string] {
    # Parse and validate identifier in one flow
    let parsed = (
        $identifier
        | default ($env.usage_identifier? | default "")
        | if $in == "" {
            error make { msg: "Provide an identifier argument in the form uuid[.expr-id[.man-id]][/CS_ID]." }
        } else { $in }
        | str trim
        | parse --regex '(?P<prefix>.*?)(?P<work>[0-9a-fA-F-]{36})(?:\.(?P<expr>[0-9A-Za-z]+))?(?:\.(?P<man>[0-9A-Za-z]+))?(?:/(?P<cs>[^/]+?))?(?:\.xml)?$'
        | if ($in | is-empty) {
            error make { msg: "Could not parse identifier" }
        } else {
            get 0
            | if $in.work == "" {
                error make { msg: "Missing work identifier" }
            } else { $in }
        }
    )

    # Build filters in one transformation
    let filters = (
        {
            expr: $parsed.expr
            man: $parsed.man
            cs: $parsed.cs
        }
        | transpose key value
        | each {|row|
            {
                key: $row.key
                value: (
                    $row.value
                    | if $row.key == "cs" and $in != null {
                        str replace --regex '(?i)\.xml$' ""
                    } else { $in }
                    | if $in == null or $in == "" {
                        null
                    } else {
                        str trim | if $in == "" { null } else { str upcase }
                    }
                )
            }
        }
        | transpose -r -d
        | into record
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
            $"  FILTER\(?work = <http://publications.europa.eu/resource/cellar/($parsed.work)>\)"
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
                work_uri: $"http://publications.europa.eu/resource/cellar/($parsed.work)"
                expression_uri: $b.expression.value
                manifestation_uri: $b.manifestation.value
                item_uri: $b.item.value
                expression_id: (
                    $b.expression.value
                    | if ($in | str contains '.') {
                        str replace --regex '^.*\.' '' | str upcase
                    } else { null }
                )
                manifestation_id: (
                    $b.manifestation.value
                    | if ($in | str contains '.') {
                        str replace --regex '^.*\.' '' | str upcase
                    } else { null }
                )
                content_stream_id: (
                    $b.item.value
                    | if ($in | str contains '/') {
                        str replace --regex '^.*/' '' | str upcase
                    } else { null }
                )
            }
        }
    )

    # Early exit if no results
    if ($results | is-empty) {
        [
            (^gum style --foreground 196 --bold "No metadata found")
            ([(^gum style --foreground 244 --bold "Work:") (^gum style --faint $"http://publications.europa.eu/resource/cellar/($parsed.work)")] | str join " ")
        ]
        | ^gum style --border-foreground 196 --border double --align center --width 60 --margin "1 2" --padding "1 4" ...$in
        exit 1
    }

    # Filter and select in one flow
    let has_filters = ($filters.expr != null) or ($filters.man != null) or ($filters.cs != null)

    let selection = (
        $results
        | if $has_filters {
            where {|r|
                ((if $filters.expr != null { $r.expression_id == $filters.expr } else { true })
                and (if $filters.man != null { $r.manifestation_id == $filters.man } else { true })
                and (if $filters.cs != null { $r.content_stream_id == $filters.cs } else { true }))
            }
            | if ($in | is-empty) {
                # Format no matches error
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
                            (if $filters.expr != null { $"Expression: ($filters.expr)" } else { "" })
                            (if $filters.man != null { $"Manifestation: ($filters.man)" } else { "" })
                            (if $filters.cs != null { $"Content Stream: ($filters.cs)" } else { "" })
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

                exit 1
            } else {
                first
            }
        } else {
            first
        }
    )

    # Display formatted output
    format-details $selection $has_filters $results
}
