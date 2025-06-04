<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    
    <xsl:output method="xml" indent="yes" omit-xml-declaration="no"/>
    
    <!-- Strict validation: Only one DOC, one ACT, and multiple ANNEX allowed -->
    <xsl:template match="/file-list">
        
        <!-- Collect document types -->
        <xsl:variable name="docCount" select="count(file[document(@path)/DOC])"/>
        <xsl:variable name="actCount" select="count(file[document(@path)/ACT])"/>
        <xsl:variable name="annexCount" select="count(file[document(@path)/ANNEX])"/>
        
        <!-- Validation: Check document counts -->
        <xsl:if test="$docCount eq 0">
            <xsl:message terminate="yes">
                <xsl:text>ERROR: No DOC element found. Exactly one DOC element is required.</xsl:text>
            </xsl:message>
        </xsl:if>
        
        <xsl:if test="$docCount gt 1">
            <xsl:message terminate="yes">
                <xsl:text>ERROR: Multiple DOC elements found (</xsl:text>
                <xsl:value-of select="$docCount"/>
                <xsl:text>). Only one DOC element is allowed.</xsl:text>
            </xsl:message>
        </xsl:if>
        
        <xsl:if test="$actCount eq 0">
            <xsl:message terminate="yes">
                <xsl:text>ERROR: No ACT element found. Exactly one ACT element is required.</xsl:text>
            </xsl:message>
        </xsl:if>
        
        <xsl:if test="$actCount gt 1">
            <xsl:message terminate="yes">
                <xsl:text>ERROR: Multiple ACT elements found (</xsl:text>
                <xsl:value-of select="$actCount"/>
                <xsl:text>). Only one ACT element is allowed.</xsl:text>
            </xsl:message>
        </xsl:if>
        
        <xsl:if test="$annexCount eq 0">
            <xsl:message terminate="yes">
                <xsl:text>ERROR: No ANNEX elements found. At least one ANNEX element is required.</xsl:text>
            </xsl:message>
        </xsl:if>
        
        <!-- Validation: Check for unsupported elements -->
        <xsl:for-each select="file">
            <xsl:variable name="filepath" select="@path"/>
            <xsl:variable name="doc" select="document($filepath)"/>
            <xsl:variable name="rootElement" select="local-name($doc/*)"/>
            
            <xsl:if test="not($rootElement = ('DOC', 'ACT', 'ANNEX'))">
                <xsl:message terminate="yes">
                    <xsl:text>ERROR: Unsupported root element '</xsl:text>
                    <xsl:value-of select="$rootElement"/>
                    <xsl:text>' found in file: </xsl:text>
                    <xsl:value-of select="$filepath"/>
                    <xsl:text>. Only DOC, ACT, and ANNEX elements are allowed.</xsl:text>
                </xsl:message>
            </xsl:if>
        </xsl:for-each>
        
        <!-- Validation passed - produce ordered output -->
        
        <!-- 1. Process DOC element (exactly one) -->
        <xsl:for-each select="file[document(@path)/DOC]">
            <xsl:copy-of select="document(@path)/DOC"/>
        </xsl:for-each>
        
        <!-- 2. Process ACT element (exactly one) -->
        <xsl:for-each select="file[document(@path)/ACT]">
            <xsl:copy-of select="document(@path)/ACT"/>
        </xsl:for-each>
        
        <!-- 3. Process ANNEX elements (one or more, maintain file order) -->
        <xsl:for-each select="file[document(@path)/ANNEX]">
            <xsl:copy-of select="document(@path)/ANNEX"/>
        </xsl:for-each>
        
    </xsl:template>
    
</xsl:stylesheet>