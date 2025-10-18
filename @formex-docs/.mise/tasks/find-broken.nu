#!/usr/bin/env nu
#MISE description="Find XML files with encoding or parsing errors"

# Check all XML files for encoding issues and malformed XML
# Returns a table with file paths and error types

let broken_files = (
    glob downloads/**/*.xml
    | par-each { |file|
        let result = (try {
            # Attempt to open and parse the file
            open --raw $file | from xml
            null  # Success - return null to filter out
        } catch { |err|
            # Capture error details
            let error_type = if ($err.debug | str contains "NonUtf8") {
                "encoding_error"
            } else if ($err.debug | str contains "xml") {
                "xml_parse_error"
            } else {
                "unknown_error"
            }

            {
                file: $file,
                error_type: $error_type,
                message: ($err.msg | str substring 0..100)
            }
        })

        $result
    }
    | compact  # Remove nulls (successful files)
    | sort-by error_type file
)

# Print summary
print $"Found ($broken_files | length) broken files\n"

# Group by error type and print counts
$broken_files
| group-by error_type
| items { |type, files|
    print $"($type): ($files | length) files"
}

print "\nFirst 10 broken files:"
$broken_files | first 10
