<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Mode template for use in main transformer -->
    <xsl:template match="ACT" mode="articles-mode">
        <articles>
            <xsl:choose>
                <xsl:when test="ENACTING.TERMS">
                    <xsl:apply-templates select="ENACTING.TERMS" mode="articles"/>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Error if no articles found -->
                    <xsl:message terminate="yes">ERROR: No ENACTING.TERMS found - articles are required</xsl:message>
                </xsl:otherwise>
            </xsl:choose>
        </articles>
    </xsl:template>

    <!-- Process enacting terms as articles -->
    <xsl:template match="ENACTING.TERMS" mode="articles">
        <xsl:for-each select="descendant::*[self::ART or self::ARTICLE]">
            <article>
                <xsl:attribute name="id">
                    <xsl:choose>
                        <xsl:when test="@ID">
                            <xsl:value-of select="@ID"/>
                        </xsl:when>
                        <xsl:when test="@IDENTIFIER">
                            <xsl:value-of select="@IDENTIFIER"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:message terminate="yes">ERROR: No @ID or @IDENTIFIER attribute found for article - cannot generate article ID</xsl:message>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:attribute>
                
                <title>
                    <xsl:choose>
                        <xsl:when test="TITLE or TI or TI.ART">
                            <xsl:apply-templates select="(TITLE | TI | TI.ART)[1]" mode="mixed-content"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:message terminate="yes">ERROR: No TITLE, TI, or TI.ART element found for article title - article titles are required</xsl:message>
                        </xsl:otherwise>
                    </xsl:choose>
                </title>
                
                <!-- Process content using table-aware strategy -->
                <xsl:choose>
                    <xsl:when test="descendant::TBL[not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::TI.ART)]">
                        <!-- Process tables first as structured content -->
                        <xsl:for-each select="descendant::TBL[not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::TI.ART)]">
                            <contentItems>
                                <table>
                                    <xsl:apply-templates select="." mode="process-table"/>
                                </table>
                            </contentItems>
                        </xsl:for-each>
                        
                        <!-- Then process lists (excluding those inside tables) -->
                        <xsl:for-each select="descendant::LIST[not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::TI.ART) and not(ancestor::LIST) and not(ancestor::ITEM) and not(ancestor::DLIST.ITEM) and not(ancestor::DEFINITION) and not(ancestor::TBL)]">
                            <contentItems>
                                <list>
                                    <xsl:apply-templates select="." mode="process-list"/>
                                </list>
                            </contentItems>
                        </xsl:for-each>
                        
                        <!-- Then process remaining non-table, non-list content -->
                        <xsl:for-each select="descendant::*[self::P or self::AL or self::NP or self::TXT or self::ALINEA]
                                                             [not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::TI.ART)]
                                                             [not(ancestor::LIST)]
                                                             [not(ancestor::TBL)]">
                            <contentItems>
                                <paragraph>
                                    <xsl:apply-templates select="." mode="mixed-content"/>
                                </paragraph>
                            </contentItems>
                        </xsl:for-each>
                    </xsl:when>
                    <xsl:when test="descendant::LIST[not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::TI.ART)]">
                        <!-- Process lists first as structured content -->
                        <xsl:for-each select="descendant::LIST[not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::TI.ART) and not(ancestor::LIST) and not(ancestor::ITEM) and not(ancestor::DLIST.ITEM) and not(ancestor::DEFINITION)]">
                            <contentItems>
                                <list>
                                    <xsl:apply-templates select="." mode="process-list"/>
                                </list>
                            </contentItems>
                        </xsl:for-each>
                        
                        <!-- Then process remaining non-list content -->
                        <xsl:for-each select="descendant::*[self::P or self::AL or self::NP or self::TXT or self::ALINEA]
                                                             [not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::TI.ART)]
                                                             [not(ancestor::LIST)]">
                            <contentItems>
                                <paragraph>
                                    <xsl:apply-templates select="." mode="mixed-content"/>
                                </paragraph>
                            </contentItems>
                        </xsl:for-each>
                    </xsl:when>
                    <xsl:when test="descendant::*[self::P or self::AL or self::NP or self::TXT or self::ALINEA][not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::TI.ART)]">
                        <!-- No tables or lists found, process as regular paragraphs -->
                        <xsl:for-each select="descendant::*[self::P or self::AL or self::NP or self::TXT or self::ALINEA][not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::TI.ART)]">
                            <contentItems>
                                <paragraph>
                                    <xsl:apply-templates select="." mode="mixed-content"/>
                                </paragraph>
                            </contentItems>
                        </xsl:for-each>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:message terminate="yes">ERROR: No content found for article - required content elements (P, AL, NP, TXT, ALINEA) not found</xsl:message>
                    </xsl:otherwise>
                </xsl:choose>
            </article>
        </xsl:for-each>
    </xsl:template>

</xsl:stylesheet>