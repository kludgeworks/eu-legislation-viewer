<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Enacting terms: articles, their paragraphs and subparagraphs, divisions (parts, titles,
  chapters, sections) and article subdivisions. Part of formex-to-legal-text.xsl; tunnel parameters as in units.xsl.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <!-- ============================================================ enacting terms -->

  <xsl:key name="f:article-by-label" match="ARTICLE" use="f:label-after-word(TI.ART)"/>

  <!-- articles are act-wide (art_14); when an act restarts its numbering in each title (2002/2370),
       a number already taken hangs off its division instead. Deleted placeholders don't count. -->
  <xsl:template match="ARTICLE" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="division" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="label" as="xs:string" select="f:label-after-word(TI.ART)"/>
    <!-- an article printed without a number, where the act numbers its others, is art_unnumbered-1:
         a number by position would be one an article prints -->
    <xsl:variable name="value" as="xs:string" select="
        if ($label != '') then $label
        else if (exists(root(.)//ARTICLE[f:label-after-word(TI.ART) != '']))
        then 'unnumbered-' || (count(preceding::ARTICLE[f:label-after-word(TI.ART) = '']) + 1)
        else string(count(preceding-sibling::ARTICLE) + 1)"/>
    <xsl:variable name="taken" as="xs:boolean" select="$label != '' and exists(key('f:article-by-label', $label, root(.))[. &lt;&lt; current()]
        [not(f:covering(.)[f:is-deletion(.)])][deep-equal(ancestor::QUOT.S, current()/ancestor::QUOT.S)])"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid(if ($taken and $division) then $division else $base, 'art', $value)"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="article">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:unit-content">
          <xsl:with-param name="num" as="element()?" select="TI.ART"/>
          <xsl:with-param name="heading" as="element()?" select="STI.ART"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <!-- levels it holds are numbered within it, under its eId (art_3/pnt_a, anx_I/pnt_a/pnt_1) -->
          <xsl:with-param name="levelBase" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="parentLevel" as="xs:string?" select="()" tunnel="yes"/>
          <xsl:with-param name="labelledParent" as="xs:string?" select="()" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <xsl:template match="PARAG" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'par', (f:label(NO.PARAG)[. != ''], string(count(preceding-sibling::PARAG) + 1))[1])"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="paragraph">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:unit-content">
          <xsl:with-param name="num" as="element()?" select="NO.PARAG"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <!-- levels it holds are numbered within it, under its eId (art_3/pnt_a, anx_I/pnt_a/pnt_1) -->
          <xsl:with-param name="levelBase" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="parentLevel" as="xs:string?" select="()" tunnel="yes"/>
          <xsl:with-param name="labelledParent" as="xs:string?" select="()" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- num, heading, then the content: a single ALINEA is the content itself (as in AKN, no unp_1);
       several become unnumbered paragraphs (in an article) or subparagraphs (in a paragraph) -->
  <xsl:template name="f:unit-content">
    <xsl:param name="num" as="element()?"/>
    <xsl:param name="heading" as="element()?"/>
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:if test="$num">
      <num>
        <xsl:call-template name="f:inlines">
          <xsl:with-param name="nodes" as="node()*" select="$num/node()"/>
        </xsl:call-template>
      </num>
    </xsl:if>
    <xsl:if test="$heading">
      <heading>
        <xsl:choose>
          <xsl:when test="$heading/P">
            <xsl:call-template name="f:lines">
              <xsl:with-param name="lines" as="element()*" select="$heading/P"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <xsl:call-template name="f:heading">
              <xsl:with-param name="nodes" as="node()*" select="$heading/node()"/>
            </xsl:call-template>
          </xsl:otherwise>
        </xsl:choose>
      </heading>
    </xsl:if>
    <xsl:variable name="body" as="element()*" select="* except ($num, $heading)"/>
    <xsl:choose>
      <xsl:when test="count($body) = 1 and $body/self::ALINEA">
        <xsl:variable name="m" as="processing-instruction()*" select="f:new-markers($body, $ctx)"/>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="$body/node()"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="$m" tunnel="yes"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="$body" mode="block">
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
        </xsl:apply-templates>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="ALINEA" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="in-article" as="xs:boolean" select="exists(parent::ARTICLE)"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, if ($in-article) then 'unp' else 'sub', string(count(preceding-sibling::ALINEA) + 1))"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="{if ($in-article) then 'paragraph' else 'subparagraph'}">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="node()"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- Parts, titles, chapters and sections (Formex DIVISION): unit kind="division", its title line as
       num (CHAPTER III). The eId is dvs_ and the label after the type word, nested under the enclosing
       division; the kind of grouping isn't classified, since that would mean reading each language's
       wording. Articles inside keep act-wide eIds (art_14), as in AKN4EU and ELI. -->
  <xsl:template match="DIVISION" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="division" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="line" as="element()?" select="TITLE/TI/*[1]"/>
    <!-- a part, title, chapter, section or subsection, as the EU subdivision table names them in the
         document's language: prt_, tis_, cpt_, sct_, sbs_ and the number after the name; else dvs_ -->
    <xsl:variable name="type" as="xs:string*" select="if ($line/self::NP) then () else f:subdivision(string($line), ('PRT', 'TIS', 'CPT', 'SCT', 'SBS'), .)"/>
    <!-- after a recognised name, the next word is the number, in whatever form: III, 2, TWO -->
    <xsl:variable name="number" as="xs:string?" select="tokenize($type[2], ' ')[1]"/>
    <xsl:variable name="label" as="xs:string" select="(
        (if (exists($type)) then f:label($number)
         else if ($line/self::NP) then f:label($line/NO.P) else f:label-after-word($line))[. != ''],
        string(count(preceding-sibling::DIVISION) + 1))[1]"/>
    <!-- divisions hang off what holds them: nothing in the act's own enacting terms (cpt_1), else
         the annex, quoted structure, attachment or level (anx_II/cpt_1) -->
    <xsl:variable name="outer" as="xs:string?" select="$base"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid(($division, $outer)[1], ($type[1] ! lower-case(.), 'dvs')[1], $label)"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="division">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:title">
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="title" as="element(TITLE)?" select="TITLE"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="node() except TITLE"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$outer" tunnel="yes"/>
          <xsl:with-param name="division" as="xs:string?" select="$eid" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- a titled part of an article in an agreement (SUBDIV, "(1) Objectives"): unit kind="subdivision",
       eId subdvs_ and its number, as AKN's subdivision; its paragraphs and subparagraphs hang off it -->
  <xsl:template match="SUBDIV" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'subdvs', (f:label(TITLE/TI/NP[1]/NO.P)[. != ''], string(count(preceding-sibling::SUBDIV) + 1))[1])"/>
    <xsl:variable name="body" as="element()*" select="* except TITLE"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="subdivision">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:title">
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="title" as="element(TITLE)?" select="TITLE"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
        <xsl:choose>
          <xsl:when test="count($body) = 1 and $body/self::ALINEA">
            <xsl:variable name="own" as="processing-instruction()*" select="f:new-markers($body, ($ctx, f:ids($m)))"/>
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" as="node()*" select="$body/node()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m), f:ids($own))" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="$own" tunnel="yes"/>
              <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
              <xsl:with-param name="levelBase" as="xs:string?" select="$eid" tunnel="yes"/>
              <xsl:with-param name="parentLevel" as="xs:string?" select="()" tunnel="yes"/>
              <xsl:with-param name="labelledParent" as="xs:string?" select="()" tunnel="yes"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <xsl:apply-templates select="$body" mode="block">
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
              <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
              <xsl:with-param name="levelBase" as="xs:string?" select="$eid" tunnel="yes"/>
              <xsl:with-param name="parentLevel" as="xs:string?" select="()" tunnel="yes"/>
              <xsl:with-param name="labelledParent" as="xs:string?" select="()" tunnel="yes"/>
            </xsl:apply-templates>
          </xsl:otherwise>
        </xsl:choose>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

</xsl:stylesheet>
