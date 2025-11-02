#!/usr/bin/env nu
#MISE description="List downloaded XML files that reference a specific Formex schema version and optionally filter by root element"
#USAGE flag "-l --limit <count>" help="Total maximum number of matching files to return"
#USAGE flag "-w --with-version" help="Include the version field in the output"
#USAGE flag "-r --root-tag <tag>" help="Filter by XML root element tag name"
#USAGE flag "-f --format <format>" help="Output format" default="table" {
#USAGE   choices "table" "json" "csv" "tsv" "jsonl"
#USAGE }
#USAGE arg versions... "Formex schema filename(s) to match (e.g. formex-06.02.1-20231031.xd)"

# Command: locate downloaded files referencing the requested schema version(s)
def main [
    ...versions: string
    --limit(-l): int = -1
    --with-version(-w)
    --root-tag(-r): string
    --format(-f): string = "table"
] {

  if ($versions | is-empty) {
      error make {
          msg: "Missing required version argument(s) (e.g. formex-06.02.1-20231031.xd)"
      }
  }

  let results = rg --files -g '*.xml' downloads
        | lines
        | each {|file|
            try {
                let xml_data = (open --raw $file | from xml)
                let schema = (
                    $xml_data
                    | get attributes.noNamespaceSchemaLocation?
                    | path basename
                )
                let root_element = ($xml_data | get tag)
                { file: $file, version: $schema, root_tag: $root_element }
            } catch {
                null
            }
        }
        | compact
        | where {|entry| $versions | any {|candidate| $candidate == $entry.version }}
        | where {|entry| 
            if ($root_tag | is-empty) {
                true
            } else {
                $entry.root_tag == $root_tag
            }
        }
        | enumerate
        | take until {|entry| $limit > 0 and $entry.index >= $limit }
        | each {|entry| $entry.item }
        | if $with_version {
            $in
        } else {
            each {|entry| $entry.file }
        }

    match $format {
        "json" => { $results | to json }
        "csv" => { $results | to csv }
        "tsv" => { $results | to tsv }
        "jsonl" => { $results | each { to json } | str join (char newline) }
        "table" => { $results }
        _ => {
            error make { msg: $"Unknown format: ($format)" }
        }
    }
}
