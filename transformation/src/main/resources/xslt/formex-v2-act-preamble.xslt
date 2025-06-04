<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Mode template for use in main transformer -->
    <xsl:template match="ACT" mode="preamble-mode">
        <preamble>
            <!-- Required: initiator -->
            <initiator>
                <xsl:choose>
                    <xsl:when test="PREAMBLE/PREAMBLE.INIT">
                        <xsl:value-of select="PREAMBLE/PREAMBLE.INIT"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required element PREAMBLE/PREAMBLE.INIT not found for initiator</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </initiator>
            
            <!-- Optional: visas -->
            <xsl:for-each select="PREAMBLE/GR.VISA/VISA">
                <visas>
                    <xsl:apply-templates select="." mode="mixed-content"/>
                </visas>
            </xsl:for-each>
            
            <!-- Optional: considerations -->
            <xsl:for-each select="PREAMBLE/GR.CONSID/CONSID">
                <considerations number="{normalize-space(NP/NO.P)}">
                    <content>
                        <xsl:apply-templates select="NP/TXT" mode="mixed-content"/>
                    </content>
                </considerations>
            </xsl:for-each>
            
            <!-- Required: finalStatement -->
            <finalStatement>
                <xsl:choose>
                    <xsl:when test="PREAMBLE/PREAMBLE.FINAL">
                        <xsl:value-of select="PREAMBLE/PREAMBLE.FINAL"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required element PREAMBLE/PREAMBLE.FINAL not found for finalStatement</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </finalStatement>
        </preamble>
    </xsl:template>

</xsl:stylesheet>