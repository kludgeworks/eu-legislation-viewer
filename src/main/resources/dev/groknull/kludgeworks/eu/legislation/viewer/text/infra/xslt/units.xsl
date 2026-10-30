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
    $labelledParent, $levelOffset, $earlier
                 the nearest labelled level, the count of unnumbered top levels in earlier annex
                 fragments, and earlier annex files: how a level's number is placed (annexes.xsl)
    $division    the eId of the enclosing division (articles.xsl)
    $annexBase, $notePrefix
                 an attached document's eId for its annexes, and the prefix of its notes' eIds
                 (documents.xsl)

  The blocks themselves are in modules by concern: articles.xsl (enacting terms), lists.xsl,
  annexes.xsl (annexes and levels), tables.xsl, documents.xsl (attachments, corrigenda, letters,
  included documents); this module has the modes, mixed content and the other blocks.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:mode name="block" on-no-match="fail" on-multiple-match="fail"/>

  <!-- the children a template doesn't take, when its output has no place for anything else
       (citations, signatures, list items, table titles): fail rather than drop them. Markers,
       page breaks and whitespace are no content. -->
  <xsl:mode name="unexpected" on-no-match="fail" on-multiple-match="fail"/>

  <xsl:template match="*" mode="unexpected">
    <xsl:sequence select="f:unknown(., 'not expected inside ' || name(..))"/>
  </xsl:template>

  <xsl:template match="text()" mode="unexpected">
    <xsl:if test="normalize-space()">
      <xsl:sequence select="f:unknown(., 'text not expected inside ' || name(..))"/>
    </xsl:if>
  </xsl:template>

  <xsl:template match="processing-instruction() | comment()" mode="unexpected"/>

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
        or self::AGR or self::ACT or self::CORR or self::GR.VISA.INIT or self::SUBDIV or self::GENERAL or self::PREAMBLE.GEN or self::CONS.DOC.GEN
        or self::CN or self::TXT.COL or self::TXT.ROW or self::TI.ART or self::STI.ART
        or self::FULL.POWER or self::FULL.POWER.INIT or self::FULL.POWER.FINAL or self::PLENIPOTENTIARY
        or self::PROLOG or self::GR.CORRECTION or self::CONTENTS.CORR or self::COLL.LETTERS or self::DIV.CONSID
        or f:wraps-blocks($n)])"/>
  </xsl:function>

  <!-- emphasis, a language span or TXT around whole blocks: a boxed passage of paragraphs and a
       list, a level inside a box -->
  <xsl:function name="f:wraps-blocks" as="xs:boolean">
    <xsl:param name="n" as="node()"/>
    <xsl:sequence select="exists($n[self::HT or self::TXT or self::LV or self::FT][node()[f:is-block(.)]])"/>
  </xsl:function>

  <!-- … become a blockContainer, as AKN's, set in the typeface (a box, italics) or in the language -->
  <xsl:template match="HT[map:contains($f:emphasis-kinds, string(@TYPE))] | LV" mode="block">
    <blockContainer>
      <xsl:if test="self::HT">
        <xsl:attribute name="emphasis" select="$f:emphasis-kinds(string(@TYPE))"/>
      </xsl:if>
      <xsl:if test="self::LV">
        <xsl:attribute name="lang" select="lower-case(@LG)"/>
      </xsl:if>
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="if (self::LV and TXT) then TXT/node() else node()"/>
      </xsl:call-template>
      <xsl:apply-templates select="self::LV[TXT]/(node() except TXT)" mode="unexpected"/>
    </blockContainer>
  </xsl:template>

  <!-- TXT, FT and plain HT around blocks carry nothing to keep: the blocks -->
  <xsl:template match="HT[@TYPE = 'NORMAL'] | TXT | FT" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- ============================================================ headings -->

  <!-- whether content holds blocks (a table, a list, a second paragraph): a heading made of it
       needs <content>. An image without caption or transcription (a signature) and a displayed
       formula are inline there, as img and math. -->
  <xsl:function name="f:has-blocks" as="xs:boolean">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:sequence select="exists($nodes[f:is-block(.)]
        [not(self::INCL.ELEMENT[@TYPE = 'TIFF'][not(CAPTION | IMG.CNT)] or self::FORMULA.S)])"/>
  </xsl:function>

  <!-- what a heading line says: an NP's text and what follows it (its number is the num), else all -->
  <xsl:function name="f:line-content" as="node()*">
    <xsl:param name="line" as="element()"/>
    <xsl:sequence select="if ($line/self::NP) then ($line/TXT/node(), $line/(node() except (NO.P | TXT))) else $line/node()"/>
  </xsl:function>

  <!-- a heading of no fixed shape (STI.ART, TI.BLK, a CN group's title): its lines (P, NP) as
       f:lines takes them, else inline content, or its blocks in <content> where it holds any -->
  <xsl:template name="f:heading">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:variable name="elements" as="element()*" select="$nodes[self::*]"/>
    <xsl:choose>
      <xsl:when test="exists($elements) and empty($elements[not(self::P or self::NP)])
                      and empty($nodes[self::text()[normalize-space()]])">
        <xsl:call-template name="f:lines">
          <xsl:with-param name="lines" as="element()*" select="$elements"/>
          <xsl:with-param name="numbers" as="xs:boolean" select="true()"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="f:has-blocks($nodes)">
        <content>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" as="node()*" select="$nodes"/>
          </xsl:call-template>
        </content>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="f:inlines">
          <xsl:with-param name="nodes" as="node()*" select="$nodes"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

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
          <xsl:variable name="run" as="node()*" select="current-group()[not(self::processing-instruction() or self::comment())]"/>
          <xsl:if test="$run[self::*[not(self::IE)] or self::text()[normalize-space()]]">
            <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:covering-run($run)[not(f:pi-attr(., 'ID') = $ctx)])"/>
            <xsl:variable name="p" as="element()">
              <p>
                <xsl:sequence select="f:provenance($m)"/>
                <xsl:call-template name="f:inlines">
                  <xsl:with-param name="nodes" as="node()*" select="$run"/>
                  <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
                </xsl:call-template>
              </p>
            </xsl:variable>
            <xsl:sequence select="f:wrap-deleted($m, $p)"/>
          </xsl:if>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
  </xsl:template>

  <!-- the lines of a heading (a TITLE's TI or STI, a heading of P): inline content joined by <eol/>;
       where a line holds blocks (a numbered paragraph with a second paragraph, a table, a list, an
       image with its caption), the lines as blocks in <content> -->
  <xsl:template name="f:lines">
    <xsl:param name="lines" as="element()*"/>
    <!-- keep an NP line's number; f:title doesn't, having made it the num -->
    <xsl:param name="numbers" as="xs:boolean" select="false()"/>
    <xsl:choose>
      <xsl:when test="some $line in $lines satisfies f:has-blocks(f:line-content($line))">
        <content>
          <xsl:for-each select="$lines">
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" as="node()*" select="if (self::NP and not($numbers)) then f:line-content(.) else ."/>
            </xsl:call-template>
          </xsl:for-each>
        </content>
      </xsl:when>
      <xsl:otherwise>
        <xsl:for-each select="$lines">
          <xsl:if test="position() gt 1">
            <eol/>
          </xsl:if>
          <xsl:if test="$numbers and self::NP">
            <xsl:call-template name="f:inlines">
              <xsl:with-param name="nodes" as="node()*" select="NO.P/node()"/>
            </xsl:call-template>
            <t v=" "/>
          </xsl:if>
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" as="node()*" select="f:line-content(.)"/>
          </xsl:call-template>
        </xsl:for-each>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- ============================================================ other blocks -->

  <!-- what an amending act inserts or substitutes; its units get eIds under …/qst_N -->
  <!-- the unit a quotation is numbered within: the one its eId hangs off (an ALINEA's text is its
       article's or paragraph's); outside any (the body, an attachment's file), the file -->
  <xsl:function name="f:quotation-scope" as="element()">
    <xsl:param name="quote" as="element(QUOT.S)"/>
    <xsl:sequence select="($quote/ancestor::*[self::ITEM or self::ARTICLE or self::PARAG
                                             or self::GR.SEQ or self::DLIST.ITEM or self::ANNEX or self::CONS.ANNEX
                                             or self::OLD.CORR or self::NEW.CORR or self::DIVISION or self::LETTER
                                             or self::CONSID or self::VISA or self::NOTE][1], root($quote)/*)[1]"/>
  </xsl:function>

  <xsl:template match="QUOT.S" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <!-- an annex's fragments, which may be files of their own: its quotations are numbered through them -->
    <xsl:param name="annexFragments" as="element()*" select="()" tunnel="yes"/>
    <xsl:variable name="scope" as="element()" select="f:quotation-scope(.)"/>
    <xsl:variable name="at" as="xs:integer?" select="(for $i in 1 to count($annexFragments) return $i[$annexFragments[$i] is $scope])[1]"/>
    <xsl:variable name="earlier" as="xs:integer" select="
        if (exists($at)) then count(subsequence($annexFragments, 1, $at - 1)//QUOT.S[f:quotation-scope(.) intersect $annexFragments])
        else 0"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'qst', string($earlier + count($scope//QUOT.S[. &lt;&lt; current()][f:quotation-scope(.) is $scope]) + 1))"/>
    <quotedStructure eId="{$eid}">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
        <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
        <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        <xsl:with-param name="levelBase" as="xs:string?" select="$eid" tunnel="yes"/>
        <xsl:with-param name="levelCtx" as="xs:string*" select="$ctx" tunnel="yes"/>
        <xsl:with-param name="parentLevel" as="xs:string?" select="()" tunnel="yes"/>
        <xsl:with-param name="labelledParent" as="xs:string?" select="()" tunnel="yes"/>
        <xsl:with-param name="quoted" as="xs:boolean" select="true()" tunnel="yes"/>
      </xsl:call-template>
    </quotedStructure>
  </xsl:template>

  <xsl:template match="ANNOTATION | GR.ANNOTATION" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="annotation" as="element()">
      <annotation>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="TITLE">
          <heading>
            <xsl:call-template name="f:lines">
              <xsl:with-param name="lines" as="element()*" select="TITLE/TI/*, TITLE/STI/*"/>
              <xsl:with-param name="numbers" as="xs:boolean" select="true()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
            </xsl:call-template>
          </heading>
        </xsl:if>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="node() except TITLE"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
        </xsl:call-template>
      </annotation>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $annotation)"/>
  </xsl:template>

  <!-- an image with its caption, and the transcription of its text (IMG.CNT) as its description -->
  <xsl:template match="INCL.ELEMENT[@TYPE = 'TIFF']" mode="block">
    <figure src="{@FILEREF}">
      <xsl:sequence select="f:image-content(.)"/>
      <xsl:if test="CAPTION">
        <caption>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" as="node()*" select="CAPTION/node()"/>
          </xsl:call-template>
        </caption>
      </xsl:if>
      <xsl:if test="IMG.CNT">
        <description>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" as="node()*" select="IMG.CNT/node()"/>
          </xsl:call-template>
        </description>
      </xsl:if>
      <xsl:apply-templates select="node() except (CAPTION | IMG.CNT)" mode="unexpected"/>
    </figure>
  </xsl:template>

  <!-- CONTENT="TECH.DESIGN" → content="tech-design" -->
  <xsl:function name="f:image-content" as="attribute()?">
    <xsl:param name="incl" as="element(INCL.ELEMENT)"/>
    <xsl:if test="$incl/@CONTENT">
      <xsl:attribute name="content" select="lower-case(replace($incl/@CONTENT, '\.', '-'))"/>
    </xsl:if>
  </xsl:function>

  <!-- another Formex file of the same package, included here: its content, in place. An included
       annex stays an annex, under the eId of what includes it: anx/anx_A, or anx/qst_1/anx_A where an
       amending act quotes it -->
  <xsl:template match="INCL.ELEMENT[@TYPE = 'FORMEX.DOC']" mode="block">
    <xsl:variable name="included" as="element()" select="doc(resolve-uri(@FILEREF, base-uri(.)))/*"/>
    <xsl:choose>
      <xsl:when test="$included/self::ANNEX">
        <xsl:apply-templates select="$included" mode="annex">
          <xsl:with-param name="included" as="xs:boolean" select="true()"/>
        </xsl:apply-templates>
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
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- where the source prints its notes; notes are collected at the end anyway -->
  <xsl:template match="LOC.NOTES" mode="block"/>

  <!-- a marginal heading where only blocks fit (beside a definition list's entry): a paragraph -->
  <xsl:template match="MARGIN" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- an article's number or heading on its own, where an amending act replaces just that
       ("Article 3 is replaced by: 'Article 3 Scope'" quoting TI.ART and STI.ART without ARTICLE) -->
  <xsl:template match="TI.ART | STI.ART" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a CONTENTS inside a level (05.56): transparent -->
  <xsl:template match="CONTENTS" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

</xsl:stylesheet>
