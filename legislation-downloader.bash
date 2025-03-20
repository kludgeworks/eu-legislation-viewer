#!/bin/bash
set -e

# Script to query EU legal document expressions by CELEX number
# Usage: ./legislation-downloader.bash <CELEX_NUMBER>
# Example: ./legislation-downloader.bash 32015R1998
# Author: Tim-Sparg_afklm
# Date: 2025-03-20
# Updated to also download amendments and consolidations
# Updated: Added support for both xhtml and fmx4 manifestations

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
    for cmd in http jq xmllint; do
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

    local expressions
    expressions=$(execute_sparql_query "${query}")

    # Extract the expression value where language value ends with ENG
    local eng_expression
    eng_expression=$(echo "${expressions}" | jq -r '.results.bindings[] | select(.language.value | endswith("/ENG")) | .expression.value')

    if [ -z "${eng_expression}" ]; then
        echo "INFO: Could not find English expression for CELEX ${celex}" >&2
    fi

    echo "${eng_expression}"
}

# Get both fmx4 and xhtml manifestations for the given expression
get_manifestations() {
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

    local manifestations_data
    manifestations_data=$(execute_sparql_query "${query}")

    # Save manifestations data for reference
    echo "${manifestations_data}" > manifestations.json

    # Extract both fmx4 and xhtml manifestations
    local manifestations_json
    manifestations_json=$(echo "${manifestations_data}" | jq '[.results.bindings[] | select(.type.value == "fmx4" or .type.value == "xhtml") | {uri: .manifestation.value, type: .type.value}]')

    # Check if we found any manifestations
    local count
    count=$(echo "${manifestations_json}" | jq 'length')
    if [ "${count}" -eq 0 ]; then
        echo "Error: Could not find fmx4 or xhtml manifestations for expression ${expression_uri}" >&2
        exit 4
    fi

    echo "${manifestations_json}"
}

# Get content streams for the given manifestation
get_content_streams() {
    local manifestation_uri="$1"
    local manifestation_type="$2"

    local query="PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>

SELECT DISTINCT ?item ?identifier ?property ?value
WHERE {
  # Find all items of the specified manifestation
  ?item cdm:item_belongs_to_manifestation <${manifestation_uri}> ;
        ?property ?value .

  # Get the item's identifier if available
  OPTIONAL { ?item cdm:item_identifier ?identifier }
}"

    local content_streams_data
    content_streams_data=$(execute_sparql_query "${query}")

    # Process and filter the content streams
    local content_streams
    content_streams=$(echo "${content_streams_data}" | jq '[.results.bindings[] | select(.property.value == "http://publications.europa.eu/ontology/cdm/cmr#manifestationMimeType")
| {
      "item.uri": .item.value,
      "identifier.literal": .identifier.value,
      "manifestation.type": "'"${manifestation_type}"'"
    }]')

    local count
    count=$(echo "${content_streams}" | jq 'length')
    if [ "${count}" -eq 0 ]; then
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

    local amendments
    amendments=$(execute_sparql_query "${query}")
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

    local consolidations
    consolidations=$(execute_sparql_query "${query}")
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
    local item_count
    item_count=$(echo "${content_streams}" | jq 'length')
    echo "Found ${item_count} items to download."

    # Extract indexes to iterate over
    local indexes
    indexes=$(echo "${content_streams}" | jq 'keys | .[]')

    # Counter for download progress
    local counter=0

    # Iterate through indexes using jq
    for i in ${indexes}; do
        # Increment counter for display
        counter=$((counter + 1))

        # Get item URI, identifier, and manifestation type using index
        local item_uri
        local identifier_literal
        local manifestation_type

        item_uri=$(echo "${content_streams}" | jq -r ".[${i}].\"item.uri\"")
        identifier_literal=$(echo "${content_streams}" | jq -r ".[${i}].\"identifier.literal\"")
        manifestation_type=$(echo "${content_streams}" | jq -r ".[${i}].\"manifestation.type\"")

        echo "Downloading (${counter}/${item_count}): ${identifier_literal} (${manifestation_type})"
        local tmp_file="${output_dir}/${identifier_literal}.tmp"
        http --stream --follow --download "${item_uri}" > "${tmp_file}"

        if [ $? -ne 0 ]; then
            echo "Warning: Failed to download ${item_uri}" >&2
            rm -f "${tmp_file}"
            continue
        fi

        # Check if file is a ZIP archive by examining the first 4 bytes (ZIP signature is PK\x03\x04)
        local first_bytes
        first_bytes=$(hexdump -n 4 -e '4/1 "%02x"' "${tmp_file}")

        if [[ "${first_bytes}" == "504b0304" ]]; then
            # Handle ZIP file
            process_zip_file "${tmp_file}" "${output_dir}" "${identifier_literal}" "${manifestation_type}"
        else
            # Handle regular file
            process_regular_file "${tmp_file}" "${output_dir}" "${identifier_literal}" "${manifestation_type}"
        fi
    done
}

