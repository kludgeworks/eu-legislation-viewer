#!/bin/bash

# Simple script to restructure XSpec files with version mapping

echo "Restructuring XSpec test files..."
echo "================================="

cd /Users/timsparg/source/github.com/kludge-works/eu.legislation.cli/formex-tools/transformations

# Use a function to get simple version
get_simple_version() {
    case "$1" in
        "01.0020040501") echo "01.00" ;;
        "01.0120040501") echo "01.01" ;;
        "01.0220040901") echo "01.02" ;;
        "02.0020050101") echo "02.00" ;;
        "03.0020051001") echo "03.00" ;;
        "04.0020061031") echo "04.00" ;;
        "04.cj20080212") echo "04.cj" ;;
        "05.0020081231") echo "05.00" ;;
        "05.0120091201") echo "05.01" ;;
        "05.1020100101") echo "05.10" ;;
        "05.1120100101") echo "05.11" ;;
        "05.2020100601") echo "05.20" ;;
        "05.2120110601") echo "05.21" ;;
        "05.2220110811") echo "05.22" ;;
        "05.2320111201") echo "05.23" ;;
        "05.2420121231") echo "05.24" ;;
        "05.3020130101") echo "05.30" ;;
        "05.5120130501") echo "05.51" ;;
        "05.5220130701") echo "05.52" ;;
        "05.5320130725") echo "05.53" ;;
        "05.53l20140201") echo "05.53l" ;;
        "05.5420140601") echo "05.54" ;;
        "05.5520141201") echo "05.55" ;;
        "05.5620160701") echo "05.56" ;;
        "05.5720161001") echo "05.57" ;;
        "05.5820161101") echo "05.58" ;;
        "05.5920170418") echo "05.59" ;;
        "06.0020210715") echo "06.00" ;;
        "06.0120220520") echo "06.01" ;;
        "06.0220230206") echo "06.02" ;;
        "06.02.120231031") echo "06.02.1" ;;
        "06.02.220241118") echo "06.02.2" ;;
        "06.02.320250123") echo "06.02.3" ;;
        *) echo "" ;;
    esac
}

count=0

for file in tests/xspec/*.xspec; do
    if [ -f "$file" ]; then
        basename_file=$(basename "$file" .xspec)
        target_version=$(echo "$basename_file" | sed 's/.*_to_//')
        
        # Get simple version
        simple_version=$(get_simple_version "$target_version")
        
        if [ -n "$simple_version" ]; then
            new_filename="v${simple_version}.xspec"
            new_path="tests/xspec/doc/${new_filename}"
            
            # Update the file content and copy
            sed 's|stylesheet="../xslt/step-by-step/[^"]*"|stylesheet="../../xslt/doc/v'"$simple_version"'.xsl"|g' "$file" > "$new_path"
            
            echo "✓ $basename_file -> $new_filename"
            ((count++))
        else
            echo "✗ No mapping for version: $target_version"
        fi
    fi
done

echo ""
echo "✅ Successfully restructured $count XSpec test files"
echo "📁 New structure: transformations/tests/xspec/doc/v{version}.xspec"