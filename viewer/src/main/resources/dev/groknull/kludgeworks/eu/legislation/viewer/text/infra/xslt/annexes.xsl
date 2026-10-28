<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Annexes (ANNEX, CONS.ANNEX, a consolidation's CONS.DOC.GEN of TYPE ANNEX), joined across
  fragments, and their flat levels (GR.SEQ) with eIds. Part of normalise.xsl.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/normalise"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <!-- ============================================================ annexes and levels -->

  <!-- the label of an annex: "ANNEX II" → "II"; "" for an annex titled just ANNEX, and for an annex
       file written as GENERAL, whose title is a heading, not a label -->
  <xsl:function name="f:annex-label" as="xs:string">
    <xsl:param name="annex" as="element()"/>
    <xsl:sequence select="if ($annex/self::GENERAL) then '' else f:label-after-word(string($annex/TITLE[1]/TI/*[1]))"/>
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

  <!-- an annex met inside content: a nested annex, its own fragment -->
  <xsl:template match="CONS.ANNEX | ANNEX | CONS.DOC.GEN[@TYPE = 'ANNEX'][parent::CONS.DOC.GEN]" mode="block">
    <xsl:apply-templates select="." mode="annex"/>
  </xsl:template>

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
    <xsl:param name="annexPosition" as="xs:integer?"/>
    <xsl:param name="anchor" as="xs:integer?"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:new-markers(., $ctx))"/>
    <xsl:variable name="nested" as="xs:boolean" select="exists(parent::CONS.ANNEX | parent::CONS.DOC.GEN[@TYPE = 'ANNEX'])"/>
    <xsl:variable name="position" as="xs:integer" select="($annexPosition, count(preceding-sibling::*[self::CONS.ANNEX or self::CONS.DOC.GEN[@TYPE = 'ANNEX']]) + 1)[1]"/>
    <xsl:variable name="label" as="xs:string" select="(f:annex-label(.)[. != ''], string($position))[1]"/>
    <xsl:variable name="eid" as="xs:string" select="
        if (not($nested) and $annexCount = 1) then f:eid($annexBase, 'anx', ())
        else f:eid(if ($nested) then $base else $annexBase, 'anx', $label)"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="annex" eurlexAnchor="{($anchor, count(preceding::CONS.ANNEX | ancestor::CONS.ANNEX) + 1)[1]}">
        <xsl:sequence select="f:provenance($m)"/>
        <!-- the first title; a second (a consolidation's annex can have two) is text of the annex -->
        <xsl:call-template name="f:title">
          <xsl:with-param name="title" as="element(TITLE)?" select="TITLE[1]"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
        <xsl:for-each select="$fragments">
          <xsl:variable name="i" as="xs:integer" select="position()"/>
          <xsl:variable name="own" as="processing-instruction()*" select="if (position() = 1) then () else f:new-markers(., ($ctx, f:ids($m)))"/>
          <xsl:call-template name="f:blocks">
            <!-- the content, nested annexes and related documents, and whatever else the fragment
                 holds besides its title and (in an OJ annex file) its bibliographic data -->
            <xsl:with-param name="nodes" as="node()*" select="CONTENTS/node() | (* except (TITLE[1] | CONTENTS | BIB.INSTANCE))"/>
            <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids(($m, $own)))" tunnel="yes"/>
            <xsl:with-param name="pending" as="processing-instruction()*" select="$own" tunnel="yes"/>
            <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
            <xsl:with-param name="levelBase" as="xs:string?" select="$eid" tunnel="yes"/>
            <xsl:with-param name="levelCtx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
            <xsl:with-param name="parentLevel" as="xs:string?" select="()" tunnel="yes"/>
            <xsl:with-param name="earlier" as="document-node()*" select="($fragments[position() lt $i] ! root(.)) except root(.)" tunnel="yes"/>
            <xsl:with-param name="labelledParent" as="xs:string?" select="()" tunnel="yes"/>
            <!-- unnumbered top levels are numbered through all fragments -->
            <xsl:with-param name="levelOffset" as="xs:integer?" select="count($fragments[position() lt $i]/CONTENTS/GR.SEQ[f:label((f:level-num(.), f:level-label-line(.))[1]) = ''])" tunnel="yes"/>
          </xsl:call-template>
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
       (lvl_ATTACHMENT-6-A), which survives renesting where a position would not -->
  <xsl:function name="f:level-label-line" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:variable name="first" as="element(P)?" select="$seq[empty(f:level-num($seq))]/TITLE/TI/*[1][self::P]"/>
    <xsl:sequence select="$first[matches(normalize-space(.), '^\p{L}+ (\S*\d\S*|\p{Lu}{1,3})$')]"/>
  </xsl:function>

  <!-- what an unnumbered level is numbered within: its parent level (also when a table or a stray
       CONTENTS sits in between), else the annex contents or the quoted structure -->
  <xsl:function name="f:level-parent" as="element()?">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:sequence select="$seq/ancestor::*[self::GR.SEQ or self::QUOT.S
                                           or self::CONTENTS[parent::CONS.ANNEX or parent::ANNEX or parent::CONS.DOC.GEN[parent::CONS.DOC.GEN]]][1]"/>
  </xsl:function>

  <xsl:function name="f:level-ordinal" as="xs:integer">
    <xsl:param name="seq" as="element(GR.SEQ)"/>
    <xsl:variable name="parent" as="element()?" select="f:level-parent($seq)"/>
    <xsl:sequence select="count($parent//GR.SEQ[. &lt;&lt; $seq][f:label((f:level-num(.), f:level-label-line(.))[1]) = '']
                                [f:level-parent(.) is $parent]) + 1"/>
  </xsl:function>

  <xsl:key name="f:level-by-label" match="GR.SEQ" use="f:label((f:level-num(.), f:level-label-line(.))[1])"/>

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
    <xsl:sequence select="exists($earlier ! key('f:level-by-label', $label, .)[not(ancestor::QUOT.S)][not(f:covering(.)[f:is-deletion(.)])])
        or f:label-taken-here($seq, $label, $scope)"/>
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

  <!-- annex sections and points: flat levels, as in AKN4EU (1.2.2.6 is a sibling of 1.2.2) -->
  <xsl:template match="GR.SEQ" mode="block">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="pending" as="processing-instruction()*" tunnel="yes"/>
    <xsl:param name="levelBase" as="xs:string?" tunnel="yes"/>
    <xsl:param name="levelCtx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="parentLevel" as="xs:string?" tunnel="yes"/>
    <xsl:param name="levelOffset" as="xs:integer?" tunnel="yes"/>
    <xsl:param name="earlier" as="document-node()*" tunnel="yes"/>
    <!-- the nearest enclosing level with a number or label: labelled levels hang off it, skipping
         unnumbered levels in between, whose positional eIds shift when Formex renests them -->
    <xsl:param name="labelledParent" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="label-line" as="element()?" select="f:level-label-line(.)"/>
    <xsl:variable name="num" as="element()?" select="f:level-num(.)"/>
    <xsl:variable name="direct-np" as="element()?" select="if ($num/parent::NP/parent::GR.SEQ) then $num/.. else ()"/>
    <xsl:variable name="label" as="xs:string" select="f:label($num)"/>
    <xsl:variable name="eid" as="xs:string" select="
        if ($label != '') then f:eid(
            if ($parentLevel and (f:local-number($num) or f:label-taken(., $label, $earlier)))
            then ($labelledParent, $parentLevel)[1] else $levelBase,
            'pnt', $label)
        else if ($label-line) then f:eid(
            if ($parentLevel and f:label-taken(., f:label($label-line), $earlier))
            then ($labelledParent, $parentLevel)[1] else $levelBase,
            'lvl', f:label($label-line))
        else f:eid(($parentLevel, $levelBase)[1], 'lvl',
                   string(f:level-ordinal(.) + (if ($parentLevel) then 0 else ($levelOffset, 0)[1])))"/>
    <xsl:variable name="m" as="processing-instruction()*" select="($pending, f:covering(.)[not(f:pi-attr(., 'ID') = $levelCtx)])"/>
    <xsl:variable name="inner" as="xs:string*" select="($ctx, f:ids($m))"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="level" depth="{(@LEVEL, 1)[1]}">
        <xsl:if test="@BOX = 'YES'">
          <xsl:attribute name="box" select="true()"/>
        </xsl:if>
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
              <xsl:with-param name="title" as="element(TITLE)?" select="TITLE"/>
              <xsl:with-param name="num-first-line" as="xs:boolean" select="false()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise>
            <xsl:call-template name="f:title">
              <xsl:with-param name="title" as="element(TITLE)?" select="TITLE"/>
              <xsl:with-param name="num-first-line" as="xs:boolean" select="exists($label-line)"/>
              <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
            </xsl:call-template>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="$direct-np/TXT/node(), $direct-np/(node() except (NO.P, TXT)),
                                               node() except (TITLE, NO.GR.SEQ, GR.SEQ, $direct-np)"/>
          <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="parentLevel" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="levelOffset" as="xs:integer?" select="0" tunnel="yes"/>
          <xsl:with-param name="labelledParent" as="xs:string?" select="if ($label != '' or $label-line) then $eid else $labelledParent" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
    <xsl:apply-templates select="GR.SEQ" mode="block">
      <xsl:with-param name="ctx" as="xs:string*" select="$inner" tunnel="yes"/>
      <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
      <xsl:with-param name="parentLevel" as="xs:string?" select="$eid" tunnel="yes"/>
      <xsl:with-param name="levelOffset" as="xs:integer?" select="0" tunnel="yes"/>
      <xsl:with-param name="labelledParent" as="xs:string?" select="if ($label != '' or $label-line) then $eid else $labelledParent" tunnel="yes"/>
    </xsl:apply-templates>
  </xsl:template>

</xsl:stylesheet>
