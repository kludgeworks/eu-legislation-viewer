#!/bin/bash
# Script to query EU legal document expressions by CELEX number
# Usage: ./legislation-downloader.bash <CELEX_NUMBER>
# Example: ./legislation-downloader.bash 32015R1998
# Author: Tim-Sparg_afklm
# Date: 2025-03-19
# Updated to also download amendments and consolidations

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

# Get consolidations for the given amendment CELEX number
get_consolidations() {
    local amendment_celex="$1"

    local query="PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT ?consolidation ?consDate ?celexId ?eli
WHERE {
  # Get the amendment regulation by its CELEX number
  ?amendment cdm:resource_legal_id_celex \"${amendment_celex}\"^^xsd:string .

  # Find consolidated versions that incorporate this amendment
  ?consolidation cdm:act_consolidated_consolidates_resource_legal ?amendment ;
                cdm:act_consolidated_date ?consDate ;
                cdm:resource_legal_id_celex ?celexId .

  # Optional ELI identifier
  OPTIONAL { ?consolidation cdm:resource_legal_eli ?eli }
}
ORDER BY ?consDate"

    local consolidations=$(execute_sparql_query "${query}")
    echo "${consolidations}"
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

    # Extract indexes to iterate over
    local indexes=$(echo "${content_streams}" | jq 'keys | .[]')

    # Counter for download progress
    local counter=0

    # Iterate through indexes using jq style
    for i in ${indexes}; do
        # Increment counter for display
        counter=$((counter + 1))

        # Get item URI and identifier using index
        local item_uri=$(echo "${content_streams}" | jq -r ".[${i}].\"item.uri\"")
        local identifier_literal=$(echo "${content_streams}" | jq -r ".[${i}].\"identifier.literal\"")

        echo "Downloading (${counter}/${item_count}): ${identifier_literal}"
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

    # Process consolidations for an amendment
process_consolidations() {
    local amendment_celex="$1"
    local amendment_dir="$2"

    echo "Finding consolidations that incorporate amendment ${amendment_celex}..."
    local consolidations_data=$(get_consolidations "${amendment_celex}")

    # Check if consolidations were found
    if [ -z "${consolidations_data}" ] || [ "$(echo "${consolidations_data}" | jq '.results.bindings | length')" -eq 0 ]; then
        echo "No consolidations found that incorporate amendment ${amendment_celex}"
        return 0
    fi

    # Get number of consolidations
    local consolidation_count=$(echo "${consolidations_data}" | jq '.results.bindings | length')
    echo "Found ${consolidation_count} consolidations incorporating amendment ${amendment_celex}"

    # Create a summary file
    echo "Creating consolidations summary file..."
    echo "# Consolidations incorporating amendment ${amendment_celex}" > "${amendment_dir}/consolidations_summary.md"
    echo "Total consolidations found: ${consolidation_count}" >> "${amendment_dir}/consolidations_summary.md"
    echo "" >> "${amendment_dir}/consolidations_summary.md"
    echo "| # | CELEX | Date | ELI |" >> "${amendment_dir}/consolidations_summary.md"
    echo "| --- | --- | --- | --- |" >> "${amendment_dir}/consolidations_summary.md"

    # Extract indexes for consolidations
    local indexes=$(echo "${consolidations_data}" | jq '.results.bindings | keys | .[]')

    # Counter for consolidation progress
    local counter=0

    # Download each consolidation
    for i in ${indexes}; do
        # Increment counter for display
        counter=$((counter + 1))

        local consolidation_celex=$(echo "${consolidations_data}" | jq -r ".results.bindings[${i}].celexId.value")
        local consolidation_date=$(echo "${consolidations_data}" | jq -r ".results.bindings[${i}].consDate.value // \"(No date)\"")
        local consolidation_eli=$(echo "${consolidations_data}" | jq -r ".results.bindings[${i}].eli.value // \"(No ELI available)\"")

        echo ""
        echo "Processing consolidation ${counter}/${consolidation_count}: ${consolidation_celex}"
        echo "Date: ${consolidation_date}"
        echo "ELI: ${consolidation_eli}"

        # Add to summary
        echo "| ${counter} | ${consolidation_celex} | ${consolidation_date} | ${consolidation_eli} |" >> "${amendment_dir}/consolidations_summary.md"

        # Download this consolidation (create a subdirectory for each)
        download_legislation "${consolidation_celex}" "${amendment_dir}" "false"
    done

    echo "All consolidations processed successfully!"
    return 0
}

# Process amendments for a legislation
process_amendments() {
    local celex="$1"
    local base_dir="${2:-.}"
    local original_dir="${base_dir}/${celex}"

    echo "Finding amendments to ${celex}..."
    local amendments_data=$(get_amendments "${celex}")

    # Check if amendments were found
    if [ -z "${amendments_data}" ] || [ "$(echo "${amendments_data}" | jq '.results.bindings | length')" -eq 0 ]; then
        echo "No amendments found for CELEX ${celex}"
        return 0
    fi

    # Get number of amendments
    local amendment_count=$(echo "${amendments_data}" | jq '.results.bindings | length')
    echo "Found ${amendment_count} amendments to ${celex}"

    # Create a summary file
    echo "Creating amendments summary file..."
    echo "# Amendments to ${celex}" > "${original_dir}/amendments_summary.md"
    echo "Total amendments found: ${amendment_count}" >> "${original_dir}/amendments_summary.md"
    echo "" >> "${original_dir}/amendments_summary.md"
    echo "| # | CELEX | Date | Title | Consolidations |" >> "${original_dir}/amendments_summary.md"
    echo "| --- | --- | --- | --- | --- |" >> "${original_dir}/amendments_summary.md"

    # Extract indexes for amendments
    local indexes=$(echo "${amendments_data}" | jq '.results.bindings | keys | .[]')

    # Counter for amendment progress
    local counter=0

    # Process each amendment
    for i in ${indexes}; do
        # Increment counter for display
        counter=$((counter + 1))

        local amendment_celex=$(echo "${amendments_data}" | jq -r ".results.bindings[${i}].amendmentCelex.value")
        local amendment_title=$(echo "${amendments_data}" | jq -r ".results.bindings[${i}].title.value // \"(No title available)\"")
        local amendment_date=$(echo "${amendments_data}" | jq -r ".results.bindings[${i}].date.value // \"(No date)\"")

        echo ""
        echo "Processing amendment ${counter}/${amendment_count}: ${amendment_celex}"
        echo "Title: ${amendment_title}"
        echo "Date: ${amendment_date}"

        # 1. Get amendment files data first
        echo "Step 1: Finding English expression for amendment..."
        local eng_expression=$(get_english_expression "${amendment_celex}")
        echo "English expression URI: ${eng_expression}"

        echo "Step 2: Finding Formex manifestation for amendment..."
        local fmx4_manifestation=$(get_formex_manifestation "${eng_expression}")
        echo "Formex manifestation URI: ${fmx4_manifestation}"

        echo "Step 3: Finding content streams for amendment..."
        local content_streams=$(get_content_streams "${fmx4_manifestation}")

        # 2. Create proper directory structure
        local amendment_dir="${original_dir}/${amendment_celex}"
        mkdir -p "${amendment_dir}"

        # 3. Download content directly to amendment directory
        echo "Step 4: Downloading amendment content..."
        download_content "${amendment_celex}" "${content_streams}" "${amendment_dir}"

        # Process consolidations for this amendment
        echo "Looking for consolidations that incorporate this amendment..."
        process_consolidations "${amendment_celex}" "${amendment_dir}"

        # Count consolidations for summary
        local consolidations_count=0
        # Count consolidation directories directly in the amendment directory
        consolidations_count=$(find "${amendment_dir}" -maxdepth 1 -type d -name "0*" | wc -l)

        # Add to summary with consolidation count
        echo "| ${counter} | ${amendment_celex} | ${amendment_date} | ${amendment_title} | ${consolidations_count} |" >> "${original_dir}/amendments_summary.md"
    done

    echo "All amendments processed successfully!"
    return 0
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
    local base_dir="./legislation"

    echo "Processing CELEX: ${celex}"

    # Download the original legislation
    download_legislation "${celex}" "${base_dir}"

    # Process amendments with consolidations
    process_amendments "${celex}" "${base_dir}"

    echo ""
    echo "Process completed successfully!"
    echo "Original legislation: ${base_dir}/${celex}"
    echo "Amendments and consolidations: ${base_dir}/${celex}"
}

# Execute main function with all arguments
main "$@"