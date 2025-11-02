#!/usr/bin/env nu

# Generate XSpec test files for all XSLT transformations
# This script creates test files to validate each transformation
# Supports any Formex root element: DOC, GENERAL, ACT, ANNEX, etc.

def main [
    element_type: string    # Root element to generate tests for
] {
    # Validate input
    if ($element_type | str length) == 0 {
        print "❌ Error: element_type is required"
        print "Usage: generate-xspec-tests.nu <element_type>"
        print "Example: generate-xspec-tests.nu DOC"
        print "Example: generate-xspec-tests.nu GENERAL"
        exit 1
    }
    
    let elem_upper = ($element_type | str upcase)
    let elem_lower = ($element_type | str downcase)
    let xslt_dir = $"../xslt/($elem_lower)"
    let xspec_dir = $"../tests/xspec/($elem_lower)"
    
    print $"🧪 Generating XSpec test files for ($elem_upper) transformations..."
    
    # Check if XSLT directory exists
    if not ($xslt_dir | path exists) {
        print $"❌ Error: XSLT directory not found: ($xslt_dir)"
        print "Please run: generate-xslt-transformations.nu $element_type"
        exit 1
    }
    
    # Create output directory if it doesn't exist
    if not ($xspec_dir | path exists) {
        mkdir $xspec_dir
        print $"📁 Created directory: ($xspec_dir)"
    }
    
    # Get all XSLT files
    let xslt_files = (ls $xslt_dir | where name ends-with ".xsl" | sort-by name)
    
    print $"Found ($xslt_files | length) XSLT files to create tests for..."
    
    # Generate XSpec test for each XSLT file
    $xslt_files | each { |xslt_file|
        let xslt_filename = ($xslt_file.name | path basename)
        let test_filename = ($xslt_filename | str replace ".xsl" ".xspec")
        let test_path = $"($xspec_dir)/($test_filename)"
        
        # Extract version information from filename
        let version_parts = ($xslt_filename | str replace ".xsl" "" | str replace "v" "")
        let to_version = $version_parts
        
        print $"📄 Creating test for ($xslt_filename)..."
        
        # Generate XSpec content
        let xspec_content = (generate_xspec_content $elem_upper $elem_lower $xslt_filename $to_version)
        
        # Write XSpec file
        $xspec_content | save -f $test_path
        
        print $"✅ Created ($test_path)"
    }
    
    print "🎉 All XSpec test files generated successfully!"
}

# Generate XSpec test content for a transformation
def generate_xspec_content [element_type: string, element_lower: string, xslt_filename: string, to_version: string] {
    let xslt_relpath = $"../xslt/($element_lower)/($xslt_filename)"
    
    # Generate element-specific test scenarios
    let test_scenarios = match $element_type {
        "DOC" => (generate_doc_test_scenarios $to_version),
        "GENERAL" => (generate_general_test_scenarios $to_version),
        _ => (generate_generic_test_scenarios $element_type $to_version)
    }
    
    let xspec_content = $'<?xml version="1.0" encoding="UTF-8"?>
<x:description xmlns:x="http://www.jenitennison.com/xslt/xspec"
               xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
               stylesheet="($xslt_relpath)">
    
    <x:doc>
        XSpec tests for Formex ($element_type) transformation: v($to_version)
        
        These tests verify that the transformation correctly:
        1. Updates schema references
        2. Preserves ($element_type) structure
        3. Applies version-specific changes
        4. Maintains data integrity
    </x:doc>
    
($test_scenarios)

</x:description>'
    
    $xspec_content
}

