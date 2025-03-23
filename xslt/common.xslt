<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Handle paragraphs -->
    <xsl:template match="P">
        <p><xsl:apply-templates/></p>
    </xsl:template>

    <!-- Default template for text nodes -->
    <xsl:template match="text()">
        <xsl:value-of select="."/>
    </xsl:template>

    <!-- Generic template for any other elements that need to be processed -->
    <xsl:template match="*" mode="generic-process">
        <xsl:apply-templates/>
    </xsl:template>

    <!-- Utility template for debugging - can be called to inspect element structure -->
    <xsl:template name="debug-element">
        <xsl:param name="element"/>
        <xsl:param name="label" select="'Debug'"/>

        <div style="border: 1px solid red; padding: 5px; margin: 5px;">
            <p><strong><xsl:value-of select="$label"/>:</strong> <xsl:value-of select="name($element)"/></p>
            <p>Attributes:</p>
            <ul>
                <xsl:for-each select="$element/@*">
                    <li><xsl:value-of select="name()"/> = <xsl:value-of select="."/></li>
                </xsl:for-each>
            </ul>
        </div>
    </xsl:template>

    <!-- Debugging template to trace template application -->
    <xsl:template match="TBL" mode="debug">
        <div style="border: 2px dashed red; padding: 10px; margin: 10px;">
            <h3>DEBUG: TBL found</h3>
            <p>NO.SEQ: <xsl:value-of select="@NO.SEQ"/></p>
            <p>COLS: <xsl:value-of select="@COLS"/></p>
            <p>Template being applied from debug mode</p>
        </div>
    </xsl:template>

</xsl:stylesheet>