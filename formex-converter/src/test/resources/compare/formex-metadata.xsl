<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  The metadata of a Formex document the viewer relies on, as fields, for checking that conversion
  loses none: identity, the Official Journal reference, a consolidation's dates and what it
  integrates. Written independently of formex-to-legal-text.xsl, as formex-values.xsl is for the
  text; the rest of the metadata (page counts, production identifiers, the titles of amending acts)
  isn't the converter's to keep, nor is the document's CELEX number, which is Cellar's (CONSLEG.REF
  only approximates a consolidation's).

  Input: what the converter reads: a package (DOC) and the main document it lists, a consolidation
  (CONS.ACT, CONS.OTH), or an act on its own (ACT, AGR, CORR). Each field is one value, normalised
  as legal-text writes it (ISO dates, numbers without leading zeros); a field may occur more than
  once (an amending act per MOD.ACT).
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="local"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:output indent="yes"/>

  <xsl:mode on-no-match="shallow-skip"/>

  <xsl:template match="/">
    <fields>
      <xsl:apply-templates select="*"/>
    </fields>
  </xsl:template>

  <!-- a package: its descriptor's ELI and publication, and the main document's own data -->
  <xsl:template match="DOC">
    <xsl:variable name="main" as="element()" select="doc(resolve-uri(FMX/DOC.MAIN.PUB/REF.PHYS/@FILE, base-uri(.)))/*"/>
    <xsl:sequence select="f:field('eli', BIB.DOC/NO.ELI/@ID)"/>
    <xsl:sequence select="f:field('oj.series', PUBLICATION.REF/COLL)"/>
    <xsl:sequence select="f:field('oj.date', f:date(PUBLICATION.REF/DATE/@ISO))"/>
    <xsl:apply-templates select="$main"/>
  </xsl:template>

  <!-- an act, an agreement or a corrigendum: its language, kind and OJ reference -->
  <xsl:template match="ACT | AGR | CORR">
    <xsl:sequence select="f:field('kind', if (self::CORR) then 'corrigendum' else 'original')"/>
    <xsl:sequence select="f:field('language', lower-case(BIB.INSTANCE/LG.DOC))"/>
    <xsl:sequence select="f:reference(BIB.INSTANCE/DOCUMENT.REF)"/>
  </xsl:template>

  <!-- a consolidated version: what it is a version of, its dates, and what it integrates -->
  <xsl:template match="CONS.ACT | CONS.OTH">
    <xsl:variable name="doc" as="element()" select="(CONS.DOC, CONS.OTH.DOC)[1]"/>
    <xsl:variable name="base" as="element(BIB.DATA)?" select="$doc/FAM.COMP/BIB.DATA"/>
    <xsl:sequence select="f:field('kind', 'consolidated')"/>
    <xsl:sequence select="f:field('language', lower-case($doc/BIB.INSTANCE/LG.DOC))"/>
    <xsl:sequence select="f:field('act', f:celex($base/NO.CELEX))"/>
    <xsl:sequence select="f:field('version', f:date(INFO.CONSLEG/@START.DATE))"/>
    <xsl:sequence select="f:field('end', f:date(INFO.CONSLEG/@END.DATE[. ne '99999999']))"/>
    <xsl:sequence select="f:field('produced', f:date(INFO.PROD/@PROD.DATE))"/>
    <xsl:sequence select="f:field('sequence', INFO.CONSLEG/@PROD.SEQ)"/>
    <xsl:sequence select="f:reference($base/BIB.INSTANCE.CONS/DOCUMENT.REF.CONS)"/>
    <xsl:sequence select="f:field('oj.date', f:date($base/BIB.INSTANCE.CONS/DATE/@ISO))"/>
    <!-- an amending act: its CELEX and its date; a corrigendum has no date of its own, so the date
         it was published -->
    <xsl:for-each select="$doc/FAM.COMP/GR.MOD.ACT/MOD.ACT/BIB.DATA[f:celex(NO.CELEX)]">
      <xsl:sequence select="f:field('amendment', f:celex(NO.CELEX) || ' ' || f:date(DATE/@ISO))"/>
    </xsl:for-each>
    <xsl:for-each select="$doc/FAM.COMP/GR.CORRIG/CORRIG/BIB.DATA[f:celex(NO.CELEX)]">
      <xsl:sequence select="f:field('corrigendum', f:celex(NO.CELEX) || ' ' || f:date((DATE, BIB.INSTANCE.CONS/DATE)[1]/@ISO))"/>
    </xsl:for-each>
    <!-- what amending acts themselves list, their own amendments and corrigenda: integrated too -->
    <xsl:for-each select="$doc/FAM.COMP/GR.MOD.ACT/MOD.ACT/(GR.MOD.ACT/MOD.ACT | GR.CORRIG/CORRIG)/BIB.DATA[f:celex(NO.CELEX)]">
      <xsl:sequence select="f:field('integrated.by.an.amendment', f:celex(NO.CELEX))"/>
    </xsl:for-each>
  </xsl:template>

  <!-- an OJ reference: series, issue number, the document number of an act published on its own
       (from October 2023: OJ L, 2026/449), first page -->
  <xsl:function name="f:reference" as="element(field)*">
    <xsl:param name="ref" as="element()?"/>
    <xsl:sequence select="f:field('oj.series', $ref/COLL)"/>
    <xsl:sequence select="f:field('oj.number', replace(normalize-space($ref/NO.OJ), '^0+', ''))"/>
    <xsl:sequence select="f:field('oj.document', $ref/NO.DOC[@TYPE = 'OJ']!(normalize-space(YEAR) || '/' || normalize-space(NO.CURRENT)))"/>
    <xsl:sequence select="f:field('oj.page', $ref/PAGE.FIRST)"/>
  </xsl:function>

  <!-- a field, when it has a value -->
  <xsl:function name="f:field" as="element(field)?">
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="xs:anyAtomicType?"/>
    <xsl:if test="normalize-space(string($value))">
      <field name="{$name}">{normalize-space(string($value))}</field>
    </xsl:if>
  </xsl:function>

  <!-- a CELEX number with its year in four digits (old sources write 303D0148 for 32003D0148);
       otherwise as the source writes it, malformed or not (1201994N/ACT) -->
  <xsl:function name="f:celex" as="xs:string?">
    <xsl:param name="celex" as="xs:string?"/>
    <xsl:variable name="c" as="xs:string" select="normalize-space($celex)"/>
    <xsl:variable name="full" as="xs:string" select="
        if (matches($c, '^[0-9][0-9]{2}[A-Z]'))
        then substring($c, 1, 1) || (if (number(substring($c, 2, 2)) gt 50) then '19' else '20') || substring($c, 2)
        else $c"/>
    <xsl:sequence select="$full[.]"/>
  </xsl:function>

  <!-- Formex's YYYYMMDD as an ISO date -->
  <xsl:function name="f:date" as="xs:string?">
    <xsl:param name="iso" as="xs:string?"/>
    <xsl:sequence select="$iso[matches(., '^[0-9]{8}$')]!(substring(., 1, 4) || '-' || substring(., 5, 2) || '-' || substring(., 7, 2))"/>
  </xsl:function>

</xsl:stylesheet>
