<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- ACT template -->
    <xsl:template match="ACT">
        <!-- Title section -->
        <div class="title">
            <!-- Extract title dynamically from the XML structure -->
            <h1>
                <xsl:value-of select="normalize-space(TITLE/TI/P[1])"/>
            </h1>

            <!-- Date and description in bold -->
            <p><strong>of <xsl:value-of select="TITLE/TI/P[2]/DATE"/></strong></p>

            <p><strong><xsl:value-of select="normalize-space(TITLE/TI/P[3])"/></strong></p>

            <p><strong><xsl:value-of select="normalize-space(TITLE/TI/P[4])"/></strong></p>
        </div>

        <!-- Preamble -->
        <p><xsl:value-of select="PREAMBLE/PREAMBLE.INIT"/></p>

        <!-- Visas -->
        <div class="visas">
            <xsl:for-each select="PREAMBLE/GR.VISA/VISA">
                <p><xsl:apply-templates select="."/></p>
            </xsl:for-each>
        </div>

        <!-- Considerations -->
        <p><xsl:value-of select="PREAMBLE/GR.CONSID/GR.CONSID.INIT"/></p>

        <div class="considerations">
            <xsl:for-each select="PREAMBLE/GR.CONSID/CONSID">
                <div class="consideration">
                    <span class="consid-number"><xsl:value-of select="NP/NO.P"/></span>
                    <p><xsl:apply-templates select="NP/TXT"/></p>
                </div>
            </xsl:for-each>
        </div>

        <!-- Final preamble - not bold -->
        <p><xsl:value-of select="PREAMBLE/PREAMBLE.FINAL"/></p>

        <!-- Articles -->
        <div class="articles">
            <xsl:for-each select="ENACTING.TERMS/ARTICLE">
                <div class="article">
                    <p class="article-title"><xsl:value-of select="TI.ART"/></p>
                    <xsl:for-each select="ALINEA">
                        <p><xsl:apply-templates select="."/></p>
                    </xsl:for-each>
                </div>
            </xsl:for-each>
        </div>

        <!-- Final -->
        <xsl:apply-templates select="FINAL"/>

        <!-- Footnotes section for ACT -->
        <xsl:if test=".//NOTE[@TYPE='FOOTNOTE']">
            <div class="footnotes-section">
                <xsl:for-each select=".//NOTE[@TYPE='FOOTNOTE']">
                    <div class="footnote-item">
                        <span class="footnote-number">
                            (<xsl:value-of select="substring-after(@NOTE.ID, 'E000')"/>)
                        </span>
                        <xsl:apply-templates select="P"/>
                    </div>
                </xsl:for-each>
            </div>
        </xsl:if>

        <!-- Process ANNEX if present (usually in separate file) - no heading -->
        <xsl:if test="//ANNEX">
            <hr/>
            <xsl:apply-templates select="//ANNEX"/>
        </xsl:if>
    </xsl:template>

    <!-- VISA template -->
    <xsl:template match="VISA">
        <!-- Handle special characters and footnotes -->
        <xsl:apply-templates/>
    </xsl:template>

    <!-- CONSID template -->
    <xsl:template match="CONSID">
        <div class="consideration">
            <span class="consid-number"><xsl:value-of select="NP/NO.P"/></span>
            <xsl:apply-templates select="NP/TXT"/>
        </div>
    </xsl:template>

    <!-- FINAL template -->
    <xsl:template match="FINAL">
        <p><xsl:apply-templates select="P"/></p>

        <div class="signature">
            <p><xsl:apply-templates select="SIGNATURE/PL.DATE/P"/></p>
            <xsl:for-each select="SIGNATURE/SIGNATORY/P">
                <p><xsl:apply-templates select="."/></p>
            </xsl:for-each>
        </div>
    </xsl:template>

    <!-- Handle ALINEA elements -->
    <xsl:template match="ALINEA">
        <xsl:apply-templates/>
    </xsl:template>

</xsl:stylesheet>