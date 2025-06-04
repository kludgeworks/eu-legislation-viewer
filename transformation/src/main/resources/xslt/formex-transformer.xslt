<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fmx="http://opoce"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                exclude-result-prefixes="fmx xsi">

    <!-- Import specialized transformers -->
    <xsl:import href="./formex-doc-transformer.xslt"/>
    <xsl:import href="./formex-act-transformer.xslt"/>
    <xsl:import href="./formex-annex-transformer.xslt"/>

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="*"/>

    <xsl:param name="sourceFileName" select="'unknown_source.xml'"/>
    <xsl:param name="filePaths" select="''"/>

    <!-- Root template that combines multiple input files into a single SimplifiedLegislationDoc -->
    <xsl:template match="/">
        <xsl:choose>
            <xsl:when test="$filePaths != ''">
                <!-- Multiple files mode - combine into SimplifiedLegislationDoc -->
                <simplifiedLegislationDoc>
                    <!-- Load all documents from the semicolon-separated file paths -->
                    <xsl:variable name="allDocs">
                        <xsl:for-each select="tokenize($filePaths, ';')">
                            <xsl:if test="normalize-space(.) != ''">
                                <xsl:copy-of select="document(.)"/>
                            </xsl:if>
                        </xsl:for-each>
                    </xsl:variable>
                    
                    <!-- Separate files by document type -->
                    <xsl:variable name="docFiles" select="$allDocs/DOC"/>
                    <xsl:variable name="actFiles" select="$allDocs/ACT"/>
                    <xsl:variable name="annexFiles" select="$allDocs/ANNEX"/>
                    
                    <!-- Extract metadata primarily from DOC and ACT files -->
                    <metadata>
                        <xsl:apply-templates select="$docFiles" mode="metadata"/>
                        <xsl:apply-templates select="$actFiles" mode="metadata"/>
                    </metadata>
                    
                    <!-- Extract body content from ACT and ANNEX files -->
                    <body>
                        <xsl:apply-templates select="$actFiles" mode="body"/>
                        
                        <!-- Add annexes if present -->
                        <xsl:if test="$annexFiles">
                            <annexes>
                                <xsl:apply-templates select="$annexFiles" mode="annex"/>
                            </annexes>
                        </xsl:if>
                    </body>
                </simplifiedLegislationDoc>
            </xsl:when>
            <xsl:otherwise>
                <!-- Single file mode - delegate to specialized transformers -->
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
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>