# Generate DOC-specific test scenarios
def generate_doc_test_scenarios [to_version: string] {
    $'    <!-- Test 1: Basic DOC transformation with schema update -->
    <x:scenario label="Transform simple DOC with schema update">
        <x:doc>
            Tests that a minimal DOC element gets its schema reference updated
            while preserving all content and structure.
        </x:doc>
        
        <x:context>
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-old.xd">
                <fmx:BIB.INSTANCE>
                    <fmx:BIB.DOC>
                        <fmx:NO.DOC>32004R0001</fmx:NO.DOC>
                        <fmx:DT.DOC>2004-01-01</fmx:DT.DOC>
                    </fmx:BIB.DOC>
                </fmx:BIB.INSTANCE>
                <fmx:WORK.DOC>
                    <fmx:ENACTING.TERMS>
                        <fmx:P>Test content</fmx:P>
                    </fmx:ENACTING.TERMS>
                </fmx:WORK.DOC>
            </fmx:DOC>
        </x:context>
        
        <x:expect label="Schema location should be updated">
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-v($to_version).xd">
                <fmx:BIB.INSTANCE>
                    <fmx:BIB.DOC>
                        <fmx:NO.DOC>32004R0001</fmx:NO.DOC>
                        <fmx:DT.DOC>2004-01-01</fmx:DT.DOC>
                    </fmx:BIB.DOC>
                </fmx:BIB.INSTANCE>
                <fmx:WORK.DOC>
                    <fmx:ENACTING.TERMS>
                        <fmx:P>Test content</fmx:P>
                    </fmx:ENACTING.TERMS>
                </fmx:WORK.DOC>
            </fmx:DOC>
        </x:expect>
    </x:scenario>
    
    <!-- Test 2: DOC with multiple NO.DOC elements -->
    <x:scenario label="Transform DOC with multiple NO.DOC elements">
        <x:doc>
            Tests handling of multiple NO.DOC elements, particularly relevant
            for versions where NO.DOC cardinality changed from single to unbounded.
        </x:doc>
        
        <x:context>
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex">
                <fmx:BIB.INSTANCE>
                    <fmx:BIB.DOC>
                        <fmx:NO.DOC>32004R0001</fmx:NO.DOC>
                        <fmx:NO.DOC>32004R0002</fmx:NO.DOC>
                        <fmx:DT.DOC>2004-01-01</fmx:DT.DOC>
                    </fmx:BIB.DOC>
                </fmx:BIB.INSTANCE>
            </fmx:DOC>
        </x:context>
        
        <x:expect label="Multiple NO.DOC elements should be preserved">
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex">
                <fmx:BIB.INSTANCE>
                    <fmx:BIB.DOC>
                        <fmx:NO.DOC>32004R0001</fmx:NO.DOC>
                        <fmx:NO.DOC>32004R0002</fmx:NO.DOC>
                        <fmx:DT.DOC>2004-01-01</fmx:DT.DOC>
                    </fmx:BIB.DOC>
                </fmx:BIB.INSTANCE>
            </fmx:DOC>
        </x:expect>
    </x:scenario>
    
    <!-- Test 3: Empty DOC element -->
    <x:scenario label="Transform empty DOC element">
        <x:doc>
            Tests that empty DOC elements are handled correctly,
            ensuring the transformation does not break on minimal content.
        </x:doc>
        
        <x:context>
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex"/>
        </x:context>
        
        <x:expect label="Empty DOC should be preserved">
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex"/>
        </x:expect>
    </x:scenario>
    
    <!-- Test 4: Identity transformation for non-DOC elements -->
    <x:scenario label="Non-DOC elements pass through unchanged">
        <x:doc>
            Tests that elements other than DOC are passed through unchanged
            by the identity transformation base.
        </x:doc>
        
        <x:context>
            <fmx:WORK.DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex">
                <fmx:ENACTING.TERMS>
                    <fmx:P>This should pass through unchanged</fmx:P>
                </fmx:ENACTING.TERMS>
            </fmx:WORK.DOC>
        </x:context>
        
        <x:expect label="Non-DOC elements unchanged">
            <fmx:WORK.DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex">
                <fmx:ENACTING.TERMS>
                    <fmx:P>This should pass through unchanged</fmx:P>
                </fmx:ENACTING.TERMS>
            </fmx:WORK.DOC>
        </x:expect>
    </x:scenario>'
}

