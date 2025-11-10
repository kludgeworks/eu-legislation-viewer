#!/usr/bin/env bash
# Script to insert XML files into formex_documents table with metadata extraction
# Usage: ./insert_xml.sh [xml_file_or_pattern]

set -euo pipefail

DB_FILE="${DB_FILE:-formex.test.db}"
XML_PATTERN="${1:-}"

# If no pattern provided, insert a test document
if [ -z "$XML_PATTERN" ]; then
    echo "No XML file specified. Inserting test document..."
    
    # Create temporary test XML file with unique root element
    # Clean up any existing temp files first
    rm -f /tmp/test_formex.*.xml
    TEST_XML=$(mktemp /tmp/test_formex.XXXXXX.xml)
    cat > "$TEST_XML" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<TEST_UNIQUE_ROOT_ELEMENT_12345 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
     xsi:noNamespaceSchemaLocation="http://formex.publications.europa.eu/schema/formex-05.59-20170418.xd">
  <BIB.DOC>
    <PROD.ID>TEST999999999</PROD.ID>
    <FIN.ID>999999</FIN.ID>
    <AUTHOR>TEST_INSERT_SCRIPT</AUTHOR>
  </BIB.DOC>
  <METADATA>
    <TEST_MARKER>This is a test document inserted by the script</TEST_MARKER>
  </METADATA>
</TEST_UNIQUE_ROOT_ELEMENT_12345>
EOF
    
    XML_PATTERN="$TEST_XML"
    echo "Using test XML: $TEST_XML"
fi

# Check if database exists
if [ ! -f "$DB_FILE" ]; then
    echo "Error: Database $DB_FILE not found"
    exit 1
fi

echo "Inserting XML files from: $XML_PATTERN"
echo "Into database: $DB_FILE"
echo ""

# Perform the insert
duckdb "$DB_FILE" << EOSQL
LOAD webbed;

INSERT INTO formex_documents 
SELECT 
    gen_random_uuid() as id,
    xml as xml_content,
    regexp_extract(
        list_filter(
            xml_extract_attributes(xml, '/*'),
            x -> x.attribute_name = 'noNamespaceSchemaLocation'
        )[1].attribute_value,
        '([^/]+\\.xd)\$',
        1
    ) as version,
    xml_extract_attributes(xml, '/*')[1].element_name as root_element,
    length(xml::VARCHAR) as file_size,
    regexp_extract('$XML_PATTERN', '([^/]+)\$', 1) as uri,
    NULL as work_id,
    NULL as expr_id,
    NULL as man_id,
    NULL as cs_id
FROM read_xml_objects('$XML_PATTERN');

-- Show what was inserted
SELECT 
    COUNT(*) as inserted_count,
    root_element,
    version
FROM formex_documents
WHERE root_element IN (
    SELECT xml_extract_attributes(xml, '/*')[1].element_name 
    FROM read_xml_objects('$XML_PATTERN')
)
GROUP BY root_element, version;
EOSQL

echo ""
echo "Insert complete!"
echo ""
echo "To verify the test document, run:"
echo "duckdb formex.test.db \"LOAD webbed; SELECT id, root_element, version, uri FROM formex_documents WHERE root_element = 'TEST_UNIQUE_ROOT_ELEMENT_12345';\""

# Clean up temp file if we created one
if [[ "$XML_PATTERN" == /tmp/*.xml ]]; then
    rm -f "$XML_PATTERN"
fi