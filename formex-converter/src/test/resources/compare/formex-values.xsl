<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  The text of a Formex document as a list of values, for checking that conversion loses none.
  Written independently of formex-to-legal-text.xsl, so the check doesn't test that code against itself.

  Input: what the converter reads, i.e. a consolidation (CONS.ACT, CONS.OTH), an OJ act or its
  package (DOC). A package brings in every file it lists, and an INCL.ELEMENT TYPE="FORMEX.DOC"
  the file it includes.

  Each value is one run of text, whitespace normalised: the text between two element boundaries.
  Page-break processing instructions don't break a run (the converter joins it); amendment
  markers do (the converter marks the change there). A formula is one value: its letters and
  digits, since operators and fences become MathML of their own.

  A value with kind="once" only needs to occur once in the output, however often the source
  repeats it.

  What the converter leaves out on purpose is left out here too, each with its reason below
  (the "dropped" rows of the Formex to legal-text mapping).
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="local"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:output indent="yes"/>

  <xsl:mode on-no-match="shallow-skip"/>

  <!-- metadata: identity, publication and lifecycle become attributes of the legal text -->
  <xsl:template match="BIB.INSTANCE | BIB.INSTANCE.CONS | BIB.DOC | BIB.DATA | INFO.CONSLEG | INFO.PROD
                       | FAM.COMP | GR.MOD.ACT | DOCUMENT.REF | DOCUMENT.REF.CONS | NO.CELEX"/>
  <!-- navigation the reader builds from the units -->
  <xsl:template match="TOC"/>
  <!-- where the source prints notes; the notes themselves are kept -->
  <xsl:template match="LOC.NOTES"/>
  <!-- a repeated note: every occurrence holds a copy of the first, which is the one kept -->
  <xsl:template match="NOTE[@NOTE.REF]"/>
  <!-- a package's descriptor and the OJ's table of contents -->
  <xsl:template match="PUBLICATION"/>

  <!-- the title of an annex: Formex may split one annex into fragments that repeat its title, and
       the converter joins them, so the title counts once (kind="once") -->
  <xsl:template match="(ANNEX | CONS.ANNEX)/TITLE/TI/P[text()[normalize-space()]][not(*)]" priority="1">
    <v kind="once" file="{tokenize(base-uri(.), '/')[last()]}" path="{path()}">{normalize-space(.)}</v>
  </xsl:template>

  <!-- a document of the act-by-act OJ C series: its number (C/2025/3971) is its OJ reference, which
       the converter keeps as the publication's series and document number (checked as metadata) -->
  <xsl:template match="NO.DOC.C[matches(normalize-space(.), '^C/[0-9]{4}/[0-9]+$')]" priority="2"/>

  <!-- a C-series document's number: the converter keeps it without its brackets -->
  <xsl:template match="NO.DOC.C" priority="1">
    <v file="{tokenize(base-uri(.), '/')[last()]}" path="{path()}">{replace(normalize-space(.), '^\((.*)\)$', '$1')}</v>
  </xsl:template>

  <!-- a number Formex marks as one (FT TYPE="NUMBER"): printed with its thousands set apart by a
       no-break space, as EUR-Lex prints it (6000: 6 000) -->
  <xsl:template match="FT[@TYPE = 'NUMBER'][matches(., '^[1-9][0-9]{3,}$')][empty(*)]" priority="1">
    <v file="{tokenize(base-uri(.), '/')[last()]}" path="{path()}">{translate(format-number(number(.), '#,##0'), ',', '&#160;')}</v>
  </xsl:template>

  <xsl:template match="/">
    <values>
      <xsl:apply-templates/>
    </values>
  </xsl:template>

  <!-- a package: the main document and every secondary document, in order -->
  <xsl:template match="DOC">
    <xsl:for-each select="FMX//REF.PHYS[@TYPE = 'DOC.XML']">
      <xsl:apply-templates select="doc(resolve-uri(@FILE, base-uri(.)))/*"/>
    </xsl:for-each>
  </xsl:template>

  <xsl:template match="INCL.ELEMENT[@TYPE = 'FORMEX.DOC']">
    <xsl:apply-templates select="doc(resolve-uri(@FILEREF, base-uri(.)))/*"/>
  </xsl:template>

  <xsl:template match="FORMULA[not(ancestor::FORMULA | ancestor::FORMULA.S)] | FORMULA.S">
    <xsl:variable name="text" as="xs:string" select="f:letters(string-join(.//text()[not(ancestor::NOTE)]))"/>
    <xsl:if test="$text">
      <v kind="formula" file="{tokenize(base-uri(.), '/')[last()]}" path="{path()}">{$text}</v>
    </xsl:if>
    <xsl:apply-templates select=".//NOTE"/>
  </xsl:template>

  <!-- runs of text among an element's children -->
  <xsl:template match="*[text()[normalize-space()]]" priority="-0.5">
    <xsl:for-each-group select="node()" group-adjacent="f:in-run(.)">
      <xsl:choose>
        <xsl:when test="current-grouping-key()">
          <xsl:variable name="run" as="xs:string" select="normalize-space(string-join(current-group()[self::text()]))"/>
          <xsl:if test="$run">
            <v file="{tokenize(base-uri(..), '/')[last()]}" path="{path(..)}">{$run}</v>
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="current-group()"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
  </xsl:template>

  <!-- part of a run: text, page breaks, comments -->
  <xsl:function name="f:in-run" as="xs:boolean">
    <xsl:param name="n" as="node()"/>
    <xsl:sequence select="exists($n[self::text() or self::comment()
        or self::processing-instruction()[name() = ('PAGE', 'COL.PAGE')]])"/>
  </xsl:function>

  <!-- a formula's letters and digits, sorted: MathML puts limits and scripts in its own order -->
  <xsl:function name="f:letters" as="xs:string">
    <xsl:param name="s" as="xs:string"/>
    <xsl:sequence select="string-join(sort(string-to-codepoints(replace($s, '[^\p{L}\p{N}]', '')) ! codepoints-to-string(.)))"/>
  </xsl:function>

</xsl:stylesheet>
