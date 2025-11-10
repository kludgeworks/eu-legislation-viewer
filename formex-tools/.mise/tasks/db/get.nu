#!/usr/bin/env nu
#MISE description="Retrieve XML file content from DuckDB by URI"
#USAGE arg "uri" help="URI of the XML file to retrieve"
#USAGE flag "-d --database <path>" help="Path to DuckDB database file" default="formex.db"

# Command: retrieve XML file content by URI
def main [
    uri: string
    --database(-d): string = "formex.db"
    --raw(-r)
] {
  
  # Build template context as JSON
  let context_json = ({
      uri: $uri
  } | to json)

  # Get the SQL template from the same directory as this script
  let template_file = ($env.MISE_TASK_DIR | path join "get.sql")

  # Render SQL template using jinjanate with JSON format
  let query = (
      $context_json 
      | jinjanate --quiet --format json $template_file -
  )

  print $query
  let xml_content = duckdb -noheader $database $query
  print --raw $xml_content
  # Execute query and get results using -list -noheader, then decode escape sequences
#   let xml_content = try {
#       duckdb -list -noheader $database $query | from xml
#   } catch {
#       error make {
#           msg: $"Database query failed: ($in)"
#       }
#   }

#   # Check if we got a result
#   if ($xml_content | is-empty) {
#       error make {
#           msg: $"No XML file found with URI: ($uri)"
#       }
#   }

#   # Output the content
#     print $xml_content
}
