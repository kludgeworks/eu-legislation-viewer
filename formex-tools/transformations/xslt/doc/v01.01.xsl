<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
    exclude-result-prefixes="fmx">
    
    <!-- 
        Formex DOC Transformation: formex-01.00-20040501 to formex-01.01-20040501
        Generated automatically from diff analysis
        
        This transformation updates DOC elements and schema references
        from formex-01.00-20040501 to formex-01.01-20040501
    -->
    
    <!-- Import base utilities -->
    <xsl:import href="../shared/identity.xsl"/>
    <xsl:import href="../shared/schema-updater.xsl"/>
    <xsl:import href="../shared/common-patterns.xsl"/>
    
    <!-- Schema URL parameters -->
    <xsl:param name="old-schema-url" select="&apos;http://formex.publications.europa.eu/schema/formex-01.00-20040501.xd&apos;"/>
    <xsl:param name="new-schema-url" select="&apos;http://formex.publications.europa.eu/schema/formex-01.01-20040501.xd&apos;"/>
    
    <!-- Update schema location in DOC root element -->
    <xsl:template match="DOC">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:call-template name="update-schema-location">
                <xsl:with-param name="old-url" select="$old-schema-url"/>
                <xsl:with-param name="new-url" select="$new-schema-url"/>
            </xsl:call-template>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>
    <!-- NO.DOC cardinality updated to unbounded -->
    <!-- No transformation needed - structure remains the same -->
    <!-- Remove gr.styles attribute group references -->
    <xsl:template match="@*[local-name() = 'STYLE' or local-name() = 'CLASS']">
        <!-- Removed deprecated style attributes -->
    </xsl:template>
    <!-- NO.DOC.C simplified to string type -->
    <xsl:template match="NO.DOC.C">
        <xsl:copy>
            <xsl:value-of select="."/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>