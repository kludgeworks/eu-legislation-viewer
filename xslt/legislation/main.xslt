<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xi="http://www.w3.org/2001/XInclude">

    <!-- Import all module stylesheets -->
    <xsl:import href="styles.xslt"/>
    <xsl:import href="act.xslt"/>
    <xsl:import href="annex.xslt"/>
    <xsl:import href="lists.xslt"/>
    <xsl:import href="formats.xslt"/>
    <xsl:import href="common.xslt"/>

    <xsl:output method="html" indent="yes" encoding="UTF-8"/>

    <!-- Main template - match document root -->
    <xsl:template match="/">
        <html>
            <head>
                <!-- Extract title dynamically from XML -->
                <title>
                    <xsl:text>Regulation (EU) </xsl:text>
                    <xsl:value-of select="//BIB.INSTANCE/NO.DOC/NO.CURRENT"/>
                    <xsl:text>/</xsl:text>
                    <xsl:value-of select="//BIB.INSTANCE/NO.DOC/YEAR"/>
                </title>
                <!-- Include styles from styles.xslt -->
                <xsl:call-template name="generate-styles"/>
            </head>
            <body>
                <!-- Find ACT element anywhere in the document structure -->
                <xsl:apply-templates select="//ACT"/>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>