#!/usr/bin/env nu

# Master automation script for Formex element transformations
# This script orchestrates the complete transformation generation process for any root element
# Supports: DOC, GENERAL, ACT, ANNEX, and other Formex root elements

def main [
    element_type: string     # Root element to generate transformations for (e.g., DOC, GENERAL, ACT)
    --regenerate(-r)         # Regenerate all files from scratch
    --test(-t)               # Run tests after generation
    --verbose(-v)            # Verbose output
] {
    # Validate element type
    if ($element_type | str length) == 0 {
        print "❌ Error: element_type is required"
        print "Usage: automation.nu <element_type> [--regenerate] [--test] [--verbose]"
        print "Example: automation.nu DOC --regenerate --test"
        print "Example: automation.nu GENERAL --test"
        exit 1
    }
    
    let elem_upper = ($element_type | str upcase)
    
    print "🚀 Formex ($elem_upper) Element Transformation Automation"
    print "=========================================================="
    
    if $regenerate {
        print "⚠️  Regeneration mode: All files will be recreated"
        clean_generated_files $elem_upper
    }
    
    # Step 1: Analyze diffs
    print "\n📊 Step 1: Analyzing ($elem_upper) changes from diff files..."
    run_diff_analysis $elem_upper $verbose
    
    # Step 2: Generate XSLT transformations
    print "\n🔧 Step 2: Generating XSLT transformations..."
    run_xslt_generation $elem_upper $verbose
    
    # Step 3: Generate XSpec tests
    print "\n🧪 Step 3: Generating XSpec test files..."
    run_xspec_generation $elem_upper $verbose
    
    # Step 4: Generate documentation
    print "\n📚 Step 4: Updating documentation..."
    update_documentation $elem_upper $verbose
    
    # Step 5: Run tests (if requested)
    if $test {
        print "\n✅ Step 5: Running tests..."
        run_tests $elem_upper $verbose
    }
    
    # Summary
    print "\n🎉 Automation Complete!"
    print "======================="
    print_summary $elem_upper
}

def clean_generated_files [element_type: string] {
    let elem_lower = ($element_type | str downcase)
    let elem_upper = ($element_type | str upcase)
    
    print "🧹 Cleaning previously generated files for ($elem_upper)..."
    
    # Clean XSLT files
    try {
        rm -rf $"../xslt/($elem_lower)/*.xsl"
    } catch { }
    
    # Clean XSpec files
    try {
        rm -rf $"../tests/xspec/($elem_lower)/*.xspec"
    } catch { }
    
    # Clean analysis files
    try {
        rm -rf $"($elem_lower)-changes-analysis.json"
    } catch { }
    
    print "✅ Cleanup complete"
}

def run_diff_analysis [element_type: string, verbose: bool] {
    if $verbose { print "   Running diff analysis script..." }
    
    try {
        nu analyze-element-changes.nu $element_type
        print "✅ Diff analysis complete"
    } catch {
        print "❌ Diff analysis failed"
        exit 1
    }
}

def run_xslt_generation [element_type: string, verbose: bool] {
    if $verbose { print "   Running XSLT generation script..." }
    
    try {
        nu generate-xslt-transformations.nu $element_type
        print "✅ XSLT generation complete"
    } catch {
        print "❌ XSLT generation failed"
        exit 1
    }
}

def run_xspec_generation [element_type: string, verbose: bool] {
    if $verbose { print "   Running XSpec generation script..." }
    
    try {
        nu generate-xspec-tests.nu $element_type
        print "✅ XSpec generation complete"
    } catch {
        print "❌ XSpec generation failed"
        exit 1
    }
}

