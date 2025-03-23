<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Text formatting templates for all HT TYPE values -->
    <xsl:template match="HT[@TYPE='ITALIC']">
        <span class="italic"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='BOLD']">
        <span class="bold"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='UC']">
        <span class="uppercase"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='SC']">
        <span class="smallcaps"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='UNDERLINE']">
        <span class="underline"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='STROKE']">
        <span class="strikethrough"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='BOX']">
        <span class="box"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='EXPANDED']">
        <span class="expanded"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='NORMAL']">
        <span class="normal"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='SUB']">
        <span class="subscript"><xsl:apply-templates/></span>
    </xsl:template>

    <xsl:template match="HT[@TYPE='SUP']">
        <span class="superscript"><xsl:apply-templates/></span>
    </xsl:template>

    <!-- Date formatting template -->
    <xsl:template match="DATE" mode="formatted">
        <xsl:value-of select="."/>
    </xsl:template>

    <!-- Handle notes/footnotes in-text references -->
    <xsl:template match="NOTE">
        <span class="footnote-ref">
            (<xsl:value-of select="substring-after(@NOTE.ID, 'E000')"/>)
        </span>
    </xsl:template>

    <!-- Handle TXT nodes explicitly -->
    <xsl:template match="TXT">
        <xsl:apply-templates/>
    </xsl:template>

</xsl:stylesheet>