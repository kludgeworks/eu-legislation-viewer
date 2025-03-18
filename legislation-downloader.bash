#!/bin/bash
# Script to query EU legal document expressions by CELEX number
# Usage: ./legislation-downloader.bash <CELEX_NUMBER>
# Example: ./legislation-downloader.bash 32015R1998
# Author: Tim-Sparg_afklm
# Date: 2025-03-18

# Constants
ENDPOINT="https://publications.europa.eu/webapi/rdf/sparql"

# Display usage information
usage() {
    echo "Usage: $0 <CELEX_NUMBER>"
    echo "Example: $0 32015R1998"
    exit 1
}

# Check if required dependencies are installed
check_dependencies() {
    for cmd in http jq xq; do
        if ! command -v $cmd &> /dev/null; then
            echo "Error: Required command '$cmd' not found"
            echo "Please install the required dependencies"
            exit 2
        fi
    done
}

# Execute SPARQL query and return JSON response
execute_sparql_query() {
    local query="$1"

    http --form POST "${ENDPOINT}" \
        Content-Type:"application/x-www-form-urlencoded" \
        Accept:"application/sparql-results+json" \
        query="${query}"
}

# Get the English expression URI for the given CELEX number
get_english_expression() {
    local celex="$1"

    local query="PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT DISTINCT ?expression ?language
WHERE {
  # Retrieve the work by its CELEX number
  ?work cdm:resource_legal_id_celex \"${celex}\"^^xsd:string .

  # Find all expressions that belong to this work
  ?expression cdm:expression_belongs_to_work ?work ;
              cdm:expression_uses_language ?language .
}"

    local expressions=$(execute_sparql_query "${query}")

    # Extract the expression value where language value ends with ENG
    local eng_expression=$(echo "${expressions}" | jq -r '.results.bindings[] | select(.language.value | endswith("/ENG")) | .expression.value')

    if [ -z "${eng_expression}" ]; then
        echo "Error: Could not find English expression for CELEX ${celex}" >&2
        exit 3
    fi

    echo "${eng_expression}"
}

# Get the Formex manifestation URI for the given expression
get_formex_manifestation() {
    local expression_uri="$1"

    local query="PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>

SELECT DISTINCT ?manifestation ?type ?mimeType
WHERE {
  # Find all manifestations of the specified expression
  ?manifestation cdm:manifestation_manifests_expression <${expression_uri}> .

  # Optional properties to know more about the manifestation
  OPTIONAL { ?manifestation cdm:manifestation_type ?type }
  OPTIONAL { ?manifestation cdm:manifestation_mime-type ?mimeType }
}"

    local manifestations_data=$(execute_sparql_query "${query}")

    # Save manifestations data for reference
    echo "${manifestations_data}" > manifestations.json

    # Extract the manifestation with type 'fmx4'
    local fmx4_manifestation=$(echo "${manifestations_data}" | jq -r '.results.bindings[] | select(.type.value == "fmx4") | .manifestation.value')

    if [ -z "${fmx4_manifestation}" ]; then
        echo "Error: Could not find Formex manifestation for expression ${expression_uri}" >&2
        exit 4
    fi

    echo "${fmx4_manifestation}"
}

# Get content streams for the given manifestation
get_content_streams() {
    local manifestation_uri="$1"

    local query="PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>

SELECT DISTINCT ?item ?identifier ?property ?value
WHERE {
  # Find all items of the specified manifestation
  ?item cdm:item_belongs_to_manifestation <${manifestation_uri}> ;
        ?property ?value .

  # Get the item's identifier if available
  OPTIONAL { ?item cdm:item_identifier ?identifier }
}"

    local content_streams_data=$(execute_sparql_query "${query}")

    # Process and filter the content streams
    local content_streams=$(echo "${content_streams_data}" | jq '[.results.bindings[] | select(.property.value == "http://publications.europa.eu/ontology/cdm/cmr#manifestationMimeType")
| {
      "item.uri": .item.value,
      "identifier.literal": .identifier.value
    }]')

    if [ "$(echo "${content_streams}" | jq 'length')" -eq 0 ]; then
        echo "Warning: No content streams found for manifestation ${manifestation_uri}" >&2
    fi

    echo "${content_streams}"
}

# Download content for each item
download_content() {
    local celex="$1"
    local content_streams="$2"
    local output_dir="./legislation/${celex}"

    # Get total number of items to download
    local item_count=$(echo "${content_streams}" | jq 'length')
    echo "Found ${item_count} items to download."

    # Exit early if there are no items to download
    if [ "${item_count}" -eq 0 ]; then
        echo "No items to download. Skipping download phase."
        return 0
    fi

    # Create output directory if it doesn't exist
    mkdir -p "${output_dir}"

    echo "Downloading content to ${output_dir}/ directory..."

    # Iterate through array indexes instead of using pipe and read
    for ((i=0; i<item_count; i++)); do
        # Get item URI and identifier using direct array access
        local item_uri=$(echo "${content_streams}" | jq -r ".[${i}].\"item.uri\"")
        local identifier_literal=$(echo "${content_streams}" | jq -r ".[${i}].\"identifier.literal\"")

        echo "Downloading (${i+1}/${item_count}): ${identifier_literal}"
        http --follow "${item_uri}" > "${output_dir}/${identifier_literal}.xml.tmp"

        if [ $? -ne 0 ]; then
            echo "Warning: Failed to download ${item_uri}" >&2
            # Remove temp file if download failed
            rm -f "${output_dir}/${identifier_literal}.xml.tmp"
        else
            # Process with xq after successful download
            cat "${output_dir}/${identifier_literal}.xml.tmp" | xq > "${output_dir}/${identifier_literal}.xml"
            rm -f "${output_dir}/${identifier_literal}.xml.tmp"
            echo "Successfully saved: ${output_dir}/${identifier_literal}.xml"
        fi
    done
}

# Main function
main() {
    # Check if CELEX number is provided
    if [ -z "$1" ]; then
        echo "Error: CELEX number is required" >&2
        usage
    fi

    # Validate dependencies
    check_dependencies

    local celex="$1"

    echo "Processing CELEX: ${celex}"

    # Step 1: Get English expression
    echo "Step 1: Finding English expression..."
    local eng_expression=$(get_english_expression "${celex}")
    echo "English expression URI: ${eng_expression}"

    # Step 2: Get Formex manifestation
    echo "Step 2: Finding Formex manifestation..."
    local fmx4_manifestation=$(get_formex_manifestation "${eng_expression}")
    echo "Formex manifestation URI: ${fmx4_manifestation}"

    # Step 3: Get content streams
    echo "Step 3: Finding content streams..."
    local content_streams=$(get_content_streams "${fmx4_manifestation}")

    # Step 4: Download content
    echo "Step 4: Downloading content..."
    download_content "${celex}" "${content_streams}"

    echo "Process completed successfully!"
}

# Execute main function with all arguments
main "$@"