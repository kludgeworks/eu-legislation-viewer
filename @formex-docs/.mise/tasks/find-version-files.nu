#!/usr/bin/env nu
#MISE description="List downloaded XML files that reference a specific Formex schema version"
#USAGE flag "--version <version>" help="Formex schema filename to match (e.g. formex-06.02.1-20231031.xd)"

let version = $env.usage_version?

if $version == null or ($version | str trim) == "" {
    error make { msg: "Missing required --version argument (e.g. formex-06.02.1-20231031.xd)" }
}

let repo_root = (pwd)

let matches = (
    glob downloads/**/*.xml
    | par-each { |file|
        let schema = (try {
            open --raw $file
            | from xml
            | get attributes.noNamespaceSchemaLocation?
            | if $in != null {
                path basename
            } else {
                null
            }
        } catch {
            null
        })

        if $schema == $version { 
            {
                file: (
                    if ($file | path type) == "relative" {
                        $file
                    } else {
                        $file | path relative-to $repo_root
                    }
                )
            }
        } else {
            null
        }
    }
    | compact
    | sort-by file
)

if ($matches | is-empty) {
    print $"No files found for version ($version)"
    exit 0
}

$matches | select file
