#!/usr/bin/env nu
#MISE description="Generate unified diffs between consecutive Formex XSD versions"
#USAGE flag "--clean" help="Remove existing diffs directory before generating new ones"

# Generate unified diffs between consecutive versions of Formex XSD files
# Output diffs will be placed in xsd/diffs/ directory
def main [--clean] {
    let xsd_dir = "xsd"
    let output_dir = $"($xsd_dir)/diffs"

    # Validate xsd directory exists
    if not ($xsd_dir | path exists) {
        error make {
            msg: $"XSD directory not found: ($xsd_dir)"
        }
    }

    # Handle existing diffs directory
    if ($output_dir | path exists) {
        if $clean {
            print $"Removing existing diffs directory: ($output_dir)"
            rm -rf $output_dir
        } else {
            # Use gum confirm for simple yes/no dialog (with timeout for non-interactive environments)
            let should_regenerate = (
                try {
                    ^gum confirm --timeout=30s --default=false "Diffs directory already exists. Remove and regenerate all diffs?"
                    true
                } catch {
                    # If gum fails (no TTY), default to false (don't regenerate)
                    false
                }
            )
            
            if $should_regenerate {
                print $"Removing existing diffs directory: ($output_dir)"
                rm -rf $output_dir
            } else {
                print $"Keeping existing diffs in: ($output_dir)"
                return
            }
        }
    }

    # Create output directory
    mkdir $output_dir

    # Get all formex XSD files, excluding catalog and other non-formex files
    let formex_files = (
        glob $"($xsd_dir)/formex*.xd"
        | path basename
        | sort-by {|file|
            # Parse version numbers for proper chronological sorting
            let normalized = ($file | str replace "Formex" "formex")
            let parts = ($normalized | parse --regex '^formex-(?P<major>\d+)\.(?P<minor>[^-]+)-(?P<date>\d+)\.xd$')

            if ($parts | length) == 0 {
                # Fallback for malformed filenames
                0
            } else {
                let major = ($parts | get 0.major | into int)
                let minor_str = ($parts | get 0.minor)
                let date = ($parts | get 0.date | into int)

                # Handle special cases in minor version using match
                let minor = match $minor_str {
                    $val if ($val | str contains ".") => {
                        # Handle versions like "02.1", "02.2", etc.
                        let version_parts = ($val | parse "{base}.{sub}")
                        let base = ($version_parts | get 0.base | into int)
                        let sub = ($version_parts | get 0.sub | into int)
                        ($base * 100) + $sub
                    }
                    "cj" => {
                        # Place "cj" between 04.00 and 05.00
                        450
                    }
                    $val if ($val | str ends-with "l") => {
                        # Handle versions like "53l"
                        let base_minor = ($val | str replace "l" "" | into int)
                        ($base_minor * 10) + 1
                    }
                    _ => {
                        # Standard numeric minor version
                        ($minor_str | into int) * 10
                    }
                }

                # Create sortable key: major * 1000000 + minor * 1000 + date_offset
                let date_offset = ($date - 20040000) # Normalize dates from 2004 baseline
                ($major * 1000000) + ($minor * 1000) + $date_offset
            }
        }
    )

    if ($formex_files | is-empty) {
        error make {
            msg: $"No Formex XSD files found in ($xsd_dir)"
        }
    }

    print ""
    print "📁 FORMEX XSD FILES DISCOVERED"
    print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    print $"Found ($formex_files | length) files in chronological order:"
    print ""
    
    # Create a nicely formatted list of files with version info
    let file_listing = (
        $formex_files 
        | enumerate 
        | each {|item|
            let index = $item.index + 1
            let file = $item.item
            # Parse version from filename
            let version_info = ($file | parse --regex '^formex-(?P<version>[^.]+\.[^-]+)-(?P<date>\d+)\.xd$')
            if ($version_info | length) > 0 {
                let version = ($version_info | get 0.version)
                let date = ($version_info | get 0.date)
                $"  ($index | into string | fill -c ' ' -w 2 -a r). ($version | fill -c ' ' -w 12 -a l) ($date) → ($file)"
            } else {
                $"  ($index | into string | fill -c ' ' -w 2 -a r). Unknown format     → ($file)"
            }
        }
    )
    $file_listing | each {|line| print $line}
    print ""

    # Generate diffs between consecutive versions
    let pairs = ($formex_files | window 2)

    if ($pairs | is-empty) {
        print "Only one XSD file found - no diffs to generate"
        return
    }

    print ""
    print "🔄 GENERATING DIFFS"
    print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    print $"Processing ($pairs | length) version pairs..."
    print ""

    let diff_results = (
        $pairs
        | enumerate
        | each {|pair_info|
            let pair = $pair_info.item
            let index = $pair_info.index
            let file1 = ($pair | get 0)
            let file2 = ($pair | get 1)
            let diff_name = $"($file1 | path parse | get stem)_to_($file2 | path parse | get stem).diff"
            
            # Create progress visualization
            let progress = (($index + 1) * 100 / ($pairs | length) | math round)
            let progress_bar = match $progress {
                $p if $p < 25 => "▱▱▱▱"
                $p if $p < 50 => "▰▱▱▱"  
                $p if $p < 75 => "▰▰▱▱"
                $p if $p < 100 => "▰▰▰▱"
                _ => "▰▰▰▰"
            }

            print $"  [($index + 1)/($pairs | length)] [($progress_bar)] ($progress)% - ($file1) → ($file2)"

            # Generate unified diff with error handling
            let diff_result = (
                try {
                    ^diff -u $"($xsd_dir)/($file1)" $"($xsd_dir)/($file2)" | complete
                } catch {|e|
                    error make {
                        msg: $"Failed to generate diff for ($file1) -> ($file2): ($e.msg)"
                    }
                }
            )

            # Save diff to file
            let diff_path = $"($output_dir)/($diff_name)"
            $diff_result.stdout | save $diff_path

            # Calculate statistics
            let diff_lines = ($diff_result.stdout | lines)
            let added_lines = ($diff_lines | where ($it | str starts-with "+") | length)
            let removed_lines = ($diff_lines | where ($it | str starts-with "-") | length)

            {
                file1: $file1
                file2: $file2
                diff_file: $diff_name
                added: $added_lines
                removed: $removed_lines
                total_lines: ($diff_lines | length)
            }
        }
    )

    print ""
    print "╭─────────────────────────────────────────╮"
    print "│           DIFF GENERATION COMPLETE!    │"
    print "╰─────────────────────────────────────────╯"
    print $"Output directory: ($output_dir)"
    print ""

    # Create enhanced summary table with change categorization
    let enhanced_results = (
        $diff_results
        | each {|result|
            let net_change = ($result.added - $result.removed)
            let total_changes = ($result.added + $result.removed)
            let change_magnitude = match $total_changes {
                $n if $n < 50 => "Minor"
                $n if $n < 200 => "Moderate" 
                $n if $n < 1000 => "Major"
                _ => "Massive"
            }
            
            $result | insert net_change $net_change | insert change_magnitude $change_magnitude | insert total_changes $total_changes
        }
    )

    print "📊 DIFF SUMMARY TABLE"
    print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    let summary_table = (
        $enhanced_results
        | select file1 file2 added removed net_change change_magnitude
        | each {|row|
            # Shorten filenames to just version numbers for better table display
            let from_short = ($row.file1 | str replace "formex-" "" | str replace ".xd" "")
            let to_short = ($row.file2 | str replace "formex-" "" | str replace ".xd" "")
            $row | update file1 $from_short | update file2 $to_short
        }
        | rename "From" "To" "Added" "Removed" "Net" "Type"
    )
    print $summary_table

    print ""
    print "📈 CHANGE STATISTICS"
    print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    let total_added = ($diff_results | get added | math sum)
    let total_removed = ($diff_results | get removed | math sum)
    let net_total = ($total_added - $total_removed)
    
    # Categorize changes by magnitude
    let magnitude_stats = (
        $enhanced_results 
        | group-by change_magnitude 
        | transpose category data 
        | each {|group| 
            {
                magnitude: $group.category
                count: ($group.data | length)
                total_added: ($group.data | get added | math sum)
                total_removed: ($group.data | get removed | math sum)
            }
        }
    )
    
    print ([
        {metric: "Total Diffs Generated" value: ($diff_results | length)}
        {metric: "Total Lines Added" value: $"+($total_added)"}
        {metric: "Total Lines Removed" value: $"-($total_removed)"}
        {metric: "Net Change" value: (if $net_total >= 0 { $"+($net_total)" } else { $"($net_total)" })}
    ] | table -i false)
    
    print ""
    print "📋 CHANGES BY MAGNITUDE"
    print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    $magnitude_stats 
    | rename "Magnitude" "Count" "Added" "Removed" 
    | each {|row| 
        $row | update Added $"+($row.Added)" | update Removed $"-($row.Removed)"
    }
}