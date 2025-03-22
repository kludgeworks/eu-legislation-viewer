<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xi="http://www.w3.org/2001/XInclude">

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>

    <!-- Main template - match document root -->
    <xsl:template match="/">
        <html>
            <head>
                <!-- Extract title dynamically from XML -->
                <title>
                    <xsl:text>Regulation (EU) </xsl:text>
                    <xsl:value-of select="//BIB.INSTANCE/NO.DOC/NO.CURRENT"/>
                    <xsl:text>/</xsl:text>
                    <xsl:value-of select="//BIB.INSTANCE/NO.DOC/YEAR"/>
                </title>
                <style>
                    body {
                    font-family: Arial, sans-serif;
                    max-width: 800px;
                    margin: 0 auto;
                    padding: 20px;
                    line-height: 1.5;
                    }
                    .title {
                    text-align: center;
                    margin-bottom: 30px;
                    }
                    .consideration {
                    margin-left: 40px;
                    margin-bottom: 20px;
                    }
                    .consid-number {
                    float: left;
                    margin-left: -40px;
                    }
                    .article {
                    margin-bottom: 20px;
                    }
                    .article-title {
                    font-style: italic;
                    text-align: center;
                    margin-bottom: 10px;
                    }
                    /* Text formatting styles for all HT TYPE values */
                    .italic {
                    font-style: italic;
                    }
                    .bold {
                    font-weight: bold;
                    }
                    .uppercase {
                    text-transform: uppercase;
                    }
                    .smallcaps {
                    font-variant: small-caps;
                    }
                    .underline {
                    text-decoration: underline;
                    }
                    .strikethrough {
                    text-decoration: line-through;
                    }
                    .box {
                    border: 1px solid black;
                    padding: 2px 4px;
                    display: inline-block;
                    }
                    .expanded {
                    letter-spacing: 0.2em;
                    }
                    .normal {
                    font-style: normal;
                    font-weight: normal;
                    text-transform: none;
                    }
                    .subscript {
                    vertical-align: sub;
                    font-size: smaller;
                    }
                    .superscript {
                    vertical-align: super;
                    font-size: smaller;
                    }
                    .signature {
                    margin-top: 30px;
                    text-align: right;
                    }
                    .footnote-ref {
                    vertical-align: super;
                    font-size: smaller;
                    }
                    .annex-title {
                    text-align: center;
                    }
                    .footnotes-section {
                    margin-top: 30px;
                    border-top: 1px solid #ccc;
                    padding-top: 10px;
                    font-size: 0.9em;
                    }
                    .footnote-item {
                    margin-bottom: 8px;
                    text-indent: -20px;
                    padding-left: 20px;
                    }
                    .footnote-number {
                    vertical-align: super;
                    font-size: smaller;
                    margin-right: 5px;
                    }
                    .section-number {
                    font-weight: bold;
                    margin-right: 12px;
                    }
                    .section {
                    margin-bottom: 15px;
                    }
                    .section-title {
                    margin-top: 20px;
                    margin-bottom: 10px;
                    }
                    /* Improve list display */
                    .alpha-list {
                    list-style-type: none;
                    padding-left: 20px;
                    margin-top: 5px;
                    }
                    .alpha-list li {
                    margin-bottom: 5px;
                    text-indent: -20px;
                    padding-left: 20px;
                    }
                    .dash-list {
                    list-style-type: none;
                    padding-left: 20px;
                    }
                    .dash-list li {
                    margin-bottom: 5px;
                    text-indent: -15px;
                    padding-left: 15px;
                    }
                    .dash-list li:before {
                    content: "— ";
                    }
                    .attachment-heading {
                    font-weight: bold;
                    text-align: center;
                    margin-top: 30px;
                    margin-bottom: 15px;
                    }
                    .sub-heading {
                    font-weight: bold;
                    margin-top: 15px;
                    margin-bottom: 10px;
                    }
                    /* No margins for level classes */
                    .level-1 { }
                    .level-2 { }
                    .level-3 { }
                    .level-4 { }
                    .level-5 { }
                    /* To handle nested lists better */
                    .nested-content {
                    margin-left: 20px;
                    }
                </style>
            </head>
            <body>
                <!-- Find ACT element anywhere in the document structure -->
                <xsl:apply-templates select="//ACT"/>
            </body>
        </html>
    </xsl:template>

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

            <!-- Process any other content at this level -->
            <xsl:apply-templates select="*[not(self::TITLE) and not(self::NO.GR.SEQ) and not(self::P) and not(self::LIST) and not(self::GR.SEQ)]"/>

            <!-- Process nested GR.SEQ elements -->
            <xsl:apply-templates select="GR.SEQ"/>
        </div>
    </xsl:template>

    <!-- Handle paragraphs -->
    <xsl:template match="P">
        <p><xsl:apply-templates/></p>
    </xsl:template>

    <!-- Handle lists -->
    <xsl:template match="LIST">
        <xsl:choose>
            <xsl:when test="@TYPE='alpha' or @TYPE='ALPHA'">
                <ol class="alpha-list">
                    <xsl:apply-templates select="ITEM"/>
                </ol>
            </xsl:when>
            <xsl:when test="@TYPE='ARAB'">
                <ol>
                    <xsl:apply-templates select="ITEM"/>
                </ol>
            </xsl:when>
            <xsl:when test="@TYPE='DASH' or @TYPE='NDASH'">
                <ul class="dash-list">
                    <xsl:apply-templates select="ITEM"/>
                </ul>
            </xsl:when>
            <xsl:when test="@TYPE='BULLET'">
                <ul>
                    <xsl:apply-templates select="ITEM"/>
                </ul>
            </xsl:when>
            <xsl:otherwise>
                <ul>
                    <xsl:apply-templates select="ITEM"/>
                </ul>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Handle list items with their structure -->
    <xsl:template match="ITEM">
        <li>
            <xsl:choose>
                <xsl:when test="NP">
                    <!-- Handle marker and direct text -->
                    <xsl:if test="NP/NO.P">
                        <xsl:value-of select="NP/NO.P"/>
                        <xsl:text>&#160;&#160;</xsl:text>
                    </xsl:if>
                    <xsl:apply-templates select="NP/TXT"/>

                    <!-- Handle nested structure: NP/P/LIST specifically -->
                    <xsl:if test="NP/P/LIST">
                        <div class="nested-content">
                            <xsl:apply-templates select="NP/P/LIST"/>
                        </div>
                    </xsl:if>

                    <!-- Apply templates for any other P elements directly in NP -->
                    <xsl:for-each select="NP/P[not(LIST)]">
                        <p><xsl:apply-templates/></p>
                    </xsl:for-each>

                    <!-- Process any P elements that follow the NP -->
                    <xsl:for-each select="P">
                        <p><xsl:apply-templates/></p>
                    </xsl:for-each>

                    <!-- Process any direct nested lists -->
                    <xsl:apply-templates select="LIST"/>
                </xsl:when>
                <xsl:when test="P">
                    <!-- Handle simple items with just P -->
                    <xsl:apply-templates select="P"/>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Fall back for items without structure -->
                    <xsl:apply-templates/>
                </xsl:otherwise>
            </xsl:choose>
        </li>
    </xsl:template>

    <!-- Handle TXT nodes explicitly -->
    <xsl:template match="TXT">
        <xsl:apply-templates/>
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

    <!-- Default template for text nodes -->
    <xsl:template match="text()">
        <xsl:value-of select="."/>
    </xsl:template>

    <!-- Handle ALINEA elements -->
    <xsl:template match="ALINEA">
        <xsl:apply-templates/>
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
</xsl:stylesheet>