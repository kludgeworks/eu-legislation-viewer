<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Inline content: text, emphasis, dates, quotes, footnote references, references, line breaks,
  and the amendment spans inside running text. f:inlines is the only way in: it groups the nodes
  by the amendment markers open over them, pairs quotation marks, and merges text split by
  page-break processing instructions.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:f="https://kludgeworks.groknull.dev/ns/normalise"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:mode name="inline" on-no-match="fail"/>

  <!-- ============================================================ entry point -->

  <xsl:template name="f:inlines">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:for-each-group select="$nodes[not(self::processing-instruction() or self::comment())]"
                        group-adjacent="f:outer-marker(., $ctx)">
      <xsl:choose>
        <xsl:when test="current-grouping-key() = ''">
          <xsl:call-template name="f:quotes">
            <xsl:with-param name="nodes" select="current-group()"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <xsl:variable name="marker" select="f:marker(current-group()[1], current-grouping-key())"/>
          <xsl:element name="{if (f:is-deletion($marker)) then 'deleted' else 'changed'}">
            <xsl:sequence select="f:amendment-attributes($marker)"/>
            <xsl:call-template name="f:inlines">
              <xsl:with-param name="nodes" select="current-group()"/>
              <xsl:with-param name="ctx" select="($ctx, current-grouping-key())" tunnel="yes"/>
            </xsl:call-template>
          </xsl:element>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
  </xsl:template>

  <!-- the open marker with this id at node $n -->
  <xsl:function name="f:marker" as="processing-instruction()">
    <xsl:param name="n" as="node()"/>
    <xsl:param name="id" as="xs:string"/>
    <xsl:sequence select="($n ! accumulator-before('f:open'))($id)"/>
  </xsl:function>

  <!-- QUOT.START … QUOT.END within the same run become <quote>; a pair that crosses elements
       stays as literal quotation marks -->
  <xsl:template name="f:quotes">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:variable name="start" select="$nodes[self::QUOT.START][let $s := . return $nodes[self::QUOT.END][@ID = $s/@REF.END]][1]"/>
    <xsl:choose>
      <xsl:when test="$start">
        <xsl:variable name="end" select="$nodes[self::QUOT.END][@ID = $start/@REF.END][1]"/>
        <xsl:call-template name="f:text-runs">
          <xsl:with-param name="nodes" select="$nodes[. &lt;&lt; $start]"/>
        </xsl:call-template>
        <quote startQuote="{f:quote-char($start)}" endQuote="{f:quote-char($end)}">
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" select="$nodes[. &gt;&gt; $start and . &lt;&lt; $end]"/>
          </xsl:call-template>
        </quote>
        <xsl:call-template name="f:quotes">
          <xsl:with-param name="nodes" select="$nodes[. &gt;&gt; $end]"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="f:text-runs">
          <xsl:with-param name="nodes" select="$nodes"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- adjacent text nodes (split by <?PAGE?>) become one <t> -->
  <xsl:template name="f:text-runs">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:for-each-group select="$nodes" group-adjacent="self::text() instance of text()">
      <xsl:choose>
        <xsl:when test="current-grouping-key()">
          <xsl:variable name="v" select="string-join(current-group())"/>
          <xsl:if test="$v != ''">
            <t v="{$v}"/>
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="current-group()" mode="inline"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
  </xsl:template>

  <xsl:function name="f:quote-char" as="xs:string">
    <xsl:param name="mark" as="element()"/>
    <xsl:sequence select="codepoints-to-string(f:hex(($mark/@CODE, if ($mark/self::QUOT.START) then '2018' else '2019')[1]))"/>
  </xsl:function>

  <xsl:function name="f:hex" as="xs:integer">
    <xsl:param name="hex" as="xs:string"/>
    <xsl:sequence select="fold-left(string-to-codepoints(upper-case($hex)), 0,
        function($acc, $c) { $acc * 16 + (if ($c le 57) then $c - 48 else $c - 55) })"/>
  </xsl:function>

  <!-- ============================================================ elements -->

  <xsl:template match="HT" mode="inline">
    <xsl:variable name="kind" select="map{
        'BOLD': 'b', 'ITALIC': 'i', 'UNDERLINE': 'u', 'SUP': 'sup', 'SUB': 'sub',
        'UC': 'uc', 'SC': 'sc', 'EXPANDED': 'expanded'}(string(@TYPE))"/>
    <xsl:choose>
      <xsl:when test="@TYPE = 'NORMAL'">
        <xsl:call-template name="f:inlines">
          <xsl:with-param name="nodes" select="node()"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$kind">
        <emphasis kind="{$kind}">
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" select="node()"/>
          </xsl:call-template>
        </emphasis>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="f:unknown(., 'HT TYPE=' || @TYPE)"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- a number set apart typographically (FT TYPE="NUMBER"): plain text -->
  <xsl:template match="FT" mode="inline">
    <xsl:call-template name="f:inlines">
      <xsl:with-param name="nodes" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DATE" mode="inline">
    <date date="{f:date(@ISO)}" asWritten="{string(.)}"/>
  </xsl:template>

  <xsl:template match="NOTE[@NOTE.ID]" mode="inline">
    <xsl:sequence select="f:note-ref(.)"/>
  </xsl:template>

  <xsl:template match="NOTE[@NOTE.REF]" mode="inline">
    <xsl:variable name="target" select="root(.)//NOTE[@NOTE.ID = current()/@NOTE.REF][1]"/>
    <xsl:sequence select="if ($target) then f:note-ref($target) else f:unknown(., 'NOTE.REF without its note')"/>
  </xsl:template>

  <xsl:template match="REF.DOC.OJ" mode="inline">
    <ref kind="oj" series="{@COLL}">
      <xsl:if test="@NO.OJ">
        <xsl:attribute name="number" select="replace(@NO.OJ, '^0+', '')"/>
      </xsl:if>
      <xsl:if test="@DATE.PUB">
        <xsl:attribute name="date" select="f:date(@DATE.PUB)"/>
      </xsl:if>
      <xsl:if test="@PAGE.FIRST">
        <xsl:attribute name="page" select="@PAGE.FIRST"/>
      </xsl:if>
      <xsl:call-template name="f:inlines">
        <xsl:with-param name="nodes" select="node()"/>
      </xsl:call-template>
    </ref>
  </xsl:template>

  <!-- 06.x: a reference with the ELI URI (REF.DOC@URI, LINK@URI) -->
  <xsl:template match="REF.DOC[@URI] | LINK" mode="inline">
    <ref kind="uri" href="{@URI}">
      <xsl:call-template name="f:inlines">
        <xsl:with-param name="nodes" select="node()"/>
      </xsl:call-template>
    </ref>
  </xsl:template>

  <xsl:template match="REF.DOC" mode="inline">
    <ref kind="oj" series="{@COLL}">
      <xsl:if test="@NO.DOC">
        <xsl:attribute name="number" select="@NO.DOC"/>
      </xsl:if>
      <xsl:if test="@DATE.PUB">
        <xsl:attribute name="date" select="f:date(@DATE.PUB)"/>
      </xsl:if>
      <xsl:call-template name="f:inlines">
        <xsl:with-param name="nodes" select="node()"/>
      </xsl:call-template>
    </ref>
  </xsl:template>

  <!-- text in another language (LV): a quoted term, an agreement's multilingual formula -->
  <xsl:template match="LV" mode="inline">
    <span lang="{lower-case(@LG)}">
      <xsl:call-template name="f:inlines">
        <xsl:with-param name="nodes" select="if (TXT) then TXT/node() else node()"/>
      </xsl:call-template>
    </span>
  </xsl:template>

  <xsl:template match="ADDR" mode="inline">
    <xsl:call-template name="f:inlines">
      <xsl:with-param name="nodes" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- 05.59+ -->
  <xsl:template match="BR" mode="inline">
    <eol/>
  </xsl:template>

  <!-- a quotation mark whose partner sits in another element -->
  <xsl:template match="QUOT.START | QUOT.END" mode="inline">
    <t v="{f:quote-char(.)}"/>
  </xsl:template>

  <!-- an image inside running text or a heading -->
  <xsl:template match="INCL.ELEMENT[@TYPE = 'TIFF']" mode="inline">
    <img src="{@FILEREF}"/>
  </xsl:template>

  <!-- an empty table cell -->
  <xsl:template match="IE" mode="inline"/>

  <xsl:template match="*" mode="inline" priority="-1">
    <xsl:sequence select="f:unknown(., ())"/>
  </xsl:template>

  <!-- ============================================================ helpers -->

  <!-- 20151105 → 2015-11-05 -->
  <xsl:function name="f:date" as="xs:string">
    <xsl:param name="iso" as="xs:string?"/>
    <xsl:variable name="d" select="replace(normalize-space($iso), '^(\d{4})(\d{2})(\d{2})$', '$1-$2-$3')"/>
    <xsl:sequence select="if ($d castable as xs:date) then $d else error(xs:QName('f:date'), 'Not a date: ' || $iso)"/>
  </xsl:function>

  <xsl:function name="f:note-ref" as="element()">
    <xsl:param name="note" as="element(NOTE)"/>
    <noteRef href="#{f:note-eid($note)}" marker="{f:note-marker($note)}"/>
  </xsl:function>

  <!-- note_3 in the main document; note_anx2-1 in the second annex file of an OJ act -->
  <xsl:function name="f:note-eid" as="xs:string">
    <xsl:param name="note" as="element(NOTE)"/>
    <xsl:variable name="root" select="root($note)/*"/>
    <!-- the annex file's own sequence number, unique in the package also for included files -->
    <xsl:variable name="file" select="
        if ($root/self::ANNEX)
        then 'anx' || replace(tokenize(base-uri($root), '/')[last()], '^.*?\.(\d+)\.(fmx\.)?xml$', '$1') || '-'
        else ''"/>
    <!-- ancestors count too: a note can sit inside another note -->
    <xsl:sequence select="'note_' || $file || (count($note/(preceding::NOTE | ancestor::NOTE)[@NOTE.ID]) + 1)"/>
  </xsl:function>

  <!-- the marker as printed: NUMBER.ORG if given, * for star numbering, else the running number
       (footnotes through the document, table notes within their table) -->
  <xsl:function name="f:note-marker" as="xs:string">
    <xsl:param name="note" as="element(NOTE)"/>
    <xsl:sequence select="
        if ($note/@NUMBER.ORG) then string($note/@NUMBER.ORG)
        else if ($note/@NUMBERING = 'STAR') then '*'
        else if ($note/@TYPE = 'TABLE')
        then string(count($note/preceding::NOTE[@NOTE.ID][@TYPE = 'TABLE'][not(@NUMBERING = 'STAR')]
                     [ancestor::TBL[1] is $note/ancestor::TBL[1]]) + 1)
        else string(count($note/preceding::NOTE[@NOTE.ID][not(@TYPE = ('TABLE', 'QUOTATION'))]
                     [not(@NUMBER.ORG or @NUMBERING = 'STAR')]) + 1)"/>
  </xsl:function>

  <!-- an element the normaliser doesn't know: fails in strict mode, so new Formex shows up in tests -->
  <xsl:function name="f:unknown" as="empty-sequence()">
    <xsl:param name="e" as="node()"/>
    <xsl:param name="detail" as="xs:string?"/>
    <xsl:message terminate="yes" error-code="f:unknown">Unsupported Formex: {path($e)}{$detail ! (' (' || . || ')')}</xsl:message>
  </xsl:function>

</xsl:stylesheet>