# Process a ZIP file based on manifestation type
process_zip_file() {
    local zip_file="$1"
    local output_dir="$2"
    local identifier="$3"
    local manifestation_type="$4"

    echo "Detected ZIP file, extracting content..."
    local extract_dir="${output_dir}/${identifier}_extracted"
    mkdir -p "${extract_dir}"

    if ! unzip -q "${zip_file}" -d "${extract_dir}"; then
        echo "Warning: Failed to extract ZIP file" >&2
        mv "${zip_file}" "${output_dir}/${identifier}.zip"
        echo "Saved original ZIP as: ${output_dir}/${identifier}.zip"
        return
    fi

    # Remove the temporary file after successful extraction
    rm -f "${zip_file}"

    # Process extracted files based on manifestation type
    if [ "${manifestation_type}" == "fmx4" ]; then
        # Find XML files in the extracted directory
        find "${extract_dir}" -name "*.xml" -type f | while read -r xml_file; do
            local xml_basename
            xml_basename=$(basename "${xml_file}")
            echo "Processing XML file: ${xml_basename}"
            cat "${xml_file}" | xmllint --format - > "${output_dir}/${identifier}_${xml_basename}"
            echo "Saved formatted XML: ${output_dir}/${identifier}_${xml_basename}"
        done
    elif [ "${manifestation_type}" == "xhtml" ]; then
        # Find HTML files in the extracted directory
        find "${extract_dir}" -name "*.html" -o -name "*.htm" -o -name "*.xhtml" -type f | while read -r html_file; do
            local html_basename
            html_basename=$(basename "${html_file}")
            echo "Found HTML file: ${html_basename}"
            cp "${html_file}" "${output_dir}/${identifier}_${html_basename}"
            echo "Saved HTML: ${output_dir}/${identifier}_${html_basename}"
        done
    fi

    echo "Extracted files available in: ${extract_dir}"
}

# Process a regular (non-ZIP) file based on manifestation type
process_regular_file() {
    local tmp_file="$1"
    local output_dir="$2"
    local identifier="$3"
    local manifestation_type="$4"

    if [ "${manifestation_type}" == "fmx4" ]; then
        cat "${tmp_file}" | xmllint --format - > "${output_dir}/${identifier}.xml"
        rm -f "${tmp_file}"
        echo "Successfully saved: ${output_dir}/${identifier}.xml"
    elif [ "${manifestation_type}" == "xhtml" ]; then
        # For xhtml, just save as html without processing
        mv "${tmp_file}" "${output_dir}/${identifier}.html"
        echo "Successfully saved: ${output_dir}/${identifier}.html"
    else
        # For unknown types, just save as txt
        mv "${tmp_file}" "${output_dir}/${identifier}.txt"
        echo "Successfully saved: ${output_dir}/${identifier}.txt (unknown format)"
    fi
}

