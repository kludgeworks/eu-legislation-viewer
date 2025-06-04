<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:param name="sourceFileName" select="'unknown_source.xml'"/>

    <!-- Root template that delegates based on document type -->
    <xsl:template match="/">
        <xsl:choose>
            <xsl:when test="/DOC">
                <xsl:apply-templates select="/DOC"/>
            </xsl:when>
            <xsl:when test="/ACT">
                <xsl:apply-templates select="/ACT"/>
            </xsl:when>
            <xsl:when test="/ANNEX">
                <xsl:apply-templates select="/ANNEX"/>
            </xsl:when>
            <xsl:otherwise>
                <error>
                    <message>Unsupported document type. Expected DOC, ACT, or ANNEX root element.</message>
                    <sourceFile><xsl:value-of select="$sourceFileName"/></sourceFile>
                    <rootElement><xsl:value-of select="name(/*[1])"/></rootElement>
                </error>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- DOC transformer -->
    <xsl:template match="/DOC">
        <simplifiedLegislationDoc sourceFile="{$sourceFileName}">
            <metadata>
                <xsl:apply-templates select="BIB.DOC"/>
                <xsl:apply-templates select="PUBLICATION.REF"/>
                <xsl:apply-templates select="PAPER/VOLUME.PAPER/TITLE/TI"/>
                <xsl:if test="BIB.DOC/EEA or PAPER/VOLUME.PAPER/TITLE/TI//NOTE[contains(P, 'EEA relevance')]">
                    <isEEARelevant>true</isEEARelevant>
                </xsl:if>
                <xsl:if test="FMX/DOC.MAIN.PUB/LEGAL.VALUE">
                    <legalValue><xsl:value-of select="FMX/DOC.MAIN.PUB/LEGAL.VALUE"/></legalValue>
                </xsl:if>
                <xsl:apply-templates select="FMX/DOC.MAIN.PUB" mode="pageInfo"/>
            </metadata>
            <body>
                <xsl:if test="FMX/DOC.SUB.PUB[@TYPE='ANNEX']">
                    <annexes>
                        <xsl:for-each select="FMX/DOC.SUB.PUB[@TYPE='ANNEX']">
                            <attachment id="{substring-after(@NO.SEQ,'.')}" level="1">
                                <title>Annex <xsl:value-of select="substring-after(@NO.SEQ,'.')"/></title>
                                <contentItem>
                                    <paragraph>Referenced file: <xsl:value-of select="REF.PHYS/@FILE"/></paragraph>
                                </contentItem>
                                <contentItem>
                                    <paragraph>Pages: <xsl:value-of select="PAGE.FIRST"/> - <xsl:value-of select="PAGE.LAST"/> (<xsl:value-of select="PAGE.TOTAL"/> pages)</paragraph>
                                </contentItem>
                            </attachment>
                        </xsl:for-each>
                    </annexes>
                </xsl:if>
            </body>
        </simplifiedLegislationDoc>
    </xsl:template>

    <!-- ACT transformer -->
    <xsl:template match="/ACT">
        <simplifiedLegislationDoc sourceFile="{$sourceFileName}">
            <metadata>
                <xsl:apply-templates select="BIB.INSTANCE"/>
                <xsl:apply-templates select="TITLE/TI" mode="mainTitle"/>
                <xsl:if test="BIB.INSTANCE/EEA or TITLE/TI/P[contains(., 'EEA relevance')]">
                    <isEEARelevant>true</isEEARelevant>
                </xsl:if>
                <xsl:apply-templates select="BIB.INSTANCE" mode="pageInfo"/>
            </metadata>
            <body>
                <xsl:apply-templates select="PREAMBLE"/>
                <xsl:apply-templates select="ENACTING.TERMS"/>
                <xsl:apply-templates select="FINAL"/>
            </body>
        </simplifiedLegislationDoc>
    </xsl:template>

    <!-- ANNEX transformer -->
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

    <!-- Common templates -->
    <xsl:template match="BIB.DOC">
        <xsl:if test="NO.DOC">
            <docIdString><xsl:value-of select="concat(NO.DOC/COM, '/', NO.DOC/NO.CURRENT, '/', NO.DOC/YEAR)"/></docIdString>
            <docNumberCurrent><xsl:value-of select="NO.DOC/NO.CURRENT"/></docNumberCurrent>
            <docYear><xsl:value-of select="NO.DOC/YEAR"/></docYear>
            <xsl:if test="NO.DOC/@TYPE"><docType><xsl:value-of select="NO.DOC/@TYPE"/></docType></xsl:if>
        </xsl:if>
        <xsl:if test="AUTHOR"><author><xsl:value-of select="AUTHOR"/></author></xsl:if>
    </xsl:template>

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

    <xsl:template match="BIB.INSTANCE | FMX/DOC.MAIN.PUB" mode="pageInfo">
        <xsl:if test="PAGE.FIRST"><pageFirst><xsl:value-of select="PAGE.FIRST"/></pageFirst></xsl:if>
        <xsl:if test="PAGE.LAST"><pageLast><xsl:value-of select="PAGE.LAST"/></pageLast></xsl:if>
        <xsl:if test="PAGE.TOTAL"><pageTotal><xsl:value-of select="PAGE.TOTAL"/></pageTotal></xsl:if>
    </xsl:template>

    <xsl:template match="PUBLICATION.REF">
        <xsl:if test="DATE/@ISO"><publicationDate><xsl:value-of select="concat(substring(DATE/@ISO,1,4), '-', substring(DATE/@ISO,5,2), '-', substring(DATE/@ISO,7,2))"/></publicationDate></xsl:if>
    </xsl:template>

    <xsl:template match="TITLE/TI" mode="mainTitle">
        <title>
            <xsl:for-each select="P">
                <xsl:if test="position() > 1"><br/></xsl:if>
                <xsl:apply-templates/>
            </xsl:for-each>
        </title>
    </xsl:template>

    <xsl:template match="PREAMBLE">
        <preamble>
            <xsl:if test="PREAMBLE.INIT"><initiator><xsl:apply-templates select="PREAMBLE.INIT/node()"/></initiator></xsl:if>
            <xsl:apply-templates select="GR.VISA/VISA"/>
            <xsl:apply-templates select="GR.CONSID/CONSID"/>
            <xsl:if test="PREAMBLE.FINAL"><finalStatement><xsl:apply-templates select="PREAMBLE.FINAL/node()"/></finalStatement></xsl:if>
        </preamble>
    </xsl:template>

    <xsl:template match="VISA">
        <visa><xsl:apply-templates/></visa>
    </xsl:template>

    <xsl:template match="CONSID">
        <consideration>
            <xsl:if test="NP/NO.P"><xsl:attribute name="number" select="normalize-space(NP/NO.P)"/></xsl:if>
            <xsl:apply-templates select="NP/TXT/node()"/>
        </consideration>
    </xsl:template>

    <xsl:template match="ENACTING.TERMS">
        <articles>
            <xsl:apply-templates select="ARTICLE"/>
        </articles>
    </xsl:template>

    <xsl:template match="ARTICLE">
        <article id="{@IDENTIFIER}">
            <xsl:apply-templates select="TI.ART"/>
            <xsl:apply-templates select="ALINEA | P"/>
        </article>
    </xsl:template>

    <xsl:template match="TI.ART">
        <title><xsl:apply-templates/></title>
    </xsl:template>

    <xsl:template match="ALINEA | P">
        <contentItem>
            <paragraph><xsl:apply-templates/></paragraph>
        </contentItem>
    </xsl:template>

    <xsl:template match="FINAL">
        <finalProvisions>
            <xsl:for-each select="P">
                <paragraph><xsl:apply-templates/></paragraph>
            </xsl:for-each>
            <xsl:apply-templates select="SIGNATURE"/>
        </finalProvisions>
    </xsl:template>

    <xsl:template match="SIGNATURE">
        <signatureBlock>
            <xsl:apply-templates select="PL.DATE"/>
            <xsl:apply-templates select="SIGNATORY/P"/>
        </signatureBlock>
    </xsl:template>

    <xsl:template match="PL.DATE/P">
        <placeAndDate><xsl:apply-templates/></placeAndDate>
    </xsl:template>

    <xsl:template match="SIGNATORY/P">
        <signatoryInfo><xsl:apply-templates/></signatoryInfo>
    </xsl:template>

    <xsl:template match="CONTENTS">
        <sections>
            <xsl:apply-templates select="GR.SEQ"/>
        </sections>
    </xsl:template>

    <xsl:template match="GR.SEQ">
        <section id="{NO.GR.SEQ}" level="{@LEVEL}">
            <xsl:apply-templates select="TITLE"/>
            <xsl:apply-templates select="P"/>
        </section>
    </xsl:template>

    <xsl:template match="TITLE[parent::GR.SEQ]">
        <title><xsl:apply-templates/></title>
    </xsl:template>

    <!-- Format text elements -->
    <xsl:template match="HT">
        <xsl:choose>
            <xsl:when test="@TYPE = 'BOLD' or @TYPE = 'UC'"><strong><xsl:apply-templates/></strong></xsl:when>
            <xsl:when test="@TYPE = 'ITALIC'"><em><xsl:apply-templates/></em></xsl:when>
            <xsl:otherwise><xsl:apply-templates/></xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="DATE">
        <date iso="{concat(substring(@ISO,1,4), '-', substring(@ISO,5,2), '-', substring(@ISO,7,2))}"><xsl:apply-templates/></date>
    </xsl:template>

    <!-- Suppress unwanted elements -->
    <xsl:template match="BIB.DOC/PROD.ID | BIB.DOC/FIN.ID | BIB.DOC/DURAB | BIB.DOC/COM"/>
    <xsl:template match="PUBLICATION.REF/COLL | PUBLICATION.REF/NO.OJ | PUBLICATION.REF/LG.OJ | PUBLICATION.REF/VOLUME.REF"/>
    <xsl:template match="BIB.INSTANCE/LG.DOC | BIB.INSTANCE/NO.SEQ | BIB.INSTANCE/DOCUMENT.REF"/>
    <xsl:template match="FMX | REF.PHYS | PAPER | PDF | VOLUME.PAPER | ITEM.VOLUME"/>
    <xsl:template match="NO.GR.SEQ | NO.P[parent::NP and ancestor::CONSID]"/>

</xsl:stylesheet>