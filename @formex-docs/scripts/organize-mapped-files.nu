#!/usr/bin/env nu
# Find mapping entries whose derived URLs appear in content-streams.txt,
# and copy the corresponding files to a new directory structure:
# mappings/<version>/<UUID.xxx.xx>/DOC_<x>.xml

# Allow overriding paths via environment variables for flexibility.
let mappings_path = ($env.MAPPINGS_PATH? | default "mappings.jsonl")
let streams_path = ($env.STREAMS_PATH? | default "content-streams.txt")

print "Processing mappings and copying files..."

# Stream through mappings, check each URL against content-streams, and copy immediately
let copied_count = (
    open $mappings_path
    | lines
    | where { |line| not ($line | str trim | is-empty) }
    | each { from json }
    | each {|row|
        # Generate URL from file path
        let url = (
            $row.file
            | str replace -r "^downloads/" "http://"
            | str replace -r "\\.xml$" ""
        )

        # Check if URL exists in content-streams using grep
        let match_result = (do { ^grep -Fxq $url $streams_path } | complete)

        if $match_result.exit_code == 0 {
            # URL found, copy the file
            let source_path = $row.file
            let version = $row.version
            let filename = ($source_path | path basename)
            let uuid_dir = ($source_path | path dirname | path basename)

            # Create destination path: mappings/<version>/<UUID.xxx.xx>/DOC_<x>.xml
            let dest_dir = (["mappings", $version, $uuid_dir] | path join)
            mkdir $dest_dir

            let dest_file = ([$dest_dir, $filename] | path join)
            cp $source_path $dest_file

            print $"  ✓ ($filename) -> ($version)/($uuid_dir)/"
            1
        } else {
            0
        }
    }
    | math sum
)

print $"\nDone! Copied ($copied_count) files."
