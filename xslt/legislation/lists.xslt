<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Handle lists -->
    <xsl:template match="LIST">
        <xsl:choose>
            <xsl:when test="@TYPE='alpha' or @TYPE='ALPHA'">
                <ol class="alpha-list">
                    <xsl:apply-templates select="ITEM"/>
                </ol>
            </xsl:when>
            <xsl:when test="@TYPE='ARAB'">
                <ol>
                    <xsl:apply-templates select="ITEM"/>
                </ol>
            </xsl:when>
            <xsl:when test="@TYPE='DASH' or @TYPE='NDASH'">
                <ul class="dash-list">
                    <xsl:apply-templates select="ITEM"/>
                </ul>
            </xsl:when>
            <xsl:when test="@TYPE='BULLET'">
                <ul>
                    <xsl:apply-templates select="ITEM"/>
                </ul>
            </xsl:when>
            <xsl:otherwise>
                <ul>
                    <xsl:apply-templates select="ITEM"/>
                </ul>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Handle list items with their structure -->
    <xsl:template match="ITEM">
        <li>
            <xsl:choose>
                <xsl:when test="NP">
                    <!-- Handle marker and direct text -->
                    <xsl:if test="NP/NO.P">
                        <xsl:value-of select="NP/NO.P"/>
                        <xsl:text>&#160;&#160;</xsl:text>
                    </xsl:if>
                    <xsl:apply-templates select="NP/TXT"/>

                    <!-- Handle nested structure: NP/P/LIST specifically -->
                    <xsl:if test="NP/P/LIST">
                        <div class="nested-content">
                            <xsl:apply-templates select="NP/P/LIST"/>
                        </div>
                    </xsl:if>

                    <!-- Other processing logic... -->
                </xsl:when>
                <!-- Other cases... -->
            </xsl:choose>
        </li>
    </xsl:template>

</xsl:stylesheet>