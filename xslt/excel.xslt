<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
                xmlns="urn:schemas-microsoft-com:office:spreadsheet">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:template match="/">
        <Workbook>
            <Worksheet ss:Name="LegalDocument">
                <Table>
                    <Row>
                        <Cell><Data ss:Type="String">Section Number</Data></Cell>
                        <Cell><Data ss:Type="String">Section Title</Data></Cell>
                    </Row>
                    <xsl:for-each select="//section">
                        <Row>
                            <Cell><Data ss:Type="String"><xsl:value-of select="num"/></Data></Cell>
                            <Cell><Data ss:Type="String"><xsl:value-of select="heading"/></Data></Cell>
                        </Row>
                    </xsl:for-each>
                </Table>
            </Worksheet>
        </Workbook>
    </xsl:template>
</xsl:stylesheet>
