<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
    exclude-result-prefixes="fmx">
    
    <!-- 
        Formex DOC Transformation: formex-05.53l-20140201 to formex-05.54-20140601
        Generated automatically from diff analysis
        
        This transformation updates DOC elements and schema references
        from formex-05.53l-20140201 to formex-05.54-20140601
    -->
    
    <!-- Import base utilities -->
    <xsl:import href="../shared/identity.xsl"/>
    <xsl:import href="../shared/schema-updater.xsl"/>
    <xsl:import href="../shared/common-patterns.xsl"/>
    
    <!-- Schema URL parameters -->
    <xsl:param name="old-schema-url" select="&apos;http://formex.publications.europa.eu/schema/formex-05.53l-20140201.xd&apos;"/>
    <xsl:param name="new-schema-url" select="&apos;http://formex.publications.europa.eu/schema/formex-05.54-20140601.xd&apos;"/>
    
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
    <!-- No specific DOC element transformations required -->
</xsl:stylesheet>