<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
                xmlns:o="urn:schemas-microsoft-com:office:office"
                xmlns:x="urn:schemas-microsoft-com:office:excel"
                xmlns:html="http://www.w3.org/TR/REC-html40">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

    <!-- Constants -->
    <xsl:variable name="source">Commission Implementing Regulation (EU) 2015/1998</xsl:variable>
    <xsl:variable name="source_date">2015-11-05</xsl:variable>
    <xsl:variable name="valid_from">2016-02-01</xsl:variable>

    <!-- Main template -->
    <xsl:template match="/">
        <ss:Workbook>
            <ss:Worksheet ss:Name="Requirements">
                <ss:Table>
                    <!-- Header row -->
                    <ss:Row>
                        <ss:Cell><ss:Data ss:Type="String">Number</ss:Data></ss:Cell>
                        <ss:Cell><ss:Data ss:Type="String">Requirement</ss:Data></ss:Cell>
                        <ss:Cell><ss:Data ss:Type="String">Guidance</ss:Data></ss:Cell>
                        <ss:Cell><ss:Data ss:Type="String">Description</ss:Data></ss:Cell>
                        <ss:Cell><ss:Data ss:Type="String">Source</ss:Data></ss:Cell>
                        <ss:Cell><ss:Data ss:Type="String">Source Date</ss:Data></ss:Cell>
                        <ss:Cell><ss:Data ss:Type="String">Valid from</ss:Data></ss:Cell>
                        <ss:Cell><ss:Data ss:Type="String">Valid to</ss:Data></ss:Cell>
                        <ss:Cell><ss:Data ss:Type="String">Changed</ss:Data></ss:Cell>
                    </ss:Row>

                    <!-- Process all regulation points with numbered identifiers -->
                    <xsl:apply-templates select="//ANNEX//GR.SEQ[NO.GR.SEQ]"/>
                </ss:Table>
            </ss:Worksheet>
        </ss:Workbook>
    </xsl:template>

    <!-- Template for requirement sections with numbered identifiers -->
    <xsl:template match="GR.SEQ[NO.GR.SEQ]">
        <ss:Row>
            <!-- Number -->
            <ss:Cell><ss:Data ss:Type="String"><xsl:value-of select="NO.GR.SEQ"/></ss:Data></ss:Cell>

            <!-- Requirement - Use the first paragraph as the main requirement -->
            <ss:Cell>
                <ss:Data ss:Type="String">
                    <xsl:choose>
                        <xsl:when test="P[1]">
                            <xsl:value-of select="normalize-space(P[1])"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <!-- Handle case when there's no direct paragraph -->
                            <xsl:text>See guidance</xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </ss:Data>
            </ss:Cell>

            <!-- Guidance - Use additional paragraphs and lists as guidance -->
            <ss:Cell>
                <ss:Data ss:Type="String">
                    <!-- Additional paragraphs -->
                    <xsl:for-each select="P[position() > 1]">
                        <xsl:value-of select="normalize-space(.)"/>
                        <xsl:if test="position() != last()"><xsl:text>&#10;</xsl:text></xsl:if>
                    </xsl:for-each>

                    <!-- Add list items if present -->
                    <xsl:if test="LIST and P[position() > 1]">
                        <xsl:text>&#10;</xsl:text>
                    </xsl:if>

                    <xsl:for-each select="LIST/ITEM">
                        <xsl:choose>
                            <xsl:when test="NP/NO.P">
                                <xsl:value-of select="NP/NO.P"/><xsl:text> </xsl:text>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:text>- </xsl:text>
                            </xsl:otherwise>
                        </xsl:choose>

                        <xsl:choose>
                            <xsl:when test="NP/TXT">
                                <xsl:value-of select="normalize-space(NP/TXT)"/>
                            </xsl:when>
                            <xsl:when test="P">
                                <xsl:value-of select="normalize-space(P)"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="normalize-space(.)"/>
                            </xsl:otherwise>
                        </xsl:choose>

                        <xsl:if test="position() != last()"><xsl:text>&#10;</xsl:text></xsl:if>
                    </xsl:for-each>
                </ss:Data>
            </ss:Cell>

            <!-- Description - Use section title from parent or ancestor -->
            <ss:Cell>
                <ss:Data ss:Type="String">
                    <xsl:choose>
                        <xsl:when test="ancestor::GR.SEQ[TITLE]">
                            <xsl:for-each select="ancestor::GR.SEQ[TITLE][1]/TITLE">
                                <xsl:choose>
                                    <xsl:when test="TI/NP/TXT">
                                        <xsl:value-of select="normalize-space(TI/NP/TXT)"/>
                                    </xsl:when>
                                    <xsl:when test="TI/P">
                                        <xsl:value-of select="normalize-space(TI/P)"/>
                                    </xsl:when>
                                </xsl:choose>
                            </xsl:for-each>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text>General requirement</xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </ss:Data>
            </ss:Cell>

            <!-- Source -->
            <ss:Cell><ss:Data ss:Type="String"><xsl:value-of select="$source"/></ss:Data></ss:Cell>

            <!-- Source Date -->
            <ss:Cell><ss:Data ss:Type="String"><xsl:value-of select="$source_date"/></ss:Data></ss:Cell>

            <!-- Valid from -->
            <ss:Cell><ss:Data ss:Type="String"><xsl:value-of select="$valid_from"/></ss:Data></ss:Cell>

            <!-- Valid to - empty -->
            <ss:Cell><ss:Data ss:Type="String"></ss:Data></ss:Cell>

            <!-- Changed - empty -->
            <ss:Cell><ss:Data ss:Type="String"></ss:Data></ss:Cell>
        </ss:Row>
    </xsl:template>
</xsl:stylesheet>