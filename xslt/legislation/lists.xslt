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
                </xsl:when>
                <!-- Handle list items with direct P element -->
                <xsl:when test="P">
                    <xsl:apply-templates select="P"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates/>
                </xsl:otherwise>
            </xsl:choose>
        </li>
    </xsl:template>

    <!-- Handle DLIST (definition list) -->
    <xsl:template match="DLIST">
        <dl class="definition-list">
            <xsl:apply-templates select="DLIST.ITEM"/>
        </dl>
    </xsl:template>

    <!-- Handle DLIST.ITEM -->
    <xsl:template match="DLIST.ITEM">
        <dt>
            <xsl:if test="PREFIX">
                <span class="prefix"><xsl:value-of select="PREFIX"/></span>
                <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:apply-templates select="TERM"/>
        </dt>
        <dd>
            <xsl:apply-templates select="DEFINITION"/>
        </dd>
    </xsl:template>

    <!-- Handle TERM element -->
    <xsl:template match="TERM">
        <xsl:apply-templates/>
    </xsl:template>

    <!-- Handle DEFINITION element -->
    <xsl:template match="DEFINITION">
        <xsl:apply-templates/>
    </xsl:template>

</xsl:stylesheet>