<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Mode template for use in main transformer -->
    <xsl:template match="ACT" mode="final-provisions-mode">
        <finalProvisions>
            <xsl:choose>
                <xsl:when test="FINAL">
                    <xsl:apply-templates select="FINAL" mode="final-provisions"/>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Error if no final provisions found -->
                    <xsl:message terminate="yes">ERROR: No FINAL element found - final provisions are required</xsl:message>
                </xsl:otherwise>
            </xsl:choose>
        </finalProvisions>
    </xsl:template>

    <!-- Process final provisions -->
    <xsl:template match="FINAL" mode="final-provisions">
        <!-- Optional: paragraphs (exclude P elements that are inside SIGNATORY) -->
        <xsl:for-each select="descendant::*[self::P or self::AL or self::NP or self::TXT][not(ancestor::SIGNATORY)]">
            <paragraphs><xsl:apply-templates select="." mode="text-content"/></paragraphs>
        </xsl:for-each>
        
        <!-- Required: signatureBlock -->
        <signatureBlock>
            <!-- Required: placeAndDate -->
            <placeAndDate>
                <xsl:choose>
                    <xsl:when test="descendant::PL.DATE/P">
                        <xsl:apply-templates select="descendant::PL.DATE/P[1]" mode="mixed-content"/>
                    </xsl:when>
                    <xsl:when test="descendant::*[contains(local-name(), 'PLACE')] and descendant::DATE[@ISO]">
                        <content type="text">
                            <value><xsl:value-of select="(descendant::*[contains(local-name(), 'PLACE')])[1]"/></value>
                        </content>
                        <content type="date">
                            <value iso="{(descendant::DATE[@ISO])[1]/@ISO}"><xsl:value-of select="(descendant::DATE[@ISO])[1]/text()"/></value>
                        </content>
                    </xsl:when>
                    <xsl:when test="descendant::DATE[@ISO]">
                        <content type="date">
                            <value iso="{(descendant::DATE[@ISO])[1]/@ISO}"><xsl:value-of select="(descendant::DATE[@ISO])[1]/text()"/></value>
                        </content>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: No place or date information found in FINAL element for placeAndDate</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </placeAndDate>
            
            <!-- Optional: signatoryInfo -->
            <xsl:for-each select="descendant::SIGNATORY">
                <signatoryInfo>
                    <xsl:apply-templates select="." mode="mixed-content"/>
                </signatoryInfo>
            </xsl:for-each>
        </signatureBlock>
    </xsl:template>

</xsl:stylesheet>