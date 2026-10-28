<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  eIds, AKN4EU style: segments joined by "/", each an abbreviation plus the unit's own label,
  e.g. art_3/par_2/pnt_a, anx/pnt_1.2.2.6, pbl_1/rct_12. Labels are taken literally: brackets and
  trailing dots are dropped, whitespace becomes "-", nothing is interpreted. Units without a label
  are numbered by position (unp_2, idt_3, lvl_1).
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    exclude-result-prefixes="#all">

  <!-- a visible label as an eId value: "(a)" → "a", "1.0.6." → "1.0.6", "10 a" → "10-a" -->
  <xsl:function name="f:label" as="xs:string">
    <xsl:param name="label" as="xs:string?"/>
    <xsl:sequence select="
        f:spaces($label)
        => replace('^[\(\[]+|[\)\]]+$', '')
        => replace('\.+$', '')
        => replace('\s+', '-')
        => replace('[^A-Za-z0-9._\-]', '')"/>
  </xsl:function>

  <!-- the label after a leading type word: "Article 3" → "3", "ANNEX II" → "II", "ANNEX" → "" -->
  <xsl:function name="f:label-after-word" as="xs:string">
    <xsl:param name="label" as="xs:string?"/>
    <xsl:sequence select="f:label(substring-after(f:spaces($label), ' '))"/>
  </xsl:function>

  <!-- whitespace normalised, non-breaking spaces included ("Article&#160;1") -->
  <xsl:function name="f:spaces" as="xs:string">
    <xsl:param name="s" as="xs:string?"/>
    <xsl:sequence select="normalize-space(replace($s, '\p{Zs}', ' '))"/>
  </xsl:function>

  <!-- base/abbr_value; without a base the segment stands alone -->
  <xsl:function name="f:eid" as="xs:string">
    <xsl:param name="base" as="xs:string?"/>
    <xsl:param name="abbr" as="xs:string"/>
    <xsl:param name="value" as="xs:string?"/>
    <xsl:variable name="segment" as="xs:string" select="if ($value) then $abbr || '_' || $value else $abbr"/>
    <xsl:sequence select="if ($base) then $base || '/' || $segment else $segment"/>
  </xsl:function>

</xsl:stylesheet>
