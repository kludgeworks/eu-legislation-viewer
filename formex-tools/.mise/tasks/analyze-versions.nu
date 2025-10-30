#!/usr/bin/env nu
#MISE description="Analyze formex schema versions and root elements across downloaded XML files"
#USAGE flag "-f --format <format>" help="Output format: table, json, jsonl, md" default="table"
#USAGE flag "-l --limit <limit>" help="Limit number of files to process (for testing)" type="int"

# Parse all XML files and extract both the schema version and root element
# then group by version and count root elements within each version

# Command: summarize schema version and root element usage across the downloaded dataset
def main [
    --format(-f): string = "table"
    --limit(-l): int
] {

    let results = (
        glob downloads/**/*.xml
        | if $limit != null { first $limit } else { $in }
        | par-each {|file|
            # Parse XML and extract both version and root element information
            # Use try/catch to handle files with encoding issues
            try {
                let parsed = (open --raw $file | from xml)
                let root_element = $parsed.tag
                let version_attr = ($parsed.attributes.noNamespaceSchemaLocation?)
                let version = if $version_attr != null {
                    $version_attr | path basename
                } else {
                    null
                }

                if $version != null {
                    {
                        file: $file,
                        version: $version,
                        root_element: $root_element
                    }
                } else {
                    null
                }
            } catch {
                # Skip files with encoding or parsing errors
                null
            }
        }
        | compact
        | group-by version
        | transpose version_name version_data
        | insert total_count {|row| $row.version_data | length}
        | insert root_elements {|row|
            $row.version_data
            | get root_element
            | group-by
            | transpose root_element instances
            | insert count {|inner| $inner.instances | length}
            | select root_element count
            | sort-by count --reverse
        }
        | select version_name total_count root_elements
        | sort-by total_count --reverse
    )


    match $format {
        "json" => { $results | to json }
        "jsonl" => { $results | each { to json } | str join (char newline) }
        "table" => {
            # Single table format with embedded root element breakdown
            $results | table --expand
        }
        _ => {
            error make {
                msg: $"Unknown format: ($format). Use: table, json, or jsonl"
            }
        }
    }
}
