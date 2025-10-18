#!/usr/bin/env nu
#MISE description="List downloaded XML files that reference a specific Formex schema version"
#USAGE flag "--version <version>" help="Formex schema filename to match (e.g. formex-06.02.1-20231031.xd)"
#USAGE flag "-l --limit <count>" help="Maximum number of matching files to return" default="100"

let version = $env.usage_version?
let limit = (
    $env.usage_limit?
    | default "100"
    | into int
)

if $version == null or ($version | str trim) == "" {
    error make { msg: "Missing required --version argument (e.g. formex-06.02.1-20231031.xd)" }
}

let repo_root = (pwd)

# Create a stream of all XML files
let matches = (
    glob downloads/**/*.xml
    # Process each file in the stream
    | each { |file|
        let schema = (try {
            open --raw $file
            | from xml
            | get attributes.noNamespaceSchemaLocation?
            | if $in != null { path basename } else { null }
        } catch {
            null
        })

        # Output the file path if it's a match, otherwise output null
        if $schema == $version { $file } else { null }
    }
    # Filter out the non-matches
    | compact
    # Take the first N items, short-circuiting the stream
    | first $limit
    # Convert the paths to be relative for clean output
    | each { |file|
        let relative = if ($file | path type) == "relative" {
            $file
        } else {
            $file | path relative-to $repo_root
        }
        { file: $relative }
    }
    | sort-by file
)

if ($matches | is-empty) {
    print $"No files found for version ($version)"
    exit 0
}

$matches | select file
