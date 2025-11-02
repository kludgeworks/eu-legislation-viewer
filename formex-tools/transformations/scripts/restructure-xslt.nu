#!/usr/bin/env nu

# Restructure XSLT files from old naming convention to new element-based structure
# This script moves DOC transformations to the new simplified naming

use version-mapping.nu *

# Get the transformation file mappings
def get_transformation_mappings [] {
    let old_dir = "../xslt/step-by-step"
    let files = (ls $old_dir | get name | path basename)
    
    $files | each {|file|
        let parts = ($file | str replace ".xsl" "" | split row "_to_")
        let from_version = ($parts | first)
        let to_version = ($parts | last)
        
        let simple_to = (mapping get-simple $to_version)
        
        {
            old_file: $file,
            old_path: ($old_dir + "/" + $file),
            from_version: $from_version,
            to_version: $to_version,
            simple_to: $simple_to,
            new_filename: ($"v" + $simple_to + ".xsl"),
            new_path: ("../xslt/doc/" + $"v" + $simple_to + ".xsl")
        }
    }
}

# Update XSLT file content for new structure
def update_xslt_content [file_path: string, simple_version: string] {
    let content = (open $file_path)
    
    # Update import paths to use shared directory
    let updated_content = ($content 
        | str replace --all "../utils/" "../shared/"
    )
    
    return $updated_content
}

# Copy and update a single XSLT file
def copy_and_update_file [mapping: record] {
    print $"Processing: ($mapping.old_file) -> ($mapping.new_filename)"
    
    # Read and update content
    let updated_content = (update_xslt_content $mapping.old_path $mapping.simple_to)
    
    # Write to new location
    $updated_content | save $mapping.new_path
    
    print $"✓ Created ($mapping.new_path)"
}

# Main restructuring function
def main [] {
    print "Restructuring XSLT files to new naming convention..."
    print "=================================================="
    
    let mappings = (get_transformation_mappings)
    
    print $"Found ($mappings | length) transformation files to convert"
    print ""
    
    # Process each file
    $mappings | each {|mapping|
        copy_and_update_file $mapping
    }
    
    print ""
    print $"✅ Successfully restructured ($mappings | length) XSLT files"
    print "📁 New structure: transformations/xslt/doc/v{version}.xsl"
    print "🔧 Updated import paths to use shared utilities"
}

# Export for use in other scripts
export def "restructure list-mappings" [] {
    get_transformation_mappings
}

export def "restructure single-file" [old_file: string] {
    let mappings = (get_transformation_mappings)
    let mapping = ($mappings | where old_file == $old_file | first)
    if ($mapping | is-empty) {
        error make {msg: $"File not found: ($old_file)"}
    } else {
        copy_and_update_file $mapping
    }
}