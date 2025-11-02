<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:formex="http://formex.publications.europa.eu/functions">
    
    <!-- 
        Common Transformation Patterns
        
        This stylesheet contains common transformation patterns that are
        frequently used across different Formex version transformations.
        
        Patterns included:
        - Attribute group removal (gr.styles)
        - Element repeatability changes
        - Element removal/addition
        - Content model changes
        
        Usage: Import this stylesheet to access common patterns
    -->
    
    <!-- Import base utilities -->
    <xsl:import href="identity.xsl"/>
    <xsl:import href="schema-updater.xsl"/>
    
    <!-- 
        Pattern: Remove attribute groups (commonly gr.styles)
        Usage: Call this template with element name and attribute group name
    -->
    <xsl:template name="remove-attribute-group">
        <xsl:param name="element-name" as="xs:string"/>
        <xsl:param name="attribute-group" as="xs:string" select="'gr.styles'"/>
        
        <!-- This would be used in specific transformations -->
        <!-- Example: <xsl:template match="DOC[@gr.styles]"> remove @gr.styles -->
    </xsl:template>
    
    <!-- 
        Pattern: Change element cardinality (e.g., make repeatable)
        Common case: NO.DOC from single to maxOccurs="unbounded"
    -->
    <xsl:template name="handle-cardinality-change">
        <xsl:param name="parent-element" as="xs:string"/>
        <xsl:param name="child-element" as="xs:string"/>
        <xsl:param name="old-max-occurs" as="xs:string" select="'1'"/>
        <xsl:param name="new-max-occurs" as="xs:string" select="'unbounded'"/>
        
        <!-- Identity transformation handles this automatically -->
        <!-- But this template documents the pattern for reference -->
    </xsl:template>
    
    <!-- 
        Pattern: Remove specific elements
        Usage: Override this template for specific elements to remove
    -->
    <xsl:template match="*" mode="remove-element">
        <xsl:param name="element-name" as="xs:string"/>
        <xsl:if test="local-name() != $element-name">
            <xsl:copy>
                <xsl:apply-templates select="@* | node()"/>
            </xsl:copy>
        </xsl:if>
    </xsl:template>
    
    <!-- 
        Pattern: Add new elements at specific positions
        Usage: Override in specific transformations
    -->
    <xsl:template name="add-element">
        <xsl:param name="parent-context" as="element()"/>
        <xsl:param name="element-name" as="xs:string"/>
        <xsl:param name="element-content" as="item()*"/>
        <xsl:param name="position" as="xs:string" select="'last'"/>
        
        <!-- Implementation would depend on specific use case -->
    </xsl:template>
    
    <!-- 
        Pattern: Handle BIB.DOC structural changes
        Common changes in BIB.DOC across versions
    -->
    <xsl:template match="BIB.DOC" mode="structural-change">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            
            <!-- Apply templates to children, allowing for insertions/removals -->
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>
    
    <!-- 
        Pattern: Handle document type additions (e.g., LSEU in v5.30)
        This pattern shows how to handle new document types
    -->
    <xsl:template match="DOC" mode="handle-new-doc-types">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            
            <!-- Process existing content -->
            <xsl:apply-templates select="node()"/>
            
            <!-- Add support for new document types would go here -->
        </xsl:copy>
    </xsl:template>
    
    <!-- 
        Pattern: Version-specific element handling
        Template to handle elements that only exist in certain versions
    -->
    <xsl:template name="version-specific-element">
        <xsl:param name="source-version" as="xs:string"/>
        <xsl:param name="target-version" as="xs:string"/>
        <xsl:param name="element-name" as="xs:string"/>
        <xsl:param name="introduced-in" as="xs:string"/>
        <xsl:param name="removed-in" as="xs:string" select="''"/>
        
        <!-- Logic to determine if element should be included -->
        <xsl:variable name="should-include" as="xs:boolean">
            <xsl:choose>
                <xsl:when test="$removed-in != '' and formex:version-compare($target-version, $removed-in) >= 0">
                    <xsl:value-of select="false()"/>
                </xsl:when>
                <xsl:when test="formex:version-compare($target-version, $introduced-in) >= 0">
                    <xsl:value-of select="true()"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="false()"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <!-- Would be used in specific element templates -->
    </xsl:template>
    
    <!-- 
        Utility function: Compare version strings
        Returns: -1 if v1 < v2, 0 if v1 = v2, 1 if v1 > v2
    -->
    <xsl:function name="formex:version-compare" as="xs:integer">
        <xsl:param name="version1" as="xs:string"/>
        <xsl:param name="version2" as="xs:string"/>
        
        <!-- Extract major.minor version numbers for comparison -->
        <xsl:variable name="v1-parts" select="tokenize(substring-before($version1, '-'), '\.')"/>
        <xsl:variable name="v2-parts" select="tokenize(substring-before($version2, '-'), '\.')"/>
        
        <!-- Simple numeric comparison (could be enhanced) -->
        <xsl:variable name="v1-major" select="xs:integer($v1-parts[1])"/>
        <xsl:variable name="v1-minor" select="if (count($v1-parts) > 1) then xs:integer($v1-parts[2]) else 0"/>
        <xsl:variable name="v2-major" select="xs:integer($v2-parts[1])"/>
        <xsl:variable name="v2-minor" select="if (count($v2-parts) > 1) then xs:integer($v2-parts[2]) else 0"/>
        
        <xsl:choose>
            <xsl:when test="$v1-major &lt; $v2-major">-1</xsl:when>
            <xsl:when test="$v1-major &gt; $v2-major">1</xsl:when>
            <xsl:when test="$v1-minor &lt; $v2-minor">-1</xsl:when>
            <xsl:when test="$v1-minor &gt; $v2-minor">1</xsl:when>
            <xsl:otherwise>0</xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
</xsl:stylesheet>