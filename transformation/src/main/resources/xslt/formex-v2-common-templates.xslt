<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <!--
    ============================================================================
    FORMEX V2 COMMON TEMPLATES
    ============================================================================
    
    This file contains shared templates used across all Formex v2 transformations.
    These templates handle mixed content processing, text extraction, and common
    utility functions.
    
    Template Modes:
    - mixed-content: Processes elements containing mixed text, dates, notes, etc.
    - text-content:  Extracts plain text content from elements
    
    Usage:
    Import this file in all other XSLT files to access shared functionality.
    This file should be imported FIRST to ensure proper template precedence.
    ============================================================================
    -->

    <!-- ===================================================================== -->
    <!-- MIXED CONTENT PROCESSING TEMPLATES                                   -->
    <!-- ===================================================================== -->
    
    <!--
    Generic mixed content template - delegates to specific element handlers
    -->
    <xsl:template match="*" mode="mixed-content">
        <xsl:apply-templates select="node()" mode="mixed-content"/>
    </xsl:template>
    
    <!--
    Handle non-empty text nodes - wrap in content element
    -->
    <xsl:template match="text()[normalize-space(.) != '']" mode="mixed-content">
        <content type="text">
            <value><xsl:value-of select="."/></value>
        </content>
    </xsl:template>
    
    <!--
    Handle whitespace-only text nodes - preserve for formatting
    -->
    <xsl:template match="text()[normalize-space(.) = '']" mode="mixed-content">
        <content type="text">
            <value><xsl:value-of select="."/></value>
        </content>
    </xsl:template>
    
    <!--
    Handle DATE elements with ISO attributes
    -->
    <xsl:template match="DATE" mode="mixed-content">
        <content type="date">
            <value iso="{@ISO}"><xsl:value-of select="normalize-space(.)"/></value>
        </content>
    </xsl:template>
    
    <!--
    Handle NOTE elements with optional attributes
    -->
    <xsl:template match="NOTE" mode="mixed-content">
        <content type="note">
            <value>
                <xsl:if test="@NOTE.ID">
                    <xsl:attribute name="id"><xsl:value-of select="@NOTE.ID"/></xsl:attribute>
                </xsl:if>
                <xsl:if test="@NUMBERING">
                    <xsl:attribute name="numbering"><xsl:value-of select="@NUMBERING"/></xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="node()" mode="mixed-content"/>
            </value>
        </content>
    </xsl:template>
    
    <!--
    Handle REF.DOC.OJ reference elements
    -->
    <xsl:template match="REF.DOC.OJ" mode="mixed-content">
        <content type="reference" referenceType="REF.DOC.OJ">
            <xsl:for-each select="@*">
                <xsl:attribute name="{local-name()}"><xsl:value-of select="."/></xsl:attribute>
            </xsl:for-each>
            <value><xsl:apply-templates select="." mode="text-content"/></value>
        </content>
    </xsl:template>
    
    <!--
    Handle HT (highlight/formatting) elements - CRITICAL for preserving formatting
    -->
    <xsl:template match="HT" mode="mixed-content">
        <content type="formatted" formatType="{@TYPE}">
            <value><xsl:value-of select="."/></value>
        </content>
    </xsl:template>
    
    <!--
    Handle P (paragraph) elements by processing their children
    -->
    <xsl:template match="P" mode="mixed-content">
        <xsl:apply-templates select="node()" mode="mixed-content"/>
    </xsl:template>
    
    <!--
    Note: Removed generic fallback template to avoid conflicts with specific element templates.
    Unknown elements will be handled by the generic "*" template above.
    -->

    <!-- ===================================================================== -->
    <!-- TEXT CONTENT EXTRACTION TEMPLATES                                    -->
    <!-- ===================================================================== -->
    
    <!--
    Generic text content extraction template
    Recursively extracts all text content from an element and its children
    -->
    <xsl:template match="*" mode="text-content">
        <xsl:choose>
            <xsl:when test="self::DATE[@ISO]">
                <xsl:value-of select="normalize-space(.)"/>
            </xsl:when>
            <xsl:when test="child::*">
                <xsl:for-each select="child::node()">
                    <xsl:choose>
                        <xsl:when test="self::text()">
                            <xsl:value-of select="."/>
                        </xsl:when>
                        <xsl:when test="self::*">
                            <xsl:apply-templates select="." mode="text-content"/>
                        </xsl:when>
                    </xsl:choose>
                </xsl:for-each>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="normalize-space(.)"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- ===================================================================== -->
    <!-- UTILITY TEMPLATES                                                    -->
    <!-- ===================================================================== -->
    
    <!--
    Check if an element contains any non-whitespace text content
    Returns: true() if element has meaningful text, false() otherwise
    -->
    <xsl:template name="has-text-content">
        <xsl:param name="element" select="."/>
        <xsl:choose>
            <xsl:when test="normalize-space($element) != ''">
                <xsl:value-of select="true()"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="false()"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- ===================================================================== -->
    <!-- LIST PROCESSING TEMPLATES                                            -->
    <!-- ===================================================================== -->
    
    <!--
    Main template for processing LIST elements recursively
    Handles nested lists with proper level tracking
    -->
    <xsl:template match="LIST" mode="process-list">
        <xsl:param name="level" select="1"/>
        
        <listType>
            <xsl:choose>
                <xsl:when test="@TYPE = 'alpha'">alpha</xsl:when>
                <xsl:when test="@TYPE = 'ALPHA'">ALPHA</xsl:when>
                <xsl:when test="@TYPE = 'ARAB'">ARAB</xsl:when>
                <xsl:when test="@TYPE = 'BULLET'">BULLET</xsl:when>
                <xsl:when test="@TYPE = 'DASH'">DASH</xsl:when>
                <xsl:when test="@TYPE = 'NDASH'">NDASH</xsl:when>
                <xsl:when test="@TYPE = 'NONE'">NONE</xsl:when>
                <xsl:when test="@TYPE = 'OTHER'">OTHER</xsl:when>
                <xsl:when test="@TYPE = 'ROMAN'">ROMAN</xsl:when>
                <xsl:when test="@TYPE = 'roman'">roman</xsl:when>
                <xsl:otherwise>OTHER</xsl:otherwise>
            </xsl:choose>
        </listType>
        <level><xsl:value-of select="$level"/></level>
        <items>
            <xsl:for-each select="ITEM">
                <listItem>
                    <!-- Extract item numbering if present -->
                    <xsl:if test="NP/NO.P and normalize-space(NP/NO.P) != ''">
                        <itemNumber><xsl:value-of select="normalize-space(NP/NO.P)"/></itemNumber>
                    </xsl:if>
                    
                    <!-- Process main item content -->
                    <xsl:if test="NP/TXT">
                        <itemContent>
                            <xsl:apply-templates select="NP/TXT" mode="mixed-content"/>
                        </itemContent>
                    </xsl:if>
                    
                    <!-- Process additional paragraphs and nested content -->
                    <!-- Check for P elements both as direct children and within NP -->
                    <xsl:variable name="allParagraphs" select="P | NP/P"/>
                    <xsl:if test="$allParagraphs">
                        <xsl:variable name="paragraphsWithoutLists" select="$allParagraphs[not(LIST)]"/>
                        <xsl:variable name="paragraphsWithLists" select="$allParagraphs[LIST]"/>
                        
                        <!-- Additional paragraphs (those without nested lists) -->
                        <xsl:if test="$paragraphsWithoutLists">
                            <additionalParagraphs>
                                <xsl:for-each select="$paragraphsWithoutLists">
                                    <mixedContent>
                                        <xsl:apply-templates select="." mode="mixed-content"/>
                                    </mixedContent>
                                </xsl:for-each>
                            </additionalParagraphs>
                        </xsl:if>
                        
                        <!-- Nested lists -->
                        <xsl:if test="$paragraphsWithLists">
                            <nestedContent>
                                <xsl:for-each select="$paragraphsWithLists/LIST">
                                    <list>
                                        <xsl:apply-templates select="." mode="process-list">
                                            <xsl:with-param name="level" select="$level + 1"/>
                                        </xsl:apply-templates>
                                    </list>
                                </xsl:for-each>
                            </nestedContent>
                        </xsl:if>
                    </xsl:if>
                </listItem>
            </xsl:for-each>
        </items>
    </xsl:template>
    
    <!--
    Template to check if an element contains lists that should be processed separately
    This helps determine whether to process content as structured lists or flat paragraphs
    -->
    <xsl:template name="contains-lists">
        <xsl:param name="element" select="."/>
        <xsl:choose>
            <xsl:when test="$element//LIST[not(ancestor::TITLE) and not(ancestor::TI)]">
                <xsl:value-of select="true()"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="false()"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- ===================================================================== -->
    <!-- TABLE PROCESSING TEMPLATES                                           -->
    <!-- ===================================================================== -->
    
    <!--
    Process TBL element into structured table representation
    -->
    <xsl:template match="TBL" mode="process-table">
        <columns><xsl:value-of select="@COLS"/></columns>
        <xsl:if test="@NO.SEQ">
            <sequenceNumber><xsl:value-of select="@NO.SEQ"/></sequenceNumber>
        </xsl:if>
        <rows>
            <xsl:for-each select="CORPUS/ROW">
                <tableRow>
                    <cells>
                        <xsl:for-each select="CELL">
                            <tableCell>
                                <column><xsl:value-of select="@COL"/></column>
                                <xsl:if test="@COLSPAN">
                                    <columnSpan><xsl:value-of select="@COLSPAN"/></columnSpan>
                                </xsl:if>
                                <cellContent>
                                    <xsl:apply-templates select="." mode="process-cell-content"/>
                                </cellContent>
                            </tableCell>
                        </xsl:for-each>
                    </cells>
                </tableRow>
            </xsl:for-each>
        </rows>
    </xsl:template>
    
    <!--
    Process cell content - handles various content types within table cells
    -->
    <xsl:template match="CELL" mode="process-cell-content">
        <xsl:choose>
            <!-- Handle lists within cells -->
            <xsl:when test="LIST">
                <xsl:for-each select="LIST">
                    <list>
                        <xsl:apply-templates select="." mode="process-list"/>
                    </list>
                </xsl:for-each>
                <!-- Also process any remaining non-list content -->
                <xsl:if test="*[not(self::LIST)] or text()[normalize-space()]">
                    <paragraph>
                        <xsl:apply-templates select="node()[not(self::LIST)]" mode="mixed-content"/>
                    </paragraph>
                </xsl:if>
            </xsl:when>
            
            <!-- Handle IE (input element) - empty form field -->
            <xsl:when test="IE">
                <paragraph>
                    <content type="input">
                        <value></value>
                    </content>
                </paragraph>
            </xsl:when>
            
            <!-- Handle structured content (NP, P, TXT, HT) -->
            <xsl:when test="NP or P or TXT or HT">
                <paragraph>
                    <xsl:apply-templates select="." mode="mixed-content"/>
                </paragraph>
            </xsl:when>
            
            <!-- Handle simple text content -->
            <xsl:when test="text()[normalize-space()]">
                <paragraph>
                    <content type="text">
                        <value><xsl:value-of select="normalize-space(.)"/></value>
                    </content>
                </paragraph>
            </xsl:when>
            
            <!-- Handle mixed content (multiple P elements, etc.) -->
            <xsl:when test="count(P) > 1">
                <xsl:for-each select="P">
                    <paragraph>
                        <xsl:apply-templates select="." mode="mixed-content"/>
                    </paragraph>
                </xsl:for-each>
            </xsl:when>
            
            <!-- Empty cell -->
            <xsl:otherwise>
                <paragraph>
                    <content type="text">
                        <value></value>
                    </content>
                </paragraph>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <!--
    Template to check if an element contains tables
    -->
    <xsl:template name="has-tables">
        <xsl:param name="element" select="."/>
        <xsl:choose>
            <xsl:when test="$element//TBL[not(ancestor::TITLE) and not(ancestor::TI)]">
                <xsl:value-of select="true()"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="false()"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>