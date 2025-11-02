#!/usr/bin/env nu

# Restructure XSpec test files to match new XSLT structure
# This script moves test files and updates their import paths

use version-mapping.nu *

# Get XSpec file mappings
def get_xspec_mappings [] {
    let old_dir = "../tests/xspec"
    let files = (ls $old_dir | get name | path basename)
    
    $files | each {|file|
        let parts = ($file | str replace ".xspec" "" | split row "_to_")
        let from_version = ($parts | first)
        let to_version = ($parts | last)
        
        let simple_to = (mapping get-simple $to_version)
        
        {
            old_file: $file,
            old_path: ($old_dir + "/" + $file),
            from_version: $from_version,
            to_version: $to_version,
            simple_to: $simple_to,
            new_filename: ($"v" + $simple_to + ".xspec"),
            new_path: ("../tests/xspec/doc/" + $"v" + $simple_to + ".xspec")
        }
    }
}

# Update XSpec test content for new structure
def update_xspec_content [file_path: string, simple_version: string] {
    let content = (open $file_path)
    
    # Update stylesheet path to point to new DOC structure  
    let updated_content = ($content 
        | str replace --all "../../xslt/step-by-step/" "../../xslt/doc/"
    )
    
    return $updated_content
}

# Copy and update a single XSpec file
def copy_and_update_xspec [mapping: record] {
    print $"Processing: ($mapping.old_file) -> ($mapping.new_filename)"
    
    # Read and update content
    let updated_content = (update_xspec_content $mapping.old_path $mapping.simple_to)
    
    # Write to new location
    $updated_content | save $mapping.new_path
    
    print $"✓ Created ($mapping.new_path)"
}

# Main restructuring function
def main [] {
    print "Restructuring XSpec test files to match new XSLT structure..."
    print "============================================================="
    
    let mappings = (get_xspec_mappings)
    
    print $"Found ($mappings | length) XSpec test files to convert"
    print ""
    
    # Process each file
    $mappings | each {|mapping|
        copy_and_update_xspec $mapping
    }
    
    print ""
    print $"✅ Successfully restructured ($mappings | length) XSpec test files"
    print "📁 New structure: transformations/tests/xspec/doc/v{version}.xspec"
    print "🔧 Updated stylesheet import paths"
}

# Export for use in other scripts
export def "restructure-xspec list-mappings" [] {
    get_xspec_mappings
}