def update_documentation [element_type: string, verbose: bool] {
    if $verbose { print "   Updating version changes documentation..." }
    
    let elem_lower = ($element_type | str downcase)
    let elem_upper = ($element_type | str upcase)
    let analysis_file = $"($elem_lower)-changes-analysis.json"
    
    # Check if analysis file exists
    if not ($analysis_file | path exists) {
        print "⚠️  Analysis file not found: ($analysis_file)"
        return
    }
    
    # Update the version changes documentation with analysis results
    let analysis_data = (open $analysis_file)
    
    # Create summary of changes
    let change_summary = ($analysis_data | each { |version|
        let from_v = $version.from_version
        let to_v = $version.to_version
        let change_count = ($version.changes | length)
        
        $"- **($from_v) → ($to_v)**: ($change_count) ($elem_upper)-related changes"
    } | str join "\n")
    
    # Update version-changes.md
    let total_versions = ($analysis_data | length)
    let total_changes = ($analysis_data | get changes | flatten | length)
    let current_date = (date now | format date "%Y-%m-%d %H:%M:%S")
    
    # Create template parts to avoid interpolation issues
    let doc_template = [
        $"# Formex ($elem_upper) Version Changes"
        ""
        $"This document summarizes the ($elem_upper)-related changes across all Formex schema versions."
        ""
        "## Generated Analysis Summary"
        ""
        "The following changes were automatically identified from diff analysis:"
        ""
        $change_summary
        ""
        "## Total Statistics"
        ""
        $"- **Total Versions**: ($total_versions)"
        $"- **Total Changes**: ($total_changes)"
        $"- **Generated XSLT Files**: ($total_versions)"
        $"- **Generated XSpec Tests**: ($total_versions)"
        ""
        "## Automation"
        ""
        "This analysis and all transformations are automatically generated using:"
        ""
        "```bash"
        "cd transformations/scripts"
        "nu automation.nu ($element_type) --regenerate --test"
        "```"
        ""
        $"Generated on: ($current_date)"
    ]
    
    let updated_doc = ($doc_template | str join "\n")
    let docs_file = $"../docs/version-changes-($elem_lower).md"
    
    $updated_doc | save -f $docs_file
    
    print "✅ Documentation updated: ($docs_file)"
}

def run_tests [element_type: string, verbose: bool] {
    let elem_upper = ($element_type | str upcase)
    
    print "   Running XSpec tests (if XSpec processor is available)..."
    
    # Check if Saxon or XSpec is available
    try {
        # This is a placeholder - actual test running would depend on XSpec setup
        print "   XSpec testing requires Saxon HE/PE/EE with XSpec"
        print $"   Tests are available in: transformations/tests/xspec/($element_type | str downcase)/"
        print "✅ Test files ready (manual test execution required)"
    } catch {
        print "⚠️  XSpec processor not found - tests generated but not executed"
    }
}

def print_summary [element_type: string] {
    let elem_lower = ($element_type | str downcase)
    let elem_upper = ($element_type | str upcase)
    
    let xslt_dir = $"../xslt/($elem_lower)/"
    let xspec_dir = $"../tests/xspec/($elem_lower)/"
    
    let xslt_count = if ($xslt_dir | path exists) {
        (ls $xslt_dir | where name ends-with ".xsl" | length)
    } else {
        0
    }
    
    let xspec_count = if ($xspec_dir | path exists) {
        (ls $xspec_dir | where name ends-with ".xspec" | length)
    } else {
        0
    }
    
    let analysis_file = $"($elem_lower)-changes-analysis.json"
    let analysis_size = if ($analysis_file | path exists) {
        (ls $analysis_file | first | get size)
    } else {
        0
    }
    
    print $"📊 Generated Files for ($elem_upper):"
    print $"   • XSLT Transformations: ($xslt_count)"
    print $"   • XSpec Test Files: ($xspec_count)"
    print $"   • Analysis Data: ($analysis_size) bytes"
    print ""
    print $"🎯 Ready for use:"
    print $"   • Apply transformations: transformations/xslt/($elem_lower)/"
    print $"   • Run tests: transformations/tests/xspec/($elem_lower)/"
    print $"   • View analysis: transformations/scripts/($analysis_file)"
}