<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Extract metadata from DOC element -->
    <xsl:template match="DOC">
        <xsl:param name="actElement"/>
        <metadata>
            <!-- Required: docIdString -->
            <docIdString>
                <xsl:choose>
                    <xsl:when test="BIB.DOC/NO.DOC">
                        <xsl:value-of select="concat(BIB.DOC/NO.DOC/COM, '/', BIB.DOC/NO.DOC/NO.CURRENT, '/', BIB.DOC/NO.DOC/YEAR)"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required element BIB.DOC/NO.DOC not found for docIdString</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </docIdString>
            
            <!-- Required: docNumberCurrent -->
            <docNumberCurrent>
                <xsl:choose>
                    <xsl:when test="BIB.DOC/NO.DOC/NO.CURRENT">
                        <xsl:value-of select="BIB.DOC/NO.DOC/NO.CURRENT"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required element BIB.DOC/NO.DOC/NO.CURRENT not found for docNumberCurrent</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </docNumberCurrent>
            
            <!-- Required: docYear -->
            <docYear>
                <xsl:choose>
                    <xsl:when test="BIB.DOC/NO.DOC/YEAR">
                        <xsl:value-of select="BIB.DOC/NO.DOC/YEAR"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required element BIB.DOC/NO.DOC/YEAR not found for docYear</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </docYear>
            
            <!-- Required: docType -->
            <docType>
                <xsl:choose>
                    <xsl:when test="BIB.DOC/NO.DOC/@TYPE">
                        <xsl:value-of select="BIB.DOC/NO.DOC/@TYPE"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required attribute BIB.DOC/NO.DOC/@TYPE not found for docType</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </docType>
            
            <!-- Required: author -->
            <author>
                <xsl:choose>
                    <xsl:when test="BIB.DOC/AUTHOR">
                        <xsl:value-of select="BIB.DOC/AUTHOR"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required element BIB.DOC/AUTHOR not found for author</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </author>
            
            <!-- Required: publicationDate -->
            <publicationDate>
                <xsl:choose>
                    <xsl:when test="PUBLICATION.REF/DATE/@ISO">
                        <xsl:attribute name="iso">
                            <xsl:value-of select="PUBLICATION.REF/DATE/@ISO"/>
                        </xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="PUBLICATION.REF/DATE/text()">
                                <xsl:value-of select="PUBLICATION.REF/DATE/text()"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="PUBLICATION.REF/DATE/@ISO"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required attribute PUBLICATION.REF/DATE/@ISO not found for publicationDate</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </publicationDate>
            
            <!-- Required: legalValue -->
            <legalValue>
                <xsl:choose>
                    <xsl:when test="FMX/DOC.MAIN.PUB/LEGAL.VALUE">
                        <xsl:value-of select="FMX/DOC.MAIN.PUB/LEGAL.VALUE"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required element FMX/DOC.MAIN.PUB/LEGAL.VALUE not found for legalValue</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </legalValue>
            
            <!-- Required: entryIntoForceDate -->
            <entryIntoForceDate>
                <xsl:choose>
                    <xsl:when test="$actElement//ARTICLE/ALINEA/DATE/@ISO">
                        <xsl:variable name="dateElement" select="($actElement//ARTICLE/ALINEA/DATE[@ISO])[1]"/>
                        <xsl:attribute name="iso">
                            <xsl:value-of select="$dateElement/@ISO"/>
                        </xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$dateElement/text()">
                                <xsl:value-of select="$dateElement/text()"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="$dateElement/@ISO"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:when>
                    <xsl:when test="$actElement//ARTICLE/descendant::DATE/@ISO">
                        <xsl:variable name="dateElement" select="($actElement//ARTICLE/descendant::DATE[@ISO])[1]"/>
                        <xsl:attribute name="iso">
                            <xsl:value-of select="$dateElement/@ISO"/>
                        </xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$dateElement/text()">
                                <xsl:value-of select="$dateElement/text()"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="$dateElement/@ISO"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required entry into force date not found in ACT element - no DATE/@ISO found in any ARTICLE</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </entryIntoForceDate>
            
            <!-- Required: title -->
            <title>
                <xsl:choose>
                    <xsl:when test="PAPER/VOLUME.PAPER/ITEM.VOLUME/TITLE/TI/P">
                        <xsl:for-each select="PAPER/VOLUME.PAPER/ITEM.VOLUME/TITLE/TI/P">
                            <xsl:apply-templates select="." mode="mixed-content"/>
                            <xsl:if test="position() != last()">
                                <fragments type="TEXT"> </fragments>
                            </xsl:if>
                        </xsl:for-each>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: Required element PAPER/VOLUME.PAPER/ITEM.VOLUME/TITLE/TI/P not found for title</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </title>
            
        </metadata>
    </xsl:template>

</xsl:stylesheet>