# Download legislation by CELEX number
download_legislation() {
    local celex="$1"
    local base_dir="${2:-.}"
    local output_dir="${base_dir}/${celex}"

    echo "Downloading legislation: ${celex} to ${output_dir}"

    # Step 1: Get English expression
    echo "Step 1: Finding English expression..."
    local eng_expression
    eng_expression=$(get_english_expression "${celex}")
    if [ -n "${eng_expression}" ]; then
      echo "English expression URI: ${eng_expression}"

      # Step 2: Get manifestations (both fmx4 and xhtml if available)
      echo "Step 2: Finding manifestations (fmx4 and xhtml)..."
      local manifestations
      manifestations=$(get_manifestations "${eng_expression}")

      echo "Found manifestations:"
      echo "${manifestations}" | jq -r '.[] | "- Type: \(.type), URI: \(.uri)"'

      # Step 3: Process each manifestation type
      local all_content_streams="[]"

      local manifest_indexes
      manifest_indexes=$(echo "${manifestations}" | jq 'keys | .[]')
      for i in ${manifest_indexes}; do
          local manifestation_uri
          local manifestation_type

          manifestation_uri=$(echo "${manifestations}" | jq -r ".[${i}].uri")
          manifestation_type=$(echo "${manifestations}" | jq -r ".[${i}].type")

          echo "Step 3: Finding content streams for ${manifestation_type} manifestation..."
          local this_content_streams
          this_content_streams=$(get_content_streams "${manifestation_uri}" "${manifestation_type}")

          # Merge the content streams
          all_content_streams=$(echo "${all_content_streams}" "${this_content_streams}" | jq -s '.[0] + .[1]')
      done

      # Step 4: Download content
      echo "Step 4: Downloading content from all manifestations..."
      download_content "${celex}" "${all_content_streams}" "${output_dir}"

      echo "Legislation ${celex} downloaded successfully!"
    fi
}

