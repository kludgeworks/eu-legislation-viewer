<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Documents within documents: attachments (related documents), corrigenda (CORRECTION, OLD.CORR,
  NEW.CORR), letters of an exchange of letters, and included Formex files. Part of formex-to-legal-text.xsl.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <!-- ============================================================ attachments -->

  <xsl:mode name="attachment" on-no-match="fail" on-multiple-match="fail"/>

  <!-- a package entry other than an annex (DOC.SUB.PUB TYPE="ASSOCIATION": the agreement a decision
       concludes, a protocol, a draft decision); its own annexes and attachments are its sub-entries -->
  <xsl:template match="DOC.SUB.PUB" mode="attachment">
    <xsl:param name="eid" as="xs:string"/>
    <xsl:call-template name="f:attachment">
      <xsl:with-param name="doc" as="element()" select="REF.PHYS/doc(resolve-uri(@FILE, base-uri(.)))/*"/>
      <xsl:with-param name="eid" as="xs:string" select="$eid"/>
      <xsl:with-param name="annexes" as="element()*" select="DOC.SUB.PUB[@TYPE = 'ANNEX']/REF.PHYS/doc(resolve-uri(@FILE, base-uri(.)))/(ANNEX | GENERAL)"/>
      <xsl:with-param name="attachments" as="element()*" select="DOC.SUB.PUB[f:is-attachment(.)]"/>
      <xsl:with-param name="notePrefix" as="xs:string?" select="f:attachment-note-prefix(.)" tunnel="yes"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a consolidation's related document (CONS.DOC.GEN TYPE ACT, GENERAL or OTH.ACT inside another);
       its own annexes and attachments are the CONS.DOC.GEN inside it -->
  <xsl:template match="CONS.DOC.GEN" mode="attachment">
    <xsl:param name="eid" as="xs:string"/>
    <xsl:call-template name="f:attachment">
      <xsl:with-param name="doc" as="element()" select="."/>
      <xsl:with-param name="eid" as="xs:string" select="$eid"/>
      <xsl:with-param name="annexes" as="element()*" select="CONS.DOC.GEN[@TYPE = 'ANNEX']"/>
      <xsl:with-param name="attachments" as="element()*" select="CONS.DOC.GEN[not(@TYPE = 'ANNEX')]"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a related document met inside an annex of a consolidation -->
  <xsl:template match="CONS.DOC.GEN" mode="block">
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:apply-templates select="." mode="attachment">
      <xsl:with-param name="eid" as="xs:string" select="f:eid($base, 'att', string(count(preceding-sibling::CONS.DOC.GEN[not(@TYPE = 'ANNEX')]) + 1))"/>
    </xsl:apply-templates>
  </xsl:template>

  <!-- a package entry that is an attached document: one other than an annex, or an "annex" whose
       file is an agreement or an act (the agreement a decision concludes, listed as its annex) -->
  <xsl:function name="f:is-attachment" as="xs:boolean">
    <xsl:param name="entry" as="element(DOC.SUB.PUB)"/>
    <xsl:sequence select="not($entry/@TYPE = 'ANNEX')
        or exists($entry/REF.PHYS/doc(resolve-uri(@FILE, base-uri(.)))/(AGR | ACT))"/>
  </xsl:function>

  <!-- note eIds of an attached document in a file of its own: note_att01000301-1 -->
  <xsl:function name="f:attachment-note-prefix" as="xs:string">
    <xsl:param name="entry" as="element(DOC.SUB.PUB)"/>
    <xsl:sequence select="'att' || f:file-seq($entry/REF.PHYS/@FILE) || '-'"/>
  </xsl:function>

  <!-- an attached document: unit kind="attachment" (AKN's attachment), its title as heading, then
       its text, with its articles, annexes and attachments under it (att_1/art_1, att_1/anx) -->
  <xsl:template name="f:attachment">
    <xsl:param name="doc" as="element()"/>
    <xsl:param name="eid" as="xs:string"/>
    <xsl:param name="annexes" as="element()*"/>
    <xsl:param name="attachments" as="element()*"/>
    <unit eId="{$eid}" kind="attachment">
      <xsl:if test="$doc/TITLE">
        <heading>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" as="element()*" select="$doc/TITLE/TI/*, $doc/TITLE/STI/*"/>
            <xsl:with-param name="numbers" as="xs:boolean" select="true()"/>
          </xsl:call-template>
        </heading>
      </xsl:if>
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="$doc/(node() except (BIB.INSTANCE | TITLE | TOC | CONS.DOC.GEN))"/>
        <xsl:with-param name="ctx" as="xs:string*" select="()" tunnel="yes"/>
        <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
        <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        <xsl:with-param name="division" as="xs:string?" select="()" tunnel="yes"/>
        <xsl:with-param name="inAttachment" as="xs:boolean" select="true()" tunnel="yes"/>
        <xsl:with-param name="levelBase" as="xs:string?" select="$eid" tunnel="yes"/>
        <xsl:with-param name="levelCtx" as="xs:string*" select="()" tunnel="yes"/>
        <xsl:with-param name="parentLevel" as="xs:string?" select="()" tunnel="yes"/>
      </xsl:call-template>
      <xsl:variable name="groups" as="array(element())*" select="f:annex-groups($annexes)"/>
      <xsl:for-each select="$groups">
        <xsl:variable name="fragments" as="element()*" select="?*"/>
        <xsl:apply-templates select="$fragments[1]" mode="annex">
          <xsl:with-param name="fragments" as="element()*" select="$fragments"/>
          <xsl:with-param name="annexCount" as="xs:integer?" select="count($groups)"/>
          <xsl:with-param name="annexPosition" as="xs:integer?" select="position()"/>
          <xsl:with-param name="ctx" as="xs:string*" select="()" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="annexBase" as="xs:string?" select="$eid" tunnel="yes"/>
        </xsl:apply-templates>
      </xsl:for-each>
      <xsl:for-each select="$attachments">
        <xsl:apply-templates select="." mode="attachment">
          <xsl:with-param name="eid" as="xs:string" select="f:eid($eid, 'att', string(position()))"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        </xsl:apply-templates>
      </xsl:for-each>
    </unit>
  </xsl:template>

  <!-- ============================================================ corrigenda, letters, embedded documents -->

  <!-- one correction of a corrigendum: where, then the old and the new wording -->
  <xsl:template match="CORRECTION" mode="block">
    <xsl:variable name="eid" as="xs:string" select="'cor_' || (count(preceding::CORRECTION) + 1)"/>
    <unit eId="{$eid}" kind="correction">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
        <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
      </xsl:call-template>
    </unit>
  </xsl:template>

  <xsl:template match="DESCRIPTION" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- the wording as published (old) and as corrected (new); both quote the same structure, so
       each has its own eId segment: cor_1/old/…, cor_1/new/… -->
  <xsl:template match="OLD.CORR | NEW.CORR" mode="block">
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="side" as="xs:string" select="if (self::OLD.CORR) then 'old' else 'new'"/>
    <xsl:element name="{$side}">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
        <xsl:with-param name="base" as="xs:string?" select="f:eid($base, $side, ())" tunnel="yes"/>
        <xsl:with-param name="levelBase" as="xs:string?" select="f:eid($base, $side, ())" tunnel="yes"/>
        <xsl:with-param name="levelCtx" as="xs:string*" select="()" tunnel="yes"/>
        <xsl:with-param name="parentLevel" as="xs:string?" select="()" tunnel="yes"/>
      </xsl:call-template>
    </xsl:element>
  </xsl:template>

  <!-- the letters of an agreement that is itself attached, or included -->
  <xsl:template match="COLL.LETTERS" mode="block">
    <xsl:apply-templates select="LETTER" mode="block"/>
    <xsl:apply-templates select="node() except LETTER" mode="unexpected"/>
  </xsl:template>

  <!-- a letter of an exchange of letters attached to an agreement: let_N, under an attachment's eId -->
  <xsl:template match="LETTER" mode="block">
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'let', string(count(preceding-sibling::LETTER) + 1))"/>
    <unit eId="{$eid}" kind="letter">
      <!-- "A. Letter from the European Union": A. is the num -->
      <xsl:call-template name="f:title">
        <xsl:with-param name="title" as="element(TITLE)?" select="TITLE"/>
        <xsl:with-param name="num-first-line" as="xs:boolean" select="false()"/>
      </xsl:call-template>
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="node() except TITLE"/>
        <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
      </xsl:call-template>
    </unit>
  </xsl:template>

  <!-- the parts of an act met inside another document's content (CONS.OTH, letters): their text,
       in order, as blocks -->
  <xsl:template match="ACT.GEN | PREAMBLE | PREAMBLE.GEN | GR.VISA | GR.CONSID | CONSID | VISA | PREAMBLE.INIT | PREAMBLE.FINAL
                       | GR.CONSID.INIT | GR.VISA.INIT | ENACTING.TERMS | FINAL | SIGNATURE | PL.DATE | SIGNATORY | DIV.CONSID
                       | FULL.POWER | FULL.POWER.INIT | FULL.POWER.FINAL | PLENIPOTENTIARY | PROLOG | GR.CORRECTION | CONTENTS.CORR" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node()"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a title met inside content: its lines as paragraphs -->
  <xsl:template match="TITLE" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="TI/node(), STI/node()"/>
    </xsl:call-template>
    <xsl:apply-templates select="node() except (TI | STI)" mode="unexpected"/>
  </xsl:template>

  <!-- an agreement quoted whole, e.g. in a corrigendum that replaces its text; a protocol an
       agreement's annex includes (written as GENERAL, 06.02.1); the rest of a corrigendum, in a
       file of its own -->
  <xsl:template match="AGR | ACT | GENERAL | CORR" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node() except BIB.INSTANCE"/>
    </xsl:call-template>
  </xsl:template>

  <!-- a table of contents is navigation the reader builds from the units; not text -->
  <xsl:template match="TOC" mode="block"/>

  <!-- notes are collected at the end of the document; what else a group of notes holds (a title,
       annotations such as "EASA Form 138 – Issue 3") stays here -->
  <xsl:template match="GR.NOTES" mode="block">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="node() except NOTE"/>
    </xsl:call-template>
  </xsl:template>

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
