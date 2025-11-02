#!/usr/bin/env nu

# Formex Meta-Transformation Test Suite
# This script tests the meta-transform.xsl functionality

def main [] {
    print "🧪 Formex Meta-Transformation Test Suite"
    print "========================================"
    
    let test_dir = "transformations/tests/samples"
    let meta_xsl = "transformations/xslt/meta-transform.xsl"
    let output_dir = "transformations/tests/output"
    
    # Create output directory
    mkdir $output_dir
    
    # Test cases
    let tests = [
        {
            name: "Auto-detect v01.00 to latest"
            input: $"($test_dir)/v01.00/minimal-doc.xml"
            params: ""
            expected_target: "06.02.3"
        },
        {
            name: "Auto-detect v06.02.3 to same (no-op)"
            input: $"($test_dir)/v06.02.3/modern-doc.xml"
            params: ""
            expected_target: "06.02.3"
        },
        {
            name: "Explicit parameters v01.00 to v06.02"
            input: $"($test_dir)/v01.00/minimal-doc.xml"
            params: "element=doc target=06.02"
            expected_target: "06.02"
        },
        {
            name: "Verbose mode test"
            input: $"($test_dir)/v01.00/minimal-doc.xml"
            params: "target=06.02.3 verbose=true"
            expected_target: "06.02.3"
        }
    ]
    
    # Check if Saxon is available
    if (which saxon | is-empty) {
        print "❌ Saxon XSLT processor not found. Please install Saxon to run tests."
        print "   You can install Saxon with: brew install saxon"
        return
    }
    
    # Run tests
    for test in $tests {
        print $"\n🔄 Running test: ($test.name)"
        print $"   Input: ($test.input)"
        print $"   Params: ($test.params)"
        
        let output_file = $"($output_dir)/test-output-($test.name | str replace ' ' '-' | str replace '(' '' | str replace ')' '').xml"
        
        # Build Saxon command
        let saxon_cmd = if ($test.params | is-empty) {
            $"saxon -s:($test.input) -xsl:($meta_xsl) -o:($output_file)"
        } else {
            $"saxon -s:($test.input) -xsl:($meta_xsl) -o:($output_file) ($test.params)"
        }
        
        print $"   Command: ($saxon_cmd)"
        
        # Run transformation
        try {
            ^saxon -s $test.input -xsl $meta_xsl -o $output_file ...(if ($test.params | is-empty) { [] } else { $test.params | split row ' ' })
            
            if ($output_file | path exists) {
                print "   ✅ Transformation completed successfully"
                
                # Check if output file contains expected target version in schema location
                let output_content = open $output_file
                if ($output_content | str contains $test.expected_target) {
                    print $"   ✅ Output contains expected target version: ($test.expected_target)"
                } else {
                    print $"   ⚠️  Output may not contain expected target version: ($test.expected_target)"
                }
            } else {
                print "   ❌ Output file was not created"
            }
        } catch { |err|
            print $"   ❌ Transformation failed: ($err.msg)"
        }
    }
    
    print "\n📊 Test Summary"
    print "==============="
    print $"Test outputs saved to: ($output_dir)"
    print "Review the output files to verify transformations are working correctly."
    
    # Cleanup suggestions
    print "\n💡 Next steps:"
    print "1. Review output files for correctness"
    print "2. Verify schema location updates"
    print "3. Test with real-world Formex documents"
    print "4. Add more comprehensive test cases"
}

# Helper function to check Saxon installation
def check_saxon [] {
    try {
        let version = (^saxon -? | lines | first)
        print $"Saxon found: ($version)"
        true
    } catch {
        false
    }
}