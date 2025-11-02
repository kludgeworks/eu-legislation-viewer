<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:formex="http://formex.publications.europa.eu/functions">
    
    <!-- 
        Schema Location Updater Utility
        
        This utility handles updating the noNamespaceSchemaLocation attribute
        on DOC elements to point to the target schema version.
        
        Parameters:
        - target-version: The target schema version (e.g., "formex-05.59-20170418")
        
        Usage: Import this stylesheet and set the target-version parameter
    -->
    
    <!-- Parameter for target schema version -->
    <xsl:param name="target-version" select="''" as="xs:string"/>
    
    <!-- Import base identity transformation -->
    <xsl:import href="identity.xsl"/>
    
    <!-- Named template to update schema location -->
    <xsl:template name="update-schema-location">
        <xsl:param name="old-url" as="xs:string"/>
        <xsl:param name="new-url" as="xs:string"/>
        
        <xsl:choose>
            <xsl:when test="@noNamespaceSchemaLocation = $old-url">
                <xsl:attribute name="noNamespaceSchemaLocation">
                    <xsl:value-of select="$new-url"/>
                </xsl:attribute>
            </xsl:when>
            <xsl:when test="@xsi:schemaLocation">
                <!-- Handle namespaced schema location -->
                <xsl:attribute name="xsi:schemaLocation">
                    <xsl:value-of select="replace(@xsi:schemaLocation, $old-url, $new-url)"/>
                </xsl:attribute>
            </xsl:when>
            <xsl:otherwise>
                <!-- Copy existing schema location attributes -->
                <xsl:copy-of select="@noNamespaceSchemaLocation | @xsi:schemaLocation"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Template to update schema location on DOC element -->
    <xsl:template match="DOC/@noNamespaceSchemaLocation">
        <xsl:choose>
            <xsl:when test="$target-version != ''">
                <xsl:attribute name="noNamespaceSchemaLocation">
                    <xsl:value-of select="concat('http://formex.publications.europa.eu/schema/', $target-version, '.xd')"/>
                </xsl:attribute>
            </xsl:when>
            <xsl:otherwise>
                <!-- If no target version specified, copy original -->
                <xsl:copy-of select="."/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <!-- Helper function to extract version from schema location -->
    <xsl:function name="formex:extract-version" as="xs:string">
        <xsl:param name="schema-location" as="xs:string"/>
        <xsl:choose>
            <xsl:when test="contains($schema-location, '/schema/')">
                <xsl:variable name="after-schema" select="substring-after($schema-location, '/schema/')"/>
                <xsl:value-of select="substring-before($after-schema, '.xd')"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="''"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
</xsl:stylesheet>