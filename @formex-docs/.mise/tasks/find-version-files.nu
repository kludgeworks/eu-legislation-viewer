#!/usr/bin/env nu
#MISE description="List downloaded XML files that reference a specific Formex schema version"
#USAGE flag -l --limit <count> help="Total maximum number of matching files to return"
#USAGE flag -p --limit-per-version <count> help="Maximum number of matching files to return per version"
#USAGE flag -w --with-version help="Include the version number in the output"
#USAGE arg versions... "Formex schema filename(s) to match (e.g. formex-06.02.1-20231031.xd)"

def main [...versions: string, --limit(-l): int = -1, --limit-per-version(-p): int = -1, --with-version(-w)] {

    if ($versions | is-empty) {
        error make { msg: "Missing required version argument(s) (e.g. formex-06.02.1-20231031.xd)" }
    }

    let repo_root = (pwd)

    let matches = if ($versions | length) == 1 {
        # --- Fast Path for Single Version ---
        let version = ($versions | get 0)
        let results = (
            glob downloads/**/*.xml
            | each { |file|
                try {
                    let schema = (
                        open --raw $file
                        | from xml
                        | get attributes.noNamespaceSchemaLocation?
                        | path basename
                    )
                    if $schema == $version {
                        { file: $file, version: $schema }
                    } else {
                        null
                    }
                } catch {
                    null
                }
            }
            | compact
            | if $limit >= 0 { first $limit } else { each { |it| $it } }
        )
        $results
    } else {
        # --- Slower, but Correct Path for Multiple Versions ---
        let results = (
            $versions
            | each { |version|
                let sub_results = (
                    glob downloads/**/*.xml
                    | each { |file|
                        try {
                            let schema = (
                                open --raw $file
                                | from xml
                                | get attributes.noNamespaceSchemaLocation?
                                | path basename
                            )
                            if $schema == $version {
                                { file: $file, version: $schema }
                            } else {
                                null
                            }
                        } catch {
                            null
                        }
                    }
                    | compact
                    | if $limit_per_version >= 0 { first $limit_per_version } else { each { |it| $it } }
                )
                $sub_results
            }
            | flatten
            | if $limit >= 0 { first $limit } else { each { |it| $it } }
        )
        $results
    }

    let final_results = (
        $matches
        | each { |match|
            let relative = if (($match.file) | path type) == "relative" {
                $match.file
            } else {
                ($match.file) | path relative-to $repo_root
            }
            { file: $relative, version: $match.version }
        }
    )

    if ($final_results | is-empty) {
        print $"No files found for version(s): ($versions | str join ", ")"
        exit 0
    }

    if $with_version {
        $final_results | select version file | sort-by version file
    } else {
        $final_results | select file | sort-by file
    }
}