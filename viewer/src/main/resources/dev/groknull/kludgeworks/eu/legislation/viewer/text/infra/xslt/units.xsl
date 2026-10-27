<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Units and blocks. Mode "block" turns Formex structure into <unit>s and blocks; f:blocks turns
  mixed content (a P holding text and a LIST, a CELL, a TXT) into a sequence of blocks, wrapping
  each run of inline content in a <p>.

  Tunnel parameters:
    $ctx         ids of the amendment markers already attributed to an enclosing unit or block
    $pending     markers covering an element that produces no block of its own (a P that becomes
                 a p and a list, a single ALINEA): attached to every block it produces
    $base        the eId of the enclosing unit
    $levelBase   the eId annex levels hang off (the annex, or a quoted structure)
    $levelCtx    $ctx at that annex or quoted structure: levels are flat, so a level's provenance
                 is everything covering it from there, including what covers its parent level
    $parentLevel the eId of the enclosing level, for unnumbered levels
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:f="https://kludgeworks.groknull.dev/ns/normalise"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:mode name="block" on-no-match="fail"/>

  <!-- ============================================================ mixed content -->

  <xsl:function name="f:is-block" as="xs:boolean">
    <xsl:param name="n" as="node()"/>
    <xsl:sequence select="exists($n[self::P or self::LIST or self::DLIST or self::TBL or self::QUOT.S
        or self::INCL.ELEMENT or self::GR.SEQ or self::NP or self::ANNOTATION or self::GR.ANNOTATION
        or self::GR.NOTES or self::PARAG or self::ALINEA or self::ITEM or self::CONS.ANNEX or self::ARTICLE
        or self::CONTENTS or self::DIVISION or self::GR.TBL or self::FORMULA.S or self::CORRECTION or self::LETTER
        or self::ACT.GEN or self::PREAMBLE or self::GR.VISA or self::GR.CONSID or self::CONSID or self::VISA
        or self::PREAMBLE.INIT or self::PREAMBLE.FINAL or self::GR.CONSID.INIT or self::ENACTING.TERMS or self::FINAL
        or self::TITLE or self::TOC or self::OLD.CORR or self::NEW.CORR or self::DESCRIPTION
        or self::SIGNATURE or self::PL.DATE or self::SIGNATORY or self::DLIST.ITEM or self::ADDR.S or self::LOC.NOTES
        or self::AGR or self::ACT or self::GR.VISA.INIT or self::SUBDIV or self::GENERAL])"/>
  </xsl:function>

  <xsl:template name="f:blocks">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:for-each-group select="$nodes" group-adjacent="f:is-block(.)">
      <xsl:choose>
        <xsl:when test="current-grouping-key()">
          <xsl:apply-templates select="current-group()" mode="block"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:variable name="run" select="current-group()[not(self::processing-instruction() or self::comment())]"/>
          <xsl:if test="$run[self::*[not(self::IE)] or self::text()[normalize-space()]]">
            <xsl:variable name="m" select="($pending, f:covering-run($run)[not(f:pi-attr(., 'ID') = $ctx)])"/>
            <xsl:variable name="p" as="element()">
              <p>
                <xsl:sequence select="f:provenance($m)"/>
                <xsl:call-template name="f:inlines">
                  <xsl:with-param name="nodes" select="$run"/>
                  <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
                </xsl:call-template>
              </p>
            </xsl:variable>
            <xsl:sequence select="f:wrap-deleted($m, $p)"/>
          </xsl:if>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
  </xsl:template>

  <!-- inline content of several lines (a heading of two P), joined by <eol/> -->
  <xsl:template name="f:lines">
    <xsl:param name="lines" as="element()*"/>
    <xsl:for-each select="$lines">
      <xsl:if test="position() gt 1">
        <eol/>
      </xsl:if>
      <xsl:call-template name="f:inlines">
        <xsl:with-param name="nodes" select="if (self::NP) then TXT/node() else node()"/>
      </xsl:call-template>
    </xsl:for-each>
  </xsl:template>

  <!-- ============================================================ enacting terms -->

  <xsl:key name="f:article-by-label" match="ARTICLE" use="f:label-after-word(TI.ART)"/>

  <!-- articles are act-wide (art_14); when an act restarts its numbering in each title (2002/2370),
       a number already taken hangs off its division instead. Deleted placeholders don't count. -->
  <xsl:template match="ARTICLE" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="division" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="label" select="f:label-after-word(TI.ART)"/>
    <xsl:variable name="value" select="($label[. != ''], string(count(preceding-sibling::ARTICLE) + 1))[1]"/>
    <xsl:variable name="taken" select="$label != '' and exists(key('f:article-by-label', $label, root(.))[. &lt;&lt; current()]
        [not(f:covering(.)[f:is-deletion(.)])][deep-equal(ancestor::QUOT.S, current()/ancestor::QUOT.S)])"/>
    <xsl:variable name="eid" select="f:eid(if ($taken and $division) then $division else $base, 'art', $value)"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="article">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:unit-content">
          <xsl:with-param name="num" select="TI.ART"/>
          <xsl:with-param name="heading" select="STI.ART"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="base" select="$eid" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <xsl:template match="PARAG" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="eid" select="f:eid($base, 'par', (f:label(NO.PARAG)[. != ''], string(count(preceding-sibling::PARAG) + 1))[1])"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="paragraph">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:unit-content">
          <xsl:with-param name="num" select="NO.PARAG"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="base" select="$eid" tunnel="yes"/>
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
          <xsl:with-param name="nodes" select="$num/node()"/>
        </xsl:call-template>
      </num>
    </xsl:if>
    <xsl:if test="$heading">
      <heading>
        <xsl:choose>
          <xsl:when test="$heading/P">
            <xsl:call-template name="f:lines">
              <xsl:with-param name="lines" select="$heading/P"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <xsl:call-template name="f:inlines">
              <xsl:with-param name="nodes" select="$heading/node()"/>
            </xsl:call-template>
          </xsl:otherwise>
        </xsl:choose>
      </heading>
    </xsl:if>
    <xsl:variable name="body" select="* except ($num, $heading)"/>
    <xsl:choose>
      <xsl:when test="count($body) = 1 and $body/self::ALINEA">
        <xsl:variable name="m" select="f:new-markers($body, $ctx)"/>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="$body/node()"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" select="$m" tunnel="yes"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="$body" mode="block">
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
        </xsl:apply-templates>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="ALINEA" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="in-article" select="exists(parent::ARTICLE)"/>
    <xsl:variable name="eid" select="f:eid($base, if ($in-article) then 'unp' else 'sub', string(count(preceding-sibling::ALINEA) + 1))"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="{if ($in-article) then 'paragraph' else 'subparagraph'}">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="node()"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
          <xsl:with-param name="base" select="$eid" tunnel="yes"/>
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
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="line" select="TITLE/TI/*[1]"/>
    <xsl:variable name="label" select="((if ($line/self::NP) then f:label($line/NO.P) else f:label-after-word($line))[. != ''],
                                        string(count(preceding-sibling::DIVISION) + 1))[1]"/>
    <!-- in an annex or a quoted structure, articles and divisions hang off the enclosing unit -->
    <xsl:variable name="outer" select="if (ancestor::*[self::CONS.ANNEX or self::ANNEX or self::QUOT.S]) then $base else ()"/>
    <xsl:variable name="eid" select="f:eid(($division, $outer)[1], 'dvs', $label)"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="division">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:title">
          <xsl:with-param name="title" select="TITLE"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="node() except TITLE"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
          <xsl:with-param name="base" select="$outer" tunnel="yes"/>
          <xsl:with-param name="division" select="$eid" tunnel="yes"/>
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
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="eid" select="f:eid($base, 'subdvs', (f:label(TITLE/TI/NP[1]/NO.P)[. != ''], string(count(preceding-sibling::SUBDIV) + 1))[1])"/>
    <xsl:variable name="body" select="* except TITLE"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="subdivision">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:title">
          <xsl:with-param name="title" select="TITLE"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
        <xsl:choose>
          <xsl:when test="count($body) = 1 and $body/self::ALINEA">
            <xsl:variable name="own" select="f:new-markers($body, ($ctx, f:ids($m)))"/>
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" select="$body/node()"/>
              <xsl:with-param name="ctx" select="($ctx, f:ids($m), f:ids($own))" tunnel="yes"/>
              <xsl:with-param name="pending" select="$own" tunnel="yes"/>
              <xsl:with-param name="base" select="$eid" tunnel="yes"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <xsl:apply-templates select="$body" mode="block">
              <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" select="()" tunnel="yes"/>
              <xsl:with-param name="base" select="$eid" tunnel="yes"/>
            </xsl:apply-templates>
          </xsl:otherwise>
        </xsl:choose>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- ============================================================ paragraphs and lists -->

  <!-- a P is no block of its own: it becomes as many blocks as it holds (text, a list, a table) -->
  <xsl:template match="P" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="node()"/>
      <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
      <xsl:with-param name="pending" select="$m" tunnel="yes"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a numbered paragraph outside the hierarchy: in a table cell, an annotation -->
  <xsl:template match="NP" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="first-block" select="TXT/node()[f:is-block(.)][1]"/>
    <xsl:variable name="p" as="element()">
      <p>
        <xsl:sequence select="f:provenance($m)"/>
        <num>
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" select="NO.P/node()"/>
            <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          </xsl:call-template>
        </num>
        <xsl:call-template name="f:inlines">
          <xsl:with-param name="nodes" select="TXT/node()[empty($first-block) or . &lt;&lt; $first-block]"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
      </p>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $p)"/>
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="TXT/node()[exists($first-block) and not(. &lt;&lt; $first-block)], node() except (NO.P, TXT)"/>
      <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
      <xsl:with-param name="pending" select="()" tunnel="yes"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:variable name="f:list-kinds" select="map{
      'ARAB': 'arabic', 'alpha': 'alpha', 'ALPHA': 'alpha-upper', 'roman': 'roman', 'ROMAN': 'roman-upper',
      'DASH': 'dash', 'NDASH': 'ndash', 'BULLET': 'bullet', 'NONE': 'none', 'OTHER': 'other'}"/>

  <!-- the element whose eId a list's points hang off -->
  <xsl:function name="f:scope" as="element()?">
    <xsl:param name="e" as="element()"/>
    <xsl:sequence select="$e/ancestor::*[self::ITEM or self::ARTICLE or self::PARAG or self::ALINEA or self::GR.SEQ
        or self::DLIST.ITEM or self::CONS.ANNEX or self::ANNEX or self::QUOT.S or self::TBL or self::CONSID
        or self::VISA or self::NOTE or self::DIVISION or self::CORRECTION or self::OLD.CORR or self::NEW.CORR
        or self::LETTER or self::SUBDIV][1]"/>
  </xsl:function>

  <!-- points of a list hang off the enclosing unit; when it holds several lists (6.5.2 has two,
       each (a) to (d)), the second and later get a segment of their own: …/lst_2/pnt_a. The first
       keeps plain eIds, so adding a list doesn't rename the points already there. -->
  <xsl:function name="f:list-base" as="xs:string?">
    <xsl:param name="list" as="element()"/>
    <xsl:param name="base" as="xs:string?"/>
    <xsl:variable name="scope" select="f:scope($list)"/>
    <xsl:variable name="siblings" select="
        if ($scope) then $scope//(LIST | DLIST)[f:scope(.) is $scope]
        else root($list)//(LIST | DLIST)[empty(f:scope(.))]"/>
    <xsl:sequence select="
        if ($siblings[. &lt;&lt; $list])
        then f:eid($base, 'lst', string(count($siblings[. &lt;&lt; $list]) + 1))
        else $base"/>
  </xsl:function>

  <xsl:template match="LIST" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="kind" select="($f:list-kinds(string(@TYPE)), 'none'[not(current()/@TYPE)])[1]"/>
    <xsl:if test="empty($kind)">
      <xsl:sequence select="f:unknown(., 'LIST TYPE=' || @TYPE)"/>
    </xsl:if>
    <xsl:variable name="list" as="element()">
      <list kind="{$kind}">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:apply-templates select="ITEM" mode="block">
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
          <xsl:with-param name="base" select="f:list-base(., $base)" tunnel="yes"/>
        </xsl:apply-templates>
      </list>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $list)"/>
  </xsl:template>

  <!-- an item a deletion marker covers, itself or its NP -->
  <xsl:function name="f:is-deleted-item" as="xs:boolean">
    <xsl:param name="item" as="element(ITEM)"/>
    <xsl:sequence select="exists(($item, $item/NP) ! f:covering(.)[f:is-deletion(.)])"/>
  </xsl:function>

  <xsl:template match="ITEM" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="own" select="f:new-markers(., $ctx)"/>
    <!-- an ITEM that is just its NP: markers around the NP belong to the item -->
    <xsl:variable name="np" select="if (count(*) = 1 and NP) then f:new-markers(NP, ($ctx, f:ids($own))) else ()"/>
    <xsl:variable name="m" select="($pending, $own, $np)"/>
    <xsl:variable name="label" select="f:label(NP/NO.P)"/>
    <!-- a label an earlier live point of the same list already has (2025/24 nests (2), (3) of one
         point in the outer list, next to its own (2), (3)): the repeat is numbered, pnt_2-2. A
         deleted placeholder doesn't count, nor is it numbered: a replacement may reuse its label. -->
    <xsl:variable name="repeat" select="
        if ($label = '' or f:is-deleted-item(.)) then 0
        else count(preceding-sibling::ITEM[f:label(NP/NO.P) = $label][not(f:is-deleted-item(.))])"/>
    <xsl:variable name="eid" select="
        if ($label != '') then f:eid($base, 'pnt', $label || (if ($repeat gt 0) then '-' || ($repeat + 1) else ''))
        else f:eid($base, 'idt', string(count(preceding-sibling::ITEM) + 1))"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="{if (NP/NO.P) then 'point' else 'indent'}">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="NP/NO.P">
          <num>
            <xsl:call-template name="f:inlines">
              <xsl:with-param name="nodes" select="NP/NO.P/node()"/>
              <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
            </xsl:call-template>
          </num>
        </xsl:if>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="NP/TXT/node(), NP/(node() except (NO.P, TXT)), node() except NP"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
          <xsl:with-param name="base" select="$eid" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- definition lists: each entry is a unit with its prefix as num and its term as heading -->
  <xsl:template match="DLIST" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="list" as="element()">
      <list kind="definition">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:apply-templates select="DLIST.ITEM" mode="block">
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
          <xsl:with-param name="base" select="f:list-base(., $base)" tunnel="yes"/>
        </xsl:apply-templates>
      </list>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $list)"/>
  </xsl:template>

  <xsl:template match="DLIST.ITEM" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" select="f:new-markers(., $ctx)"/>
    <xsl:variable name="label" select="f:label(PREFIX)"/>
    <xsl:variable name="eid" select="f:eid($base, 'pnt', ($label[. != ''], string(count(preceding-sibling::DLIST.ITEM) + 1))[1])"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="definition">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:unit-content">
          <xsl:with-param name="num" select="PREFIX"/>
          <xsl:with-param name="heading" select="TERM"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="base" select="$eid" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <xsl:template match="DEFINITION" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- ============================================================ annexes and levels -->

  <!-- the label of an annex: "ANNEX II" → "II"; "" for an annex titled just ANNEX -->
  <xsl:function name="f:annex-label" as="xs:string">
    <xsl:param name="annex" as="element()"/>
    <xsl:sequence select="f:label-after-word(string($annex/TITLE/TI/*[1]))"/>
  </xsl:function>

  <!-- Top-level annexes, grouped into the annexes of the act. Formex splits a single annex into
       fragments, each titled just ANNEX: the OJ text of 2015/1998 across four files, its
       consolidations around the annexes of its checklists (chapters 7 to 12 sit in the fourth
       fragment). Consecutive label-less fragments with the same title are one annex, so eIds
       don't depend on where the Publications Office cut the file. -->
  <xsl:function name="f:annex-groups" as="array(element())*">
    <xsl:param name="annexes" as="element()*"/>
    <xsl:iterate select="$annexes">
      <xsl:param name="group" as="element()*" select="()"/>
      <xsl:on-completion>
        <xsl:sequence select="if (exists($group)) then array{$group} else ()"/>
      </xsl:on-completion>
      <xsl:variable name="previous" select="$group[last()]"/>
      <xsl:variable name="continues" select="exists($previous) and f:annex-label(.) = '' and f:annex-label($previous) = ''
                                             and normalize-space(TITLE) = normalize-space($previous/TITLE)"/>
      <xsl:choose>
        <xsl:when test="$continues">
          <xsl:next-iteration><xsl:with-param name="group" select="$group, ."/></xsl:next-iteration>
        </xsl:when>
        <xsl:otherwise>
          <xsl:sequence select="if (exists($group)) then array{$group} else ()"/>
          <xsl:next-iteration><xsl:with-param name="group" select="."/></xsl:next-iteration>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:iterate>
  </xsl:function>

  <!-- one annex, from one or more fragments ($fragments; a nested annex is its own fragment) -->
  <xsl:template match="CONS.ANNEX | ANNEX" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="fragments" as="element()*" select="."/>
    <!-- top level: how many annexes the act has, this one's position, its first EUR-Lex anchor -->
    <xsl:param name="annexCount" as="xs:integer?"/>
    <xsl:param name="annexPosition" as="xs:integer?"/>
    <xsl:param name="anchor" as="xs:integer?"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="nested" select="exists(parent::CONS.ANNEX)"/>
    <xsl:variable name="position" select="($annexPosition, count(preceding-sibling::CONS.ANNEX) + 1)[1]"/>
    <xsl:variable name="label" select="(f:annex-label(.)[. != ''], string($position))[1]"/>
    <xsl:variable name="eid" select="
        if (not($nested) and $annexCount = 1) then 'anx'
        else f:eid(if ($nested) then $base else (), 'anx', $label)"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="annex" eurlexAnchor="{($anchor, count(preceding::CONS.ANNEX | ancestor::CONS.ANNEX) + 1)[1]}">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:title">
          <xsl:with-param name="title" select="TITLE"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
        <xsl:for-each select="$fragments">
          <xsl:variable name="i" select="position()"/>
          <xsl:variable name="own" select="if (position() = 1) then () else f:new-markers(., ($ctx, f:ids($m)))"/>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" select="CONTENTS/node(), CONS.ANNEX"/>
            <xsl:with-param name="ctx" select="($ctx, f:ids(($m, $own)))" tunnel="yes"/>
            <xsl:with-param name="pending" select="$own" tunnel="yes"/>
            <xsl:with-param name="base" select="$eid" tunnel="yes"/>
            <xsl:with-param name="levelBase" select="$eid" tunnel="yes"/>
            <xsl:with-param name="levelCtx" select="($ctx, f:ids($m))" tunnel="yes"/>
            <xsl:with-param name="parentLevel" select="()" tunnel="yes"/>
            <xsl:with-param name="earlier" select="($fragments[position() lt $i] ! root(.)) except root(.)" tunnel="yes"/>
            <xsl:with-param name="labelledParent" select="()" tunnel="yes"/>
            <!-- unnumbered top levels are numbered through all fragments -->
            <xsl:with-param name="levelOffset" select="count($fragments[position() lt $i]/CONTENTS/GR.SEQ[f:label((f:level-num(.), f:level-label-line(.))[1]) = ''])" tunnel="yes"/>
          </xsl:call-template>
        </xsl:for-each>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- an annex or level title: the first line is the num (ANNEX II), or a numbered NP gives num
       and heading (1.2 RECRUITMENT); further lines join the heading; STI is the subheading -->
  <xsl:template name="f:title">
    <xsl:param name="title" as="element(TITLE)?"/>
    <xsl:param name="num-first-line" as="xs:boolean" select="true()"/>
    <xsl:variable name="lines" select="$title/TI/*"/>
    <xsl:variable name="np" select="$lines[1][self::NP]"/>
    <xsl:variable name="num" select="if ($np) then $np/NO.P else if ($num-first-line) then $lines[1] else ()"/>
    <xsl:variable name="heading" select="if ($np) then $lines else $lines except $num"/>
    <xsl:if test="$num">
      <num>
        <xsl:call-template name="f:inlines">
          <xsl:with-param name="nodes" select="$num/node()"/>
        </xsl:call-template>
      </num>
    </xsl:if>
    <xsl:if test="$heading">
      <heading>
        <xsl:call-template name="f:lines">
          <xsl:with-param name="lines" select="$heading"/>
        </xsl:call-template>
      </heading>
    </xsl:if>
    <xsl:if test="$title/STI">
      <subheading>
        <xsl:call-template name="f:lines">
          <xsl:with-param name="lines" select="$title/STI/*"/>
        </xsl:call-template>
      </subheading>
    </xsl:if>
  </xsl:template>

  <xsl:function name="f:level-num" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:sequence select="($seq/NO.GR.SEQ, $seq/TITLE/TI/*[1][self::NP]/NO.P, $seq[not(TITLE)]/*[1][self::NP]/NO.P)[1]"/>
  </xsl:function>

  <!-- an unnumbered level whose title starts with a label line: a word and an identifier with a
       digit, or of up to three letters ("ATTACHMENT 6-A", "PART 2", "SUBPART A", "SECTION IV").
       The line is the level's num, as "ANNEX II" is an annex's; its eId uses the whole line
       (lvl_ATTACHMENT-6-A), which survives renesting where a position would not -->
  <xsl:function name="f:level-label-line" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:variable name="first" select="$seq[empty(f:level-num($seq))]/TITLE/TI/*[1][self::P]"/>
    <xsl:sequence select="$first[matches(normalize-space(.), '^\p{L}+ (\S*\d\S*|\p{Lu}{1,3})$')]"/>
  </xsl:function>

  <!-- what an unnumbered level is numbered within: its parent level (also when a table or a stray
       CONTENTS sits in between), else the annex contents or the quoted structure -->
  <xsl:function name="f:level-parent" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:sequence select="$seq/ancestor::*[self::GR.SEQ or self::QUOT.S
                                           or self::CONTENTS[parent::CONS.ANNEX or parent::ANNEX]][1]"/>
  </xsl:function>

  <xsl:function name="f:level-ordinal" as="xs:integer">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:variable name="parent" select="f:level-parent($seq)"/>
    <xsl:sequence select="count($parent//GR.SEQ[. &lt;&lt; $seq][f:label((f:level-num(.), f:level-label-line(.))[1]) = '']
                                [f:level-parent(.) is $parent]) + 1"/>
  </xsl:function>

  <xsl:key name="f:level-by-label" match="GR.SEQ" use="f:label((f:level-num(.), f:level-label-line(.))[1])"/>

  <!-- the annex (group of fragments) or quoted structure a level's number belongs to -->
  <xsl:function name="f:level-scope" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:sequence select="$seq/ancestor::*[self::QUOT.S or self::CONS.ANNEX or self::ANNEX or self::TBL][1]"/>
  </xsl:function>

  <!-- Numbered levels hang off the annex (anx/pnt_12.6.2), except
       - local enumerators, "(a)", "(1)", "iii)": Air Ops writes the points of a rule as levels;
       - a number an earlier level in the same annex already has: attachments restart their own
         numbering (checklist "PART 2 … 2.1", form "1.1 Name …").
       Those hang off their parent level. -->
  <xsl:function name="f:local-number" as="xs:boolean">
    <xsl:param name="num" as="element()"/>
    <xsl:sequence select="matches(normalize-space(string($num)), '^\(|\)$')"/>
  </xsl:function>

  <xsl:function name="f:label-taken" as="xs:boolean">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:param name="label" as="xs:string"/>
    <!-- earlier fragments of the same annex that are separate files (an OJ act's annex files) -->
    <xsl:param name="earlier" as="document-node()*"/>
    <xsl:variable name="scope" select="f:level-scope($seq)"/>
    <xsl:sequence select="exists($earlier ! key('f:level-by-label', $label, .)[not(ancestor::QUOT.S)][not(f:covering(.)[f:is-deletion(.)])])
        or f:label-taken-here($seq, $label, $scope)"/>
  </xsl:function>

  <xsl:function name="f:label-taken-here" as="xs:boolean">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:param name="label" as="xs:string"/>
    <xsl:param name="scope" as="element()?"/>
    <!-- a deleted placeholder doesn't hold on to its number -->
    <xsl:sequence select="exists(key('f:level-by-label', $label, root($seq))[. &lt;&lt; $seq]
        [not(f:covering(.)[f:is-deletion(.)])]
        [let $s := f:level-scope(.) return
           if ($scope/self::CONS.ANNEX and $s/self::CONS.ANNEX) then empty($s/parent::CONS.ANNEX) = empty($scope/parent::CONS.ANNEX)
           else if (empty($scope)) then empty($s)
           else $s is $scope])"/>
  </xsl:function>

  <!-- annex sections and points: flat levels, as in AKN4EU (1.2.2.6 is a sibling of 1.2.2) -->
  <xsl:template match="GR.SEQ" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="levelBase" as="xs:string?" tunnel="yes"/>
    <xsl:param name="levelCtx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="parentLevel" as="xs:string?" tunnel="yes"/>
    <xsl:param name="levelOffset" as="xs:integer?" tunnel="yes"/>
    <xsl:param name="earlier" as="document-node()*" tunnel="yes"/>
    <!-- the nearest enclosing level with a number or label: labelled levels hang off it, skipping
         unnumbered levels in between, whose positional eIds shift when Formex renests them -->
    <xsl:param name="labelledParent" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="label-line" select="f:level-label-line(.)"/>
    <xsl:variable name="num" select="f:level-num(.)"/>
    <xsl:variable name="direct-np" select="if ($num/parent::NP/parent::GR.SEQ) then $num/.. else ()"/>
    <xsl:variable name="label" select="f:label($num)"/>
    <xsl:variable name="eid" select="
        if ($label != '') then f:eid(
            if ($parentLevel and (f:local-number($num) or f:label-taken(., $label, $earlier)))
            then ($labelledParent, $parentLevel)[1] else $levelBase,
            'pnt', $label)
        else if ($label-line) then f:eid(
            if ($parentLevel and f:label-taken(., f:label($label-line), $earlier))
            then ($labelledParent, $parentLevel)[1] else $levelBase,
            'lvl', f:label($label-line))
        else f:eid(($parentLevel, $levelBase)[1], 'lvl',
                   string(f:level-ordinal(.) + (if ($parentLevel) then 0 else ($levelOffset, 0)[1])))"/>
    <xsl:variable name="m" select="($pending, f:covering(.)[not(f:pi-attr(., 'ID') = $levelCtx)])"/>
    <xsl:variable name="inner" select="($ctx, f:ids($m))"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="level" depth="{(@LEVEL, 1)[1]}">
        <xsl:if test="@BOX = 'YES'">
          <xsl:attribute name="box" select="true()"/>
        </xsl:if>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:choose>
          <xsl:when test="NO.GR.SEQ or $direct-np">
            <num>
              <xsl:call-template name="f:inlines">
                <xsl:with-param name="nodes" select="$num/node()"/>
                <xsl:with-param name="ctx" select="$inner" tunnel="yes"/>
              </xsl:call-template>
            </num>
            <xsl:call-template name="f:title">
              <xsl:with-param name="title" select="TITLE"/>
              <xsl:with-param name="num-first-line" select="false()"/>
              <xsl:with-param name="ctx" select="$inner" tunnel="yes"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <xsl:call-template name="f:title">
              <xsl:with-param name="title" select="TITLE"/>
              <xsl:with-param name="num-first-line" select="exists($label-line)"/>
              <xsl:with-param name="ctx" select="$inner" tunnel="yes"/>
            </xsl:call-template>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="$direct-np/TXT/node(), $direct-np/(node() except (NO.P, TXT)),
                                               node() except (TITLE, NO.GR.SEQ, GR.SEQ, $direct-np)"/>
          <xsl:with-param name="ctx" select="$inner" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
          <xsl:with-param name="base" select="$eid" tunnel="yes"/>
          <xsl:with-param name="parentLevel" select="$eid" tunnel="yes"/>
          <xsl:with-param name="levelOffset" select="0" tunnel="yes"/>
          <xsl:with-param name="labelledParent" select="if ($label != '' or $label-line) then $eid else $labelledParent" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
    <xsl:apply-templates select="GR.SEQ" mode="block">
      <xsl:with-param name="ctx" select="$inner" tunnel="yes"/>
      <xsl:with-param name="pending" select="()" tunnel="yes"/>
      <xsl:with-param name="parentLevel" select="$eid" tunnel="yes"/>
      <xsl:with-param name="levelOffset" select="0" tunnel="yes"/>
      <xsl:with-param name="labelledParent" select="if ($label != '' or $label-line) then $eid else $labelledParent" tunnel="yes"/>
    </xsl:apply-templates>
  </xsl:template>

  <!-- ============================================================ tables -->

  <xsl:template match="TBL" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="scope" select="f:scope(.)"/>
    <xsl:variable name="eid" select="f:eid($base, 'tbl', string(1 + (
        if ($scope) then count($scope//TBL[f:scope(.) is $scope][. &lt;&lt; current()])
        else count(preceding::TBL[empty(f:scope(.))]))))"/>
    <xsl:variable name="table" as="element()">
      <table>
        <xsl:if test="@COLS">
          <xsl:attribute name="cols" select="@COLS"/>
        </xsl:if>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="TITLE">
          <caption>
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" select="TITLE/TI/node(), TITLE/STI/node()"/>
              <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" select="()" tunnel="yes"/>
            </xsl:call-template>
          </caption>
        </xsl:if>
        <xsl:apply-templates select="CORPUS/*" mode="block">
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
          <xsl:with-param name="base" select="$eid" tunnel="yes"/>
        </xsl:apply-templates>
      </table>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $table)"/>
    <xsl:apply-templates select="GR.SEQ" mode="block"/>
  </xsl:template>

  <!-- tables that belong together under one title -->
  <xsl:template match="GR.TBL" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="group" as="element()">
      <tableGroup>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="TITLE">
          <caption>
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" select="TITLE/TI/node(), TITLE/STI/node()"/>
              <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" select="()" tunnel="yes"/>
            </xsl:call-template>
          </caption>
        </xsl:if>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="node() except TITLE"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
        </xsl:call-template>
      </tableGroup>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $group)"/>
  </xsl:template>

  <xsl:template match="ROW" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <tr>
      <xsl:if test="@TYPE = 'HEADER'">
        <xsl:attribute name="header" select="true()"/>
      </xsl:if>
      <xsl:sequence select="f:provenance($m)"/>
      <xsl:apply-templates select="CELL" mode="block">
        <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
        <xsl:with-param name="pending" select="()" tunnel="yes"/>
      </xsl:apply-templates>
    </tr>
  </xsl:template>

  <xsl:template match="CELL" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:variable name="m" select="f:new-markers(., $ctx)"/>
    <td>
      <xsl:if test="@COLSPAN">
        <xsl:attribute name="colspan" select="@COLSPAN"/>
      </xsl:if>
      <xsl:if test="@ROWSPAN">
        <xsl:attribute name="rowspan" select="@ROWSPAN"/>
      </xsl:if>
      <xsl:if test="@TYPE = 'HEADER'">
        <xsl:attribute name="header" select="true()"/>
      </xsl:if>
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" select="node()"/>
        <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
        <xsl:with-param name="pending" select="$m" tunnel="yes"/>
      </xsl:call-template>
    </td>
  </xsl:template>

  <!-- a group of rows with a heading row (checklists in 2015/1998) -->
  <xsl:template match="BLK" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:variable name="m" select="f:new-markers(., $ctx)"/>
    <rowGroup>
      <xsl:for-each select="TI.BLK/NP">
        <num>
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" select="NO.P/node()"/>
          </xsl:call-template>
        </num>
        <heading>
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" select="TXT/node()"/>
          </xsl:call-template>
        </heading>
      </xsl:for-each>
      <xsl:apply-templates select="ROW | BLK" mode="block">
        <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
        <xsl:with-param name="pending" select="$m" tunnel="yes"/>
      </xsl:apply-templates>
    </rowGroup>
  </xsl:template>

  <!-- ============================================================ other blocks -->

  <!-- what an amending act inserts or substitutes; its units get eIds under …/qst_N -->
  <xsl:template match="QUOT.S" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="unit" select="ancestor::*[self::ITEM or self::ARTICLE or self::PARAG or self::ALINEA
                                                  or self::GR.SEQ or self::DLIST.ITEM or self::ANNEX or self::CONS.ANNEX
                                                  or self::OLD.CORR or self::NEW.CORR or self::DIVISION or self::LETTER
                                                  or self::CONSID or self::VISA or self::NOTE][1]"/>
    <xsl:variable name="eid" select="f:eid($base, 'qst', string(count($unit//QUOT.S[. &lt;&lt; current()]) + 1))"/>
    <quotedStructure eId="{$eid}">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" select="node()"/>
        <xsl:with-param name="pending" select="()" tunnel="yes"/>
        <xsl:with-param name="base" select="$eid" tunnel="yes"/>
        <xsl:with-param name="levelBase" select="$eid" tunnel="yes"/>
        <xsl:with-param name="levelCtx" select="$ctx" tunnel="yes"/>
        <xsl:with-param name="parentLevel" select="()" tunnel="yes"/>
        <xsl:with-param name="labelledParent" select="()" tunnel="yes"/>
      </xsl:call-template>
    </quotedStructure>
  </xsl:template>

  <xsl:template match="ANNOTATION | GR.ANNOTATION" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="annotation" as="element()">
      <annotation>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="TITLE">
          <heading>
            <xsl:call-template name="f:lines">
              <xsl:with-param name="lines" select="TITLE/TI/*, TITLE/STI/*"/>
              <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
            </xsl:call-template>
          </heading>
        </xsl:if>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="node() except TITLE"/>
          <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
        </xsl:call-template>
      </annotation>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $annotation)"/>
  </xsl:template>

  <xsl:template match="INCL.ELEMENT[@TYPE = 'TIFF']" mode="block">
    <figure src="{@FILEREF}"/>
  </xsl:template>

  <!-- another Formex file of the same package, included here: its content, in place -->
  <xsl:template match="INCL.ELEMENT[@TYPE = 'FORMEX.DOC']" mode="block">
    <xsl:variable name="included" select="doc(resolve-uri(@FILEREF, base-uri(.)))/*"/>
    <xsl:choose>
      <xsl:when test="$included/self::ANNEX">
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="$included/CONTENTS/node()"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="$included" mode="block"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="INCL.ELEMENT" mode="block">
    <xsl:sequence select="f:unknown(., 'INCL.ELEMENT TYPE=' || @TYPE)"/>
  </xsl:template>

  <!-- an address: its lines as paragraphs -->
  <xsl:template match="ADDR.S | ADDR" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- where the source prints its notes; notes are collected at the end anyway -->
  <xsl:template match="LOC.NOTES" mode="block"/>

  <!-- a CONTENTS inside a level (05.56): transparent -->
  <xsl:template match="CONTENTS" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- ============================================================ corrigenda, letters, embedded documents -->

  <!-- one correction of a corrigendum: where, then the old and the new wording -->
  <xsl:template match="CORRECTION" mode="block">
    <xsl:variable name="eid" select="'cor_' || (count(preceding::CORRECTION) + 1)"/>
    <unit eId="{$eid}" kind="correction">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" select="node()"/>
        <xsl:with-param name="base" select="$eid" tunnel="yes"/>
      </xsl:call-template>
    </unit>
  </xsl:template>

  <xsl:template match="DESCRIPTION" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- the wording as published (old) and as corrected (new); both quote the same structure, so
       each has its own eId segment: cor_1/old/…, cor_1/new/… -->
  <xsl:template match="OLD.CORR | NEW.CORR" mode="block">
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="side" select="if (self::OLD.CORR) then 'old' else 'new'"/>
    <xsl:element name="{$side}">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" select="node()"/>
        <xsl:with-param name="base" select="f:eid($base, $side, ())" tunnel="yes"/>
        <xsl:with-param name="levelBase" select="f:eid($base, $side, ())" tunnel="yes"/>
        <xsl:with-param name="levelCtx" select="()" tunnel="yes"/>
        <xsl:with-param name="parentLevel" select="()" tunnel="yes"/>
      </xsl:call-template>
    </xsl:element>
  </xsl:template>

  <!-- a letter of an exchange of letters attached to an agreement -->
  <xsl:template match="LETTER" mode="block">
    <xsl:variable name="eid" select="'let_' || (count(preceding-sibling::LETTER) + 1)"/>
    <unit eId="{$eid}" kind="letter">
      <xsl:if test="TITLE">
        <heading>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" select="TITLE/TI/*, TITLE/STI/*"/>
          </xsl:call-template>
        </heading>
      </xsl:if>
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" select="node() except TITLE"/>
        <xsl:with-param name="base" select="$eid" tunnel="yes"/>
      </xsl:call-template>
    </unit>
  </xsl:template>

  <!-- the parts of an act met inside another document's content (CONS.OTH, letters): their text,
       in order, as blocks -->
  <xsl:template match="ACT.GEN | PREAMBLE | GR.VISA | GR.CONSID | CONSID | VISA | PREAMBLE.INIT | PREAMBLE.FINAL
                       | GR.CONSID.INIT | GR.VISA.INIT | ENACTING.TERMS | FINAL | SIGNATURE | PL.DATE | SIGNATORY" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a title met inside content: its lines as paragraphs -->
  <xsl:template match="TITLE" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="TI/node(), STI/node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- an agreement quoted whole, e.g. in a corrigendum that replaces its text; a protocol an
       agreement's annex includes (written as GENERAL, 06.02.1) -->
  <xsl:template match="AGR | ACT | GENERAL" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="node() except BIB.INSTANCE"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a table of contents is navigation the reader builds from the units; not text -->
  <xsl:template match="TOC" mode="block"/>

  <!-- notes are collected at the end of the document -->
  <xsl:template match="GR.NOTES" mode="block"/>

  <xsl:template match="processing-instruction() | comment()" mode="block"/>

  <xsl:template match="text()" mode="block">
    <xsl:if test="normalize-space()">
      <xsl:sequence select="f:unknown(.., 'text where only blocks are allowed')"/>
    </xsl:if>
  </xsl:template>

  <xsl:template match="*" mode="block" priority="-1">
    <xsl:sequence select="f:unknown(., ())"/>
  </xsl:template>

</xsl:stylesheet>
