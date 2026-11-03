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
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:mode name="inline" on-no-match="fail" on-multiple-match="fail"/>

  <!-- ============================================================ entry point -->

  <xsl:template name="f:inlines">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:for-each-group select="$nodes[not(self::processing-instruction() or self::comment())]"
                        group-adjacent="f:outer-marker(., $ctx)">
      <xsl:choose>
        <xsl:when test="current-grouping-key() = ''">
          <xsl:call-template name="f:quotes">
            <xsl:with-param name="nodes" as="node()*" select="current-group()"/>
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <xsl:variable name="marker" as="processing-instruction()" select="f:marker(current-group()[1], current-grouping-key())"/>
          <xsl:element name="{if (f:is-deletion($marker)) then 'deleted' else 'changed'}">
            <xsl:sequence select="f:amendment-attributes($marker)"/>
            <xsl:call-template name="f:inlines">
              <xsl:with-param name="nodes" as="node()*" select="current-group()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, current-grouping-key())" tunnel="yes"/>
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
    <xsl:variable name="start" as="element(QUOT.START)?" select="$nodes[self::QUOT.START][let $s := . return $nodes[self::QUOT.END][@ID = $s/@REF.END]][1]"/>
    <xsl:choose>
      <xsl:when test="$start">
        <xsl:variable name="end" as="element(QUOT.END)?" select="$nodes[self::QUOT.END][@ID = $start/@REF.END][1]"/>
        <xsl:call-template name="f:text-runs">
          <xsl:with-param name="nodes" as="node()*" select="$nodes[. &lt;&lt; $start]"/>
        </xsl:call-template>
        <quote startQuote="{f:quote-char($start)}" endQuote="{f:quote-char($end)}">
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" as="node()*" select="$nodes[. &gt;&gt; $start and . &lt;&lt; $end]"/>
          </xsl:call-template>
        </quote>
        <xsl:call-template name="f:quotes">
          <xsl:with-param name="nodes" as="node()*" select="$nodes[. &gt;&gt; $end]"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="f:text-runs">
          <xsl:with-param name="nodes" as="node()*" select="$nodes"/>
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
          <xsl:variable name="v" as="xs:string" select="string-join(current-group())"/>
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
    <!-- a CODE that is no XML character (0000, 0018: it happens) means the default -->
    <xsl:variable name="code" as="xs:integer" select="f:hex(($mark/@CODE[f:hex(.) ge 32 and not(f:hex(.) = (65534, 65535)) and not(f:hex(.) ge 55296 and f:hex(.) le 57343)],
                                                             if ($mark/self::QUOT.START) then '2018' else '2019')[1])"/>
    <xsl:sequence select="codepoints-to-string($code)"/>
  </xsl:function>

  <xsl:function name="f:hex" as="xs:integer">
    <xsl:param name="hex" as="xs:string"/>
    <xsl:sequence select="fold-left(string-to-codepoints(upper-case($hex)), 0,
        function($acc, $c) { $acc * 16 + (if ($c le 57) then $c - 48 else $c - 55) })"/>
  </xsl:function>

  <!-- ============================================================ elements -->

  <!-- typefaces, HT/@TYPE → legal-text's emphasis kinds (inline emphasis, a blockContainer) -->
  <xsl:variable name="f:emphasis-kinds" as="map(xs:string, xs:string)" select="map{
      'BOLD': 'b', 'ITALIC': 'i', 'UNDERLINE': 'u', 'SUP': 'sup', 'SUB': 'sub',
      'UC': 'uc', 'SC': 'sc', 'EXPANDED': 'expanded', 'STROKE': 'strike', 'BOX': 'box'}"/>

  <xsl:template match="HT" mode="inline">
    <xsl:variable name="kind" as="xs:string?" select="$f:emphasis-kinds(string(@TYPE))"/>
    <xsl:choose>
      <xsl:when test="@TYPE = 'NORMAL'">
        <xsl:call-template name="f:inlines">
          <xsl:with-param name="nodes" as="node()*" select="node()"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="$kind">
        <emphasis kind="{$kind}">
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" as="node()*" select="node()"/>
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
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="DATE" mode="inline">
    <date asWritten="{string(.)}">
      <xsl:sequence select="f:date-attribute('date', @ISO)"/>
    </date>
  </xsl:template>

  <!-- a note: every NOTE but a reference to one (NOTE.REF); older budgets give their notes no NOTE.ID -->
  <xsl:template match="NOTE[not(@NOTE.REF)]" mode="inline">
    <xsl:param name="notePrefix" as="xs:string?" tunnel="yes"/>
    <xsl:sequence select="f:note-ref(., $notePrefix)"/>
  </xsl:template>

  <!-- notes by id, for references to a note printed elsewhere (NOTE.REF): a Combined Nomenclature
       table refers to the same note hundreds of times -->
  <xsl:key name="f:note-by-id" match="NOTE[@NOTE.ID]" use="@NOTE.ID"/>

  <!-- every Formex document of the input: an OJ package's files and what they include, or the
       document alone -->
  <xsl:variable name="f:documents" as="element()*" select="
      f:with-included(if (/DOC) then /DOC/FMX//REF.PHYS[ends-with(@FILE, '.xml')]/doc(resolve-uri(@FILE, base-uri(.)))/* else /*)"/>

  <xsl:template match="NOTE[@NOTE.REF]" mode="inline">
    <xsl:param name="notePrefix" as="xs:string?" tunnel="yes"/>
    <xsl:sequence select="f:note-ref(f:note-target(.), $notePrefix)"/>
  </xsl:template>

  <!-- the note a NOTE.REF repeats: in the same file, else in another file of the package (a table in
       an included file refers to a note of the act); one that refers to no note stops the converter -->
  <xsl:function name="f:note-target" as="element(NOTE)">
    <xsl:param name="ref" as="element(NOTE)"/>
    <xsl:variable name="target" as="element(NOTE)?" select="(key('f:note-by-id', $ref/@NOTE.REF, root($ref)), $f:documents ! key('f:note-by-id', $ref/@NOTE.REF, root(.)))[1]"/>
    <xsl:sequence select="if ($target) then $target else f:unknown($ref, 'NOTE.REF without its note')"/>
  </xsl:function>

  <xsl:template match="REF.DOC.OJ" mode="inline">
    <ref kind="oj" series="{@COLL}">
      <xsl:if test="@NO.OJ">
        <xsl:attribute name="number" select="replace(@NO.OJ, '^0+', '')"/>
      </xsl:if>
      <xsl:sequence select="f:date-attribute('date', @DATE.PUB)"/>
      <xsl:if test="@PAGE.FIRST">
        <xsl:attribute name="page" select="@PAGE.FIRST"/>
      </xsl:if>
      <xsl:call-template name="f:inlines">
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
      </xsl:call-template>
    </ref>
  </xsl:template>

  <!-- 06.x: a reference with the ELI URI (REF.DOC@URI, LINK@URI) -->
  <xsl:template match="REF.DOC[@URI] | LINK" mode="inline">
    <ref kind="uri" href="{@URI}">
      <xsl:call-template name="f:inlines">
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
      </xsl:call-template>
    </ref>
  </xsl:template>

  <xsl:template match="REF.DOC" mode="inline">
    <ref kind="oj" series="{@COLL}">
      <xsl:if test="@NO.DOC">
        <xsl:attribute name="number" select="@NO.DOC"/>
      </xsl:if>
      <xsl:sequence select="f:date-attribute('date', @DATE.PUB)"/>
      <xsl:call-template name="f:inlines">
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
      </xsl:call-template>
    </ref>
  </xsl:template>

  <!-- text in another language (LV): a quoted term, an agreement's multilingual formula -->
  <xsl:template match="LV" mode="inline">
    <span xml:lang="{lower-case(@LG)}">
      <xsl:call-template name="f:inlines">
        <xsl:with-param name="nodes" as="node()*" select="if (TXT) then TXT/node() else node()"/>
      </xsl:call-template>
      <xsl:apply-templates select="TXT/../(node() except TXT)" mode="unexpected"/>
    </span>
  </xsl:template>

  <!-- wrappers whose text is running text where they occur: TXT outside a numbered paragraph, a
       definition, a marginal heading (MARGIN, "CHAPTER 9 0 —" before a budget heading), a C-series
       number in a title met inside content -->
  <xsl:template match="TXT | DEFINITION | MARGIN | NO.DOC.C" mode="inline">
    <xsl:call-template name="f:inlines">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- anonymised text: the placeholder as printed (██████), and what it holds (a note reference) -->
  <xsl:template match="ANONYMOUS" mode="inline">
    <xsl:if test="@PLACEHOLDER">
      <t v="{@PLACEHOLDER}"/>
    </xsl:if>
    <xsl:call-template name="f:inlines">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a reference to the European Court Reports (C-280/00 … [2003] ECR I-7747): its text -->
  <xsl:template match="REF.DOC.ECR" mode="inline">
    <xsl:call-template name="f:inlines">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="ADDR" mode="inline">
    <xsl:call-template name="f:inlines">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
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
    <img src="{@FILEREF}">
      <xsl:sequence select="f:image-content(.)"/>
    </img>
  </xsl:template>

  <!-- an empty table cell -->
  <xsl:template match="IE" mode="inline"/>

  <xsl:template match="*" mode="inline" priority="-1">
    <xsl:sequence select="f:unknown(., ())"/>
  </xsl:template>

  <!-- ============================================================ helpers -->

  <!-- 20151105 → "2015-11-05", whether it is a date or not (19931911 → "1993-19-11") -->
  <xsl:function name="f:iso-date" as="xs:string">
    <xsl:param name="iso" as="xs:string?"/>
    <xsl:sequence select="replace(normalize-space($iso), '^(\d{4})(\d{2})(\d{2})$', '$1-$2-$3')"/>
  </xsl:function>

  <!-- 20151105 → 2015-11-05 -->
  <xsl:function name="f:date" as="xs:string">
    <xsl:param name="iso" as="xs:string?"/>
    <xsl:variable name="d" as="xs:string" select="f:iso-date($iso)"/>
    <xsl:sequence select="if ($d castable as xs:date) then $d else error(xs:QName('f:date'), 'Not a date: ' || $iso)"/>
  </xsl:function>

  <!-- a date in the text or a reference, as an attribute; none when the source's value is not a
       date (19931911, 20230931, 1981: they happen), so the text keeps its wording without it -->
  <xsl:function name="f:date-attribute" as="attribute()?">
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="iso" as="xs:string?"/>
    <xsl:variable name="d" as="xs:string" select="f:iso-date($iso)"/>
    <xsl:if test="$d castable as xs:date">
      <xsl:attribute name="{$name}" select="$d"/>
    </xsl:if>
  </xsl:function>

  <xsl:function name="f:note-ref" as="element()">
    <xsl:param name="note" as="element(NOTE)"/>
    <xsl:param name="prefix" as="xs:string?"/>
    <noteRef href="#{f:note-eid($note, $prefix)}" marker="{f:note-marker($note)}"/>
  </xsl:function>

  <!-- Note numbers in one pass: counting preceding::NOTE for every note is quadratic, minutes for a
       55 MB consolidation. f:notes-started at a note counts the notes before it and around it (a
       note can sit inside another); f:footnotes-ended the running-numbered ones before it. -->
  <xsl:accumulator name="f:notes-started" as="xs:integer" initial-value="0">
    <xsl:accumulator-rule match="NOTE[not(@NOTE.REF)]" phase="start" select="$value + 1"/>
  </xsl:accumulator>

  <xsl:accumulator name="f:footnotes-ended" as="xs:integer" initial-value="0">
    <xsl:accumulator-rule match="NOTE[not(@NOTE.REF)][not(@TYPE = ('TABLE', 'QUOTATION'))][not(@NUMBERING = 'STAR')]
                                     [not(@NUMBER.ORG) or f:renumbered(.)][empty(f:deletions(.))]"
                          phase="end" select="$value + 1"/>
  </xsl:accumulator>

  <!-- note_3 in the main document; note_anx2-1 in the second annex file of an OJ act; in another
       file, the file's own sequence number, unique in the package: note_inc000402-1 in an included
       protocol, agreement or act (any file whose NO.SEQ, 0001.0001, sits below the main
       document's, 0001), note_att0301-1 in an attached document's own file ($prefix, which names
       that file: a file the attachment includes has its own) -->
  <xsl:function name="f:note-eid" as="xs:string">
    <xsl:param name="note" as="element(NOTE)"/>
    <xsl:param name="prefix" as="xs:string?"/>
    <xsl:variable name="root" as="element()" select="root($note)/*"/>
    <xsl:variable name="seq" as="xs:string" select="f:file-seq(base-uri($root))"/>
    <xsl:variable name="file" as="xs:string" select="
        if ($root/self::ANNEX) then 'anx' || $seq || '-'
        else if ($prefix = 'att' || $seq || '-') then $prefix
        else if ($root/self::GENERAL or contains($root/BIB.INSTANCE/NO.SEQ, '.')) then 'inc' || $seq || '-'
        else string($prefix)"/>
    <!-- the value before a node includes the node's own start rule -->
    <xsl:sequence select="'note_' || $file || ($note ! accumulator-before('f:notes-started'))"/>
  </xsl:function>

  <!-- a package file's own sequence number: L_2009353EN.01000301.xml → 01000301 -->
  <xsl:function name="f:file-seq" as="xs:string">
    <xsl:param name="uri" as="xs:string?"/>
    <xsl:sequence select="replace(tokenize($uri, '/')[last()], '^.*?\.(\d+)\.(fmx\.)?xml$', '$1')"/>
  </xsl:function>

  <!-- the marker as printed: NUMBER.ORG if given (but in a consolidation's footnotes:
       f:renumbered), * for star numbering, else the running number (footnotes through the
       document, table notes within their table) -->
  <xsl:function name="f:note-marker" as="xs:string">
    <xsl:param name="note" as="element(NOTE)"/>
    <xsl:sequence select="
        if ($note/@NUMBER.ORG and not(f:renumbered($note))) then string($note/@NUMBER.ORG)
        else if ($note/@NUMBERING = 'STAR') then '*'
        else if ($note/@TYPE = 'TABLE')
        then string(count($note/ancestor::TBL[1]//NOTE[not(@NOTE.REF)][@TYPE = 'TABLE'][not(@NUMBERING = 'STAR')]
                     [. &lt;&lt; $note][empty(. intersect $note/ancestor::NOTE)]
                     [ancestor::TBL[1] is $note/ancestor::TBL[1]]) + 1)
        else string(($note ! accumulator-before('f:footnotes-ended')) + 1)"/>
  </xsl:function>

  <!-- a consolidation's footnote: numbered through it, as EUR-Lex prints them, NUMBER.ORG (the
       number it had in the act it came from) left out, whatever its NUMBERING: ARAB or OTHER
       (02015R1998-20260322, 02008R0767's seventh version). An OJ act prints NUMBER.ORG (32010R1005,
       22022D1121); a star-numbered footnote keeps its *, which no consolidation in Cellar's xhtml
       has shown otherwise -->
  <xsl:function name="f:renumbered" as="xs:boolean">
    <xsl:param name="note" as="element(NOTE)"/>
    <xsl:sequence select="exists(root($note)/(CONS.ACT | CONS.OTH)) and ($note/@TYPE, 'FOOTNOTE')[1] = 'FOOTNOTE'"/>
  </xsl:function>

  <!-- an element the converter doesn't know: fails in strict mode, so new Formex shows up in tests.
       It never returns: item()*, so that it can stand where a value is expected (f:list-kind) -->
  <xsl:function name="f:unknown" as="item()*">
    <xsl:param name="e" as="node()"/>
    <xsl:param name="detail" as="xs:string?"/>
    <xsl:message terminate="yes" error-code="f:unknown">Unsupported Formex: {path($e)}{$detail ! (' (' || . || ')')}</xsl:message>
  </xsl:function>

</xsl:stylesheet>
