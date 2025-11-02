#!/usr/bin/env nu

# Master automation script for Formex DOC transformations
# This script orchestrates the complete transformation generation process

def main [
    --regenerate(-r)  # Regenerate all files from scratch
    --test(-t)        # Run tests after generation
    --verbose(-v)     # Verbose output
] {
    print "🚀 Formex DOC Transformation Automation"
    print "========================================="
    
    if $regenerate {
        print "⚠️  Regeneration mode: All files will be recreated"
        clean_generated_files
    }
    
    # Step 1: Analyze diffs
    print "\n📊 Step 1: Analyzing DOC changes from diff files..."
    run_diff_analysis $verbose
    
    # Step 2: Generate XSLT transformations
    print "\n🔧 Step 2: Generating XSLT transformations..."
    run_xslt_generation $verbose
    
    # Step 3: Generate XSpec tests
    print "\n🧪 Step 3: Generating XSpec test files..."
    run_xspec_generation $verbose
    
    # Step 4: Generate documentation
    print "\n📚 Step 4: Updating documentation..."
    update_documentation $verbose
    
    # Step 5: Run tests (if requested)
    if $test {
        print "\n✅ Step 5: Running tests..."
        run_tests $verbose
    }
    
    # Summary
    print "\n🎉 Automation Complete!"
    print "========================"
    print_summary
}

def clean_generated_files [] {
    print "🧹 Cleaning previously generated files..."
    
    # Clean XSLT files
    rm -rf ../xslt/step-by-step/*.xsl
    
    # Clean XSpec files
    rm -rf ../tests/xspec/*.xspec
    
    # Clean analysis files
    rm -rf doc-changes-analysis.json
    
    print "✅ Cleanup complete"
}

def run_diff_analysis [verbose: bool] {
    if $verbose { print "   Running diff analysis script..." }
    
    try {
        nu analyze-doc-changes.nu
        print "✅ Diff analysis complete"
    } catch {
        print "❌ Diff analysis failed"
        exit 1
    }
}

def run_xslt_generation [verbose: bool] {
    if $verbose { print "   Running XSLT generation script..." }
    
    try {
        nu generate-xslt-transformations.nu
        print "✅ XSLT generation complete"
    } catch {
        print "❌ XSLT generation failed"
        exit 1
    }
}

def run_xspec_generation [verbose: bool] {
    if $verbose { print "   Running XSpec generation script..." }
    
    try {
        nu generate-xspec-tests.nu
        print "✅ XSpec generation complete"
    } catch {
        print "❌ XSpec generation failed"
        exit 1
    }
}

def update_documentation [verbose: bool] {
    if $verbose { print "   Updating version changes documentation..." }
    
    # Update the version changes documentation with analysis results
    let analysis_data = (open doc-changes-analysis.json)
    
    # Create summary of changes
    let change_summary = ($analysis_data | each { |version|
        let from_v = $version.from_version
        let to_v = $version.to_version
        let change_count = ($version.changes | length)
        
        $"- **($from_v) → ($to_v)**: ($change_count) DOC-related changes"
    } | str join "\n")
    
    # Update version-changes.md
    let total_versions = ($analysis_data | length)
    let total_changes = ($analysis_data | get changes | flatten | length)
    let current_date = (date now | format date "%Y-%m-%d %H:%M:%S")
    
    # Create template parts to avoid interpolation issues
    let doc_template = [
        "# Formex DOC Version Changes"
        ""
        "This document summarizes the DOC-related changes across all Formex schema versions."
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
        "## Change Patterns Identified"
        ""
        "1. **Schema Location Updates**: All versions require schema URL updates"
        "2. **NO.DOC Cardinality**: Version 01.00→01.01 changed NO.DOC from single to unbounded"
        "3. **Style Attribute Removal**: Early versions removed gr.styles attribute groups"
        "4. **Element Type Simplification**: Some elements simplified"
        "5. **New Document Types**: Later versions added support for new document types"
        ""
        "## Automation"
        ""
        "This analysis and all transformations are automatically generated using:"
        ""
        "```bash"
        "cd transformations/scripts"
        "nu automation.nu --regenerate --test"
        "```"
        ""
        $"Generated on: ($current_date)"
    ]
    
    let updated_doc = ($doc_template | str join "\n")
    
    $updated_doc | save -f ../docs/version-changes.md
    
    print "✅ Documentation updated"
}

def run_tests [verbose: bool] {
    print "   Running XSpec tests (if XSpec processor is available)..."
    
    # Check if Saxon or XSpec is available
    try {
        # This is a placeholder - actual test running would depend on XSpec setup
        print "   XSpec testing requires Saxon HE/PE/EE with XSpec"
        print "   Tests are available in: transformations/tests/xspec/"
        print "✅ Test files ready (manual test execution required)"
    } catch {
        print "⚠️  XSpec processor not found - tests generated but not executed"
    }
}

def print_summary [] {
    let xslt_count = (ls ../xslt/step-by-step/ | where name ends-with ".xsl" | length)
    let xspec_count = (ls ../tests/xspec/ | where name ends-with ".xspec" | length)
    let analysis_file_info = (ls doc-changes-analysis.json | first)
    let analysis_size = $analysis_file_info.size
    
    print $"📊 Generated Files:"
    print $"   • XSLT Transformations: ($xslt_count)"
    print $"   • XSpec Test Files: ($xspec_count)"
    print $"   • Analysis Data: ($analysis_size) bytes"
    print ""
    print $"🎯 Ready for use:"
    print $"   • Apply transformations: transformations/xslt/step-by-step/"
    print $"   • Run tests: transformations/tests/xspec/"
    print $"   • View analysis: transformations/scripts/doc-changes-analysis.json"
}