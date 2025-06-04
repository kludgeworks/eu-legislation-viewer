<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:param name="sourceFileName" select="'unknown_source.xml'"/>

    <!-- Specialized transformer for DOC manifest/index documents -->
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
                <xsl:comment>Transformed from a DOC manifest-like structure. Content from linked files (DOC_2, DOC_3, etc.) would require further processing via document() if their full content is desired here.</xsl:comment>
                <xsl:if test="FMX/DOC.MAIN.PUB/REF.PHYS/@TYPE = 'DOC.XML'">
                    <xsl:comment>Consider using xsl:result-document or further processing for <xsl:value-of select="FMX/DOC.MAIN.PUB/REF.PHYS/@FILE"/> </xsl:comment>
                </xsl:if>
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

    <!-- Supporting templates for DOC-specific processing -->
    <xsl:template match="BIB.DOC">
        <xsl:if test="NO.DOC">
            <docIdString><xsl:value-of select="concat(NO.DOC/COM, '/', NO.DOC/NO.CURRENT, '/', NO.DOC/YEAR)"/></docIdString>
            <docNumberCurrent><xsl:value-of select="NO.DOC/NO.CURRENT"/></docNumberCurrent>
            <docYear><xsl:value-of select="NO.DOC/YEAR"/></docYear>
            <xsl:if test="NO.DOC/@TYPE"><docType><xsl:value-of select="NO.DOC/@TYPE"/></docType></xsl:if>
        </xsl:if>
        <xsl:if test="AUTHOR"><author><xsl:value-of select="AUTHOR"/></author></xsl:if>
    </xsl:template>

    <xsl:template match="FMX/DOC.MAIN.PUB" mode="pageInfo">
        <xsl:if test="PAGE.FIRST"><pageFirst><xsl:value-of select="PAGE.FIRST"/></pageFirst></xsl:if>
        <xsl:if test="PAGE.LAST"><pageLast><xsl:value-of select="PAGE.LAST"/></pageLast></xsl:if>
        <xsl:if test="PAGE.TOTAL"><pageTotal><xsl:value-of select="PAGE.TOTAL"/></pageTotal></xsl:if>
    </xsl:template>

    <xsl:template match="PUBLICATION.REF">
        <xsl:if test="DATE/@ISO"><publicationDate><xsl:value-of select="concat(substring(DATE/@ISO,1,4), '-', substring(DATE/@ISO,5,2), '-', substring(DATE/@ISO,7,2))"/></publicationDate></xsl:if>
    </xsl:template>

    <!-- Mode templates for multi-file processing -->
    <xsl:template match="/DOC" mode="metadata">
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
    </xsl:template>

    <xsl:template match="/DOC" mode="body">
        <!-- DOC files typically contain metadata, not body content -->
    </xsl:template>

    <!-- Suppress unwanted elements in DOC documents -->
    <xsl:template match="BIB.DOC/PROD.ID | BIB.DOC/FIN.ID | BIB.DOC/DURAB | BIB.DOC/COM"/>
    <xsl:template match="PUBLICATION.REF/COLL | PUBLICATION.REF/NO.OJ | PUBLICATION.REF/LG.OJ | PUBLICATION.REF/VOLUME.REF"/>
    <xsl:template match="FMX | REF.PHYS | PAPER | PDF | VOLUME.PAPER | ITEM.VOLUME"/>

</xsl:stylesheet>