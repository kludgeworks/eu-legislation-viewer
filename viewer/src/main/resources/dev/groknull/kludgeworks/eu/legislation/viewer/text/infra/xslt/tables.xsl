<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Tables (TBL, GR.TBL), rows, cells and groups of rows (BLK). Part of normalise.xsl.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:f="https://kludgeworks.groknull.dev/ns/normalise"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <!-- ============================================================ tables -->

  <xsl:template match="TBL" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="scope" as="element()?" select="f:scope(.)"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'tbl', string(1 + (
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
            <xsl:apply-templates select="TITLE/(node() except (TI | STI))" mode="unexpected"/>
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
    <!-- what follows the rows: text blocks (GR.SEQ), the table's notes group (its notes are collected,
         its annotations stay here); anything else fails -->
    <xsl:apply-templates select="* except (TITLE, CORPUS)" mode="block"/>
  </xsl:template>

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
              <xsl:with-param name="nodes" select="TITLE/TI/node(), TITLE/STI/node()"/>
              <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" select="()" tunnel="yes"/>
            </xsl:call-template>
            <xsl:apply-templates select="TITLE/(node() except (TI | STI))" mode="unexpected"/>
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
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <tr>
      <xsl:if test="@TYPE = 'HEADER'">
        <xsl:attribute name="header" select="true()"/>
      </xsl:if>
      <xsl:sequence select="f:provenance($m)"/>
      <xsl:apply-templates select="CELL" mode="block">
        <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
        <xsl:with-param name="pending" select="()" tunnel="yes"/>
      </xsl:apply-templates>
      <xsl:apply-templates select="node() except CELL" mode="unexpected"/>
    </tr>
  </xsl:template>

  <xsl:template match="CELL" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="f:new-markers(., $ctx)"/>
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
    <xsl:variable name="m" as="processing-instruction()*" select="f:new-markers(., $ctx)"/>
    <rowGroup>
      <!-- the title: a numbered paragraph gives num and heading, anything else the heading -->
      <xsl:for-each select="TI.BLK">
        <xsl:choose>
          <xsl:when test="NP">
            <num>
              <xsl:call-template name="f:inlines">
                <xsl:with-param name="nodes" select="NP/NO.P/node()"/>
              </xsl:call-template>
            </num>
            <heading>
              <xsl:call-template name="f:inlines">
                <xsl:with-param name="nodes" select="NP/TXT/node()"/>
              </xsl:call-template>
            </heading>
            <xsl:apply-templates select="node() except NP, NP/(node() except (NO.P | TXT))" mode="unexpected"/>
          </xsl:when>
          <xsl:otherwise>
            <heading>
              <xsl:call-template name="f:inlines">
                <xsl:with-param name="nodes" select="node()"/>
              </xsl:call-template>
            </heading>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:for-each>
      <xsl:for-each select="STI.BLK">
        <subheading>
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" select="node()"/>
          </xsl:call-template>
        </subheading>
      </xsl:for-each>
      <!-- everything else: rows and nested groups; anything unexpected fails -->
      <xsl:apply-templates select="* except (TI.BLK, STI.BLK)" mode="block">
        <xsl:with-param name="ctx" select="($ctx, f:ids($m))" tunnel="yes"/>
        <xsl:with-param name="pending" select="$m" tunnel="yes"/>
      </xsl:apply-templates>
    </rowGroup>
  </xsl:template>

</xsl:stylesheet>
