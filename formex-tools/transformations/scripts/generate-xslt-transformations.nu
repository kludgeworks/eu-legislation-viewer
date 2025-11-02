#!/usr/bin/env nu

# Generate XSLT transformations based on DOC changes analysis
# This script creates step-by-step XSLT files for each version transition

def main [] {
    let analysis_file = "doc-changes-analysis.json"
    let xslt_dir = "../xslt/step-by-step"
    
    print "🔧 Generating XSLT transformations from DOC changes analysis..."
    
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
        let xslt_filename = $"($from_short)_to_($to_short).xsl"
        let xslt_path = $"($xslt_dir)/($xslt_filename)"
        
        print $"📄 Generating ($xslt_filename)..."
        
        # Generate XSLT content
        let xslt_content = (generate_xslt_content $from_version $to_version $changes)
        
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
def generate_xslt_content [from_version: string, to_version: string, changes: list] {
    let from_schema_url = $"http://formex.publications.europa.eu/schema/($from_version).xd"
    let to_schema_url = $"http://formex.publications.europa.eu/schema/($to_version).xd"
    
    # Analyze changes to determine transformation rules
    let transformation_rules = (analyze_changes_for_xslt $changes)
    
    # Build XSLT content
    let xslt_header = '<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
    exclude-result-prefixes="fmx">
    
    <!-- 
        Formex DOC Transformation: ' + $from_version + ' to ' + $to_version + '
        Generated automatically from diff analysis
        
        This transformation updates DOC elements and schema references
        from ' + $from_version + ' to ' + $to_version + '
    -->
    
    <!-- Import base utilities -->
    <xsl:import href="../utils/identity.xsl"/>
    <xsl:import href="../utils/schema-updater.xsl"/>
    <xsl:import href="../utils/common-patterns.xsl"/>
    
    <!-- Schema URL parameters -->
    <xsl:param name="old-schema-url" select="&apos;' + $from_schema_url + '&apos;"/>
    <xsl:param name="new-schema-url" select="&apos;' + $to_schema_url + '&apos;"/>
    
    <!-- Update schema location in DOC root element -->
    <xsl:template match="fmx:DOC">
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

# Analyze changes to generate specific XSLT transformation rules
def analyze_changes_for_xslt [changes: list] {
    mut rules = []
    
    # Look for specific patterns in changes
    for change in $changes {
        let content = $change.content
        let change_type = $change.type
        
        # Handle NO.DOC cardinality changes (01.00 -> 01.01)
        let has_no_doc = ($content | str contains "NO.DOC")
        let has_max_occurs = ($content | str contains "maxOccurs")
        if ($has_no_doc and $has_max_occurs) {
            if $change_type == "addition" {
                let rule = "<!-- NO.DOC cardinality updated to unbounded -->
    <!-- No transformation needed - structure remains the same -->"
                $rules = ($rules | append $rule)
            }
        }
        
        # Handle gr.styles attribute group removals
        let has_gr_styles = ($content | str contains "gr.styles")
        if ($has_gr_styles and $change_type == "removal") {
            let rule = "<!-- Remove gr.styles attribute group references -->
    <xsl:template match=\"@*[local-name() = 'STYLE' or local-name() = 'CLASS']\">
        <!-- Removed deprecated style attributes -->
    </xsl:template>"
            $rules = ($rules | append $rule)
        }
        
        # Handle element type simplifications (e.g., NO.DOC.C)
        let has_no_doc_c = ($content | str contains "NO.DOC.C")
        let has_string_type = ($content | str contains "type=\"xd:string")
        if ($has_no_doc_c and $has_string_type and $change_type == "addition") {
            let rule = "<!-- NO.DOC.C simplified to string type -->
    <xsl:template match=\"fmx:NO.DOC.C\">
        <xsl:copy>
            <xsl:value-of select=\".\"/>
        </xsl:copy>
    </xsl:template>"
            $rules = ($rules | append $rule)
        }
        
        # Handle new document types (e.g., LSEU in 5.24 -> 5.30)
        let has_lseu = ($content | str contains "LSEU")
        if ($has_lseu and $change_type == "addition") {
            let rule = "<!-- Support for new LSEU document type -->
    <!-- New document type - no transformation needed for existing documents -->"
            $rules = ($rules | append $rule)
        }
    }
    
    # If no specific rules were found, add a default comment
    if ($rules | length) == 0 {
        $rules = ($rules | append "<!-- No specific DOC element transformations required -->")
    }
    
    $rules
}