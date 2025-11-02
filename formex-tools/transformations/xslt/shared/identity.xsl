<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    
    <!-- 
        Identity Transformation Template
        
        This is a base identity transformation that copies all elements, 
        attributes, and text nodes unchanged. This serves as the foundation 
        for all Formex DOC transformations.
        
        Usage: Import this stylesheet and override specific templates
        as needed for version-specific transformations.
    -->
    
    <!-- Identity template: copy everything by default -->
    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>
    
    <!-- Preserve XML declarations and processing instructions -->
    <xsl:template match="processing-instruction() | comment()">
        <xsl:copy/>
    </xsl:template>
    
    <!-- Output configuration -->
    <xsl:output method="xml" 
                encoding="UTF-8" 
                indent="yes" 
                omit-xml-declaration="no"/>
    
</xsl:stylesheet>