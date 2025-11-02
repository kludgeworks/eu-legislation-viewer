#!/usr/bin/env nu

# Analyze all diff files to extract element-specific changes for XSLT generation
# This script parses each diff file and identifies changes that affect specified elements
# Supports any Formex root element: DOC, GENERAL, ACT, ANNEX, etc.

def main [
    element_type: string    # Root element to analyze (e.g., DOC, GENERAL, ACT)
] {
    # Validate input
    if ($element_type | str length) == 0 {
        print "❌ Error: element_type is required"
        print "Usage: analyze-element-changes.nu <element_type>"
        print "Example: analyze-element-changes.nu DOC"
        print "Example: analyze-element-changes.nu GENERAL"
        exit 1
    }
    
    let elem_upper = ($element_type | str upcase)
    let elem_lower = ($element_type | str downcase)
    let diff_dir = "../../xsd/diffs"
    let output_file = $"($elem_lower)-changes-analysis.json"
    
    print $"🔍 Analyzing ($elem_upper)-related changes in all diff files..."
    
    # Get all diff files in chronological order
    let diff_files = (ls $diff_dir | where name ends-with ".diff" | sort-by name)
    
    print $"Found ($diff_files | length) diff files to analyze"
    
    # Analyze each diff file
    let changes = ($diff_files | each { |file|
        let filename = ($file.name | path basename)
        let version_parts = ($filename | str replace ".diff" "" | split row "_to_")
        let from_version = ($version_parts | get 0)
        let to_version = ($version_parts | get 1)
        
        print $"📄 Analyzing ($filename)..."
        
        # Read and analyze the diff content
        let diff_content = (open $file.name)
        let element_changes = (analyze_element_changes $diff_content $elem_upper)
        
        {
            filename: $filename,
            from_version: $from_version,
            to_version: $to_version,
            changes: $element_changes
        }
    })
    
    # Save results
    $changes | to json | save -f $output_file
    print $"✅ Analysis complete! Results saved to ($output_file)"
    
    # Summary
    let total_changes = ($changes | get changes | flatten | length)
    print $"📊 Summary: Found ($total_changes) ($elem_upper)-related changes across all versions"
}

# Analyze a single diff file for element-related changes
# Generic function that works for any element type
def analyze_element_changes [diff_content: string, element_type: string] {
    let lines = ($diff_content | lines)
    mut element_changes = []
    mut current_section = null
    mut in_element_context = false
    
    # Build search patterns for this element type
    # Main element name
    let main_elem = $element_type
    
    # Common child elements by type
    let elem_children = match $element_type {
        "DOC" => ["DOC", "WORK.DOC", "NO.DOC", "BIB.DOC"],
        "GENERAL" => ["GENERAL", "BIB.INSTANCE", "NO.COM", "PROLOG", "CONTENTS"],
        "ACT" => ["ACT", "PREAMBLE", "ENACTING.CLAUSE", "BODY"],
        "ANNEX" => ["ANNEX", "ANNEX.TITLE", "ANNEX.NUMBER"],
        _ => [$element_type]
    }
    
    for line in $lines {
        # Check if we're in an element-related section
        let has_main_elem = ($line | str contains $main_elem)
        let has_child_elems = ($elem_children | any { |child| $line | str contains $child })
        
        if ($has_main_elem or $has_child_elems) {
            $in_element_context = true
        }
        
        # Extract changes only when in element context
        if $in_element_context {
            if ($line | str starts-with "-") {
                # Removed line
                let change = {
                    type: "removal",
                    content: ($line | str substring 1..),
                    line: $line
                }
                $element_changes = ($element_changes | append $change)
            } else if ($line | str starts-with "+") {
                # Added line
                let change = {
                    type: "addition",
                    content: ($line | str substring 1..),
                    line: $line
                }
                $element_changes = ($element_changes | append $change)
            } else if ($line | str starts-with "@@") {
                # Context header
                $current_section = $line
            }
        }
        
        # Reset context if we've moved away from element-related content
        let starts_with_at = ($line | str starts-with "@@")
        let has_element_in_context = ($line | str contains $main_elem)
        if (not $has_element_in_context and $starts_with_at) {
            $in_element_context = false
        }
    }
    
    $element_changes
}
