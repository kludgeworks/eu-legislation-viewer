<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <!-- Import common templates first (lowest precedence) -->
    <xsl:import href="formex-v2-common-templates.xslt"/>
    
    <!-- Import specialized transformers (higher precedence for domain-specific overrides) -->
    <xsl:import href="formex-v2-doc-metadata.xslt"/>
    <xsl:import href="formex-v2-act-preamble.xslt"/>
    <xsl:import href="formex-v2-act-articles.xslt"/>
    <xsl:import href="formex-v2-final-provisions.xslt"/>
    <xsl:import href="formex-v2-annex-sections.xslt"/>

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Main template for processing combined documents -->
    <xsl:template match="/">
        <SimplifiedLegislationDoc>
            <xsl:variable name="docElement" select="//DOC"/>
            <xsl:variable name="actElement" select="//ACT"/>
            <xsl:variable name="annexElements" select="//ANNEX"/>
            
            <!-- Extract metadata from DOC element using imported transformer -->
            <xsl:apply-templates select="$docElement">
                <xsl:with-param name="actElement" select="$actElement"/>
            </xsl:apply-templates>
            
            <!-- Extract body content from ACT and ANNEX elements -->
            <body>
                <!-- Extract preamble from ACT element -->
                <xsl:apply-templates select="$actElement" mode="preamble-mode"/>
                
                <!-- Extract articles from ACT element -->  
                <xsl:apply-templates select="$actElement" mode="articles-mode"/>
                
                <!-- Extract final provisions from ACT element -->
                <xsl:apply-templates select="$actElement" mode="final-provisions-mode"/>
                
                <!-- Extract annexes from ANNEX elements -->
                <xsl:if test="$annexElements">
                    <xsl:apply-templates select="." mode="annexes-mode"/>
                </xsl:if>
            </body>
        </SimplifiedLegislationDoc>
    </xsl:template>

</xsl:stylesheet>