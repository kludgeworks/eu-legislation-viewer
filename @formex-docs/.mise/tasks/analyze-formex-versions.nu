#!/usr/bin/env nu
#MISE description="Analyze formex schema versions across downloaded XML files"

# Parse all XML files and extract the noNamespaceSchemaLocation attribute
# from the root element, then group and count distinct versions

glob downloads/**/*.xml
| par-each { |file|
    # Parse XML and extract the noNamespaceSchemaLocation attribute
    # from the root element, then get just the filename
    # Use try/catch to handle files with encoding issues
    try {
        open --raw $file
        | from xml
        | get attributes.noNamespaceSchemaLocation?
        | if $in != null {
            path basename
        } else {
            null
        }
    } catch {
        # Skip files with encoding errors
        null
    }
}
| compact  # Remove null values
| group-by  # Group by the version string itself
| transpose name files
| insert count { |row| $row.files | length }
| select name count
| sort-by count --reverse