# Generate GENERAL-specific test scenarios
def generate_general_test_scenarios [to_version: string] {
    $'    <!-- Test 1: Basic GENERAL transformation with schema update -->
    <x:scenario label="Transform simple GENERAL with schema update">
        <x:doc>
            Tests that a minimal GENERAL element gets its schema reference updated
            while preserving all content and structure.
        </x:doc>
        
        <x:context>
            <fmx:GENERAL xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-old.xd">
                <fmx:BIB.INSTANCE/>
                <fmx:CONTENTS/>
            </fmx:GENERAL>
        </x:context>
        
        <x:expect label="Schema location should be updated">
            <fmx:GENERAL xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-v($to_version).xd">
                <fmx:BIB.INSTANCE/>
                <fmx:CONTENTS/>
            </fmx:GENERAL>
        </x:expect>
    </x:scenario>
    
    <!-- Test 2: GENERAL with optional elements -->
    <x:scenario label="Transform GENERAL with optional document titles">
        <x:doc>
            Tests handling of optional title elements in GENERAL,
            which became optional in version 06.02.1.
        </x:doc>
        
        <x:context>
            <fmx:GENERAL xmlns:fmx="http://formex.publications.europa.eu/schema/formex">
                <fmx:BIB.INSTANCE/>
                <fmx:TITLE>Document Title</fmx:TITLE>
                <fmx:CURR.TITLE>Current Title</fmx:CURR.TITLE>
                <fmx:CONTENTS/>
            </fmx:GENERAL>
        </x:context>
        
        <x:expect label="Optional title elements should be preserved">
            <fmx:GENERAL xmlns:fmx="http://formex.publications.europa.eu/schema/formex">
                <fmx:BIB.INSTANCE/>
                <fmx:TITLE>Document Title</fmx:TITLE>
                <fmx:CURR.TITLE>Current Title</fmx:CURR.TITLE>
                <fmx:CONTENTS/>
            </fmx:GENERAL>
        </x:expect>
    </x:scenario>
    
    <!-- Test 3: Empty GENERAL element -->
    <x:scenario label="Transform empty GENERAL element">
        <x:doc>
            Tests that empty GENERAL elements are handled correctly,
            ensuring the transformation does not break on minimal content.
        </x:doc>
        
        <x:context>
            <fmx:GENERAL xmlns:fmx="http://formex.publications.europa.eu/schema/formex"/>
        </x:context>
        
        <x:expect label="Empty GENERAL should be preserved">
            <fmx:GENERAL xmlns:fmx="http://formex.publications.europa.eu/schema/formex"/>
        </x:expect>
    </x:scenario>
    
    <!-- Test 4: Identity transformation for non-GENERAL elements -->
    <x:scenario label="Non-GENERAL elements pass through unchanged">
        <x:doc>
            Tests that elements other than GENERAL are passed through unchanged
            by the identity transformation base.
        </x:doc>
        
        <x:context>
            <fmx:CONTENTS xmlns:fmx="http://formex.publications.europa.eu/schema/formex">
                <fmx:P>This should pass through unchanged</fmx:P>
            </fmx:CONTENTS>
        </x:context>
        
        <x:expect label="Non-GENERAL elements unchanged">
            <fmx:CONTENTS xmlns:fmx="http://formex.publications.europa.eu/schema/formex">
                <fmx:P>This should pass through unchanged</fmx:P>
            </fmx:CONTENTS>
        </x:expect>
    </x:scenario>'
}

# Generate generic test scenarios for any element type
def generate_generic_test_scenarios [element_type: string, to_version: string] {
    $'    <!-- Test 1: Basic ($element_type) transformation with schema update -->
    <x:scenario label="Transform simple ($element_type) with schema update">
        <x:doc>
            Tests that the ($element_type) element gets its schema reference updated
            while preserving all content and structure.
        </x:doc>
        
        <x:context>
            <fmx:($element_type) xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-old.xd"/>
        </x:context>
        
        <x:expect label="Schema location should be updated">
            <fmx:($element_type) xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-v($to_version).xd"/>
        </x:expect>
    </x:scenario>
    
    <!-- Test 2: Empty ($element_type) element -->
    <x:scenario label="Transform empty ($element_type) element">
        <x:doc>
            Tests that empty ($element_type) elements are handled correctly,
            ensuring the transformation does not break on minimal content.
        </x:doc>
        
        <x:context>
            <fmx:($element_type) xmlns:fmx="http://formex.publications.europa.eu/schema/formex"/>
        </x:context>
        
        <x:expect label="Empty ($element_type) should be preserved">
            <fmx:($element_type) xmlns:fmx="http://formex.publications.europa.eu/schema/formex"/>
        </x:expect>
    </x:scenario>'
}
