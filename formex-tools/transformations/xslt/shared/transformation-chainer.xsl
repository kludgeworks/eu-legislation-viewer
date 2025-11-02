<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="fmx xs">
    
    <!-- 
        Formex Transformation Chainer
        
        This stylesheet provides the core chaining logic to apply multiple
        sequential transformations from one version to another.
        
        It works by recursively applying individual step transformations
        until the target version is reached.
    -->
    
    <xsl:import href="version-detector.xsl"/>
    
    <!-- Main chaining template -->
    <xsl:template name="fmx:chain-transformations">
        <xsl:param name="input-doc" as="document-node()"/>
        <xsl:param name="element-type" as="xs:string"/>
        <xsl:param name="from-version" as="xs:string"/>
        <xsl:param name="to-version" as="xs:string"/>
        
        <xsl:choose>
            <!-- If we're already at the target version, return the document -->
            <xsl:when test="$from-version = $to-version">
                <xsl:copy-of select="$input-doc"/>
            </xsl:when>
            
            <!-- If we can't transform (invalid path), return error -->
            <xsl:when test="not(fmx:can-transform($from-version, $to-version))">
                <xsl:message terminate="yes">
                    <xsl:text>ERROR: Cannot transform from version </xsl:text>
                    <xsl:value-of select="$from-version"/>
                    <xsl:text> to version </xsl:text>
                    <xsl:value-of select="$to-version"/>
                    <xsl:text>. Invalid transformation path.</xsl:text>
                </xsl:message>
            </xsl:when>
            
            <!-- Apply sequential transformations -->
            <xsl:otherwise>
                <xsl:variable name="transformation-path" select="fmx:get-transformation-path($from-version, $to-version)"/>
                <xsl:variable name="next-version" select="$transformation-path[1]"/>
                
                <!-- Apply first transformation step -->
                <xsl:variable name="intermediate-doc">
                    <xsl:call-template name="fmx:apply-single-transformation">
                        <xsl:with-param name="input-doc" select="$input-doc"/>
                        <xsl:with-param name="element-type" select="$element-type"/>
                        <xsl:with-param name="target-version" select="$next-version"/>
                    </xsl:call-template>
                </xsl:variable>
                
                <!-- Recursively apply remaining transformations -->
                <xsl:call-template name="fmx:chain-transformations">
                    <xsl:with-param name="input-doc" select="$intermediate-doc"/>
                    <xsl:with-param name="element-type" select="$element-type"/>
                    <xsl:with-param name="from-version" select="$next-version"/>
                    <xsl:with-param name="to-version" select="$to-version"/>
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <!-- Apply a single transformation step -->
    <xsl:template name="fmx:apply-single-transformation">
        <xsl:param name="input-doc" as="document-node()"/>
        <xsl:param name="element-type" as="xs:string"/>
        <xsl:param name="target-version" as="xs:string"/>
        
        <xsl:variable name="transform-path" select="concat($element-type, '/v', $target-version, '.xsl')"/>
        
        <!-- Load and apply the transformation -->
        <xsl:variable name="stylesheet-uri" select="resolve-uri($transform-path, base-uri())"/>
        
        <xsl:choose>
            <xsl:when test="doc-available($stylesheet-uri)">
                <!-- Apply the transformation using document() and transform() functions -->
                <!-- Note: This is a simplified approach. In practice, you might need to use -->
                <!-- Saxon's extension functions or external transformation tools -->
                <xsl:message>
                    <xsl:text>Applying transformation: </xsl:text>
                    <xsl:value-of select="$transform-path"/>
                    <xsl:text> to version </xsl:text>
                    <xsl:value-of select="$target-version"/>
                </xsl:message>
                
                <!-- For demonstration, we'll copy the document and update schema references -->
                <!-- In a real implementation, you'd apply the full transformation -->
                <xsl:apply-templates select="$input-doc" mode="transform-step">
                    <xsl:with-param name="target-version" select="$target-version"/>
                </xsl:apply-templates>
            </xsl:when>
            <xsl:otherwise>
                <xsl:message terminate="yes">
                    <xsl:text>ERROR: Transformation stylesheet not found: </xsl:text>
                    <xsl:value-of select="$stylesheet-uri"/>
                </xsl:message>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <!-- Default transformation step mode (simplified for demonstration) -->
    <xsl:template match="node() | @*" mode="transform-step">
        <xsl:param name="target-version"/>
        <xsl:copy>
            <xsl:apply-templates select="node() | @*" mode="transform-step">
                <xsl:with-param name="target-version" select="$target-version"/>
            </xsl:apply-templates>
        </xsl:copy>
    </xsl:template>
    
    <!-- Update schema location during transformation step -->
    <xsl:template match="@xsi:schemaLocation | @schemaLocation" mode="transform-step">
        <xsl:param name="target-version"/>
        <xsl:attribute name="{name()}">
            <xsl:text>http://formex.publications.europa.eu/schema/formex-</xsl:text>
            <xsl:value-of select="$target-version"/>
            <xsl:text>.xd</xsl:text>
        </xsl:attribute>
    </xsl:template>
    
</xsl:stylesheet>