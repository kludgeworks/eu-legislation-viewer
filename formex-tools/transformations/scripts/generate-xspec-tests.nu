#!/usr/bin/env nu

# Generate XSpec test files for all XSLT transformations
# This script creates test files to validate each transformation

def main [] {
    let xslt_dir = "../xslt/step-by-step"
    let xspec_dir = "../tests/xspec"
    
    print "🧪 Generating XSpec test files for all XSLT transformations..."
    
    # Get all XSLT files
    let xslt_files = (ls $xslt_dir | where name ends-with ".xsl" | sort-by name)
    
    print $"Found ($xslt_files | length) XSLT files to create tests for..."
    
    # Generate XSpec test for each XSLT file
    $xslt_files | each { |xslt_file|
        let xslt_filename = ($xslt_file.name | path basename)
        let test_filename = ($xslt_filename | str replace ".xsl" ".xspec")
        let test_path = $"($xspec_dir)/($test_filename)"
        
        # Extract version information from filename
        let version_parts = ($xslt_filename | str replace ".xsl" "" | split row "_to_")
        let from_version = ($version_parts | get 0)
        let to_version = ($version_parts | get 1)
        
        print $"📄 Creating test for ($xslt_filename)..."
        
        # Generate XSpec content
        let xspec_content = (generate_xspec_content $xslt_filename $from_version $to_version)
        
        # Write XSpec file
        $xspec_content | save -f $test_path
        
        print $"✅ Created ($test_path)"
    }
    
    print "🎉 All XSpec test files generated successfully!"
}

# Generate XSpec test content for a transformation
def generate_xspec_content [xslt_filename: string, from_version: string, to_version: string] {
    let xspec_content = '<?xml version="1.0" encoding="UTF-8"?>
<x:description xmlns:x="http://www.jenitennison.com/xslt/xspec"
               xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
               stylesheet="../xslt/step-by-step/' + $xslt_filename + '">
    
    <x:doc>
        XSpec tests for Formex DOC transformation: ' + $from_version + ' to ' + $to_version + '
        
        These tests verify that the transformation correctly:
        1. Updates schema references
        2. Preserves DOC structure
        3. Applies version-specific changes
        4. Maintains data integrity
    </x:doc>
    
    <!-- Test 1: Basic DOC transformation with schema update -->
    <x:scenario label="Transform simple DOC with schema update">
        <x:doc>
            Tests that a minimal DOC element gets its schema reference updated
            while preserving all content and structure.
        </x:doc>
        
        <x:context>
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-' + $from_version + '.xd">
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
                     xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-' + $to_version + '.xd">
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
    
    <!-- Test 2: DOC with multiple NO.DOC elements (if applicable) -->
    <x:scenario label="Transform DOC with multiple NO.DOC elements">
        <x:doc>
            Tests handling of multiple NO.DOC elements, particularly relevant
            for versions where NO.DOC cardinality changed from single to unbounded.
        </x:doc>
        
        <x:context>
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-' + $from_version + '.xd">
                <fmx:BIB.INSTANCE>
                    <fmx:BIB.DOC>
                        <fmx:NO.DOC>32004R0001</fmx:NO.DOC>
                        <fmx:NO.DOC>32004R0002</fmx:NO.DOC>
                        <fmx:DT.DOC>2004-01-01</fmx:DT.DOC>
                    </fmx:BIB.DOC>
                </fmx:BIB.INSTANCE>
                <fmx:WORK.DOC>
                    <fmx:ENACTING.TERMS>
                        <fmx:P>Test content with multiple document numbers</fmx:P>
                    </fmx:ENACTING.TERMS>
                </fmx:WORK.DOC>
            </fmx:DOC>
        </x:context>
        
        <x:expect label="Multiple NO.DOC elements should be preserved">
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-' + $to_version + '.xd">
                <fmx:BIB.INSTANCE>
                    <fmx:BIB.DOC>
                        <fmx:NO.DOC>32004R0001</fmx:NO.DOC>
                        <fmx:NO.DOC>32004R0002</fmx:NO.DOC>
                        <fmx:DT.DOC>2004-01-01</fmx:DT.DOC>
                    </fmx:BIB.DOC>
                </fmx:BIB.INSTANCE>
                <fmx:WORK.DOC>
                    <fmx:ENACTING.TERMS>
                        <fmx:P>Test content with multiple document numbers</fmx:P>
                    </fmx:ENACTING.TERMS>
                </fmx:WORK.DOC>
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
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-' + $from_version + '.xd"/>
        </x:context>
        
        <x:expect label="Empty DOC should get schema updated">
            <fmx:DOC xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://formex.publications.europa.eu/schema/formex formex-' + $to_version + '.xd"/>
        </x:expect>
    </x:scenario>
    
    <!-- Test 4: Identity transformation for non-DOC elements -->
    <x:scenario label="Non-DOC elements should pass through unchanged">
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
    </x:scenario>

</x:description>'
    
    $xspec_content
}