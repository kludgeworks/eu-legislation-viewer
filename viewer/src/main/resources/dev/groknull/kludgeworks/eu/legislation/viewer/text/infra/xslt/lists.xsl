<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Paragraphs and lists: P, NP, LIST and ITEM, definition lists. Part of normalise.xsl.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/normalise"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <!-- ============================================================ paragraphs and lists -->

  <!-- a P is no block of its own: it becomes as many blocks as it holds (text, a list, a table) -->
  <xsl:template match="P" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
      <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
      <xsl:with-param name="pending" as="processing-instruction()*" select="$m" tunnel="yes"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a numbered paragraph outside the hierarchy: in a table cell, an annotation -->
  <xsl:template match="NP" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="first-block" as="node()?" select="TXT/node()[f:is-block(.)][1]"/>
    <xsl:variable name="p" as="element()">
      <p>
        <xsl:sequence select="f:provenance($m)"/>
        <num>
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" as="node()*" select="NO.P/node()"/>
            <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          </xsl:call-template>
        </num>
        <xsl:call-template name="f:inlines">
          <xsl:with-param name="nodes" as="node()*" select="TXT/node()[empty($first-block) or . &lt;&lt; $first-block]"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
      </p>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $p)"/>
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="TXT/node()[exists($first-block) and not(. &lt;&lt; $first-block)], node() except (NO.P, TXT)"/>
      <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
      <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:variable name="f:list-kinds" as="map(xs:string, xs:string)" select="map{
      'ARAB': 'arabic', 'alpha': 'alpha', 'ALPHA': 'alpha-upper', 'roman': 'roman', 'ROMAN': 'roman-upper',
      'DASH': 'dash', 'NDASH': 'ndash', 'BULLET': 'bullet', 'NONE': 'none', 'OTHER': 'other'}"/>

  <!-- the element whose eId a list's points hang off -->
  <xsl:function name="f:scope" as="element()?">
    <xsl:param name="e" as="element()"/>
    <xsl:sequence select="$e/ancestor::*[self::ITEM or self::ARTICLE or self::PARAG or self::ALINEA or self::GR.SEQ
        or self::DLIST.ITEM or self::CONS.ANNEX or self::ANNEX or self::QUOT.S or self::TBL or self::CONSID
        or self::VISA or self::NOTE or self::DIVISION or self::CORRECTION or self::OLD.CORR or self::NEW.CORR
        or self::CONS.DOC.GEN[parent::CONS.DOC.GEN] or self::LETTER or self::SUBDIV][1]"/>
  </xsl:function>

  <!-- points of a list hang off the enclosing unit; when it holds several lists (6.5.2 has two,
       each (a) to (d)), the second and later get a segment of their own: …/lst_2/pnt_a. The first
       keeps plain eIds, so adding a list doesn't rename the points already there. -->
  <xsl:function name="f:list-base" as="xs:string?">
    <xsl:param name="list" as="element()"/>
    <xsl:param name="base" as="xs:string?"/>
    <xsl:variable name="scope" as="element()?" select="f:scope($list)"/>
    <xsl:variable name="siblings" as="element()*" select="
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
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="kind" as="xs:string" select="($f:list-kinds(string(@TYPE)), 'none'[not(current()/@TYPE)])[1]"/>
    <xsl:if test="empty($kind)">
      <xsl:sequence select="f:unknown(., 'LIST TYPE=' || @TYPE)"/>
    </xsl:if>
    <xsl:variable name="list" as="element()">
      <list kind="{$kind}">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:apply-templates select="ITEM" mode="block">
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="f:list-base(., $base)" tunnel="yes"/>
        </xsl:apply-templates>
        <xsl:apply-templates select="node() except ITEM" mode="unexpected"/>
      </list>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $list)"/>
  </xsl:template>

  <!-- a list whose items an amending act quotes (LIST › QUOT.S › ITEM): each quotation is a quoted
       structure of its items; items of the list itself stay a list -->
  <xsl:template match="LIST[QUOT.S]" mode="block" priority="1">
    <xsl:variable name="kind" as="xs:string" select="($f:list-kinds(string(@TYPE)), 'none')[1]"/>
    <xsl:for-each-group select="node()" group-adjacent="exists(self::QUOT.S)">
      <xsl:choose>
        <xsl:when test="current-grouping-key()">
          <xsl:apply-templates select="current-group()" mode="block"/>
        </xsl:when>
        <xsl:when test="current-group()[self::ITEM]">
          <list kind="{$kind}">
            <xsl:apply-templates select="current-group()[self::ITEM]" mode="block">
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
            </xsl:apply-templates>
          </list>
          <xsl:apply-templates select="current-group()[not(self::ITEM)]" mode="unexpected"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="current-group()" mode="unexpected"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
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
    <xsl:variable name="own" as="processing-instruction()*" select="f:new-markers(., $ctx)"/>
    <!-- an ITEM that is just its NP: markers around the NP belong to the item -->
    <xsl:variable name="np" as="processing-instruction()*" select="if (count(*) = 1 and NP) then f:new-markers(NP, ($ctx, f:ids($own))) else ()"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, $own, $np)"/>
    <xsl:variable name="label" as="xs:string" select="f:label(NP/NO.P)"/>
    <!-- a label an earlier live point of the same list already has (2025/24 nests (2), (3) of one
         point in the outer list, next to its own (2), (3)): the repeat is numbered, pnt_2-2. A
         deleted placeholder doesn't count, nor is it numbered: a replacement may reuse its label. -->
    <xsl:variable name="repeat" as="xs:integer" select="
        if ($label = '' or f:is-deleted-item(.)) then 0
        else count(preceding-sibling::ITEM[f:label(NP/NO.P) = $label][not(f:is-deleted-item(.))])"/>
    <xsl:variable name="eid" as="xs:string" select="
        if ($label != '') then f:eid($base, 'pnt', $label || (if ($repeat gt 0) then '-' || ($repeat + 1) else ''))
        else f:eid($base, 'idt', string(count(preceding-sibling::ITEM) + 1))"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="{if (NP/NO.P) then 'point' else 'indent'}">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="NP/NO.P">
          <num>
            <xsl:call-template name="f:inlines">
              <xsl:with-param name="nodes" as="node()*" select="NP/NO.P/node()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
            </xsl:call-template>
          </num>
        </xsl:if>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="NP/TXT/node(), NP/(node() except (NO.P, TXT)), node() except NP"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
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
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="list" as="element()">
      <list kind="definition">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:apply-templates select="DLIST.ITEM" mode="block">
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="f:list-base(., $base)" tunnel="yes"/>
        </xsl:apply-templates>
        <xsl:apply-templates select="node() except DLIST.ITEM" mode="unexpected"/>
      </list>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $list)"/>
  </xsl:template>

  <xsl:template match="DLIST.ITEM" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="f:new-markers(., $ctx)"/>
    <xsl:variable name="label" as="xs:string" select="f:label(PREFIX)"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'pnt', ($label[. != ''], string(count(preceding-sibling::DLIST.ITEM) + 1))[1])"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="definition">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:unit-content">
          <xsl:with-param name="num" as="element()?" select="PREFIX"/>
          <xsl:with-param name="heading" as="element()?" select="TERM"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <xsl:template match="DEFINITION" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

</xsl:stylesheet>
