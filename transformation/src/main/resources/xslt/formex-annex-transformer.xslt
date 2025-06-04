<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:param name="sourceFileName" select="'unknown_source.xml'"/>

    <!-- Specialized transformer for ANNEX documents -->
    <xsl:template match="/ANNEX">
        <simplifiedLegislationDoc sourceFile="{$sourceFileName}">
            <metadata>
                <xsl:apply-templates select="BIB.INSTANCE"/>
                <xsl:apply-templates select="TITLE/TI" mode="mainTitle"/>
                <xsl:if test="BIB.INSTANCE/EEA">
                    <isEEARelevant>true</isEEARelevant>
                </xsl:if>
                <xsl:apply-templates select="BIB.INSTANCE" mode="pageInfo"/>
            </metadata>
            <body>
                <xsl:apply-templates select="CONTENTS"/>
            </body>
        </simplifiedLegislationDoc>
    </xsl:template>

    <!-- Supporting templates for ANNEX-specific processing -->
    <xsl:template match="BIB.INSTANCE">
        <xsl:choose>
            <xsl:when test="DOCUMENT.REF/COLL and DOCUMENT.REF/NO.OJ and DOCUMENT.REF/YEAR">
                <docIdString><xsl:value-of select="concat(DOCUMENT.REF/COLL, ' ', DOCUMENT.REF/NO.OJ, '/', DOCUMENT.REF/YEAR)"/></docIdString>
            </xsl:when>
            <xsl:when test="NO.DOC">
                <docIdString><xsl:value-of select="concat(NO.DOC/COM, '/', NO.DOC/NO.CURRENT, '/', NO.DOC/YEAR)"/></docIdString>
            </xsl:when>
        </xsl:choose>
        <xsl:if test="NO.DOC">
            <docNumberCurrent><xsl:value-of select="NO.DOC/NO.CURRENT"/></docNumberCurrent>
            <docYear><xsl:value-of select="NO.DOC/YEAR"/></docYear>
            <xsl:if test="NO.DOC/@TYPE"><docType><xsl:value-of select="NO.DOC/@TYPE"/></docType></xsl:if>
        </xsl:if>
        <xsl:if test="DATE/@ISO"><documentDate><xsl:value-of select="concat(substring(DATE/@ISO,1,4), '-', substring(DATE/@ISO,5,2), '-', substring(DATE/@ISO,7,2))"/></documentDate></xsl:if>
        <xsl:if test="NO.SEQ"><sequenceNumber><xsl:value-of select="NO.SEQ"/></sequenceNumber></xsl:if>
    </xsl:template>

    <xsl:template match="BIB.INSTANCE" mode="pageInfo">
        <xsl:if test="PAGE.FIRST"><pageFirst><xsl:value-of select="PAGE.FIRST"/></pageFirst></xsl:if>
        <xsl:if test="PAGE.LAST"><pageLast><xsl:value-of select="PAGE.LAST"/></pageLast></xsl:if>
        <xsl:if test="PAGE.TOTAL"><pageTotal><xsl:value-of select="PAGE.TOTAL"/></pageTotal></xsl:if>
    </xsl:template>

    <xsl:template match="TITLE/TI" mode="mainTitle">
        <title>
            <xsl:for-each select="P">
                <xsl:if test="position() > 1"><br/></xsl:if>
                <xsl:apply-templates/>
            </xsl:for-each>
        </title>
    </xsl:template>

    <xsl:template match="CONTENTS">
        <xsl:variable name="hasAttachments" select="GR.SEQ[@LEVEL=1 and (starts-with(normalize-space(TITLE/TI/P/HT[@TYPE='BOLD']), 'ATTACHMENT') or starts-with(normalize-space(TITLE/TI/P), 'ATTACHMENT'))]"/>
        <xsl:if test="GR.SEQ[not($hasAttachments)]">
            <sections>
                <xsl:apply-templates select="GR.SEQ[not(starts-with(normalize-space(TITLE/TI/P/HT[@TYPE='BOLD']), 'ATTACHMENT') or starts-with(normalize-space(TITLE/TI/P), 'ATTACHMENT'))]"/>
            </sections>
        </xsl:if>
        <xsl:if test="$hasAttachments">
            <attachments>
                <xsl:apply-templates select="$hasAttachments" mode="attachment"/>
            </attachments>
        </xsl:if>
    </xsl:template>

    <xsl:template match="GR.SEQ">
        <xsl:variable name="isAttachment" select="starts-with(normalize-space(TITLE/TI//HT[@TYPE='BOLD']), 'ATTACHMENT') or starts-with(normalize-space(TITLE/TI/P/HT), 'ATTACHMENT')"/>
        <xsl:if test="not($isAttachment)">
            <section id="{NO.GR.SEQ}" level="{@LEVEL}">
                <xsl:apply-templates select="TITLE"/>
                <xsl:apply-templates select="P | LIST | TBL | GR.SEQ | ./COMMENT | text()[normalize-space()]"/>
            </section>
        </xsl:if>
    </xsl:template>

    <xsl:template match="GR.SEQ" mode="attachment">
        <xsl:if test="not(ancestor::attachments/attachment[@id=current()/NO.GR.SEQ])">
            <xsl:element name="attachment">
                <xsl:attribute name="id">
                    <xsl:choose>
                        <xsl:when test="NO.GR.SEQ"><xsl:value-of select="NO.GR.SEQ"/></xsl:when>
                        <xsl:otherwise><xsl:value-of select="generate-id()"/></xsl:otherwise>
                    </xsl:choose>
                </xsl:attribute>
                <xsl:if test="@LEVEL"><xsl:attribute name="level" select="@LEVEL"/></xsl:if>
                <xsl:apply-templates select="TITLE"/>
                <xsl:apply-templates select="P | LIST | TBL | GR.SEQ | ./COMMENT | text()[normalize-space()]"/>
            </xsl:element>
        </xsl:if>
    </xsl:template>

    <xsl:template match="TITLE[parent::SUBDIV or parent::GR.SEQ]">
        <title><xsl:apply-templates/></title>
    </xsl:template>

    <xsl:template match="P | ALINEA | NP">
        <xsl:choose>
            <xsl:when test="ancestor::TITLE">
                <xsl:apply-templates/>
            </xsl:when>
            <xsl:when test="ancestor::ITEM">
                <xsl:apply-templates/>
            </xsl:when>
            <xsl:otherwise>
                <contentItem>
                    <xsl:choose>
                        <xsl:when test="self::NP">
                            <numberedParagraph>
                                <xsl:if test="NO.P"><xsl:attribute name="number" select="normalize-space(NO.P)"/></xsl:if>
                                <xsl:apply-templates select="TXT/node() | node()[not(self::NO.P or self::TXT)]"/>
                            </numberedParagraph>
                        </xsl:when>
                        <xsl:otherwise>
                            <paragraph><xsl:apply-templates/></paragraph>
                        </xsl:otherwise>
                    </xsl:choose>
                </contentItem>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="GR.SEQ/text()[normalize-space()]">
        <contentItem><paragraph><xsl:value-of select="normalize-space(.)"/></paragraph></contentItem>
    </xsl:template>

    <xsl:template match="LIST">
        <contentItem>
            <list>
                <xsl:if test="@TYPE"><xsl:attribute name="type" select="normalize-space(@TYPE)"/></xsl:if>
                <xsl:apply-templates select="ITEM | DLIST.ITEM"/>
            </list>
        </contentItem>
    </xsl:template>

    <xsl:template match="ITEM">
        <item>
            <xsl:apply-templates/>
        </item>
    </xsl:template>

    <xsl:template match="ITEM/NP">
        <xsl:if test="NO.P"><itemPrefix><xsl:apply-templates select="NO.P/node()"/></itemPrefix></xsl:if>
        <itemContent>
            <xsl:apply-templates select="TXT/node() | node()[not(self::NO.P or self::TXT)]"/>
        </itemContent>
    </xsl:template>
    
    <xsl:template match="ITEM/P">
        <itemContent>
            <xsl:apply-templates/>
        </itemContent>
    </xsl:template>

    <xsl:template match="TBL">
        <contentItem>
            <table>
                <xsl:if test="@COLS"><xsl:attribute name="numCols" select="@COLS"/></xsl:if>
                <xsl:apply-templates select="TITLE"/>
                <xsl:apply-templates select="CORPUS/ROW"/>
            </table>
        </contentItem>
    </xsl:template>

    <xsl:template match="TBL/TITLE">
        <title><xsl:apply-templates/></title>
    </xsl:template>

    <xsl:template match="ROW">
        <row>
            <xsl:if test="@TYPE = 'HEADER'"><xsl:attribute name="isHeader">true</xsl:attribute></xsl:if>
            <xsl:apply-templates select="CELL"/>
        </row>
    </xsl:template>

    <xsl:template match="CELL">
        <cell>
            <xsl:if test="@COLSPAN"><xsl:attribute name="colspan" select="@COLSPAN"/></xsl:if>
            <xsl:if test="@ROWSPAN"><xsl:attribute name="rowspan" select="@ROWSPAN"/></xsl:if>
            <xsl:if test="@TYPE = 'HEADER'"><xsl:attribute name="isHeader">true</xsl:attribute></xsl:if>
            <xsl:apply-templates/>
        </cell>
    </xsl:template>

    <!-- Common formatting templates -->
    <xsl:template match="HT">
        <xsl:choose>
            <xsl:when test="@TYPE = 'BOLD' or @TYPE = 'UC'"><strong><xsl:apply-templates/></strong></xsl:when>
            <xsl:when test="@TYPE = 'ITALIC'"><em><xsl:apply-templates/></em></xsl:when>
            <xsl:when test="@TYPE = 'SUP'"><sup><xsl:apply-templates/></sup></xsl:when>
            <xsl:when test="@TYPE = 'SUB'"><sub><xsl:apply-templates/></sub></xsl:when>
            <xsl:when test="@TYPE = 'UNDERLINE'"><underline><xsl:apply-templates/></underline></xsl:when>
            <xsl:otherwise><xsl:apply-templates/></xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="DATE">
        <date iso="{concat(substring(@ISO,1,4), '-', substring(@ISO,5,2), '-', substring(@ISO,7,2))}"><xsl:apply-templates/></date>
    </xsl:template>

    <xsl:template match="COMMENT">
        <contentItem>
            <paragraph><em>[Comment: <xsl:apply-templates/>]</em></paragraph>
        </contentItem>
    </xsl:template>

    <!-- Mode template for multi-file processing -->
    <xsl:template match="/ANNEX" mode="annex">
        <attachment>
            <xsl:if test="TITLE">
                <xsl:apply-templates select="TITLE"/>
            </xsl:if>
            <xsl:apply-templates select="CONTENTS"/>
        </attachment>
    </xsl:template>

    <!-- Suppress unwanted elements in ANNEX documents -->
    <xsl:template match="BIB.INSTANCE/LG.DOC | BIB.INSTANCE/NO.SEQ | BIB.INSTANCE/DOCUMENT.REF"/>
    <xsl:template match="NO.GR.SEQ | NO.P[parent::NP and ancestor::ITEM]"/>
    <xsl:template match="GR.SEQ/TITLE/TI/P | GR.SEQ/TITLE/TI/NP/NO.P | GR.SEQ/TITLE/TI/NP/TXT[parent::NP and ancestor::TITLE]"/>

</xsl:stylesheet>