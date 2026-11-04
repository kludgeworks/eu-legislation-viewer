<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Documents within documents: attachments (related documents), corrigenda (CORRECTION, OLD.CORR,
  NEW.CORR) and letters of an exchange of letters. Included Formex files (INCL.ELEMENT) are in
  units.xsl. Part of formex-to-legal-text.xsl.
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

  <!-- a consolidated treaty's document of protocols or of annexes (PROTOCOLS, ANNEXES TO THE TREATY
       ON THE FUNCTIONING OF THE EUROPEAN UNION): an attached document under its title, holding
       them. They are the treaty's, numbered through it, so their eIds hang off the treaty, not off
       the document: pro_3, as its ELI (eli/treaty/tfeu_2016/pro_3), and anx_I. The annexes printed
       after a protocol are its own: pro_3/anx -->
  <xsl:template match="CONS.DOC.GEN[f:treaty-annexes(.)]" mode="attachment" priority="1">
    <xsl:param name="eid" as="xs:string"/>
    <xsl:variable name="all" as="element(CONS.ANNEX)+" select="CONTENTS/CONS.ANNEX"/>
    <xsl:variable name="own" as="element(CONS.ANNEX)*" select="$all[exists(f:protocol(.))]"/>
    <xsl:variable name="top" as="element(CONS.ANNEX)+" select="$all except $own"/>
    <unit eId="{$eid}" kind="attachment">
      <heading>
        <xsl:call-template name="f:lines">
          <xsl:with-param name="lines" as="element()*" select="TITLE/TI/*, TITLE/STI/*"/>
        </xsl:call-template>
      </heading>
      <xsl:for-each select="$top">
        <xsl:apply-templates select="." mode="annex">
          <xsl:with-param name="annexCount" as="xs:integer?" select="count($top)"/>
          <xsl:with-param name="unlabelledCount" as="xs:integer?" select="count($top[f:annex-label(.) = ''])"/>
          <xsl:with-param name="annexPosition" as="xs:integer?" select="position()"/>
          <xsl:with-param name="ownAnnexes" as="element()*" select="$own[f:protocol(.) is current()]"/>
          <xsl:with-param name="ctx" as="xs:string*" select="()" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="()" tunnel="yes"/>
          <xsl:with-param name="annexBase" as="xs:string?" select="()" tunnel="yes"/>
        </xsl:apply-templates>
      </xsl:for-each>
    </unit>
  </xsl:template>

  <!-- one of a consolidated treaty's documents whose content is just protocols or annexes -->
  <xsl:function name="f:treaty-annexes" as="xs:boolean">
    <xsl:param name="document" as="element(CONS.DOC.GEN)"/>
    <xsl:sequence select="root($document)/CONS.OTH/INFO.CONSLEG/@LEG.VAL = 'TREATY'
        and exists($document/CONTENTS/CONS.ANNEX) and empty($document/(* except (TITLE | CONTENTS)) | $document/CONTENTS/(* except CONS.ANNEX))"/>
  </xsl:function>

  <!-- the protocol a treaty's annex belongs to: the one printed before it among the protocols
       (ANNEX after PROTOCOL (No 22)); none for a protocol, or for one of the treaty's own annexes -->
  <xsl:function name="f:protocol" as="element(CONS.ANNEX)?">
    <xsl:param name="annex" as="element(CONS.ANNEX)"/>
    <xsl:sequence select="if (f:annex-abbr($annex) = 'pro') then () else $annex/preceding-sibling::CONS.ANNEX[f:annex-abbr(.) = 'pro'][1]"/>
  </xsl:function>

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
          </xsl:call-template>
        </heading>
      </xsl:if>
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="$doc/(node() except (BIB.INSTANCE | TITLE | TOC | CONS.DOC.GEN))"/>
        <xsl:with-param name="ctx" as="xs:string*" select="()" tunnel="yes"/>
        <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
        <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        <xsl:with-param name="division" as="map(*)?" select="()" tunnel="yes"/>
        <xsl:with-param name="levels" as="map(*)" select="map{'base': $eid}" tunnel="yes"/>
      </xsl:call-template>
      <!-- a document in a file of its own: the file's notes after it, before its annexes' files -->
      <xsl:if test="$doc is root($doc)/*">
        <xsl:call-template name="f:notes">
          <xsl:with-param name="notes" as="element(NOTE)*" select="f:with-included($doc)//NOTE[not(@NOTE.REF)][not(f:table-note(.))]"/>
        </xsl:call-template>
      </xsl:if>
      <xsl:variable name="groups" as="array(element())*" select="f:annex-groups($annexes)"/>
      <xsl:for-each select="$groups">
        <xsl:variable name="fragments" as="element()*" select="?*"/>
        <xsl:apply-templates select="$fragments[1]" mode="annex">
          <xsl:with-param name="fragments" as="element()*" select="$fragments"/>
          <xsl:with-param name="annexCount" as="xs:integer?" select="count($groups)"/>
          <!-- an act whose one unlabelled annex comes beside ANNEX 1, ANNEX 2: that one is anx -->
          <xsl:with-param name="unlabelledCount" as="xs:integer?" select="count($groups[f:annex-label(?1) = ''])"/>
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
    <xsl:variable name="eid" as="xs:string" select="f:eid((), 'cor', string(count(preceding::CORRECTION) + 1))"/>
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
       each has its own eId segment: cor_1/old/…, cor_1/new/…. A correction with several "for … read
       …" pairs numbers them: cor_1/old_2/…, cor_1/new_2/… -->
  <xsl:template match="OLD.CORR | NEW.CORR" mode="block">
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="side" as="xs:string" select="if (self::OLD.CORR) then 'old' else 'new'"/>
    <xsl:variable name="sides" as="element()+" select="../*[node-name() = node-name(current())]"/>
    <xsl:variable name="eid" as="xs:string" select="
        f:eid($base, $side, if (count($sides) gt 1) then string(count(preceding-sibling::*[node-name() = node-name(current())]) + 1) else ())"/>
    <xsl:element name="{$side}">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
        <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        <xsl:with-param name="levels" as="map(*)" select="map{'base': $eid}" tunnel="yes"/>
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
    <!-- an annex's fragments, which may be files of their own: its letters are numbered through them -->
    <xsl:param name="annexFragments" as="element()*" select="()" tunnel="yes"/>
    <xsl:variable name="scope" as="element()" select="(ancestor::*[self::ANNEX or self::CONS.ANNEX or self::CONS.DOC.GEN or self::QUOT.S
                                                       or self::OLD.CORR or self::NEW.CORR][1], root(.)/*)[1]"/>
    <xsl:variable name="at" as="xs:integer?" select="f:node-index($annexFragments, $scope)"/>
    <xsl:variable name="earlier" as="xs:integer" select="if (exists($at)) then count(subsequence($annexFragments, 1, $at - 1)//LETTER) else 0"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid($base, 'let', string($earlier + count($scope//LETTER[. &lt;&lt; current()]) + 1))"/>
    <unit eId="{$eid}" kind="letter">
      <!-- "A. Letter from the European Union": A. is the num -->
      <xsl:call-template name="f:title">
        <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
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

  <!-- notes are collected after their file, or a consolidated version's table; what else a group of
       notes holds (a title, annotations such as "EASA Form 138 – Issue 3") stays here -->
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
