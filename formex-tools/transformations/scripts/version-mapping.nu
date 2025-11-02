#!/usr/bin/env nu

# Version mapping from full formex versions to simplified versions
# This script creates the mapping table for restructuring

def create_version_mapping [] {
    let version_map = [
        {full: "01.0020040501", simple: "01.00"},
        {full: "01.0120040501", simple: "01.01"},
        {full: "01.0220040901", simple: "01.02"},
        {full: "02.0020050101", simple: "02.00"},
        {full: "03.0020051001", simple: "03.00"},
        {full: "04.0020061031", simple: "04.00"},
        {full: "04.cj20080212", simple: "04.cj"},
        {full: "05.0020081231", simple: "05.00"},
        {full: "05.0120091201", simple: "05.01"},
        {full: "05.1020100101", simple: "05.10"},
        {full: "05.1120100101", simple: "05.11"},
        {full: "05.2020100601", simple: "05.20"},
        {full: "05.2120110601", simple: "05.21"},
        {full: "05.2220110811", simple: "05.22"},
        {full: "05.2320111201", simple: "05.23"},
        {full: "05.2420121231", simple: "05.24"},
        {full: "05.3020130101", simple: "05.30"},
        {full: "05.5120130501", simple: "05.51"},
        {full: "05.5220130701", simple: "05.52"},
        {full: "05.5320130725", simple: "05.53"},
        {full: "05.53l20140201", simple: "05.53l"},
        {full: "05.5420140601", simple: "05.54"},
        {full: "05.5520141201", simple: "05.55"},
        {full: "05.5620160701", simple: "05.56"},
        {full: "05.5720161001", simple: "05.57"},
        {full: "05.5820161101", simple: "05.58"},
        {full: "05.5920170418", simple: "05.59"},
        {full: "06.0020210715", simple: "06.00"},
        {full: "06.0120220520", simple: "06.01"},
        {full: "06.0220230206", simple: "06.02"},
        {full: "06.02.120231031", simple: "06.02.1"},
        {full: "06.02.220241118", simple: "06.02.2"},
        {full: "06.02.320250123", simple: "06.02.3"}
    ]
    
    return $version_map
}

# Get simple version from full version
def get_simple_version [full_version: string] {
    let mapping = (create_version_mapping)
    let result = ($mapping | where full == $full_version | first)
    if ($result | is-empty) {
        error make {msg: $"Version not found: ($full_version)"}
    } else {
        return $result.simple
    }
}

# Get full version from simple version  
def get_full_version [simple_version: string] {
    let mapping = (create_version_mapping)
    let result = ($mapping | where simple == $simple_version | first)
    if ($result | is-empty) {
        error make {msg: $"Simple version not found: ($simple_version)"}
    } else {
        return $result.full
    }
}

# Main function to show mapping
def main [] {
    print "Formex Version Mapping (Full -> Simple):"
    print "========================================"
    let mapping = (create_version_mapping)
    $mapping | each {|row| $"($row.full) -> ($row.simple)"}
}

# Export functions for use in other scripts
export def "mapping get-simple" [full_version: string] {
    get_simple_version $full_version
}

export def "mapping get-full" [simple_version: string] {
    get_full_version $simple_version
}

export def "mapping list" [] {
    create_version_mapping
}