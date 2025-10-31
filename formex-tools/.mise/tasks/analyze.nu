#!/usr/bin/env nu
#MISE description="Analyze formex schema data from version or root element perspective across downloaded XML files"
#USAGE flag "-f --format <format>" help="Output format: table, json, jsonl" default="table"
#USAGE flag "-l --limit <limit>" help="Limit number of files to process (for testing)"
#USAGE flag "-p --perspective <perspective>" help="Analysis perspective: version (default), root_element" default="version"

# Parse all XML files and extract both the schema version and root element
# then analyze from different perspectives (version-first or root_element-first)

# Valid perspective options for validation
let valid_perspectives = ["version", "root_element"]

# Helper function for version perspective
def analyze-by-version [data: table] {
    $data 
    | group-by version
    | transpose version_name group_data
    | insert total_count {|row| $row.group_data | length}
    | insert root_elements {|row|
        $row.group_data 
        | get root_element
        | group-by
        | transpose root_element instances
        | insert count {|inner| $inner.instances | length}
        | select root_element count 
        | sort-by count --reverse
    }
    | select version_name total_count root_elements
    | sort-by total_count --reverse
}

# Helper function for root_element perspective
def analyze-by-root-element [data: table] {
    $data 
    | group-by root_element
    | transpose root_element_name group_data
    | insert total_count {|row| $row.group_data | length}
    | insert versions {|row|
        $row.group_data 
        | get version
        | group-by
        | transpose version instances
        | insert count {|inner| $inner.instances | length}
        | select version count 
        | sort-by count --reverse
    }
    | select root_element_name total_count versions
    | sort-by total_count --reverse
}

# Command: summarize schema version and root element usage across the downloaded dataset
def main [
    --format(-f): string = "table"
    --limit(-l): int
    --perspective(-p): string = "version"
] {

    # Extract data from XML files
    let base_data = (
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
    )

    # Apply perspective transformation using helper functions
    let results = match $perspective {
        "version" => (analyze-by-version $base_data),
        "root_element" => (analyze-by-root-element $base_data),
        _ => {
            let valid_opts = ($valid_perspectives | str join ", ")
            error make { 
                msg: $"Unknown perspective: ($perspective). Use: ($valid_opts)"
            }
        }
    }


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
