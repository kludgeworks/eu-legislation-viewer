<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Paragraphs and lists: P, NP, LIST and ITEM, definition lists. Part of formex-to-legal-text.xsl.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
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

  <!-- a numbered paragraph outside a list (in a table cell, an annotation, a subparagraph): a point,
       as a list's numbered item is, holding its text and what follows it (EUR-Lex sets it as one, its
       number beside it). A run of them is a list of its own, numbered among the lists around it, as a
       run of stray ITEMs is: its eId is its label's (tab_1/tr_8/td_1/pnt_9.2), or for a number that is
       only a mark (a dash) an indent's, by its place in the run -->
  <xsl:template match="NP" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="label" as="xs:string" select="f:label(NO.P)"/>
    <!-- a heading line's NP, set as blocks where the heading holds some (a table's title), is no run -->
    <xsl:variable name="run-base" as="xs:string?" select="if (f:np-point(.)) then f:list-base(f:np-run-start(.), $base) else $base"/>
    <xsl:variable name="eid" as="xs:string" select="
        if ($label != '') then f:eid($run-base, 'pnt', $label)
        else f:eid($run-base, 'idt', string(count(f:np-run-before(.)) + 1))"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="{if ($label != '') then 'point' else 'indent'}">
        <xsl:sequence select="f:provenance($m)"/>
        <num>
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" as="node()*" select="NO.P/node()"/>
            <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          </xsl:call-template>
        </num>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="TXT/node(), node() except (NO.P, TXT)"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="levels" as="map(*)" select="map{'base': $eid, 'ctx': ($ctx, f:ids($m))}" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- the NP point just before one in its run: its sibling, or where each is a P's only element, the
       one in the P before -->
  <xsl:function name="f:np-previous" as="element(NP)?">
    <xsl:param name="np" as="element(NP)"/>
    <xsl:variable name="item" as="element()" select="if ($np/parent::P[count(*) = 1]) then $np/.. else $np"/>
    <xsl:variable name="previous" as="element()?" select="$item/preceding-sibling::*[1]"/>
    <xsl:sequence select="($previous/self::NP, $previous/self::P[count(*) = 1]/NP)[f:np-point(.)][1]"/>
  </xsl:function>

  <!-- the NP points before one in its run, nearest first -->
  <xsl:function name="f:np-run-before" as="element(NP)*">
    <xsl:param name="np" as="element(NP)"/>
    <xsl:variable name="previous" as="element(NP)?" select="f:np-previous($np)"/>
    <xsl:sequence select="if (exists($previous)) then ($previous, f:np-run-before($previous)) else ()"/>
  </xsl:function>

  <xsl:function name="f:np-run-start" as="element(NP)">
    <xsl:param name="np" as="element(NP)"/>
    <xsl:sequence select="(f:np-run-before($np)[last()], $np)[1]"/>
  </xsl:function>

  <!-- whether an NP is a point of a run (the template above): not one that numbers what holds it, a
       list's item, a recital of the preamble, a level, a row group; nor a heading's line -->
  <xsl:function name="f:np-point" as="xs:boolean">
    <xsl:param name="np" as="element(NP)"/>
    <xsl:sequence select="empty($np/parent::*[self::ITEM or self::TI or self::STI or self::TI.BLK])
        and empty($np/parent::CONSID[f:preamble-unit(.)][NP[1] is $np])
        and empty($np/parent::GR.SEQ[f:level-num(.)/.. is $np])"/>
  </xsl:function>

  <xsl:variable name="f:list-kinds" as="map(xs:string, xs:string)" select="map{
      'ARAB': 'arabic', 'alpha': 'alpha', 'ALPHA': 'alphaUpper', 'roman': 'roman', 'ROMAN': 'romanUpper',
      'DASH': 'dash', 'NDASH': 'ndash', 'BULLET': 'bullet', 'NONE': 'none', 'OTHER': 'other'}"/>

  <!-- the element whose eId a list's points hang off. Citations and recitals are units only in the
       main document's preamble: in an act inside another document (an attached act, a letter's, a
       consolidation's related document) its preamble is text, and its lists hang off that document -->
  <xsl:function name="f:scope" as="element()?">
    <xsl:param name="e" as="element()"/>
    <xsl:sequence select="$e/ancestor::*[self::ITEM or self::ARTICLE or self::PARAG or self::ALINEA or self::GR.SEQ
        or self::DLIST.ITEM or self::CONS.ANNEX or self::ANNEX or self::QUOT.S or self::TBL
        or self::CONSID[f:preamble-unit(.)] or self::VISA[f:preamble-unit(.)] or self::NOTE or self::DIVISION
        or self::CORRECTION or self::OLD.CORR or self::NEW.CORR
        or self::CONS.DOC.GEN[parent::CONS.DOC.GEN] or self::LETTER or self::SUBDIV or self::NP[f:np-point(.)]][1]"/>
  </xsl:function>

  <xsl:function name="f:preamble-unit" as="xs:boolean">
    <xsl:param name="e" as="element()"/>
    <xsl:sequence select="empty($e/ancestor::*[self::CONS.DOC.GEN or self::LETTER])
        and not(contains(root($e)/*/BIB.INSTANCE/NO.SEQ, '.'))"/>
  </xsl:function>

  <!-- points of a list hang off the enclosing unit; when it holds several lists (6.5.2 has two,
       each (a) to (d)), the second and later get a segment of their own: …/list_2/pnt_a. The first
       keeps plain eIds, so adding a list doesn't rename the points already there. In a level with
       sub-levels, which number themselves under it too (point I. of a list, sub-level I.), every
       list gets the segment: …/level_2/list_1/pnt_1. -->
  <xsl:function name="f:list-base" as="xs:string?">
    <xsl:param name="list" as="element()"/>
    <xsl:param name="base" as="xs:string?"/>
    <xsl:variable name="scope" as="element()?" select="f:scope($list)"/>
    <!-- its place among the lists of its scope, itself included (f:lists counts them in document order) -->
    <xsl:variable name="ordinal" as="xs:integer" select="($list ! accumulator-before('f:lists'))(f:scope-key($list))"/>
    <!-- also directly in an annex, beside its own points, where an item's label is one of theirs
         (point 1. of a list, annex point (1)) -->
    <xsl:variable name="beside-levels" as="xs:boolean" select="exists($scope/self::GR.SEQ//GR.SEQ[f:level-parent(.) is $scope])
        or exists($scope[self::CONS.ANNEX or self::ANNEX]) and (some $label in $list/(ITEM/NP/NO.P | DLIST.ITEM/PREFIX)/f:label(.)[. != '']
             satisfies exists(key('f:level-label-in-annex', generate-id($scope) || ' ' || $label, root($scope))))"/>
    <xsl:sequence select="
        if ($ordinal gt 1 or $beside-levels)
        then f:eid($base, 'list', string($ordinal))
        else $base"/>
  </xsl:function>

  <!-- the lists of each scope, counted in document order: what each list's number is -->
  <xsl:accumulator name="f:lists" as="map(xs:string, xs:integer)" initial-value="map{}">
    <xsl:accumulator-rule match="LIST | DLIST | ITEM[f:stray-start(.)] | DLIST.ITEM[f:stray-start(.)] | NP[f:np-point(.)][empty(f:np-previous(.))]"
                          select="let $k := f:scope-key(.) return map:put($value, $k, ($value($k), 0)[1] + 1)"/>
  </xsl:accumulator>

  <xsl:function name="f:scope-key" as="xs:string">
    <xsl:param name="e" as="element()"/>
    <xsl:sequence select="(f:scope($e) ! generate-id(.), '')[1]"/>
  </xsl:function>

  <!-- an annex's levels by their labels, for every annex they are in -->
  <xsl:key name="f:level-label-in-annex" match="GR.SEQ"
           use="let $label := f:label(f:level-num(.)) return ancestor::*[self::CONS.ANNEX or self::ANNEX] ! (generate-id(.) || ' ' || $label)"/>

  <xsl:template match="LIST" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="list" as="element()">
      <list kind="{f:list-kind(.)}">
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
       structure of its items; items of the list itself stay a list, which carries what amended the
       list as a whole -->
  <xsl:template match="LIST[QUOT.S]" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="kind" as="xs:string" select="f:list-kind(.)"/>
    <xsl:variable name="content" as="element()*">
      <xsl:for-each-group select="node()" group-adjacent="exists(self::QUOT.S)">
        <xsl:choose>
          <xsl:when test="current-grouping-key()">
            <xsl:apply-templates select="current-group()" mode="block">
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
            </xsl:apply-templates>
          </xsl:when>
          <xsl:when test="current-group()[self::ITEM]">
            <list kind="{$kind}">
              <xsl:sequence select="f:provenance($m)"/>
              <xsl:apply-templates select="current-group()[self::ITEM]" mode="block">
                <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
                <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
                <xsl:with-param name="base" as="xs:string?" select="f:list-base(current(), $base)" tunnel="yes"/>
              </xsl:apply-templates>
            </list>
            <xsl:apply-templates select="current-group()[not(self::ITEM)]" mode="unexpected"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:apply-templates select="current-group()" mode="unexpected"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:for-each-group>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $content)"/>
  </xsl:template>

  <!-- a list's kind from its TYPE; a TYPE the converter doesn't know stops it (f:unknown) -->
  <xsl:function name="f:list-kind" as="xs:string">
    <xsl:param name="list" as="element(LIST)"/>
    <xsl:variable name="kind" as="xs:string?" select="($f:list-kinds(string($list/@TYPE)), 'none'[not($list/@TYPE)])[1]"/>
    <xsl:sequence select="if (exists($kind)) then $kind else f:unknown($list, 'LIST TYPE=' || $list/@TYPE)"/>
  </xsl:function>

  <!-- An ITEM or DLIST.ITEM outside its list (an amendment adds "in Bulgarian:" after the list's end):
       a run of them is a list of its own, numbered among the lists around it (pnt_a/list_2/idt_1) -->
  <xsl:function name="f:stray-start" as="xs:boolean">
    <xsl:param name="item" as="element()"/>
    <xsl:sequence select="empty($item/parent::LIST | $item/parent::DLIST)
        and not($item/preceding-sibling::*[1][node-name() = node-name($item)])"/>
  </xsl:function>

  <xsl:function name="f:item-base" as="xs:string?">
    <xsl:param name="item" as="element()"/>
    <xsl:param name="base" as="xs:string?"/>
    <xsl:sequence select="
        if ($item/parent::LIST or $item/parent::DLIST) then $base
        else f:list-base(($item[f:stray-start(.)], $item/preceding-sibling::*[node-name() = node-name($item)][f:stray-start(.)][1])[1], $base)"/>
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
    <!-- a label an earlier point of the same list already has (2025/24 nests (2), (3) of one point in
         the outer list, next to its own (2), (3)) is kept as published: the points share pnt_2 and are
         marked ambiguous -->
    <xsl:variable name="eid" as="xs:string" select="
        if ($label != '') then f:eid(f:item-base(., $base), 'pnt', $label)
        else f:eid(f:item-base(., $base), 'idt', string(count(preceding-sibling::ITEM) + 1))"/>
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
          <!-- levels it holds are numbered within it, under its eId (art_3/pnt_a, anx_I/pnt_a/pnt_1) -->
          <xsl:with-param name="levels" as="map(*)" select="map{'base': $eid, 'ctx': ($ctx, f:ids($m))}" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- definition lists: each entry is a unit with its prefix as num and its term as heading; what
       is printed between term and definition (—), the list's separator -->
  <xsl:template match="DLIST" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="list" as="element()">
      <list kind="definition">
        <xsl:if test="@SEPARATOR">
          <xsl:attribute name="separator" select="@SEPARATOR"/>
        </xsl:if>
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
    <!-- an unlabelled definition is numbered by position as def_N, which a labelled point (pnt_1) can't
         collide with, as an unlabelled list item is idt_N -->
    <xsl:variable name="eid" as="xs:string" select="
        if ($label != '') then f:eid(f:item-base(., $base), 'pnt', $label)
        else f:eid(f:item-base(., $base), 'def', string(count(preceding-sibling::DLIST.ITEM) + 1))"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="definition">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:unit-content">
          <xsl:with-param name="num" as="element()?" select="PREFIX"/>
          <xsl:with-param name="heading" as="element()?" select="TERM"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <!-- levels it holds are numbered within it, under its eId (art_3/pnt_a, anx_I/pnt_a/pnt_1) -->
          <xsl:with-param name="levels" as="map(*)" select="map{'base': $eid, 'ctx': ($ctx, f:ids($m))}" tunnel="yes"/>
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
