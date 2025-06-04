<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <!-- Mode template for use in main transformer -->
    <xsl:template match="/" mode="annexes-mode">
        <annexes>
            <xsl:for-each select="//ANNEX">
                <section>
                    <xsl:attribute name="id">
                        <xsl:choose>
                            <xsl:when test="@ID">
                                <xsl:value-of select="@ID"/>
                            </xsl:when>
                            <xsl:when test="BIB.INSTANCE/NO.SEQ">
                                <xsl:value-of select="BIB.INSTANCE/NO.SEQ"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:message terminate="yes">ERROR: No @ID or BIB.INSTANCE/NO.SEQ found for annex section - cannot generate section ID</xsl:message>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:attribute>
                    <xsl:attribute name="level">
                        <xsl:choose>
                            <xsl:when test="@LEVEL">
                                <xsl:value-of select="@LEVEL"/>
                            </xsl:when>
                            <xsl:when test="descendant::GR.SEQ[@LEVEL][1]/@LEVEL">
                                <xsl:value-of select="descendant::GR.SEQ[@LEVEL][1]/@LEVEL"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:message terminate="yes">ERROR: No @LEVEL attribute found on ANNEX or descendant GR.SEQ elements - section level is required</xsl:message>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:attribute>
                    
                    <title>
                        <xsl:choose>
                            <xsl:when test="TITLE/TI">
                                <xsl:apply-templates select="TITLE/TI" mode="mixed-content"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:message terminate="yes">ERROR: No TITLE/TI found for annex title</xsl:message>
                            </xsl:otherwise>
                        </xsl:choose>
                    </title>
                    
                    <!-- Process content using table-aware strategy -->
                    <xsl:choose>
                        <xsl:when test="descendant::TBL[not(ancestor::TITLE) and not(ancestor::TI)]">
                            <!-- Process tables first as structured content -->
                            <xsl:for-each select="descendant::TBL[not(ancestor::TITLE) and not(ancestor::TI)]">
                                <contentItems>
                                    <table>
                                        <xsl:apply-templates select="." mode="process-table"/>
                                    </table>
                                </contentItems>
                            </xsl:for-each>
                            
                            <!-- Then process lists (excluding those inside tables) -->
                            <xsl:for-each select="descendant::LIST[not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::LIST) and not(ancestor::ITEM) and not(ancestor::DLIST.ITEM) and not(ancestor::DEFINITION) and not(ancestor::TBL)]">
                                <contentItems>
                                    <list>
                                        <xsl:apply-templates select="." mode="process-list"/>
                                    </list>
                                </contentItems>
                            </xsl:for-each>
                            
                            <!-- Then process remaining non-table, non-list content -->
                            <xsl:for-each select="descendant::*[self::P or self::AL or self::NP or self::TXT]
                                                                 [not(ancestor::TITLE) and not(ancestor::TI)]
                                                                 [not(ancestor::LIST)]
                                                                 [not(ancestor::TBL)]">
                                <contentItems>
                                    <paragraph>
                                        <xsl:apply-templates select="." mode="mixed-content"/>
                                    </paragraph>
                                </contentItems>
                            </xsl:for-each>
                        </xsl:when>
                        <xsl:when test="descendant::LIST[not(ancestor::TITLE) and not(ancestor::TI)]">
                            <!-- Process lists first as structured content -->
                            <xsl:for-each select="descendant::LIST[not(ancestor::TITLE) and not(ancestor::TI) and not(ancestor::LIST) and not(ancestor::ITEM) and not(ancestor::DLIST.ITEM) and not(ancestor::DEFINITION)]">
                                <contentItems>
                                    <list>
                                        <xsl:apply-templates select="." mode="process-list"/>
                                    </list>
                                </contentItems>
                            </xsl:for-each>
                            
                            <!-- Then process remaining non-list content -->
                            <xsl:for-each select="descendant::*[self::P or self::AL or self::NP or self::TXT]
                                                                 [not(ancestor::TITLE) and not(ancestor::TI)]
                                                                 [not(ancestor::LIST)]">
                                <contentItems>
                                    <paragraph>
                                        <xsl:apply-templates select="." mode="mixed-content"/>
                                    </paragraph>
                                </contentItems>
                            </xsl:for-each>
                        </xsl:when>
                        <xsl:when test="descendant::*[self::P or self::AL or self::NP or self::TXT][not(ancestor::TITLE) and not(ancestor::TI)]">
                            <!-- No tables or lists found, process as regular paragraphs -->
                            <xsl:for-each select="descendant::*[self::P or self::AL or self::NP or self::TXT][not(ancestor::TITLE) and not(ancestor::TI)]">
                                <contentItems>
                                    <paragraph>
                                        <xsl:apply-templates select="." mode="mixed-content"/>
                                    </paragraph>
                                </contentItems>
                            </xsl:for-each>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:message terminate="yes">ERROR: No content found for annex - required content elements (P, AL, NP, TXT) not found</xsl:message>
                        </xsl:otherwise>
                    </xsl:choose>
                </section>
            </xsl:for-each>
        </annexes>
    </xsl:template>

</xsl:stylesheet>