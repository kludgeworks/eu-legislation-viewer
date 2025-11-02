#!/bin/bash

# Formex Meta-Transformation Test Suite (Bash version)
# This script tests the meta-transform.xsl functionality

set -e

echo "🧪 Formex Meta-Transformation Test Suite"
echo "========================================"

TEST_DIR="transformations/tests/samples"
META_XSL="transformations/xslt/meta-transform.xsl"
OUTPUT_DIR="transformations/tests/output"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Check if Saxon is available
if ! command -v saxon &> /dev/null; then
    echo "❌ Saxon XSLT processor not found."
    echo "   Please install Saxon. On macOS: brew install saxon"
    echo "   Or download from: https://www.saxonica.com/download/download_page.xml"
    exit 1
fi

echo "✅ Saxon found: $(saxon -? 2>&1 | head -n 1)"

# Test function
run_test() {
    local test_name="$1"
    local input_file="$2"
    local params="$3"
    local expected_target="$4"
    
    echo ""
    echo "🔄 Running test: $test_name"
    echo "   Input: $input_file"
    echo "   Params: $params"
    
    local output_file="$OUTPUT_DIR/test-output-$(echo "$test_name" | tr ' ' '-' | tr -d '()').xml"
    
    # Build Saxon command
    local saxon_cmd="saxon -s:$input_file -xsl:$META_XSL -o:$output_file"
    if [ -n "$params" ]; then
        saxon_cmd="$saxon_cmd $params"
    fi
    
    echo "   Command: $saxon_cmd"
    
    # Run transformation
    if eval "$saxon_cmd" 2>&1; then
        if [ -f "$output_file" ]; then
            echo "   ✅ Transformation completed successfully"
            
            # Check if output contains expected target version
            if grep -q "$expected_target" "$output_file"; then
                echo "   ✅ Output contains expected target version: $expected_target"
            else
                echo "   ⚠️  Output may not contain expected target version: $expected_target"
            fi
        else
            echo "   ❌ Output file was not created"
        fi
    else
        echo "   ❌ Transformation failed"
    fi
}

# Run test cases
echo ""
echo "Running test cases..."

run_test "Auto-detect v01.00 to latest" \
         "$TEST_DIR/v01.00/minimal-doc.xml" \
         "" \
         "06.02.3"

run_test "Auto-detect v06.02.3 to same" \
         "$TEST_DIR/v06.02.3/modern-doc.xml" \
         "" \
         "06.02.3"

run_test "Explicit parameters v01.00 to v06.02" \
         "$TEST_DIR/v01.00/minimal-doc.xml" \
         "element=doc target=06.02" \
         "06.02"

run_test "Verbose mode test" \
         "$TEST_DIR/v01.00/minimal-doc.xml" \
         "target=06.02.3 verbose=true" \
         "06.02.3"

echo ""
echo "📊 Test Summary"
echo "==============="
echo "Test outputs saved to: $OUTPUT_DIR"
echo "Review the output files to verify transformations are working correctly."

echo ""
echo "💡 Next steps:"
echo "1. Review output files for correctness"
echo "2. Verify schema location updates"
echo "3. Test with real-world Formex documents"
echo "4. Add more comprehensive test cases"