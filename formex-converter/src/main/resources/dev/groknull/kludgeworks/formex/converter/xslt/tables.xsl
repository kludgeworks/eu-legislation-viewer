<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Tables (TBL, GR.TBL), rows, cells and groups of rows (BLK). Part of formex-to-legal-text.xsl.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <!-- ============================================================ tables -->

  <xsl:template match="TBL" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="levels" as="map(*)?" select="()" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="scope" as="element()?" select="f:scope(.)"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'tab', string(1 + (
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
              <xsl:with-param name="nodes" as="node()*" select="TITLE/TI/node(), TITLE/STI/node()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
            </xsl:call-template>
            <xsl:apply-templates select="TITLE/(node() except (TI | STI))" mode="unexpected"/>
          </caption>
        </xsl:if>
        <xsl:apply-templates select="CORPUS/node()" mode="block">
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <!-- its eId, and each row's number in it, groups of rows (BLK) included, counted once -->
          <xsl:with-param name="table" as="map(*)" tunnel="yes" select="map{
              'eid': $eid,
              'rows': map:merge(CORPUS//ROW[ancestor::TBL[1] is current()] ! map { generate-id(.): position() })}"/>
        </xsl:apply-templates>
        <!-- its notes, which EUR-Lex prints under it (a last row), not with its file's: each as it
             would be there, out of the table's context -->
        <xsl:call-template name="f:notes">
          <xsl:with-param name="notes" as="element(NOTE)*" select=".//NOTE[not(@NOTE.REF)][f:table-note(.)][ancestor::TBL[1] is current()]"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="table" as="map(*)?" select="()" tunnel="yes"/>
          <xsl:with-param name="division" as="map(*)?" select="()" tunnel="yes"/>
          <xsl:with-param name="levels" as="map(*)" select="map{}" tunnel="yes"/>
          <xsl:with-param name="annexFragments" as="element()*" select="()" tunnel="yes"/>
          <xsl:with-param name="annexBase" as="xs:string?" select="()" tunnel="yes"/>
          <xsl:with-param name="quoted" as="xs:boolean" select="false()" tunnel="yes"/>
          <xsl:with-param name="init" as="element(GR.CONSID.INIT)?" select="()" tunnel="yes"/>
        </xsl:call-template>
      </table>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $table)"/>
    <!-- what follows the rows: text blocks (GR.SEQ), the table's notes group (its notes are the
         table's or its file's, its annotations stay here, their lists under the table:
         anx/tab_1/def_1); anything else fails -->
    <xsl:apply-templates select="node() except (TITLE, CORPUS)" mode="block">
      <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
      <xsl:with-param name="levels" as="map(*)" select="map:merge(($levels, map{
          'base': $eid,
          'parent': $eid,
          'labelled': $eid,
          'unlabelledBase': $base,
          'ctx': ($ctx, f:ids($m))
      }), map{'duplicates': 'use-last'})" tunnel="yes"/>
    </xsl:apply-templates>
  </xsl:template>

  <!-- a consolidated version's table note (a NOTE TYPE="TABLE" in a TBL): its table prints it, not its
       file's notes, as EUR-Lex does (in its last row); an act as published prints it with the file's -->
  <xsl:function name="f:table-note" as="xs:boolean">
    <xsl:param name="note" as="element(NOTE)"/>
    <xsl:sequence select="$note/@TYPE = 'TABLE' and exists($note/ancestor::TBL) and exists(root($note)/(CONS.ACT | CONS.OTH))"/>
  </xsl:function>

  <!-- tables that belong together under one title -->
  <xsl:template match="GR.TBL" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="group" as="element()">
      <tableGroup>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="TITLE">
          <caption>
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" as="node()*" select="TITLE/TI/node(), TITLE/STI/node()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
            </xsl:call-template>
            <xsl:apply-templates select="TITLE/(node() except (TI | STI))" mode="unexpected"/>
          </caption>
        </xsl:if>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="node() except TITLE"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
        </xsl:call-template>
      </tableGroup>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $group)"/>
  </xsl:template>

  <xsl:template match="ROW" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <tr>
      <xsl:if test="@TYPE = 'HEADER'">
        <xsl:attribute name="header" select="true()"/>
      </xsl:if>
      <xsl:sequence select="f:provenance($m)"/>
      <xsl:apply-templates select="CELL" mode="block">
        <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
      </xsl:apply-templates>
      <xsl:apply-templates select="node() except CELL" mode="unexpected"/>
    </tr>
  </xsl:template>

  <!-- a cell of text alone (98% of 2016/2070's 2.3 million): one p of one t, as the template below
       makes it, without its eId, markers and blocks; unless a marker no ancestor has attributed is
       open over it (one opened in an earlier cell), whose provenance only the template below gives -->
  <xsl:template match="CELL[not(*)][not(processing-instruction())][not(comment())]" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:choose>
      <xsl:when test="exists(map:keys(accumulator-before('f:open'))[not(. = $ctx)])">
        <xsl:next-match/>
      </xsl:when>
      <xsl:otherwise>
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
          <xsl:if test="normalize-space()">
            <p><t v="{string(.)}"/></p>
          </xsl:if>
        </td>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- a cell is a context: what it numbers restarts in the next row (a level "PART A" in every row
       of a list of substances), so its units hang off tab_1/tr_103/td_7, by the source's column -->
  <xsl:template match="CELL" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="table" as="map(*)?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="f:new-markers(., $ctx)"/>
    <xsl:variable name="eid" as="xs:string?" select="
        if (exists($table)) then
          f:eid(f:eid($table?eid, 'tr', string($table?rows(generate-id(..)))), 'td', string((@COL, count(preceding-sibling::CELL) + 1)[1]))
        else ()"/>
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
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
        <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        <xsl:with-param name="pending" as="processing-instruction()*" select="$m" tunnel="yes"/>
        <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        <xsl:with-param name="levels" as="map(*)" select="map{'base': $eid, 'ctx': ($ctx, f:ids($m))}" tunnel="yes"/>
      </xsl:call-template>
    </td>
  </xsl:template>

  <!-- a header cell split diagonally: the column's heading, then the row's -->
  <xsl:template match="TXT.COL | TXT.ROW" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- ============================================================ CN tables -->

  <!-- an extract of the Combined Nomenclature (CN): a table with a header row (CN.HEADER), a row per
       code (CN.UNIT) and groups of rows under a title (GR.CN.UNIT). Each child of a row is a cell; the
       CN.* elements inside a cell (CN.RATE › CN.CONV.RATE) only say what the text is. -->
  <xsl:template match="CN" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="table" as="element()">
      <table>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:apply-templates select="* except GR.NOTES" mode="cn">
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
        </xsl:apply-templates>
      </table>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $table)"/>
    <xsl:apply-templates select="GR.NOTES" mode="block"/>
  </xsl:template>

  <xsl:mode name="cn" on-no-match="fail" on-multiple-match="fail"/>

  <xsl:template match="CN.HEADER | CN.UNIT" mode="cn">
    <tr>
      <xsl:if test="self::CN.HEADER">
        <xsl:attribute name="header" select="true()"/>
      </xsl:if>
      <xsl:for-each select="*">
        <td>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" as="node()*" select="f:cn-content(.)"/>
          </xsl:call-template>
        </td>
      </xsl:for-each>
      <xsl:apply-templates select="text() | processing-instruction() | comment()" mode="unexpected"/>
    </tr>
  </xsl:template>

  <xsl:template match="GR.CN.UNIT" mode="cn">
    <rowGroup columns="{max((1, .//CN.UNIT ! count(*)))}">
      <!-- its title: a TITLE, or the title's content directly (a numbered paragraph, text) -->
      <xsl:call-template name="f:title">
        <xsl:with-param name="title" as="element(TITLE)?" select="TI.GR.CN.UNIT/TITLE"/>
      </xsl:call-template>
      <xsl:if test="TI.GR.CN.UNIT[not(TITLE)][normalize-space()]">
        <heading>
          <xsl:call-template name="f:heading">
            <xsl:with-param name="nodes" as="node()*" select="TI.GR.CN.UNIT/node()"/>
          </xsl:call-template>
        </heading>
      </xsl:if>
      <xsl:apply-templates select="TI.GR.CN.UNIT[TITLE]/(node() except TITLE)" mode="unexpected"/>
      <xsl:apply-templates select="* except TI.GR.CN.UNIT" mode="cn"/>
    </rowGroup>
  </xsl:template>

  <xsl:template match="node()" mode="cn" priority="-1">
    <xsl:apply-templates select="." mode="unexpected"/>
  </xsl:template>

  <!-- a cell's content, with the CN.* elements that only label it taken off -->
  <xsl:function name="f:cn-content" as="node()*">
    <xsl:param name="cell" as="element()"/>
    <xsl:sequence select="$cell/node() ! (if (self::*[starts-with(local-name(), 'CN.')]) then f:cn-content(.) else .)"/>
  </xsl:function>

  <!-- a group of rows with a heading row (checklists in 2015/1998) -->
  <xsl:template match="BLK" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="f:new-markers(., $ctx)"/>
    <!-- as wide as its widest row, each cell its colspan -->
    <rowGroup columns="{max((1, .//ROW ! sum(CELL ! xs:integer((@COLSPAN, 1)[1]))))}">
      <!-- the title: a numbered paragraph gives num and heading, anything else the heading -->
      <xsl:for-each select="TI.BLK">
        <xsl:choose>
          <xsl:when test="NP">
            <num>
              <xsl:call-template name="f:inlines">
                <xsl:with-param name="nodes" as="node()*" select="NP/NO.P/node()"/>
              </xsl:call-template>
            </num>
            <heading>
              <xsl:call-template name="f:heading">
                <xsl:with-param name="nodes" as="node()*" select="NP/TXT/node(), f:np-rest(NP), node() except NP"/>
              </xsl:call-template>
            </heading>
          </xsl:when>
          <xsl:otherwise>
            <!-- its lines (P), or inline content -->
            <heading>
              <xsl:call-template name="f:heading">
                <xsl:with-param name="nodes" as="node()*" select="node()"/>
              </xsl:call-template>
            </heading>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:for-each>
      <xsl:for-each select="STI.BLK">
        <subheading>
          <xsl:call-template name="f:heading">
            <xsl:with-param name="nodes" as="node()*" select="node()"/>
          </xsl:call-template>
        </subheading>
      </xsl:for-each>
      <!-- everything else: rows and nested groups; anything unexpected fails -->
      <xsl:apply-templates select="* except (TI.BLK, STI.BLK)" mode="block">
        <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        <xsl:with-param name="pending" as="processing-instruction()*" select="$m" tunnel="yes"/>
      </xsl:apply-templates>
    </rowGroup>
  </xsl:template>

</xsl:stylesheet>
