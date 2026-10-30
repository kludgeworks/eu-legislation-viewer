<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  The text of a legal text as a list of values, the counterpart of formex-values.xsl: each t, each
  date's wording, and each formula as its letters and digits. Values the converter makes up
  (a quotation mark whose partner sits in another element, a note marker) come out as extra values,
  which the check allows; only values missing from this list are losses.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:lt="https://kludgeworks.groknull.dev/ns/legal-text/1"
    xmlns:m="http://www.w3.org/1998/Math/MathML"
    xmlns:f="local"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:output indent="yes"/>

  <xsl:mode on-no-match="shallow-skip"/>

  <xsl:template match="/">
    <values>
      <xsl:apply-templates/>
    </values>
  </xsl:template>

  <!-- a run of text; the converter splits runs where the source does (elements, amendment markers) -->
  <xsl:template match="lt:t[normalize-space(@v)]">
    <v>{normalize-space(@v)}</v>
  </xsl:template>

  <!-- a C-series document's number, moved from above the title into the publication data -->
  <xsl:template match="lt:publication[@document]">
    <v>{@document}</v>
  </xsl:template>

  <!-- a date's wording (date, docDate, the date of a signature) -->
  <xsl:template match="lt:*[normalize-space(@asWritten)]">
    <v>{normalize-space(@asWritten)}</v>
    <xsl:apply-templates/>
  </xsl:template>

  <!-- letters and digits, without the function sign the converter adds for FUNCTION -->
  <xsl:template match="m:math">
    <xsl:variable name="text" as="xs:string" select="f:letters(string-join(.//(m:mi | m:mn | m:mo)[. != 'ƒ']))"/>
    <xsl:if test="$text">
      <v kind="formula">{$text}</v>
    </xsl:if>
  </xsl:template>

  <!-- a formula's letters and digits, sorted: MathML puts limits and scripts in its own order -->
  <xsl:function name="f:letters" as="xs:string">
    <xsl:param name="s" as="xs:string"/>
    <xsl:sequence select="string-join(sort(string-to-codepoints(replace($s, '[^\p{L}\p{N}]', '')) ! codepoints-to-string(.)))"/>
  </xsl:function>

</xsl:stylesheet>
