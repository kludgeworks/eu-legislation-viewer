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
    $annexBase, $inAttachment, $notePrefix
                 an attached document's eId for its annexes, whether we are in one, and the
                 prefix of its notes' eIds (documents.xsl)

  The blocks themselves are in modules by concern: articles.xsl (enacting terms), lists.xsl,
  annexes.xsl (annexes and levels), tables.xsl, documents.xsl (attachments, corrigenda, letters,
  included documents); this module has the modes, mixed content and the other blocks.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/normalise"
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

  <!-- … become those blocks: the emphasis and the language are not kept, a block has neither -->
  <xsl:template match="HT | TXT | LV | FT" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- ============================================================ flattening -->

  <!-- content for where only inline content fits (a heading line), which holds blocks: a title line
       that is a numbered paragraph with a second paragraph, a definition list, a list. Each block
       goes on a line of its own; a numbered paragraph's number is kept, followed by a space. -->
  <xsl:template name="f:flat">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:variable name="out" as="element()*">
      <xsl:call-template name="f:flat-lines">
        <xsl:with-param name="nodes" as="node()*" select="$nodes"/>
      </xsl:call-template>
    </xsl:variable>
    <!-- no line break at the start or end, nor two in a row -->
    <xsl:sequence select="for $i in 1 to count($out) return
        $out[$i][not(self::*:eol and ($i = 1 or $i = count($out) or $out[$i + 1][self::*:eol]))]"/>
  </xsl:template>

  <!-- what holds lines without being a block itself: a title and its lines, a table's rows and cells -->
  <xsl:function name="f:holds-lines" as="xs:boolean">
    <xsl:param name="n" as="node()"/>
    <xsl:sequence select="exists($n[self::TITLE or self::TI or self::STI or self::CORPUS or self::ROW or self::CELL
                                    or self::BLK or self::TI.BLK or self::STI.BLK or self::CAPTION or self::IMG.CNT or self::DEFINITION])"/>
  </xsl:function>

  <xsl:template name="f:flat-lines">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:for-each-group select="$nodes" group-adjacent="f:is-block(.) or f:holds-lines(.)">
      <xsl:choose>
        <xsl:when test="current-grouping-key()">
          <xsl:for-each select="current-group()">
            <eol/>
            <xsl:choose>
              <!-- a displayed formula is inline maths here; an image an inline image, its caption and
                   transcription the lines after it -->
              <xsl:when test="self::FORMULA.S">
                <xsl:call-template name="f:inlines">
                  <xsl:with-param name="nodes" as="node()*" select="."/>
                </xsl:call-template>
              </xsl:when>
              <xsl:when test="self::INCL.ELEMENT[@TYPE = 'TIFF']">
                <xsl:apply-templates select="." mode="inline"/>
                <xsl:call-template name="f:flat-lines">
                  <xsl:with-param name="nodes" as="node()*" select="CAPTION, IMG.CNT"/>
                </xsl:call-template>
                <xsl:apply-templates select="node() except (CAPTION | IMG.CNT)" mode="unexpected"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:if test="self::NP or self::DLIST.ITEM">
                  <xsl:call-template name="f:inlines">
                    <xsl:with-param name="nodes" as="node()*" select="(NO.P | PREFIX | TERM)/node()"/>
                  </xsl:call-template>
                  <t v=" "/>
                </xsl:if>
                <xsl:call-template name="f:flat-lines">
                  <xsl:with-param name="nodes" as="node()*" select="node() except (self::NP/NO.P | self::DLIST.ITEM/(PREFIX | TERM))"/>
                </xsl:call-template>
              </xsl:otherwise>
            </xsl:choose>
            <eol/>
          </xsl:for-each>
        </xsl:when>
        <xsl:otherwise>
          <xsl:call-template name="f:inlines">
            <xsl:with-param name="nodes" as="node()*" select="current-group()"/>
          </xsl:call-template>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
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

  <!-- inline content of several lines (a heading of two P), joined by <eol/> -->
  <xsl:template name="f:lines">
    <xsl:param name="lines" as="element()*"/>
    <!-- keep an NP line's number; f:title doesn't, having made it the num -->
    <xsl:param name="numbers" as="xs:boolean" select="false()"/>
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
      <xsl:call-template name="f:flat">
        <xsl:with-param name="nodes" as="node()*" select="if (self::NP) then (TXT/node(), node() except (NO.P | TXT)) else node()"/>
      </xsl:call-template>
    </xsl:for-each>
  </xsl:template>

  <!-- ============================================================ other blocks -->

  <!-- what an amending act inserts or substitutes; its units get eIds under …/qst_N -->
  <xsl:template match="QUOT.S" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="unit" as="element()?" select="ancestor::*[self::ITEM or self::ARTICLE or self::PARAG or self::ALINEA
                                                  or self::GR.SEQ or self::DLIST.ITEM or self::ANNEX or self::CONS.ANNEX
                                                  or self::OLD.CORR or self::NEW.CORR or self::DIVISION or self::LETTER
                                                  or self::CONSID or self::VISA or self::NOTE][1]"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'qst', string(count($unit//QUOT.S[. &lt;&lt; current()]) + 1))"/>
    <quotedStructure eId="{$eid}">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
        <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
        <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        <xsl:with-param name="levelBase" as="xs:string?" select="$eid" tunnel="yes"/>
        <xsl:with-param name="levelCtx" as="xs:string*" select="$ctx" tunnel="yes"/>
        <xsl:with-param name="parentLevel" as="xs:string?" select="()" tunnel="yes"/>
        <xsl:with-param name="labelledParent" as="xs:string?" select="()" tunnel="yes"/>
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

  <!-- another Formex file of the same package, included here: its content, in place -->
  <xsl:template match="INCL.ELEMENT[@TYPE = 'FORMEX.DOC']" mode="block">
    <xsl:variable name="included" as="element()" select="doc(resolve-uri(@FILEREF, base-uri(.)))/*"/>
    <xsl:choose>
      <xsl:when test="$included/self::ANNEX">
        <!-- its title ("ANNEX I") as paragraphs, then its content -->
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="$included/TITLE, $included/CONTENTS/node()"/>
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
