#!/usr/bin/env nu
#MISE description="Query DuckDB for XML files that reference a specific Formex schema version and optionally filter by root element"
#USAGE flag "-l --limit <count>" help="Maximum number of matching files to return"
#USAGE flag "-w --with-version" help="Include the version field in the output"
#USAGE flag "-v --version <versions>" help="Filter by Formex schema version (comma-separated for multiple)"
#USAGE flag "-r --root-element <elements>" help="Filter by XML root element tag name (comma-separated for multiple)"
#USAGE flag "-f --format <format>" help="Output format" default="table" {
#USAGE   choices "table" "json" "csv" "tsv" "jsonl"
#USAGE }
#USAGE flag "-d --database <path>" help="Path to DuckDB database file" default="formex.db"

# Command: query database for files referencing the requested schema version(s)
def main [
    --limit(-l): int
    --with-version(-w)
    --version(-v): string
    --root-element(-r): string
    --format(-f): string = "table"
    --database(-d): string = "formex.db"
] {

  # Parse comma-separated values into lists
  let versions = if ($version | is-empty) { 
      [] 
  } else { 
      $version | split row ',' | each { str trim }
  }
  
  let root_elements = if ($root_element | is-empty) { 
      null 
  } else { 
      $root_element | split row ',' | each { str trim }
  }

  # Build template context as JSON
  # Note: Nushell automatically converts unset flags to false and unset optional ints to null
  let context_json = ({
      versions: $versions,
      root_elements: $root_elements,
      with_version: $with_version,
      limit: $limit
  } | to json)

  # Get the SQL template from the same directory as this script
  let template_file = ($env.MISE_TASK_DIR | path join "search.sql")

  # Render SQL template using jinjanate with JSON format
  let query = (
      $context_json 
      | jinjanate --format json $template_file -
  )

  # Execute query and get results
  let results = try {
      duckdb -csv $database $query
      | from csv
  } catch {
      error make {
          msg: $"Database query failed: ($in)"
      }
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
