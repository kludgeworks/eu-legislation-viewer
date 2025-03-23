<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- ANNEX template to handle the annex content -->
    <xsl:template match="ANNEX">
        <div class="annex">
            <xsl:apply-templates select="TITLE"/>
            <xsl:apply-templates select="CONTENTS"/>

            <!-- Footnotes section for ANNEX -->
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
        </div>
    </xsl:template>

    <!-- Handle ANNEX title - centered -->
    <xsl:template match="ANNEX/TITLE">
        <h3 class="annex-title"><xsl:apply-templates/></h3>
    </xsl:template>

    <!-- Handle ANNEX contents -->
    <xsl:template match="ANNEX/CONTENTS">
        <div class="annex-content">
            <xsl:apply-templates/>
        </div>
    </xsl:template>

    <!-- GR.SEQ sections in ANNEX -->
    <xsl:template match="GR.SEQ">
        <xsl:variable name="level">
            <xsl:choose>
                <xsl:when test="@LEVEL"><xsl:value-of select="@LEVEL"/></xsl:when>
                <xsl:otherwise>1</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <div class="section level-{$level}">
            <!-- Process TITLE element with different structures -->
            <xsl:if test="TITLE">
                <xsl:choose>
                    <!-- Handle titles that have ATTACHMENT in them -->
                    <xsl:when test="contains(TITLE/TI/P, 'ATTACHMENT')">
                        <h3 class="attachment-heading"><xsl:value-of select="TITLE/TI/P"/></h3>
                    </xsl:when>
                    <!-- Handle regular titles with NP structure -->
                    <xsl:when test="TITLE/TI/NP">
                        <h4 class="section-title">
                            <xsl:if test="TITLE/TI/NP/NO.P">
                                <xsl:value-of select="TITLE/TI/NP/NO.P"/>
                                <xsl:text> </xsl:text>
                            </xsl:if>
                            <xsl:apply-templates select="TITLE/TI/NP/TXT"/>
                        </h4>
                    </xsl:when>
                    <!-- Handle simple paragraph titles -->
                    <xsl:when test="TITLE/TI/P">
                        <h4 class="sub-heading"><xsl:apply-templates select="TITLE/TI/P"/></h4>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="TITLE"/>
                    </xsl:otherwise>
                </xsl:choose>

                <!-- NEW CODE: Handle subtitle (STI) if present -->
                <xsl:if test="TITLE/STI">
                    <h5 class="subtitle">
                        <xsl:apply-templates select="TITLE/STI/P"/>
                    </h5>
                </xsl:if>
            </xsl:if>

            <!-- Handle section numbering when NO.GR.SEQ is present -->
            <xsl:if test="NO.GR.SEQ">
                <!-- Use this for paragraph with section number directly in text -->
                <xsl:if test="P">
                    <p>
                        <span class="section-number"><xsl:value-of select="NO.GR.SEQ"/></span>
                        <xsl:apply-templates select="P[1]/node()"/>
                    </p>
                    <!-- Apply templates for any additional paragraphs after the first one -->
                    <xsl:for-each select="P[position() > 1]">
                        <p><xsl:apply-templates/></p>
                    </xsl:for-each>
                </xsl:if>

                <!-- If there's a section number but no direct paragraph -->
                <xsl:if test="not(P)">
                    <p><span class="section-number"><xsl:value-of select="NO.GR.SEQ"/></span></p>
                </xsl:if>
            </xsl:if>

            <!-- Process regular paragraphs when there's no section number -->
            <xsl:if test="not(NO.GR.SEQ) and P">
                <xsl:apply-templates select="P"/>
            </xsl:if>

            <!-- Process lists at this level -->
            <xsl:apply-templates select="LIST"/>

            <!-- Process TBL elements at this level -->
            <xsl:apply-templates select="TBL" />

            <!-- Process any other content at this level -->
            <xsl:apply-templates select="*[not(self::TITLE) and not(self::NO.GR.SEQ) and not(self::P) and not(self::LIST) and not(self::TBL) and not(self::GR.SEQ)]"/>

            <!-- Process nested GR.SEQ elements -->
            <xsl:apply-templates select="GR.SEQ"/>
        </div>
    </xsl:template>

</xsl:stylesheet>