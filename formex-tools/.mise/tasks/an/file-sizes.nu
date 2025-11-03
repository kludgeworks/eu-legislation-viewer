#!/usr/bin/env nu
#MISE description="Analyze size statistics of downloaded XML files"
#MISE alias="fs"
#USAGE arg "[pattern]" help="Glob pattern for files (default: downloads/**/*.xml)"

def main [pattern?: string] {
    let sizes = (
        if ($pattern | is-empty) {
            glob downloads/**/*.xml
        } else {
            glob $pattern
        }
        | par-each { |file|
            try {
                (ls $file | first).size | into int
            } catch {
                null
            }
        }
        | compact
        | sort
    )
    let count = $sizes | length
    let sum = $sizes | math sum
    let mean = $sum / $count
    let median = (
        if ($count mod 2) == 1 {
            $sizes | get ((($count - 1) / 2) | math floor)
        } else {
            let mid1 = ((($count / 2) - 1) | math floor)
            let mid2 = (($count / 2) | math floor)
            (($sizes | get $mid1) + ($sizes | get $mid2)) / 2
        }
    )

    {
        min: ($sizes | first | into filesize),
        max: ($sizes | last | into filesize),
        sum: ($sum | into filesize),
        mean: ($mean | into filesize),
        median: ($median | into filesize),
        count: $count
    }
}
