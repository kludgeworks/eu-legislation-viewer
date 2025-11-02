#!/usr/bin/env nu

# Analyze all diff files to extract DOC-related changes for XSLT generation
# This script parses each diff file and identifies changes that affect DOC elements

def main [] {
    let diff_dir = "../../xsd/diffs"
    let output_file = "doc-changes-analysis.json"
    
    print "🔍 Analyzing DOC-related changes in all diff files..."
    
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
        let doc_changes = (analyze_doc_changes $diff_content)
        
        {
            filename: $filename,
            from_version: $from_version,
            to_version: $to_version,
            changes: $doc_changes
        }
    })
    
    # Save results
    $changes | to json | save -f $output_file
    print $"✅ Analysis complete! Results saved to ($output_file)"
    
    # Summary
    let total_changes = ($changes | get changes | flatten | length)
    print $"📊 Summary: Found ($total_changes) DOC-related changes across all versions"
}

# Analyze a single diff file for DOC-related changes
def analyze_doc_changes [diff_content: string] {
    let lines = ($diff_content | lines)
    mut doc_changes = []
    mut current_section = null
    mut in_doc_context = false
    
    for line in $lines {
        # Check if we're in a DOC-related section
        let has_doc = ($line | str contains "DOC")
        let has_work_doc = ($line | str contains "WORK.DOC")
        let has_no_doc = ($line | str contains "NO.DOC")
        
        if ($has_doc or $has_work_doc or $has_no_doc) {
            $in_doc_context = true
        }
        
        # Extract changes only when in DOC context
        if $in_doc_context {
            if ($line | str starts-with "-") {
                # Removed line
                let change = {
                    type: "removal",
                    content: ($line | str substring 1..),
                    line: $line
                }
                $doc_changes = ($doc_changes | append $change)
            } else if ($line | str starts-with "+") {
                # Added line
                let change = {
                    type: "addition",
                    content: ($line | str substring 1..),
                    line: $line
                }
                $doc_changes = ($doc_changes | append $change)
            } else if ($line | str starts-with "@@") {
                # Context header
                $current_section = $line
            }
        }
        
        # Reset context if we've moved away from DOC-related content
        let starts_with_at = ($line | str starts-with "@@")
        let has_doc_in_context = ($line | str contains "DOC")
        if (not $has_doc_in_context and $starts_with_at) {
            $in_doc_context = false
        }
    }
    
    $doc_changes
}