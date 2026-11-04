<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Annexes (ANNEX, CONS.ANNEX, a consolidation's CONS.DOC.GEN of TYPE ANNEX), joined across
  fragments, and their flat levels (GR.SEQ) with eIds. Part of formex-to-legal-text.xsl.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <!-- ============================================================ annexes and levels -->

  <!-- the label of an annex: "ANNEX II" → "II"; "" for an annex titled just ANNEX, and for an annex
       file written as GENERAL, whose title is a heading, not a label. A protocol's is its number, as
       in its ELI (pro_3): "PROTOCOL (No 3)" → "3" -->
  <xsl:function name="f:annex-label" as="xs:string">
    <xsl:param name="annex" as="element()"/>
    <xsl:variable name="label" as="xs:string" select="if ($annex/self::GENERAL) then '' else f:typed-label(string($annex/TITLE[1]/TI/*[1]), ('ANX', 'APP', 'PRO'), $annex)"/>
    <xsl:sequence select="if (f:annex-abbr($annex) = 'pro') then replace($label, '^\P{N}+', '') else $label"/>
  </xsl:function>

  <!-- Top-level annexes, grouped into the annexes of the act. Formex splits a single annex into
       fragments, each titled just ANNEX: the OJ text of 2015/1998 across four files, its
       consolidations around the annexes of its checklists (chapters 7 to 12 sit in the fourth
       fragment). Consecutive label-less fragments with the same title are one annex, so eIds
       don't depend on where the Publications Office cut the file. -->
  <xsl:function name="f:annex-groups" as="array(element())*">
    <xsl:param name="annexes" as="element()*"/>
    <xsl:iterate select="$annexes">
      <xsl:param name="group" as="element()*" select="()"/>
      <xsl:on-completion>
        <xsl:sequence select="if (exists($group)) then array{$group} else ()"/>
      </xsl:on-completion>
      <xsl:variable name="previous" as="element()?" select="$group[last()]"/>
      <xsl:variable name="continues" as="xs:boolean" select="exists($previous) and f:annex-label(.) = '' and f:annex-label($previous) = ''
                                             and normalize-space(string-join(TITLE, ' ')) = normalize-space(string-join($previous/TITLE, ' '))"/>
      <xsl:choose>
        <xsl:when test="$continues">
          <xsl:next-iteration><xsl:with-param name="group" as="element()*" select="$group, ."/></xsl:next-iteration>
        </xsl:when>
        <xsl:otherwise>
          <xsl:sequence select="if (exists($group)) then array{$group} else ()"/>
          <xsl:next-iteration><xsl:with-param name="group" as="element()*" select="."/></xsl:next-iteration>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:iterate>
  </xsl:function>

  <xsl:mode name="annex" on-no-match="fail" on-multiple-match="fail"/>

  <!-- what an OJ act's package files under some of its annex files (the DOC.SUB.PUB in their
       entries), in order -->
  <xsl:function name="f:filed-under" as="element(DOC.SUB.PUB)*">
    <xsl:param name="descriptor" as="element(DOC)?"/>
    <xsl:param name="files" as="element()*"/>
    <xsl:sequence select="$descriptor/FMX//DOC.SUB.PUB[REF.PHYS/@FILE = $files ! tokenize(base-uri(.), '/')[last()]]/DOC.SUB.PUB"/>
  </xsl:function>

  <!-- the files that continue annex files: an untitled annex file filed under one is the rest of
       it, which the OJ printed across files (ESA 2010's Annex A, chapter by chapter), and so on
       down; a titled one is an annex of its own -->
  <xsl:function name="f:continuations" as="element()*">
    <xsl:param name="descriptor" as="element(DOC)?"/>
    <xsl:param name="files" as="element()*"/>
    <xsl:for-each select="f:filed-under($descriptor, $files)[@TYPE = 'ANNEX']/REF.PHYS/doc(resolve-uri(@FILE, base-uri(.)))/(ANNEX | GENERAL)[not(normalize-space(TITLE))]">
      <xsl:sequence select=". , f:continuations($descriptor, .)"/>
    </xsl:for-each>
  </xsl:function>

  <!-- an annex met inside content: a nested annex, its own fragment, under the eId of what holds it
       (an annex, or a level of one: anx/level_PART-10/anx_1) -->
  <xsl:template match="CONS.ANNEX | ANNEX | CONS.DOC.GEN[@TYPE = 'ANNEX'][parent::CONS.DOC.GEN]" mode="block">
    <xsl:apply-templates select="." mode="annex">
      <xsl:with-param name="inContent" as="xs:boolean" select="true()"/>
      <xsl:with-param name="holder" as="element()" select=".."/>
    </xsl:apply-templates>
  </xsl:template>

  <!-- an appendix, as the EU subdivision table names it, is app_, a protocol pro_; any other annex anx_ -->
  <xsl:function name="f:annex-abbr" as="xs:string">
    <xsl:param name="annex" as="element()"/>
    <xsl:variable name="type" as="xs:string?" select="f:subdivision(string($annex/TITLE[1]/TI/*[1]), ('APP', 'PRO'), $annex)[1]"/>
    <xsl:sequence select="if (exists($type)) then lower-case($type) else 'anx'"/>
  </xsl:function>

  <!-- the unit whose eId the annexes in an element hang off: the nearest that has one, else the file -->
  <xsl:function name="f:annex-scope" as="element()">
    <xsl:param name="e" as="element()"/>
    <xsl:sequence select="($e/ancestor-or-self::*[self::ITEM or self::ARTICLE or self::PARAG or self::GR.SEQ
        or self::DLIST.ITEM or self::ANNEX or self::CONS.ANNEX or self::CONS.DOC.GEN or self::QUOT.S
        or self::OLD.CORR or self::NEW.CORR or self::DIVISION or self::LETTER][1], root($e)/*)[1]"/>
  </xsl:function>

  <!-- the annexes that hang off the same unit as those an element holds, in order, those in files
       it includes too: two GENERAL files included in two paragraphs of one annex are siblings -->
  <xsl:function name="f:annex-siblings" as="element()*">
    <xsl:param name="holder" as="element()"/>
    <xsl:variable name="scope" as="element()" select="f:annex-scope($holder)"/>
    <xsl:variable name="all" as="element()*" select="
        for $c in $scope//(CONS.ANNEX | ANNEX | CONS.DOC.GEN[@TYPE = 'ANNEX'] | INCL.ELEMENT[@TYPE = 'FORMEX.DOC'])
                          [f:annex-scope(..) is $scope]
        return
          if ($c/self::INCL.ELEMENT) then doc(resolve-uri($c/@FILEREF, base-uri($c)))/(ANNEX | GENERAL)
          else $c"/>
    <!-- a file included twice (a party's statutes, listed again) is one annex -->
    <xsl:sequence select="for $i in 1 to count($all) return $all[$i][not(some $j in 1 to $i - 1 satisfies $all[$j] is $all[$i])]"/>
  </xsl:function>

  <!-- one annex, from one or more fragments ($fragments); an OJ act's annex file can be GENERAL -->
  <xsl:template match="CONS.ANNEX | ANNEX | CONS.DOC.GEN[@TYPE = 'ANNEX'][parent::CONS.DOC.GEN] | GENERAL" mode="annex">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="base" as="xs:string?" tunnel="yes"/>
    <!-- the attachment whose annex this is: att_1/anx -->
    <xsl:param name="annexBase" as="xs:string?" tunnel="yes"/>
    <xsl:param name="fragments" as="element()*" select="."/>
    <!-- top level: how many annexes the act has, this one's position, its first EUR-Lex anchor -->
    <xsl:param name="annexCount" as="xs:integer?"/>
    <!-- top level: how many of the act's annexes have no label -->
    <xsl:param name="unlabelledCount" as="xs:integer?"/>
    <xsl:param name="annexPosition" as="xs:integer?"/>
    <xsl:param name="anchor" as="xs:integer?"/>
    <!-- inside a quotation: an annex the amending act inserts or substitutes, under the quotation's eId -->
    <xsl:param name="quoted" as="xs:boolean" select="false()" tunnel="yes"/>
    <!-- a file another annex includes (INCL.ELEMENT): an annex of that annex, under its eId -->
    <xsl:param name="included" as="xs:boolean" select="false()"/>
    <!-- met inside content, not one of the act's own annexes: under the eId of what holds it -->
    <xsl:param name="inContent" as="xs:boolean" select="false()"/>
    <!-- what holds it there, whose other annexes number it: its position among them, and plain
         anx (app) where it is their one unlabelled annex (ANNEX beside ANNEX 1) -->
    <xsl:param name="holder" as="element()?"/>
    <!-- a treaty's protocol: the annexes printed after it, which are its own (pro_3/anx_I) -->
    <xsl:param name="ownAnnexes" as="element()*" select="()"/>
    <!-- an OJ act's package descriptor, which lists what it files under an annex -->
    <xsl:param name="descriptor" as="element(DOC)?" tunnel="yes"/>
    <!-- whether EUR-Lex anchors it: not an annex filed under another, which it prints unanchored -->
    <xsl:param name="anchored" as="xs:boolean" select="true()"/>
    <xsl:variable name="siblings" as="element()*" select="$holder ! f:annex-siblings(.)"/>
    <xsl:variable name="at" as="xs:integer?" select="f:node-index($siblings, current())"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="nested" as="xs:boolean" select="exists(parent::CONS.ANNEX | parent::CONS.DOC.GEN[@TYPE = 'ANNEX'])"/>
    <!-- one of an OJ act's annexes, which are files of their own (a package's), each printed with its notes -->
    <xsl:variable name="files" as="xs:boolean" select="not($inContent or $quoted or $included or $nested) and (every $x in $fragments satisfies $x is root($x)/*)"/>
    <!-- with the files the package continues it in -->
    <xsl:variable name="parts" as="element()*" select="if ($files) then ($fragments, f:continuations($descriptor, $fragments)) else $fragments"/>
    <xsl:variable name="position" as="xs:integer" select="($annexPosition, $at, count(preceding-sibling::*[self::CONS.ANNEX or self::CONS.DOC.GEN[@TYPE = 'ANNEX']]) + 1)[1]"/>
    <xsl:variable name="lone-unlabelled" as="xs:boolean" select="exists($at) and f:annex-label(.) = ''
        and count($siblings[f:annex-label(.) = ''][f:annex-abbr(.) = f:annex-abbr(current())]) = 1"/>
    <xsl:variable name="label" as="xs:string" select="(f:annex-label(.)[. != ''], string($position))[1]"/>
    <xsl:variable name="abbr" as="xs:string" select="f:annex-abbr(.)"/>
    <xsl:variable name="eid" as="xs:string" select="
        if (not($nested) and ($annexCount = 1 or $unlabelledCount = 1 and f:annex-label(.) = '')) then f:eid($annexBase, $abbr, ())
        else f:eid(if ($nested or $inContent or $quoted or $included) then $base else $annexBase, $abbr, if ($lone-unlabelled) then () else $label)"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="annex">
        <!-- EUR-Lex anchors the act's own annexes, not those it quotes or an annex includes -->
        <xsl:if test="not($quoted or $included) and $anchored">
          <xsl:attribute name="eurlexAnchor" select="($anchor, count(preceding::CONS.ANNEX | ancestor::CONS.ANNEX) + 1)[1]"/>
        </xsl:if>
        <xsl:sequence select="f:provenance($m)"/>
        <!-- the first title; a second (a consolidation's annex can have two) is text of the annex. A
             GENERAL file's title is a heading, with no label line -->
        <xsl:call-template name="f:title">
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="title" as="element(TITLE)?" select="TITLE[1]"/>
          <xsl:with-param name="num-first-line" as="xs:boolean" select="not(self::GENERAL)"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
        <xsl:for-each select="$parts">
          <xsl:variable name="i" as="xs:integer" select="position()"/>
          <xsl:variable name="own" as="processing-instruction()*" select="if (position() = 1) then () else f:new-markers(., ($ctx, f:ids($m)))"/>
          <xsl:variable name="blocks" as="node()*">
            <xsl:call-template name="f:blocks">
              <!-- the content, nested annexes and related documents, and whatever else the fragment
                   holds besides its title and (in an OJ annex file) its bibliographic data -->
              <xsl:with-param name="nodes" as="node()*" select="CONTENTS/node() | (* except (TITLE[1] | CONTENTS | BIB.INSTANCE))"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids(($m, $own)))" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="$own" tunnel="yes"/>
              <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
              <xsl:with-param name="levels" as="map(*)" select="map{'base': $eid, 'offset': count($parts[position() lt $i]/CONTENTS/GR.SEQ[f:level-label(.) = '']), 'earlier': ($parts[position() lt $i] ! root(.)) except root(.), 'ctx': ($ctx, f:ids($m))}" tunnel="yes"/>
              <xsl:with-param name="annexFragments" as="element()*" select="$parts" tunnel="yes"/>
              <!-- unnumbered top levels are numbered through all fragments -->
            </xsl:call-template>
          </xsl:variable>
          <xsl:variable name="notes" as="element()?">
            <xsl:if test="$files">
              <xsl:call-template name="f:notes">
                <xsl:with-param name="notes" as="element(NOTE)*" select="f:with-included(.)//NOTE[not(@NOTE.REF)][not(f:table-note(.))]"/>
              </xsl:call-template>
            </xsl:if>
          </xsl:variable>
          <xsl:choose>
            <!-- an annex the OJ prints across files: each under the title it repeats, its notes after it -->
            <xsl:when test="$files and count($parts) gt 1">
              <file name="{replace(tokenize(base-uri(.), '/')[last()], '\.xml$', '')}">
                <xsl:if test="$i gt 1">
                  <xsl:call-template name="f:title">
                    <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
                    <xsl:with-param name="title" as="element(TITLE)?" select="TITLE[1]"/>
                    <xsl:with-param name="num-first-line" as="xs:boolean" select="not(self::GENERAL)"/>
                    <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
                  </xsl:call-template>
                </xsl:if>
                <xsl:sequence select="$blocks, $notes"/>
              </file>
            </xsl:when>
            <xsl:otherwise>
              <xsl:sequence select="$blocks, $notes"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each>
        <xsl:for-each select="$ownAnnexes">
          <xsl:apply-templates select="." mode="annex">
            <xsl:with-param name="annexCount" as="xs:integer?" select="count($ownAnnexes)"/>
            <xsl:with-param name="unlabelledCount" as="xs:integer?" select="count($ownAnnexes[f:annex-label(.) = ''])"/>
            <xsl:with-param name="annexPosition" as="xs:integer?" select="position()"/>
            <xsl:with-param name="annexBase" as="xs:string?" select="$eid" tunnel="yes"/>
            <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
            <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
            <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          </xsl:apply-templates>
        </xsl:for-each>
        <!-- what else the package files under it: its own annexes, appendices mostly (anx_A/app_1),
             then its attached documents (anx_A/att_1) -->
        <xsl:variable name="entries" as="element(DOC.SUB.PUB)*" select="if ($files) then f:filed-under($descriptor, $parts) else ()"/>
        <xsl:variable name="groups" as="array(element())*" select="
            f:annex-groups(($entries[@TYPE = 'ANNEX'][not(f:is-attachment(.))]/REF.PHYS/doc(resolve-uri(@FILE, base-uri(.)))/(ANNEX | GENERAL)) except $parts)"/>
        <xsl:for-each select="$groups">
          <xsl:variable name="own" as="element()*" select="?*"/>
          <xsl:apply-templates select="$own[1]" mode="annex">
            <xsl:with-param name="fragments" as="element()*" select="$own"/>
            <xsl:with-param name="annexCount" as="xs:integer?" select="count($groups)"/>
            <xsl:with-param name="unlabelledCount" as="xs:integer?" select="count($groups[f:annex-label(?1) = ''])"/>
            <xsl:with-param name="annexPosition" as="xs:integer?" select="position()"/>
            <xsl:with-param name="anchored" as="xs:boolean" select="false()"/>
            <xsl:with-param name="annexBase" as="xs:string?" select="$eid" tunnel="yes"/>
            <xsl:with-param name="ctx" as="xs:string*" select="()" tunnel="yes"/>
            <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
            <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          </xsl:apply-templates>
        </xsl:for-each>
        <xsl:for-each select="$entries[f:is-attachment(.)]">
          <xsl:apply-templates select="." mode="attachment">
            <xsl:with-param name="eid" as="xs:string" select="f:eid($eid, 'att', string(position()))"/>
            <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          </xsl:apply-templates>
        </xsl:for-each>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- an annex or level title: the first line is the num (ANNEX II), or a numbered NP gives num
       and heading (1.2 RECRUITMENT); further lines join the heading; STI is the subheading -->
  <xsl:template name="f:title">
    <xsl:param name="title" as="element(TITLE)?"/>
    <xsl:param name="num-first-line" as="xs:boolean" select="true()"/>
    <xsl:variable name="lines" as="element()*" select="$title/TI/*"/>
    <xsl:variable name="np" as="element(NP)?" select="$lines[1][self::NP]"/>
    <xsl:variable name="num" as="element()?" select="if ($np) then $np/NO.P else if ($num-first-line) then $lines[1] else ()"/>
    <xsl:variable name="heading" as="element()*" select="if ($np) then $lines else $lines except $num"/>
    <xsl:if test="$num">
      <num>
        <xsl:call-template name="f:inlines">
          <xsl:with-param name="nodes" as="node()*" select="$num/node()"/>
        </xsl:call-template>
      </num>
    </xsl:if>
    <xsl:if test="$heading">
      <heading>
        <xsl:call-template name="f:lines">
          <xsl:with-param name="lines" as="element()*" select="$heading"/>
          <xsl:with-param name="num" as="element(NP)?" select="$np"/>
        </xsl:call-template>
      </heading>
    </xsl:if>
    <xsl:if test="$title/STI">
      <subheading>
        <xsl:call-template name="f:lines">
          <xsl:with-param name="lines" as="element()*" select="$title/STI/*"/>
        </xsl:call-template>
      </subheading>
    </xsl:if>
    <xsl:apply-templates select="$title/(node() except (TI | STI))" mode="unexpected"/>
  </xsl:template>

  <xsl:function name="f:level-num" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:sequence select="($seq/NO.GR.SEQ, $seq/TITLE/TI/*[1][self::NP]/NO.P, $seq[not(TITLE)]/*[1][self::NP]/NO.P)[1]"/>
  </xsl:function>

  <!-- an unnumbered level whose title starts with a label line: a word and an identifier with a
       digit, or of up to three letters ("ATTACHMENT 6-A", "PART 2", "SUBPART A", "SECTION IV").
       The line is the level's num, as "ANNEX II" is an annex's; its eId uses the whole line
       (level_ATTACHMENT-6-A), which survives renesting where a position would not -->
  <xsl:function name="f:level-label-line" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:variable name="first" as="element(P)?" select="$seq[empty(f:level-num($seq))]/TITLE/TI/*[1][self::P]"/>
    <xsl:sequence select="$first[matches(normalize-space(.), '^\p{L}+ (\S*\d\S*|\p{Lu}{1,3})$')]"/>
  </xsl:function>

  <!-- what an unnumbered level is numbered within: its parent level (also when a table or a stray
       CONTENTS sits in between), else the unit that holds it (a list point, an article, a paragraph,
       a definition, a subdivision, a recital, a correction's wording, an
       annotation, a figure's description), the quoted structure, the contents of an annex or other document
       (GENERAL), or the enacting terms -->
  <xsl:function name="f:level-parent" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:sequence select="$seq/ancestor::*[self::GR.SEQ or self::QUOT.S or self::CONSID or self::ITEM or self::ARTICLE
                                           or self::PARAG or self::DLIST.ITEM or self::SUBDIV or self::OLD.CORR or self::NEW.CORR or self::IMG.CNT
                                           or self::ANNOTATION[empty(ancestor::ANNOTATION | ancestor::GR.ANNOTATION)]
                                           or self::GR.ANNOTATION[empty(ancestor::ANNOTATION | ancestor::GR.ANNOTATION)]
                                           or self::ENACTING.TERMS
                                           or self::CONTENTS[not(ancestor::GR.SEQ)]][1]"/>
  </xsl:function>

  <xsl:function name="f:level-ordinal" as="xs:integer">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:variable name="parent" as="element()?" select="f:level-parent($seq)"/>
    <xsl:sequence select="count($parent//GR.SEQ[. &lt;&lt; $seq][f:level-label(.) = '']
                                [f:level-parent(.) is $parent]) + 1"/>
  </xsl:function>

  <!-- a level's label as an eId value: its number, else its label line (ATTACHMENT 6-A); '' if neither -->
  <xsl:function name="f:level-label" as="xs:string">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:sequence select="f:label((f:level-num($seq), f:level-label-line($seq))[1])"/>
  </xsl:function>

  <xsl:key name="f:level-by-label" match="GR.SEQ" use="f:level-label(.)"/>

  <!-- the annex (group of fragments) or quoted structure a level's number belongs to -->
  <xsl:function name="f:level-scope" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:sequence select="$seq/ancestor::*[self::QUOT.S or self::CONS.ANNEX or self::ANNEX or self::TBL
                                           or self::CONS.DOC.GEN[parent::CONS.DOC.GEN]][1]"/>
  </xsl:function>

  <!-- Numbered levels hang off the annex (anx/pnt_12.6.2), except
       - local enumerators, "(a)", "(1)", "iii)": Air Ops writes the points of a rule as levels;
       - a number an earlier level in the same annex already has: attachments restart their own
         numbering (checklist "PART 2 … 2.1", form "1.1 Name …").
       Those hang off their parent level. -->
  <xsl:function name="f:local-number" as="xs:boolean">
    <xsl:param name="num" as="element()"/>
    <xsl:sequence select="matches(normalize-space(string($num)), '^\(|\)$')"/>
  </xsl:function>

  <xsl:function name="f:label-taken" as="xs:boolean">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:param name="label" as="xs:string"/>
    <!-- earlier fragments of the same annex that are separate files (an OJ act's annex files) -->
    <xsl:param name="earlier" as="document-node()*"/>
    <xsl:variable name="scope" as="element()?" select="f:level-scope($seq)"/>
    <!-- a top-level level later in the annex has nowhere else to hang: its label isn't free for a
         nested one before it (Appendix A inside one standard, Appendix A of the next) -->
    <xsl:sequence select="exists($earlier ! key('f:level-by-label', $label, .)[not(ancestor::QUOT.S)][not(f:covering(.)[f:is-deletion(.)])])
        or f:label-taken-here($seq, $label, $scope)
        or exists($seq/ancestor::GR.SEQ) and exists(key('f:level-by-label', $label, root($seq))[. &gt;&gt; $seq][empty(ancestor::GR.SEQ)]
             [not(f:covering(.)[f:is-deletion(.)])][let $s := f:level-scope(.) return if (empty($scope)) then empty($s) else $s is $scope])"/>
  </xsl:function>

  <xsl:function name="f:label-taken-here" as="xs:boolean">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:param name="label" as="xs:string"/>
    <xsl:param name="scope" as="element()?"/>
    <!-- a deleted placeholder doesn't hold on to its number -->
    <xsl:sequence select="exists(key('f:level-by-label', $label, root($seq))[. &lt;&lt; $seq]
        [not(f:covering(.)[f:is-deletion(.)])]
        [let $s := f:level-scope(.) return
           if ($scope/self::CONS.ANNEX and $s/self::CONS.ANNEX) then empty($s/parent::CONS.ANNEX) = empty($scope/parent::CONS.ANNEX)
           else if (empty($scope)) then empty($s)
           else $s is $scope])"/>
  </xsl:function>

  <!-- the nearest enclosing level with a number or a label line -->
  <xsl:function name="f:labelled-ancestor" as="element(GR.SEQ)?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:sequence select="$seq/ancestor::GR.SEQ[f:level-label(.) != ''][1]"/>
  </xsl:function>

  <!-- a label an earlier level already has under the same labelled level (each model's "Side 1" in
       PART I of a vehicle annex): hanging it off that level wouldn't tell them apart -->
  <xsl:function name="f:label-taken-under" as="xs:boolean">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:param name="label" as="xs:string"/>
    <xsl:variable name="under" as="element(GR.SEQ)?" select="f:labelled-ancestor($seq)"/>
    <xsl:sequence select="exists(key('f:level-by-label', $label, root($seq))[. &lt;&lt; $seq]
        [not(f:covering(.)[f:is-deletion(.)])]
        [let $u := f:labelled-ancestor(.) return if (empty($under)) then empty($u) else $u is $under])"/>
  </xsl:function>

  <!-- annex sections and points: flat levels, as in AKN4EU (1.2.2.6 is a sibling of 1.2.2) -->
  <xsl:template match="GR.SEQ" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="levels" as="map(*)" select="map{}" tunnel="yes"/>
    <xsl:variable name="levelBase" as="xs:string?" select="$levels?base"/>
    <xsl:variable name="levelCtx" as="xs:string*" select="$levels?ctx"/>
    <xsl:variable name="parentLevel" as="xs:string?" select="$levels?parent"/>
    <xsl:variable name="levelOffset" as="xs:integer?" select="$levels?offset"/>
    <xsl:variable name="earlier" as="document-node()*" select="$levels?earlier"/>
    <!-- the nearest enclosing level with a number or label: labelled levels hang off it, skipping
         unnumbered levels in between, whose positional eIds shift when Formex renests them -->
    <xsl:variable name="labelledParent" as="xs:string?" select="$levels?labelled"/>
    <xsl:variable name="label-line" as="element()?" select="f:level-label-line(.)"/>
    <xsl:variable name="num" as="element()?" select="f:level-num(.)"/>
    <xsl:variable name="direct-np" as="element()?" select="if ($num/parent::NP/parent::GR.SEQ) then $num/.. else ()"/>
    <xsl:variable name="label" as="xs:string" select="f:label($num)"/>
    <!-- a label taken again under the same labelled level hangs off the immediate parent level -->
    <xsl:variable name="near" as="xs:string?" select="
        if ($parentLevel and f:label-taken-under(., f:label(($num, $label-line)[1]))) then $parentLevel
        else ($labelledParent, $parentLevel)[1]"/>
    <xsl:variable name="eid" as="xs:string" select="
        if ($label != '') then f:eid(
            if ($parentLevel and (f:local-number($num) or f:label-taken(., $label, $earlier)))
            then $near else $levelBase,
            'pnt', $label)
        else if ($label-line) then f:eid(
            if ($parentLevel and f:label-taken(., f:label($label-line), $earlier))
            then $near else $levelBase,
            'level', f:label($label-line))
        else f:eid(($parentLevel, $levelBase)[1], 'level',
                   string(f:level-ordinal(.) + (if ($parentLevel) then 0 else ($levelOffset, 0)[1])))"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:covering(.)[not(f:pi-attr(., 'ID') = $levelCtx)])"/>
    <xsl:variable name="inner" as="xs:string*" select="($ctx, f:ids($m))"/>
    <!-- what the level holds after its first sub-level (a GR.ANNOTATION after its points): its levels
         being flat, it follows them, in the order of the text, not the level's own blocks -->
    <xsl:variable name="trailing" as="node()*" select="(node() except (TITLE, NO.GR.SEQ, GR.SEQ, $direct-np))[. &gt;&gt; current()/GR.SEQ[1]]"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="level" depth="{(@LEVEL, 1)[1]}">
        <xsl:if test="@BOX = 'YES'">
          <xsl:attribute name="box" select="true()"/>
        </xsl:if>
        <xsl:sequence select="f:numbered($label)"/>
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:choose>
          <xsl:when test="NO.GR.SEQ or $direct-np">
            <num>
              <xsl:call-template name="f:inlines">
                <xsl:with-param name="nodes" as="node()*" select="$num/node()"/>
                <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
              </xsl:call-template>
            </num>
            <xsl:call-template name="f:title">
              <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
              <xsl:with-param name="title" as="element(TITLE)?" select="TITLE"/>
              <xsl:with-param name="num-first-line" as="xs:boolean" select="false()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <xsl:call-template name="f:title">
              <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
              <xsl:with-param name="title" as="element(TITLE)?" select="TITLE"/>
              <xsl:with-param name="num-first-line" as="xs:boolean" select="exists($label-line)"/>
              <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
            </xsl:call-template>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="$direct-np/TXT/node(), f:np-rest($direct-np),
                                               node() except (TITLE, NO.GR.SEQ, GR.SEQ, $direct-np, $trailing)"/>
          <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="levels" as="map(*)" select="map:merge(($levels, map{'parent': $eid, 'labelled': if ($label != '' or $label-line) then $eid else $labelledParent, 'offset': 0}), map{'duplicates': 'use-last'})" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
    <xsl:for-each-group select="GR.SEQ | $trailing" group-adjacent="exists(self::GR.SEQ)">
      <xsl:choose>
        <xsl:when test="current-grouping-key()">
          <xsl:apply-templates select="current-group()" mode="block">
            <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
            <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
            <xsl:with-param name="levels" as="map(*)" select="map:merge(($levels, map{'parent': $eid, 'labelled': if ($label != '' or $label-line) then $eid else $labelledParent, 'offset': 0}), map{'duplicates': 'use-last'})" tunnel="yes"/>
          </xsl:apply-templates>
        </xsl:when>
        <xsl:otherwise>
          <xsl:variable name="blocks" as="node()*">
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" as="node()*" select="current-group()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
              <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
              <xsl:with-param name="levels" as="map(*)" select="map:merge(($levels, map{'parent': $eid, 'labelled': if ($label != '' or $label-line) then $eid else $labelledParent, 'offset': 0}), map{'duplicates': 'use-last'})" tunnel="yes"/>
            </xsl:call-template>
          </xsl:variable>
          <xsl:sequence select="f:wrap-deleted($m, $blocks)"/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
  </xsl:template>

</xsl:stylesheet>
