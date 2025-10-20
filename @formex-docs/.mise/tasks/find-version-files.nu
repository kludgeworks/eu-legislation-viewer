#!/usr/bin/env nu
#MISE description="List downloaded XML files that reference a specific Formex schema version"
#USAGE flag "-l --limit <count>" help="Total maximum number of matching files to return"
#USAGE flag "-f --format <format>" help="Output format" default="table" {
#USAGE   choices "table" "json" "csv" "tsv" "jsonl"
#USAGE }
#USAGE arg versions... "Formex schema filename(s) to match (e.g. formex-06.02.1-20231031.xd)"

def main [...versions: string, --limit(-l): int = -1, --limit-per-version(-p): int = -1, --with-version(-w), --format(-f): string = "table"] {q

    if ($versions | is-empty) {
        error make { msg: "Missing required version argument(s) (e.g. formex-06.02.1-20231031.xd)" }
    }
    
    let results = rg --files -g '*.xml' downloads
        | lines
        | each {|file|
            try {
                let schema = (
                    open --raw $file
                    | from xml
                    | get attributes.noNamespaceSchemaLocation?
                    | path basename
                )
                { file: $file, version: $schema }
            } catch {
                null
            }
        }
        | compact
        | where {|entry| $versions | any {|candidate| $candidate == $entry.version }}
        | enumerate
        | take until {|entry| $limit > 0 and $entry.index >= $limit }
        | each {|entry| $entry.item }

    match $format {
        "json" => { $results | to json }
        "csv" => { $results | to csv }
        "tsv" => { $results | to tsv }
        "jsonl" => { $results | each { to json } | str join (char newline) }
        "table" => { $results }
        _ => {
            error make { msg: $"Unknown format: ($format). Use: table, json, csv, tsv, or jsonl" }
        }
    }        
}
