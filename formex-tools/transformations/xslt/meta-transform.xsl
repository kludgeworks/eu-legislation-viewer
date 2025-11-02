<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    exclude-result-prefixes="fmx xs">
    
    <!-- 
        Formex Meta-Transformation Stylesheet
        
        This is the main entry point for Formex transformations.
        It automatically detects the source version and applies sequential
        transformations to reach the target version.
        
        Usage:
        saxon input.xml meta-transform.xsl element=doc target=v06.02.3
        
        Parameters:
        - element: Target element directory (doc, general, annex, etc.)
                  If not specified, will auto-detect from root element
        - target: Target version (e.g., v06.02.3, 06.02.3, or latest)
                 If not specified, defaults to latest version
        - source: Source version (optional, auto-detected if not specified)
        - verbose: Set to 'true' for detailed logging (default: false)
    -->
    
    <xsl:import href="shared/version-detector.xsl"/>
    <xsl:import href="shared/transformation-chainer.xsl"/>
    
    <!-- Parameters -->
    <xsl:param name="element" as="xs:string?" select="()"/>
    <xsl:param name="target" as="xs:string?" select="()"/>
    <xsl:param name="source" as="xs:string?" select="()"/>
    <xsl:param name="verbose" as="xs:string" select="'false'"/>
    
    <!-- Constants -->
    <xsl:variable name="LATEST_VERSION" select="'06.02.3'"/>
    
    <!-- Main template -->
    <xsl:template match="/">
        <xsl:variable name="input-doc" select="."/>
        
        <!-- Auto-detect source version from document -->
        <xsl:variable name="detected-source-version">
            <xsl:choose>
                <xsl:when test="$source">
                    <xsl:value-of select="fmx:normalize-version($source)"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="fmx:detect-document-version">
                        <xsl:with-param name="doc" select="$input-doc"/>
                    </xsl:call-template>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <!-- Auto-detect element type from root element -->
        <xsl:variable name="detected-element-type">
            <xsl:choose>
                <xsl:when test="$element">
                    <xsl:value-of select="$element"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="fmx:detect-root-element($input-doc)"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <!-- Determine target version -->
        <xsl:variable name="resolved-target-version">
            <xsl:choose>
                <xsl:when test="$target">
                    <xsl:value-of select="fmx:normalize-version($target)"/>
                </xsl:when>
                <xsl:when test="$target = 'latest' or not($target)">
                    <xsl:value-of select="$LATEST_VERSION"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="fmx:normalize-version($target)"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <!-- Validation and logging -->
        <xsl:if test="not($detected-source-version)">
            <xsl:message terminate="yes">
                <xsl:text>ERROR: Could not detect source version from document. </xsl:text>
                <xsl:text>Please specify the source version using the 'source' parameter.</xsl:text>
            </xsl:message>
        </xsl:if>
        
        <xsl:if test="not($detected-element-type)">
            <xsl:message terminate="yes">
                <xsl:text>ERROR: Could not detect element type from document root. </xsl:text>
                <xsl:text>Please specify the element type using the 'element' parameter.</xsl:text>
            </xsl:message>
        </xsl:if>
        
        <xsl:if test="not($resolved-target-version)">
            <xsl:message terminate="yes">
                <xsl:text>ERROR: Invalid target version specified: </xsl:text>
                <xsl:value-of select="$target"/>
            </xsl:message>
        </xsl:if>
        
        <!-- Verbose logging -->
        <xsl:if test="$verbose = 'true'">
            <xsl:message>
                <xsl:text>Formex Meta-Transformation Starting...</xsl:text>
            </xsl:message>
            <xsl:message>
                <xsl:text>  Source Version: </xsl:text>
                <xsl:value-of select="$detected-source-version"/>
            </xsl:message>
            <xsl:message>
                <xsl:text>  Target Version: </xsl:text>
                <xsl:value-of select="$resolved-target-version"/>
            </xsl:message>
            <xsl:message>
                <xsl:text>  Element Type: </xsl:text>
                <xsl:value-of select="$detected-element-type"/>
            </xsl:message>
        </xsl:if>
        
        <!-- Check if source and target are the same -->
        <xsl:choose>
            <xsl:when test="$detected-source-version = $resolved-target-version">
                <xsl:if test="$verbose = 'true'">
                    <xsl:message>
                        <xsl:text>Source and target versions are identical. Copying document unchanged.</xsl:text>
                    </xsl:message>
                </xsl:if>
                <xsl:copy-of select="$input-doc"/>
            </xsl:when>
            
            <!-- Apply transformation chain -->
            <xsl:otherwise>
                <xsl:if test="$verbose = 'true'">
                    <xsl:message>
                        <xsl:text>Starting transformation chain...</xsl:text>
                    </xsl:message>
                </xsl:if>
                
                <xsl:call-template name="fmx:chain-transformations">
                    <xsl:with-param name="input-doc" select="$input-doc"/>
                    <xsl:with-param name="element-type" select="$detected-element-type"/>
                    <xsl:with-param name="from-version" select="$detected-source-version"/>
                    <xsl:with-param name="to-version" select="$resolved-target-version"/>
                </xsl:call-template>
                
                <xsl:if test="$verbose = 'true'">
                    <xsl:message>
                        <xsl:text>Transformation chain completed successfully.</xsl:text>
                    </xsl:message>
                </xsl:if>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <!-- Template to detect document version from various sources -->
    <xsl:template name="fmx:detect-document-version">
        <xsl:param name="doc" as="document-node()"/>
        
        <!-- Try to detect from xsi:schemaLocation -->
        <xsl:variable name="schema-location" select="$doc//@xsi:schemaLocation"/>
        <xsl:variable name="version-from-schema" select="fmx:detect-version-from-schema($schema-location)"/>
        
        <xsl:choose>
            <xsl:when test="$version-from-schema">
                <xsl:value-of select="fmx:full-to-simple-version($version-from-schema)"/>
            </xsl:when>
            
            <!-- Try to detect from schemaLocation (without namespace) -->
            <xsl:when test="$doc//@schemaLocation">
                <xsl:variable name="alt-schema-location" select="$doc//@schemaLocation"/>
                <xsl:variable name="alt-version-from-schema" select="fmx:detect-version-from-schema($alt-schema-location)"/>
                <xsl:if test="$alt-version-from-schema">
                    <xsl:value-of select="fmx:full-to-simple-version($alt-version-from-schema)"/>
                </xsl:if>
            </xsl:when>
            
            <!-- Try to detect from namespace URI patterns -->
            <xsl:when test="contains(namespace-uri($doc/*), 'formex')">
                <!-- This is a fallback - might need more sophisticated logic -->
                <xsl:variable name="namespace" select="namespace-uri($doc/*)"/>
                <xsl:if test="contains($namespace, 'formex-')">
                    <xsl:variable name="version-part" select="substring-after($namespace, 'formex-')"/>
                    <xsl:value-of select="fmx:full-to-simple-version($version-part)"/>
                </xsl:if>
            </xsl:when>
        </xsl:choose>
    </xsl:template>
    
    <!-- Function to normalize version input (remove 'v' prefix if present) -->
    <xsl:function name="fmx:normalize-version" as="xs:string?">
        <xsl:param name="version-input" as="xs:string?"/>
        
        <xsl:if test="$version-input">
            <xsl:choose>
                <xsl:when test="starts-with($version-input, 'v')">
                    <xsl:value-of select="substring($version-input, 2)"/>
                </xsl:when>
                <xsl:when test="$version-input = 'latest'">
                    <xsl:value-of select="$LATEST_VERSION"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$version-input"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:if>
    </xsl:function>
    
</xsl:stylesheet>