# Process consolidations for an amendment
process_consolidations() {
    local amendment_celex="$1"
    local amendment_dir="$2"

    echo "Finding consolidations that incorporate amendment ${amendment_celex}..."
    local consolidations_data
    consolidations_data=$(get_consolidations "${amendment_celex}")

    # Check if consolidations were found
    local consolidation_count=0
    if [ -n "${consolidations_data}" ]; then
        consolidation_count=$(echo "${consolidations_data}" | jq '.results.bindings | length')
    fi

    if [ -z "${consolidations_data}" ] || [ "${consolidation_count}" -eq 0 ]; then
        echo "No consolidations found that incorporate amendment ${amendment_celex}"
        return 0
    fi

    echo "Found ${consolidation_count} consolidations incorporating amendment ${amendment_celex}"

    # Create a summary file
    {
        echo "# Consolidations incorporating amendment ${amendment_celex}"
        echo "Total consolidations found: ${consolidation_count}"
        echo ""
        echo "| # | CELEX | Date | ELI |"
        echo "| --- | --- | --- | --- |"
    } > "${amendment_dir}/consolidations_summary.md"


    # Extract indexes for consolidations
    local indexes
    indexes=$(echo "${consolidations_data}" | jq '.results.bindings | keys | .[]')

    # Counter for consolidation progress
    local counter=0

    # Download each consolidation
    for i in ${indexes}; do
        # Increment counter for display
        counter=$((counter + 1))

        local consolidation_celex
        local consolidation_date
        local consolidation_eli

        consolidation_celex=$(echo "${consolidations_data}" | jq -r ".results.bindings[${i}].celexId.value")
        consolidation_date=$(echo "${consolidations_data}" | jq -r ".results.bindings[${i}].consDate.value // \"(No date)\"")
        consolidation_eli=$(echo "${consolidations_data}" | jq -r ".results.bindings[${i}].eli.value // \"(No ELI available)\"")

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
    local amendments_data
    amendments_data=$(get_amendments "${celex}")

    # Check if amendments were found
    local amendment_count=0
    if [ -n "${amendments_data}" ]; then
        amendment_count=$(echo "${amendments_data}" | jq '.results.bindings | length')
    fi

    if [ -z "${amendments_data}" ] || [ "${amendment_count}" -eq 0 ]; then
        echo "No amendments found for CELEX ${celex}"
        return 0
    fi

    echo "Found ${amendment_count} amendments to ${celex}"

    # Create a summary file
    {
        echo "# Amendments to ${celex}"
        echo "Total amendments found: ${amendment_count}"
        echo ""
        echo "| # | CELEX | Date | Title | Consolidations |"
        echo "| --- | --- | --- | --- | --- |"
    } > "${original_dir}/amendments_summary.md"

    # Extract indexes for amendments
    local indexes
    indexes=$(echo "${amendments_data}" | jq '.results.bindings | keys | .[]')

    # Counter for amendment progress
    local counter=0

    # Process each amendment
    for i in ${indexes}; do
        # Increment counter for display
        counter=$((counter + 1))

        local amendment_celex
        local amendment_title
        local amendment_date

        amendment_celex=$(echo "${amendments_data}" | jq -r ".results.bindings[${i}].amendmentCelex.value")
        amendment_title=$(echo "${amendments_data}" | jq -r ".results.bindings[${i}].title.value // \"(No title available)\"")
        amendment_date=$(echo "${amendments_data}" | jq -r ".results.bindings[${i}].date.value // \"(No date)\"")

        echo ""
        echo "Processing amendment ${counter}/${amendment_count}: ${amendment_celex}"
        echo "Title: ${amendment_title}"
        echo "Date: ${amendment_date}"

        echo "Step 1: Finding English expression for amendment..."
        local eng_expression
        eng_expression=$(get_english_expression "${amendment_celex}")
        if [ -n "${eng_expression}" ]; then
          echo "English expression URI: ${eng_expression}"

          echo "Step 2: Finding manifestations (fmx4 and xhtml) for amendment..."
          local manifestations
          manifestations=$(get_manifestations "${eng_expression}")

          echo "Found manifestations for amendment:"
          echo "${manifestations}" | jq -r '.[] | "- Type: \(.type), URI: \(.uri)"'

          # 3. Process each manifestation type and collect content streams
          local all_content_streams="[]"

          local manifest_indexes
          manifest_indexes=$(echo "${manifestations}" | jq 'keys | .[]')
          for j in ${manifest_indexes}; do
              local manifestation_uri
              local manifestation_type

              manifestation_uri=$(echo "${manifestations}" | jq -r ".[${j}].uri")
              manifestation_type=$(echo "${manifestations}" | jq -r ".[${j}].type")

              echo "Step 3: Finding content streams for ${manifestation_type} manifestation..."
              local this_content_streams
              this_content_streams=$(get_content_streams "${manifestation_uri}" "${manifestation_type}")

              # Merge the content streams
              all_content_streams=$(echo "${all_content_streams}" "${this_content_streams}" | jq -s '.[0] + .[1]')
          done

          local amendment_dir="${original_dir}/${amendment_celex}"
          mkdir -p "${amendment_dir}"

          echo "Step 4: Downloading amendment content..."
          download_content "${amendment_celex}" "${all_content_streams}" "${amendment_dir}"

          # Process consolidations for this amendment
          echo "Looking for consolidations that incorporate this amendment..."
          process_consolidations "${amendment_celex}" "${amendment_dir}"

          # Count consolidations for summary
          local consolidations_count=0
          # Count consolidation directories directly in the amendment directory
          consolidations_count=$(find "${amendment_dir}" -maxdepth 1 -type d -name "0*" | wc -l)

          # Add to summary with consolidation count
          echo "| ${counter} | ${amendment_celex} | ${amendment_date} | ${amendment_title} | ${consolidations_count} |" >> "${original_dir}/amendments_summary.md"
        fi
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