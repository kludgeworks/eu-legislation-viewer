#!/usr/bin/env nu

# Generate XSLT transformations based on element changes analysis
# This script creates step-by-step XSLT files for each version transition
# Supports any Formex root element: DOC, GENERAL, ACT, ANNEX, etc.

def main [
    element_type: string    # Root element to generate transformations for
] {
    # Validate input
    if ($element_type | str length) == 0 {
        print "❌ Error: element_type is required"
        print "Usage: generate-xslt-transformations.nu <element_type>"
        print "Example: generate-xslt-transformations.nu DOC"
        print "Example: generate-xslt-transformations.nu GENERAL"
        exit 1
    }
    
    let elem_upper = ($element_type | str upcase)
    let elem_lower = ($element_type | str downcase)
    let analysis_file = $"($elem_lower)-changes-analysis.json"
    let xslt_dir = $"../xslt/($elem_lower)"
    
    print $"🔧 Generating XSLT transformations for ($elem_upper) from changes analysis..."
    
    # Check if analysis file exists
    if not ($analysis_file | path exists) {
        print $"❌ Error: Analysis file not found: ($analysis_file)"
        print "Please run: analyze-element-changes.nu $element_type"
        exit 1
    }
    
    # Create output directory if it doesn't exist
    if not ($xslt_dir | path exists) {
        mkdir $xslt_dir
        print $"📁 Created directory: ($xslt_dir)"
    }
    
    # Load the analysis data
    let changes_data = (open $analysis_file)
    
    print $"Processing ($changes_data | length) version transitions..."
    
    # Generate XSLT for each version transition
    $changes_data | each { |version_data|
        let from_version = $version_data.from_version
        let to_version = $version_data.to_version
        let changes = $version_data.changes
        
        # Extract version numbers for filename
        let from_short = (extract_version_short $from_version)
        let to_short = (extract_version_short $to_version)
        let xslt_filename = $"v($to_short).xsl"
        let xslt_path = $"($xslt_dir)/($xslt_filename)"
        
        print $"📄 Generating ($xslt_filename)..."
        
        # Generate XSLT content
        let xslt_content = (generate_xslt_content $elem_upper $elem_lower $from_version $to_version $changes)
        
        # Write XSLT file
        $xslt_content | save -f $xslt_path
        
        print $"✅ Created ($xslt_path)"
    }
    
    print "🎉 All XSLT transformations generated successfully!"
}

# Extract short version identifier for filenames
def extract_version_short [version: string] {
    $version | str replace "formex-" "" | str replace -a "-" ""
}

# Generate XSLT content for a version transition
def generate_xslt_content [element_type: string, element_lower: string, from_version: string, to_version: string, changes: list] {
    let from_schema_url = $"http://formex.publications.europa.eu/schema/($from_version).xd"
    let to_schema_url = $"http://formex.publications.europa.eu/schema/($to_version).xd"
    
    # Analyze changes to determine transformation rules
    let transformation_rules = (analyze_changes_for_xslt $element_type $changes)
    
    # Build XSLT content
    let xslt_header = $'<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
    exclude-result-prefixes="fmx">
    
    <!-- 
        Formex ($element_type) Transformation: ($from_version) to ($to_version)
        Generated automatically from diff analysis
        
        This transformation updates ($element_type) elements and schema references
        from ($from_version) to ($to_version)
    -->
    
    <!-- Import base utilities -->
    <xsl:import href="../shared/identity.xsl"/>
    <xsl:import href="../shared/schema-updater.xsl"/>
    <xsl:import href="../shared/common-patterns.xsl"/>
    
    <!-- Schema URL parameters -->
    <xsl:param name="old-schema-url" select="'\''($from_schema_url)'\''"/>
    <xsl:param name="new-schema-url" select="'\''($to_schema_url)'\''"/>
    
    <!-- Update schema location in ($element_type) root element -->
    <xsl:template match="($element_type)">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:call-template name="update-schema-location">
                <xsl:with-param name="old-url" select="$old-schema-url"/>
                <xsl:with-param name="new-url" select="$new-schema-url"/>
            </xsl:call-template>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>
'
    
    # Add specific transformation rules based on changes (deduplicated)
    let specific_rules = ($transformation_rules | uniq | str join "\n    ")
    
    let xslt_footer = "
</xsl:stylesheet>"
    
    $"($xslt_header)    ($specific_rules)($xslt_footer)"
}

# Analyze changes to generate specific XSLT transformation rules for element type
def analyze_changes_for_xslt [element_type: string, changes: list] {
    mut rules = []
    
    # Element-specific pattern matching
    let patterns = match $element_type {
        "DOC" => {
            no_doc: "NO.DOC",
            work_doc: "WORK.DOC",
            bib: "BIB.DOC",
            styles: "gr.styles"
        },
        "GENERAL" => {
            bib: "BIB.INSTANCE",
            title: "TITLE",
            prolog: "PROLOG",
            contents: "CONTENTS"
        },
        _ => {
            element: $element_type
        }
    }
    
    # Look for specific patterns in changes
    for change in $changes {
        let content = $change.content
        let change_type = $change.type
        
        # Universal pattern: schema location changes
        if ($content | str contains "schemaLocation") {
            # Schema location updates are handled by main template
            continue
        }
        
        # DOC-specific patterns
        if $element_type == "DOC" {
            # Handle NO.DOC cardinality changes
            if ($content | str contains "NO.DOC") and ($content | str contains "maxOccurs") {
                if $change_type == "addition" {
                    let rule = "<!-- NO.DOC cardinality updated to unbounded -->
    <!-- No transformation needed - structure remains the same -->"
                    $rules = ($rules | append $rule)
                }
            }
            
            # Handle gr.styles attribute group removals
            if ($content | str contains "gr.styles") and $change_type == "removal" {
                let rule = "<!-- Remove gr.styles attribute group references -->
    <xsl:template match=\"@*[local-name() = 'STYLE' or local-name() = 'CLASS']\">
        <!-- Removed deprecated style attributes -->
    </xsl:template>"
                $rules = ($rules | append $rule)
            }
        }
        
        # GENERAL-specific patterns
        if $element_type == "GENERAL" {
            # Handle SIGNATURE changes (v06.02 → v06.02.1)
            if ($content | str contains "SIGNATURE") and $change_type == "removal" {
                let rule = "<!-- SIGNATURE element removed from GENERAL model -->
    <xsl:template match=\"fmx:SIGNATURE[parent::fmx:GENERAL]\">
        <!-- Removed in this version -->
    </xsl:template>"
                $rules = ($rules | append $rule)
            }
            
            # Handle BIB.REF removal
            if ($content | str contains "BIB.REF") and ($content | str contains "GENERAL") and $change_type == "removal" {
                let rule = "<!-- BIB.REF element removed from GENERAL model -->
    <xsl:template match=\"fmx:BIB.REF[parent::fmx:GENERAL]\">
        <!-- Removed in this version -->
    </xsl:template>"
                $rules = ($rules | append $rule)
            }
            
            # Handle element title optional changes
            if ($content | str contains "TITLE") and ($content | str contains "minOccurs") {
                let rule = "<!-- Document titles changed to optional in GENERAL -->"
                $rules = ($rules | append $rule)
            }
        }
    }
    
    # If no specific rules were found, add a default comment
    if ($rules | length) == 0 {
        $rules = ($rules | append $"<!-- No specific ($element_type) element transformations required -->")
    }
    
    $rules
}
