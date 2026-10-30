<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  The same fields as formex-metadata.xsl, read from legal text: where the converter writes each.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:lt="https://kludgeworks.groknull.dev/ns/legal-text/1"
    xmlns:f="local"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:output indent="yes"/>

  <xsl:mode on-no-match="shallow-skip"/>

  <xsl:template match="/lt:legalText">
    <xsl:variable name="publication" as="element(lt:publication)?" select="lt:source/lt:publication"/>
    <fields>
      <xsl:sequence select="f:field('kind', @kind)"/>
      <xsl:sequence select="f:field('language', @xml:lang)"/>
      <xsl:sequence select="f:field('act', @act)"/>
      <xsl:sequence select="f:field('document', lt:source/@celex)"/>
      <xsl:sequence select="f:field('version', @version)"/>
      <xsl:sequence select="f:field('eli', lt:source/@eli)"/>
      <xsl:sequence select="f:field('oj.series', $publication/@series)"/>
      <xsl:sequence select="f:field('oj.number', $publication/@number)"/>
      <xsl:sequence select="f:field('oj.document', $publication/@document)"/>
      <xsl:sequence select="f:field('oj.date', $publication/@date)"/>
      <xsl:sequence select="f:field('oj.page', $publication/@page)"/>
      <xsl:sequence select="f:field('end', lt:lifecycle/@end)"/>
      <xsl:sequence select="f:field('produced', lt:lifecycle/@produced)"/>
      <xsl:sequence select="f:field('sequence', lt:lifecycle/@sequence)"/>
      <xsl:for-each select="lt:lifecycle/lt:eventRef">
        <xsl:sequence select="f:field(@type, @act || ' ' || @date)"/>
        <xsl:sequence select="f:field('integrated.by.an.amendment', @act)"/>
      </xsl:for-each>
    </fields>
  </xsl:template>

  <!-- a field, when it has a value -->
  <xsl:function name="f:field" as="element(field)?">
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="xs:anyAtomicType?"/>
    <xsl:if test="normalize-space(string($value))">
      <field name="{$name}">{normalize-space(string($value))}</field>
    </xsl:if>
  </xsl:function>

</xsl:stylesheet>
