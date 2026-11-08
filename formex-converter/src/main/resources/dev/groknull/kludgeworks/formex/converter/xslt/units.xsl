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
    $levels      the levels' context (annexes.xsl), which each unit that numbers levels within it
                 starts afresh (an annex, an article, a point, a cell, a quoted structure):
                   base     the eId its levels hang off
                   ctx      $ctx there: levels are flat, so a level's provenance is everything
                            covering it from there, including what covers its parent level
                   parent   the eId of the enclosing level, for unnumbered levels
                   labelled the nearest labelled level
                   offset, earlier
                            the count of unnumbered top levels in earlier annex fragments, and
                            earlier annex files: how a level's number is placed
    $division    the enclosing division: its eId, and the base it applies to (a division and an
                 article use it only while their base is that one, articles.xsl); for recitals, the
                 enclosing group of recitals' eId
    $annexBase, $notePrefix
                 an attached document's eId for its annexes, and the prefix of its notes' eIds
                 (documents.xsl)
    $annexFragments
                 the fragments (files) of the annex being converted: what is numbered through them
                 (quotations, letters) counts the earlier ones
    $quoted      inside a quoted structure: an annex there is numbered among the quotation's
    $table       the enclosing table: its eId, and each of its rows' numbers: units in cells hang
                 off tab_N/tr_N/td_N (tables.xsl)
    $init        the recitals' opening words, where 01.01 puts them inside the first CONSID

  The blocks themselves are in modules by concern: articles.xsl (enacting terms), lists.xsl,
  annexes.xsl (annexes and levels), tables.xsl, documents.xsl (attachments, corrigenda, letters);
  this module has the modes, mixed content, included documents and the other blocks.
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
    <xsl:sequence select="exists($n[self::HT or self::TXT or self::LV or self::FT][node()[f:is-block(.)][not(self::INCL.ELEMENT[@TYPE = 'TIFF'][not(CAPTION | IMG.CNT)] or self::FORMULA.S)]])"/>
  </xsl:function>

  <!-- … become a blockContainer, as AKN's, set in the typeface (a box, italics) or in the language -->
  <xsl:template match="HT[map:contains($f:emphasis-kinds, string(@TYPE))] | LV" mode="block">
    <blockContainer>
      <xsl:if test="self::HT">
        <xsl:attribute name="emphasis" select="$f:emphasis-kinds(string(@TYPE))"/>
      </xsl:if>
      <xsl:if test="self::LV">
        <xsl:attribute name="xml:lang" select="lower-case(@LG)"/>
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
  <!-- what NPs hold besides their number and text, which follows its text wherever its text goes;
       not the whitespace between its children, which may sit inside an amendment marker that opens
       before the number (<NP><?CLG.MDFO?> <NO.P>(e)</NO.P><TXT>… a title<?CLG.MDFC?> listed …</TXT>):
       moved after the text, it would make the marker seem to cover all of the text -->
  <xsl:function name="f:np-rest" as="node()*">
    <xsl:param name="np" as="element(NP)*"/>
    <xsl:sequence select="$np/(node() except (NO.P | TXT))[not(self::text()[not(normalize-space())])]"/>
  </xsl:function>

  <xsl:function name="f:line-content" as="node()*">
    <xsl:param name="line" as="element()"/>
    <xsl:sequence select="if ($line/self::NP) then ($line/TXT/node(), f:np-rest($line)) else $line/node()"/>
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
          <xsl:variable name="run" as="node()*" select="current-group()[not(self::processing-instruction() or self::comment()) or f:empty-marker(.)]"/>
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
    <!-- the NP line whose number f:title made the num, which the line doesn't repeat; every other
         NP line keeps its number -->
    <xsl:param name="num" as="element(NP)?" select="()"/>
    <xsl:choose>
      <xsl:when test="some $line in $lines satisfies f:has-blocks(f:line-content($line))">
        <content>
          <xsl:for-each select="$lines">
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" as="node()*" select="if (. is $num) then f:line-content(.) else ."/>
            </xsl:call-template>
          </xsl:for-each>
        </content>
      </xsl:when>
      <xsl:otherwise>
        <xsl:for-each select="$lines">
          <xsl:if test="position() gt 1">
            <eol/>
          </xsl:if>
          <xsl:if test="self::NP and not(. is $num)">
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

  <!-- the unit a quotation is numbered within: the one its eId hangs off (an ALINEA's text is its
       article's or paragraph's); outside any (the body, an attachment's file), the file -->
  <xsl:function name="f:quotation-scope" as="element()">
    <xsl:param name="quote" as="element(QUOT.S)"/>
    <xsl:sequence select="($quote/ancestor::*[self::ITEM or self::ARTICLE or self::PARAG
                                             or self::GR.SEQ or self::DLIST.ITEM or self::ANNEX or self::CONS.ANNEX
                                             or self::OLD.CORR or self::NEW.CORR or self::DIVISION or self::LETTER
                                             or self::CONSID or self::VISA or self::NOTE][1], root($quote)/*)[1]"/>
  </xsl:function>

  <!-- what an amending act inserts or substitutes; its units get eIds under …/qstr_N -->
  <xsl:template match="QUOT.S" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <!-- an annex's fragments, which may be files of their own: its quotations are numbered through them -->
    <xsl:param name="annexFragments" as="element()*" select="()" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="scope" as="element()" select="f:quotation-scope(.)"/>
    <xsl:variable name="at" as="xs:integer?" select="f:node-index($annexFragments, $scope)"/>
    <xsl:variable name="earlier" as="xs:integer" select="
        if (exists($at)) then count(subsequence($annexFragments, 1, $at - 1)//QUOT.S[f:quotation-scope(.) intersect $annexFragments])
        else 0"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'qstr', string($earlier + count($scope//QUOT.S[. &lt;&lt; current()][f:quotation-scope(.) is $scope]) + 1))"/>
    <xsl:variable name="quotedStructure" as="element()">
      <quotedStructure eId="{$eid}">
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="node()"/>
          <xsl:with-param name="ctx" as="xs:string*" select="if ($m[f:is-deletion(.)]) then ($ctx, f:ids($m)) else $ctx" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="levels" as="map(*)" select="map{'base': $eid, 'ctx': if ($m[f:is-deletion(.)]) then ($ctx, f:ids($m)) else $ctx}" tunnel="yes"/>
          <xsl:with-param name="quoted" as="xs:boolean" select="true()" tunnel="yes"/>
        </xsl:call-template>
      </quotedStructure>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $quotedStructure)"/>
  </xsl:template>

  <!-- an annotation (the notes printed with a form) numbers what it holds apart from the text around
       it: an outermost one is a context, ann_N through the file (anx_II/ann_1/level_PART-I) -->
  <xsl:template match="ANNOTATION | GR.ANNOTATION" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="outermost" as="xs:boolean" select="empty(ancestor::*[self::ANNOTATION or self::GR.ANNOTATION])"/>
    <xsl:variable name="inner" as="xs:string?" select="
        if ($outermost) then f:eid($base, 'ann', string(count(preceding::*[self::ANNOTATION or self::GR.ANNOTATION]
                                                                  [empty(ancestor::*[self::ANNOTATION or self::GR.ANNOTATION])]) + 1))
        else $base"/>
    <xsl:variable name="annotation" as="element()">
      <annotation>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="TITLE">
          <heading>
            <xsl:call-template name="f:lines">
              <xsl:with-param name="lines" as="element()*" select="TITLE/TI/*, TITLE/STI/*"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
            </xsl:call-template>
          </heading>
        </xsl:if>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="node() except TITLE"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$inner" tunnel="yes"/>
          <xsl:with-param name="levels" as="map(*)" select="map{'base': $inner, 'ctx': ($ctx, f:ids($m))}" tunnel="yes"/>
        </xsl:call-template>
      </annotation>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $annotation)"/>
  </xsl:template>

  <!-- an image with its caption, and the transcription of its text (IMG.CNT) as its description,
       whose units hang off fgr_N, numbered through the file -->
  <xsl:template match="INCL.ELEMENT[@TYPE = 'TIFF']" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="figure" as="xs:string" select="f:eid($base, 'fgr', string(count(preceding::INCL.ELEMENT[@TYPE = 'TIFF']) + 1))"/>
    <xsl:variable name="fig" as="element()">
      <figure src="{@FILEREF}">
        <xsl:sequence select="f:image-content(.)"/>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="CAPTION">
          <caption>
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" as="node()*" select="CAPTION/node()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
              <xsl:with-param name="base" as="xs:string?" select="$figure" tunnel="yes"/>
              <xsl:with-param name="levels" as="map(*)" select="map{'base': $figure, 'ctx': ($ctx, f:ids($m))}" tunnel="yes"/>
            </xsl:call-template>
          </caption>
        </xsl:if>
        <xsl:if test="IMG.CNT">
          <description>
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" as="node()*" select="IMG.CNT/node()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
              <xsl:with-param name="base" as="xs:string?" select="$figure" tunnel="yes"/>
              <xsl:with-param name="levels" as="map(*)" select="map{'base': $figure, 'ctx': ($ctx, f:ids($m))}" tunnel="yes"/>
            </xsl:call-template>
          </description>
        </xsl:if>
        <xsl:apply-templates select="node() except (CAPTION | IMG.CNT)" mode="unexpected"/>
      </figure>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $fig)"/>
  </xsl:template>

  <!-- CONTENT="TECH.DESIGN" → content="techDesign" -->
  <xsl:function name="f:image-content" as="attribute()?">
    <xsl:param name="incl" as="element(INCL.ELEMENT)"/>
    <xsl:if test="$incl/@CONTENT">
      <xsl:variable name="words" as="xs:string+" select="tokenize(lower-case($incl/@CONTENT), '\.')"/>
      <xsl:attribute name="content" select="string-join(($words[1], $words[position() gt 1] ! (upper-case(substring(., 1, 1)) || substring(., 2))))"/>
    </xsl:if>
  </xsl:function>

  <!-- another Formex file of the same package, included here: its content, in place. An included
       annex stays an annex, under the eId of what includes it: anx/anx_A, or anx/qstr_1/anx_A where an
       amending act quotes it; so does a GENERAL file, an annex whose title is a heading -->
  <xsl:template match="INCL.ELEMENT[@TYPE = 'FORMEX.DOC']" mode="block">
    <xsl:param name="letterOffset" as="xs:integer?" select="()" tunnel="yes"/>
    <xsl:variable name="included" as="element()" select="doc(resolve-uri(@FILEREF, base-uri(.)))/*"/>
    <xsl:variable name="prior-letters" as="xs:integer" select="
        xs:integer(sum(preceding::INCL.ELEMENT[@TYPE = 'FORMEX.DOC'][not(ancestor::BIB.INSTANCE)] ! count(doc(resolve-uri(@FILEREF, base-uri(.)))//LETTER)))"/>
    <xsl:choose>
      <xsl:when test="$included[self::ANNEX or self::GENERAL]">
        <xsl:apply-templates select="$included" mode="annex">
          <xsl:with-param name="included" as="xs:boolean" select="true()"/>
          <xsl:with-param name="holder" as="element()" select=".."/>
        </xsl:apply-templates>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="$included" mode="block">
          <xsl:with-param name="letterOffset" as="xs:integer?" select="($letterOffset, 0)[1] + $prior-letters" tunnel="yes"/>
        </xsl:apply-templates>
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
