#!/bin/bash
# Script to query EU legal document expressions by CELEX number
# Usage: ./legislation-downloader.bash <CELEX_NUMBER>
# Example: ./legislation-downloader.bash 32015R1998
# Author: Tim-Sparg_afklm
# Date: 2025-03-19
# Updated to also download amendments

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

# Get amendments for the given CELEX number
get_amendments() {
    local celex="$1"
    
    local query="PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT DISTINCT ?amendment ?amendmentCelex ?title ?date
WHERE {
  # Retrieve the original regulation by its CELEX number
  ?regulation cdm:resource_legal_id_celex \"${celex}\"^^xsd:string .

  # Find all amendments to this regulation
  ?amendment cdm:resource_legal_amends_resource_legal ?regulation .
  
  # Get CELEX number of the amendment
  ?amendment cdm:resource_legal_id_celex ?amendmentCelex .
  
  # Get optional title and date metadata
  OPTIONAL { 
    ?expression cdm:expression_belongs_to_work ?amendment ;
                cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/ENG> ;
                cdm:expression_title ?title .
  }
  OPTIONAL { ?amendment cdm:work_date_document ?date }
}
ORDER BY ?date"

    local amendments=$(execute_sparql_query "${query}")
    echo "${amendments}"
}

# Download content for each item
download_content() {
    local celex="$1"
    local content_streams="$2"
    local output_dir="$3"
    
    # Create output directory if it doesn't exist
    mkdir -p "${output_dir}"
    
    echo "Downloading content to ${output_dir}/ directory..."
    
    # Get total number of items to download
    local item_count=$(echo "${content_streams}" | jq 'length')
    echo "Found ${item_count} items to download."
    
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

# Download legislation by CELEX number
download_legislation() {
    local celex="$1"
    local base_dir="${2:-.}"
    local output_dir="${base_dir}/${celex}"
    
    echo "Downloading legislation: ${celex} to ${output_dir}"
    
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
    download_content "${celex}" "${content_streams}" "${output_dir}"
    
    echo "Legislation ${celex} downloaded successfully!"
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
    local base_dir="."
    
    echo "Processing CELEX: ${celex}"
    
    # Download the original legislation
    download_legislation "${celex}" "${base_dir}"
    
    # Create amendments directory
    local amendments_dir="${base_dir}/${celex}_amendments"
    mkdir -p "${amendments_dir}"
    
    # Find amendments
    echo "Finding amendments to ${celex}..."
    local amendments_data=$(get_amendments "${celex}")
    
    # Check if amendments were found
    if [ -z "${amendments_data}" ] || [ "$(echo "${amendments_data}" | jq '.results.bindings | length')" -eq 0 ]; then
        echo "No amendments found for CELEX ${celex}"
    else
        # Get number of amendments
        local amendment_count=$(echo "${amendments_data}" | jq '.results.bindings | length')
        echo "Found ${amendment_count} amendments to ${celex}"
        
        # Create a summary file
        echo "Creating amendments summary file..."
        echo "# Amendments to ${celex}" > "${amendments_dir}/amendments_summary.md"
        echo "Total amendments found: ${amendment_count}" >> "${amendments_dir}/amendments_summary.md"
        echo "" >> "${amendments_dir}/amendments_summary.md"
        echo "| # | CELEX | Date | Title |" >> "${amendments_dir}/amendments_summary.md"
        echo "| --- | --- | --- | --- |" >> "${amendments_dir}/amendments_summary.md"
        
        # Download each amendment
        for ((i=0; i<amendment_count; i++)); do
            local amendment_celex=$(echo "${amendments_data}" | jq -r ".results.bindings[${i}].amendmentCelex.value")
            local amendment_title=$(echo "${amendments_data}" | jq -r ".results.bindings[${i}].title.value // \"(No title available)\"")
            local amendment_date=$(echo "${amendments_data}" | jq -r ".results.bindings[${i}].date.value // \"(No date)\"")
            
            echo ""
            echo "Processing amendment ${i+1}/${amendment_count}: ${amendment_celex}"
            echo "Title: ${amendment_title}"
            echo "Date: ${amendment_date}"
            
            # Add to summary
            echo "| ${i+1} | ${amendment_celex} | ${amendment_date} | ${amendment_title} |" >> "${amendments_dir}/amendments_summary.md"
            
            # Download this amendment
            download_legislation "${amendment_celex}" "${amendments_dir}"
        done
    fi
    
    echo ""
    echo "Process completed successfully!"
    echo "Original legislation: ${base_dir}/${celex}"
    echo "Amendments: ${amendments_dir}"
}

# Execute main function with all arguments
main "$@"