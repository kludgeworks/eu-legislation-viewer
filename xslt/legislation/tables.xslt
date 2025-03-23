<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Main TBL template -->
    <xsl:template match="TBL">
        <div class="table-container">
            <!-- Optional title for the table -->
            <xsl:if test="TITLE">
                <div class="table-title">
                    <xsl:apply-templates select="TITLE"/>
                </div>
            </xsl:if>

            <!-- Optional GR.SEQ elements (explanatory info) -->
            <xsl:apply-templates select="GR.SEQ"/>

            <!-- Optional GR.NOTES -->
            <xsl:apply-templates select="GR.NOTES"/>

            <!-- Main table element -->
            <table class="eu-legislation-table" id="table-{@NO.SEQ}">
                <!-- Apply templates to the CORPUS which contains rows -->
                <xsl:apply-templates select="CORPUS"/>
            </table>
        </div>
    </xsl:template>

    <!-- CORPUS template - contains the actual table content -->
    <xsl:template match="CORPUS">
        <tbody>
            <xsl:apply-templates select="ROW"/>
        </tbody>
    </xsl:template>

    <!-- ROW template - transform to HTML tr -->
    <xsl:template match="ROW">
        <tr>
            <xsl:apply-templates select="CELL"/>
        </tr>
    </xsl:template>

    <!-- CELL template - transform to HTML td with attributes -->
    <xsl:template match="CELL">
        <td>
            <!-- Handle colspan attribute if present -->
            <xsl:if test="@COLSPAN">
                <xsl:attribute name="colspan">
                    <xsl:value-of select="@COLSPAN"/>
                </xsl:attribute>
            </xsl:if>

            <!-- Handle rowspan attribute if present -->
            <xsl:if test="@ROWSPAN">
                <xsl:attribute name="rowspan">
                    <xsl:value-of select="@ROWSPAN"/>
                </xsl:attribute>
            </xsl:if>

            <!-- Process cell content -->
            <xsl:apply-templates/>
        </td>
    </xsl:template>

    <!-- IE (empty cell) template -->
    <xsl:template match="IE">
        <!-- Non-breaking space for empty cells -->
        <xsl:text>&#160;</xsl:text>
    </xsl:template>

</xsl:stylesheet>