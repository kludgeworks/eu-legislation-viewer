<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Formex 4 (any schema version, 05.51 to 06.02.3) → legal-text 1.0.

  Input, one of:
  - a consolidated version: the CONS.ACT file, or CONS.OTH for consolidated documents other than acts;
  - an OJ act, agreement or corrigendum: its package descriptor (DOC, *.doc.xml), which lists the
    main file and the annex files in order; or the ACT, AGR or CORR file alone.
  Other Formex documents (notices, case law, the OJ's tables of contents) are not legal acts: they
  are refused with f:out-of-scope.

  The output validates against legal-text.xsd. An element the converter doesn't know stops the
  transformation (f:unknown), so a new Formex construct shows up as a failing test, not as lost text.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns:lt="https://kludgeworks.groknull.dev/ns/legal-text/1"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:include href="provenance.xsl"/>
  <xsl:include href="eid.xsl"/>
  <xsl:include href="inline.xsl"/>
  <xsl:include href="units.xsl"/>
  <xsl:include href="articles.xsl"/>
  <xsl:include href="lists.xsl"/>
  <xsl:include href="annexes.xsl"/>
  <xsl:include href="tables.xsl"/>
  <xsl:include href="documents.xsl"/>
  <xsl:include href="math.xsl"/>

  <xsl:output method="xml" encoding="UTF-8" indent="no"/>

  <!-- the CELEX number of an OJ act; Formex doesn't carry it (consolidations do) -->
  <xsl:param name="celex" as="xs:string?"/>
  <xsl:param name="converter" as="xs:string" select="'1.0.0'"/>

  <xsl:variable name="f:namespace" as="xs:string" select="'https://kludgeworks.groknull.dev/ns/legal-text/1'"/>
  <xsl:variable name="f:schema" as="xs:string" select="'https://kludgeworks.groknull.dev/schema/legal-text/1.0/legal-text.xsd'"/>

  <xsl:mode on-no-match="fail" on-multiple-match="fail" use-accumulators="#all"/>
  <xsl:mode name="document" on-no-match="fail" on-multiple-match="fail"/>
  <xsl:mode name="note" on-no-match="fail" on-multiple-match="fail"/>
  <xsl:mode name="recital" on-no-match="fail" on-multiple-match="fail"/>

  <xsl:template match="/">
    <xsl:apply-templates select="*" mode="document"/>
  </xsl:template>

  <!-- the default mode handles the parts of a document (title, preamble, conclusions): anything else fails -->
  <xsl:template match="*" priority="-1">
    <xsl:sequence select="f:unknown(., ())"/>
  </xsl:template>

  <!-- the same, when the root element itself is the input -->
  <xsl:template match="/DOC | /ACT | /AGR | /CORR | /CONS.ACT | /CONS.OTH | /*">
    <xsl:apply-templates select="." mode="document"/>
  </xsl:template>

  <!-- ============================================================ inputs -->

  <!-- an OJ act's package: the main document, then its annexes in the order the package lists them.
       An annex file can be written as GENERAL (the overview of a third country's law an adequacy
       decision annexes): same shape, TITLE and CONTENTS -->
  <xsl:template match="DOC" mode="document">
    <xsl:variable name="main" as="element()" select="doc(resolve-uri(FMX/DOC.MAIN.PUB/REF.PHYS/@FILE, base-uri(.)))/*"/>
    <xsl:variable name="annexes" as="element()*" select="FMX/DOC.SUB.PUB[@TYPE = 'ANNEX']/REF.PHYS/doc(resolve-uri(@FILE, base-uri(.)))/(ANNEX | GENERAL)"/>
    <!-- a package of something other than a legal act is refused like the document itself -->
    <xsl:if test="not($main/(self::ACT | self::AGR | self::CORR | self::CONS.ACT | self::CONS.OTH))">
      <xsl:apply-templates select="$main" mode="document"/>
    </xsl:if>
    <xsl:sequence select="f:refuse-corrected-notice($main)"/>
    <xsl:call-template name="f:legal-text">
      <xsl:with-param name="act" as="element()" select="$main"/>
      <xsl:with-param name="annexes" as="element()*" select="$annexes"/>
      <xsl:with-param name="package" as="element(DOC)?" select="."/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="ACT | AGR | CORR | CONS.ACT | CONS.OTH" mode="document">
    <xsl:sequence select="f:refuse-corrected-notice(.)"/>
    <xsl:call-template name="f:legal-text">
      <xsl:with-param name="act" as="element()" select="."/>
    </xsl:call-template>
  </xsl:template>

  <!-- a corrigendum of a notice includes the notice: of the courts (CJT, Case T-499/17) or of a
       competition (COMPETITION, EPSO): not a legal act, refused before anything is written -->
  <xsl:function name="f:refuse-corrected-notice" as="empty-sequence()">
    <xsl:param name="act" as="element()"/>
    <xsl:variable name="notice" as="element()?" select="f:with-included($act)[self::CJT or self::COMPETITION][1]"/>
    <xsl:if test="$notice">
      <xsl:message terminate="yes" error-code="f:out-of-scope">Not a legal act: corrects a notice (Formex {local-name($notice)})</xsl:message>
    </xsl:if>
  </xsl:function>

  <!-- notices (GENERAL), case law, the OJ's tables of contents (PUBLICATION) and the like -->
  <xsl:template match="*" mode="document">
    <xsl:message terminate="yes" error-code="f:out-of-scope">Not a legal act: Formex {local-name()} is out of scope</xsl:message>
  </xsl:template>

  <!-- ============================================================ document -->

  <xsl:template name="f:legal-text">
    <xsl:param name="act" as="element()"/>
    <xsl:param name="annexes" as="element()*" select="()"/>
    <xsl:param name="package" as="element(DOC)?" select="()"/>
    <xsl:variable name="consolidated" as="xs:boolean" select="exists($act/(self::CONS.ACT | self::CONS.OTH))"/>
    <!-- where the bibliographic data is, and where the text is -->
    <xsl:variable name="doc" as="element()" select="($act/CONS.DOC, $act/CONS.OTH.DOC, $act)[1]"/>
    <xsl:variable name="content" as="element()" select="($doc/CONS.DOC.GEN, $doc)[1]"/>
    <xsl:variable name="info" as="element(INFO.CONSLEG)?" select="$act/INFO.CONSLEG"/>
    <xsl:variable name="act-celex" as="xs:string" select="
        if ($consolidated) then '3' || $info/@CONSLEG.REF
        else ($celex[normalize-space()], error(xs:QName('f:celex'), 'An OJ act needs the $celex parameter'))[1]"/>
    <!-- a document, so that the marking pass can use a key -->
    <xsl:variable name="text" as="document-node(element(lt:legalText))">
      <xsl:document>
        <legalText act="{$act-celex}" kind="{if ($consolidated) then 'consolidated' else if ($act/self::CORR) then 'corrigendum' else 'original'}">
          <xsl:attribute name="xsi:schemaLocation" select="$f:namespace || ' ' || $f:schema"/>
          <xsl:if test="$consolidated">
            <xsl:attribute name="version" select="f:date($info/@START.DATE)"/>
          </xsl:if>
          <xsl:attribute name="lang" select="lower-case(($doc/BIB.INSTANCE/LG.DOC, $content/BIB.INSTANCE/LG.DOC)[1])"/>
          <xsl:attribute name="formexVersion" select="f:formex-version($act)"/>
          <xsl:attribute name="converter" select="$converter"/>

          <source celex="{if ($consolidated) then '0' || $info/@CONSLEG.REF || '-' || $info/@START.DATE else $act-celex}">
            <xsl:if test="$doc/BIB.INSTANCE/NO.ELI">
              <xsl:attribute name="eli" select="normalize-space($doc/BIB.INSTANCE/NO.ELI)"/>
            </xsl:if>
            <xsl:choose>
              <xsl:when test="$consolidated">
                <xsl:variable name="bib" as="element(BIB.INSTANCE.CONS)?" select="$doc/FAM.COMP/BIB.DATA/BIB.INSTANCE.CONS"/>
                <xsl:sequence select="f:publication($bib/DOCUMENT.REF.CONS/COLL, $bib/DOCUMENT.REF.CONS/NO.OJ,
                                                    $bib/DATE/@ISO, $bib/DOCUMENT.REF.CONS/PAGE.FIRST, $content/TITLE/TI/NO.DOC.C)"/>
              </xsl:when>
              <xsl:when test="$package">
                <xsl:sequence select="f:publication($package/PUBLICATION.REF/COLL, $package/PUBLICATION.REF/NO.OJ,
                                                    $package/PUBLICATION.REF/DATE/@ISO, $package/FMX/DOC.MAIN.PUB/PAGE.FIRST,
                                                    $content/TITLE/TI/NO.DOC.C)"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:variable name="ref" as="element(DOCUMENT.REF)?" select="$doc/BIB.INSTANCE/DOCUMENT.REF"/>
                <xsl:sequence select="f:publication($ref/COLL, $ref/NO.OJ, (), $ref/PAGE.FIRST, $content/TITLE/TI/NO.DOC.C)"/>
              </xsl:otherwise>
            </xsl:choose>
          </source>

          <xsl:if test="$consolidated">
            <lifecycle>
              <xsl:attribute name="start" select="f:date($info/@START.DATE)"/>
              <!-- an end or production date that is not a date (END.DATE="20062131") is left out; the start
                   date identifies the version, so it stays required -->
              <xsl:if test="$info/@END.DATE != '99999999'">
                <xsl:sequence select="f:date-attribute('end', $info/@END.DATE)"/>
              </xsl:if>
              <xsl:sequence select="f:date-attribute('produced', $info/@CONSLEG.DATE)"/>
              <xsl:if test="$info/@PROD.SEQ">
                <xsl:attribute name="sequence" select="$info/@PROD.SEQ"/>
              </xsl:if>
              <!-- an amending act whose CELEX the source writes malformed (1201994N/ACT) can't be referred to:
                   it is left out -->
              <xsl:for-each-group select="$doc/FAM.COMP//(MOD.ACT | CORRIG)/BIB.DATA[matches(f:celex(NO.CELEX), '^[0-9A-Z][0-9]{4}[A-Z]{1,2}[0-9A-Z()]*(/[0-9A-Z()]+)*$')]"
                                  group-by="normalize-space(NO.CELEX)">
                <eventRef type="{if (parent::CORRIG) then 'corrigendum' else 'amendment'}" act="{f:celex(current-grouping-key())}">
                  <xsl:sequence select="f:date-attribute('date', DATE/@ISO)"/>
                </eventRef>
              </xsl:for-each-group>
            </lifecycle>
          </xsl:if>

          <!-- the title; a second one (a consolidated document can have two) is text of the body -->
          <xsl:apply-templates select="$content/TITLE[1]"/>
          <xsl:apply-templates select="$content/PREAMBLE"/>

          <body>
            <xsl:call-template name="f:blocks">
              <!-- ACT.GEN: the whole of an act consolidated with a non-regular structure (CONS.DOC.GEN TYPE="OTH.ACT") -->
              <xsl:with-param name="nodes" as="node()*" select="$content/ENACTING.TERMS/node() | $content/CONTENTS/(node() except CONS.ANNEX)
                                                   | $content/CONTENTS.CORR/node() | $content/ACT.GEN | f:document-rest($act, $doc, $content)"/>
              <xsl:with-param name="ctx" as="xs:string*" select="()" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
              <xsl:with-param name="base" as="xs:string?" select="()" tunnel="yes"/>
            </xsl:call-template>
          </body>

          <xsl:apply-templates select="$content/FINAL"/>

          <!-- a consolidation's annexes: CONS.ANNEX, or for a non-regular structure CONS.DOC.GEN TYPE="ANNEX" -->
          <xsl:variable name="groups" as="array(element())*" select="f:annex-groups(($content/CONS.ANNEX, $content/CONTENTS/CONS.ANNEX,
                                                              $content/self::CONS.DOC.GEN/CONS.DOC.GEN[@TYPE = 'ANNEX'], $annexes))"/>
          <xsl:variable name="annex-units" as="element()*">
            <xsl:for-each select="$groups">
              <xsl:variable name="fragments" as="element()*" select="?*"/>
              <xsl:apply-templates select="$fragments[1]" mode="annex">
                <xsl:with-param name="fragments" as="element()*" select="$fragments"/>
                <xsl:with-param name="annexCount" as="xs:integer?" select="count($groups)"/>
                <xsl:with-param name="annexPosition" as="xs:integer?" select="position()"/>
                <xsl:with-param name="anchor" as="xs:integer?" select="
                    if ($fragments[1][self::ANNEX or self::GENERAL]) then index-of(($annexes ! generate-id()), generate-id($fragments[1]))
                    else count($fragments[1]/preceding::CONS.ANNEX) + 1"/>
                <xsl:with-param name="ctx" as="xs:string*" select="()" tunnel="yes"/>
                <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
                <xsl:with-param name="base" as="xs:string?" select="()" tunnel="yes"/>
              </xsl:apply-templates>
            </xsl:for-each>
          </xsl:variable>
          <xsl:variable name="letters" as="element()*">
            <xsl:apply-templates select="$content/COLL.LETTERS/LETTER" mode="block">
              <xsl:with-param name="ctx" as="xs:string*" select="()" tunnel="yes"/>
              <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
              <xsl:with-param name="base" as="xs:string?" select="()" tunnel="yes"/>
            </xsl:apply-templates>
          </xsl:variable>
          <!-- attached documents: a consolidation's related documents, a package's entries other than annexes -->
          <xsl:variable name="attachments" as="element()*">
            <xsl:for-each select="$content/self::CONS.DOC.GEN/CONS.DOC.GEN[not(@TYPE = 'ANNEX')],
                                  $package/FMX/DOC.SUB.PUB[f:is-attachment(.)]">
              <xsl:apply-templates select="." mode="attachment">
                <xsl:with-param name="eid" as="xs:string" select="'att_' || position()"/>
              </xsl:apply-templates>
            </xsl:for-each>
          </xsl:variable>
          <xsl:if test="$annex-units or $letters or $attachments">
            <annexes>
              <xsl:sequence select="$annex-units, $letters, $attachments"/>
            </annexes>
          </xsl:if>

          <!-- the notes of the act, its annexes and the documents they include -->
          <xsl:variable name="notes" as="element(NOTE)*" select="f:with-included(($act, $annexes))//NOTE[not(@NOTE.REF)]"/>
          <!-- and those of attached documents from other files, whose eIds carry the file: note_att0301-1 -->
          <xsl:variable name="attached-notes" as="element()*">
            <xsl:for-each select="$package/FMX//DOC.SUB.PUB[f:is-attachment(.)]">
              <xsl:variable name="docs" as="element()*" select="(REF.PHYS, DOC.SUB.PUB[@TYPE = 'ANNEX']/REF.PHYS)/doc(resolve-uri(@FILE, base-uri(.)))/*"/>
              <xsl:apply-templates select="f:with-included($docs)//NOTE[not(@NOTE.REF)]" mode="note">
                <xsl:with-param name="notePrefix" as="xs:string?" select="f:attachment-note-prefix(.)" tunnel="yes"/>
              </xsl:apply-templates>
            </xsl:for-each>
          </xsl:variable>
          <xsl:if test="$notes or $attached-notes">
            <notes>
              <xsl:apply-templates select="$notes" mode="note"/>
              <xsl:sequence select="$attached-notes"/>
            </notes>
          </xsl:if>
        </legalText>
      </xsl:document>
    </xsl:variable>
    <xsl:apply-templates select="$text/lt:legalText" mode="f:ambiguity"/>
  </xsl:template>

  <!-- ============================================================ labels the source repeats -->

  <!-- Where the published text gives one label twice under one parent (Annex XI points 1., 2., 2.),
       the units keep the label, and so share their eId, as published; they are marked ambiguous, which
       legal-text.sch allows. A shared eId without that (no label, or different labels) is a
       converter error. -->
  <xsl:mode name="f:ambiguity" on-no-match="shallow-copy"/>

  <xsl:key name="f:live-unit" match="lt:unit[not(ancestor::lt:deleted)]" use="@eId"/>

  <xsl:template match="lt:unit[not(ancestor::lt:deleted)][f:ambiguous(.)]" mode="f:ambiguity">
    <xsl:copy>
      <xsl:apply-templates select="@*" mode="#current"/>
      <xsl:attribute name="ambiguous" select="true()"/>
      <xsl:apply-templates select="node()" mode="#current"/>
    </xsl:copy>
  </xsl:template>

  <!-- a unit shares its eId because the source repeats its label in one sequence (the same list,
       article or annex: copies in two lists are two sequences, which the eIds must tell apart), or
       because its parent's eId is shared that way: then its own (pnt_2/pnt_a under both points 2) is
       too -->
  <xsl:function name="f:ambiguous" as="xs:boolean">
    <xsl:param name="unit" as="element(lt:unit)"/>
    <xsl:variable name="copies" as="element(lt:unit)+" select="key('f:live-unit', $unit/@eId, root($unit))"/>
    <xsl:sequence select="count($copies) gt 1 and (
        (f:printed-label($unit) != ''
         and (every $c in $copies satisfies f:printed-label($c) = f:printed-label($unit)
              and $c/.. is $unit/..))
        or (every $c in $copies satisfies f:unit-parent($c)[self::lt:unit][f:ambiguous(.)]))"/>
  </xsl:function>

  <!-- the label's letters and digits: print that differs only in spacing, punctuation or case is
       the same label (Article 2 and Article2, 1) and 1.) -->
  <xsl:function name="f:printed-label" as="xs:string">
    <xsl:param name="unit" as="element(lt:unit)"/>
    <xsl:sequence select="lower-case(replace(string-join($unit/lt:num//@v), '[^\p{L}\p{N}]', ''))"/>
  </xsl:function>

  <xsl:function name="f:unit-parent" as="node()">
    <xsl:param name="unit" as="element(lt:unit)"/>
    <xsl:sequence select="($unit/ancestor::*[@eId][1], root($unit))[1]"/>
  </xsl:function>

  <!-- documents and, recursively, the Formex documents they include (INCL.ELEMENT TYPE="FORMEX.DOC") -->
  <xsl:function name="f:with-included" as="element()*">
    <xsl:param name="docs" as="element()*"/>
    <xsl:variable name="included" as="element()*" select="$docs//INCL.ELEMENT[@TYPE = 'FORMEX.DOC']/doc(resolve-uri(@FILEREF, base-uri(.)))/*"/>
    <xsl:sequence select="$docs, if (exists($included)) then f:with-included($included) else ()"/>
  </xsl:function>

  <!-- what a document holds besides the parts the legal text takes from it elsewhere: its
       metadata, title, preamble, enacting terms, closing, annexes, letters and attached documents.
       It goes into the body, where anything unexpected fails. -->
  <xsl:function name="f:document-rest" as="element()*">
    <xsl:param name="act" as="element()"/>
    <xsl:param name="doc" as="element()"/>
    <xsl:param name="content" as="element()"/>
    <xsl:sequence select="($act | $doc | $content)/* except (
        $doc, $content, $act/(INFO.CONSLEG | INFO.PROD), ($doc | $content)/(BIB.INSTANCE | FAM.COMP),
        $content/(TITLE[1] | PREAMBLE | ENACTING.TERMS | CONTENTS | CONTENTS.CORR | ACT.GEN | FINAL
                  | CONS.ANNEX | CONS.DOC.GEN | COLL.LETTERS))"/>
  </xsl:function>

  <xsl:function name="f:formex-version" as="xs:string">
    <xsl:param name="e" as="element()"/>
    <xsl:sequence select="replace($e/@xsi:noNamespaceSchemaLocation, '^.*formex-([^-/]+)-\d+\.xd$', '$1')"/>
  </xsl:function>

  <xsl:function name="f:publication" as="element()?">
    <xsl:param name="series" as="xs:string?"/>
    <xsl:param name="number" as="xs:string?"/>
    <xsl:param name="date" as="xs:string?"/>
    <xsl:param name="page" as="xs:string?"/>
    <!-- a C-series document's number, printed above its title: (2004/C 88 E/0748) -->
    <xsl:param name="document" as="xs:string?"/>
    <xsl:if test="normalize-space($series)">
      <publication series="{normalize-space($series)}">
        <xsl:if test="normalize-space($number)">
          <xsl:attribute name="number" select="replace(normalize-space($number), '^0+', '')"/>
        </xsl:if>
        <xsl:sequence select="f:date-attribute('date', $date)"/>
        <xsl:if test="normalize-space($page)">
          <xsl:attribute name="page" select="normalize-space($page)"/>
        </xsl:if>
        <xsl:if test="normalize-space($document)">
          <xsl:attribute name="document" select="replace(normalize-space($document), '^\((.*)\)$', '$1')"/>
        </xsl:if>
      </publication>
    </xsl:if>
  </xsl:function>

  <!-- ============================================================ preface -->

  <!-- the long title, split by position as AKN4EU does: type and number, date, purpose -->
  <xsl:template match="TITLE">
    <preface>
      <longTitle>
        <xsl:for-each select="TI/(P | LIST | NP)">
          <xsl:choose>
            <!-- a line that is a list or a numbered paragraph, or holds a table (a corrigendum's
                 title): its blocks -->
            <xsl:when test="self::LIST or self::NP or f:has-blocks(node())">
              <xsl:call-template name="f:blocks">
                <xsl:with-param name="nodes" as="node()*" select="."/>
              </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
          <p>
            <xsl:choose>
              <xsl:when test="position() = 1 and node()[1][self::HT]">
                <docType>
                  <xsl:call-template name="f:inlines">
                    <xsl:with-param name="nodes" as="node()*" select="node()[1]"/>
                  </xsl:call-template>
                </docType>
                <xsl:variable name="rest" as="node()*" select="node()[position() gt 1]"/>
                <xsl:if test="matches(string-join($rest ! string()), '^\s')">
                  <t v=" "/>
                </xsl:if>
                <!-- the number and what follows on the line, with its emphasis -->
                <xsl:variable name="number" as="element()*">
                  <xsl:call-template name="f:inlines">
                    <xsl:with-param name="nodes" as="node()*" select="$rest"/>
                  </xsl:call-template>
                </xsl:variable>
                <docNumber>
                  <xsl:sequence select="f:trim-start($number)"/>
                </docNumber>
              </xsl:when>
              <xsl:when test="position() = 2 and count(DATE) = 1">
                <xsl:for-each-group select="node()" group-adjacent="exists(self::DATE)">
                  <xsl:choose>
                    <xsl:when test="current-grouping-key()">
                      <docDate asWritten="{string(.)}">
                        <xsl:sequence select="f:date-attribute('date', @ISO)"/>
                      </docDate>
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:call-template name="f:inlines">
                        <xsl:with-param name="nodes" as="node()*" select="current-group()"/>
                      </xsl:call-template>
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:for-each-group>
              </xsl:when>
              <xsl:when test="position() = 3">
                <docPurpose>
                  <xsl:call-template name="f:inlines">
                    <xsl:with-param name="nodes" as="node()*" select="node()"/>
                  </xsl:call-template>
                </docPurpose>
              </xsl:when>
              <xsl:otherwise>
                <xsl:call-template name="f:inlines">
                  <xsl:with-param name="nodes" as="node()*" select="node()"/>
                </xsl:call-template>
              </xsl:otherwise>
            </xsl:choose>
          </p>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each>
        <!-- a subtitle ("on products excluded from the scope of the Agreement …") -->
        <xsl:for-each select="STI/P">
          <p>
            <xsl:call-template name="f:inlines">
              <xsl:with-param name="nodes" as="node()*" select="node()"/>
            </xsl:call-template>
          </p>
        </xsl:for-each>
        <!-- NO.DOC.C goes into the publication data -->
        <xsl:apply-templates select="node() except (TI | STI), TI/(node() except (P | LIST | NP | NO.DOC.C)), STI/(node() except P)" mode="unexpected"/>
      </longTitle>
    </preface>
  </xsl:template>

  <!-- ============================================================ preamble -->

  <xsl:template match="PREAMBLE">
    <xsl:if test="normalize-space(.) or GR.VISA or GR.CONSID">
      <preamble eId="pbl_1">
        <xsl:apply-templates select="*"/>
      </preamble>
    </xsl:if>
  </xsl:template>

  <!-- text of a preamble outside formulas, citations and recitals; an agreement's full powers (the
       plenipotentiaries the parties designated) -->
  <xsl:template match="PREAMBLE/(P | NP | LIST | TBL | FULL.POWER)">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="."/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="PREAMBLE.INIT | PREAMBLE.FINAL">
    <xsl:if test="normalize-space()">
      <formula name="{if (self::PREAMBLE.INIT) then 'actingEntity' else 'enactingFormula'}">
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="node()"/>
        </xsl:call-template>
      </formula>
    </xsl:if>
  </xsl:template>

  <xsl:template match="GR.VISA">
    <citations>
      <xsl:if test="GR.VISA.INIT">
        <intro>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" as="node()*" select="GR.VISA.INIT/node()"/>
          </xsl:call-template>
        </intro>
      </xsl:if>
      <xsl:apply-templates select="node() except (GR.VISA.INIT | VISA)" mode="unexpected"/>
      <!-- citations are numbered through the preamble, which can have several groups of them -->
      <xsl:variable name="before" as="xs:integer" select="count(preceding-sibling::GR.VISA/VISA)"/>
      <xsl:for-each select="VISA">
        <xsl:variable name="m" as="processing-instruction()*" select="f:new-markers(., ())"/>
        <xsl:variable name="eid" as="xs:string" select="'pbl_1/cit_' || ($before + position())"/>
        <xsl:variable name="unit" as="element()">
          <unit eId="{$eid}" kind="citation">
            <xsl:sequence select="f:provenance($m)"/>
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" as="node()*" select="node()"/>
              <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
              <xsl:with-param name="ctx" as="xs:string*" select="f:ids($m)" tunnel="yes"/>
            </xsl:call-template>
          </unit>
        </xsl:variable>
        <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
      </xsl:for-each>
    </citations>
  </xsl:template>

  <!-- a GR.CONSID with no recital, only its opening words (a Parliament resolution: "having regard
       to Rules 94 and 96 …"): those words, as the preamble's text -->
  <xsl:template match="GR.CONSID[not(CONSID | DIV.CONSID | LIST)]">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" as="node()*" select="GR.CONSID.INIT/node()"/>
    </xsl:call-template>
    <xsl:apply-templates select="node() except GR.CONSID.INIT" mode="unexpected"/>
  </xsl:template>

  <xsl:template match="GR.CONSID">
    <recitals>
      <!-- the opening words; 01.01 can put them inside the first CONSID, before its group -->
      <xsl:variable name="init" as="element(GR.CONSID.INIT)?" select="(GR.CONSID.INIT, CONSID[1][not(NP)]/GR.CONSID.INIT)[1]"/>
      <!-- unnumbered CONSIDs before the first numbered recital: "Whereas:" written as a recital -->
      <xsl:variable name="leading" as="element(CONSID)*" select="CONSID[f:leads-recitals(.)]"/>
      <xsl:if test="$init or $leading">
        <intro>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" as="node()*" select="$init/node(), $leading/node()"/>
          </xsl:call-template>
        </intro>
      </xsl:if>
      <xsl:apply-templates select="node() except GR.CONSID.INIT" mode="recital">
        <xsl:with-param name="init" as="element(GR.CONSID.INIT)?" select="$init" tunnel="yes"/>
      </xsl:apply-templates>
    </recitals>
  </xsl:template>

  <!-- Among numbered recitals, an unnumbered CONSID is no recital of its own: one before the first
       numbered recital opens them ("Whereas:"), one after a numbered recital continues it (a further
       paragraph, a table) and is part of it. Only where all are unnumbered are they numbered by
       position. -->
  <xsl:function name="f:numbered-recital" as="xs:boolean">
    <xsl:param name="consid" as="element()"/>
    <xsl:sequence select="exists($consid/NP[1]/NO.P[f:label(.) != ''])"/>
  </xsl:function>

  <xsl:function name="f:unnumbered-consid" as="xs:boolean">
    <xsl:param name="consid" as="element()"/>
    <xsl:sequence select="exists($consid/self::CONSID[not(f:numbered-recital(.))][not(DIV.CONSID | GR.CONSID.INIT)])"/>
  </xsl:function>

  <xsl:function name="f:continues-recital" as="xs:boolean">
    <xsl:param name="consid" as="element()"/>
    <xsl:sequence select="f:unnumbered-consid($consid) and exists($consid/preceding-sibling::CONSID[f:numbered-recital(.)])"/>
  </xsl:function>

  <xsl:function name="f:leads-recitals" as="xs:boolean">
    <xsl:param name="consid" as="element()"/>
    <xsl:sequence select="f:unnumbered-consid($consid) and empty($consid/preceding-sibling::CONSID[f:numbered-recital(.)])
                          and exists($consid/following-sibling::CONSID[f:numbered-recital(.)])"/>
  </xsl:function>

  <!-- a recital (CONSID; an ITEM where the recitals are a LIST). Its eId is its number, preamble-wide
       also inside groups: pbl_1/rct_12. A recital of two numbered NP takes the first number. -->
  <xsl:template match="CONSID | GR.CONSID/LIST/ITEM" mode="recital">
    <!-- a continuation is part of the recital before it; opening words of the recitals are their
         intro, of a group's recitals the group's text -->
    <xsl:choose>
      <xsl:when test="f:leads-recitals(.)">
        <xsl:if test="not(parent::GR.CONSID)">
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" as="node()*" select="node()"/>
          </xsl:call-template>
        </xsl:if>
      </xsl:when>
      <xsl:when test="not(f:continues-recital(.))">
        <xsl:call-template name="f:recital"/>
      </xsl:when>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="f:recital">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:variable name="number" as="element(NO.P)?" select="NP[1]/NO.P"/>
    <xsl:variable name="next" as="element(CONSID)?" select="following-sibling::CONSID[f:numbered-recital(.)][1]"/>
    <xsl:variable name="continued" as="element(CONSID)*" select="
        if (f:numbered-recital(.)) then following-sibling::CONSID[f:continues-recital(.)][empty($next) or . &lt;&lt; $next] else ()"/>
    <xsl:variable name="eid" as="xs:string" select="'pbl_1/rct_' || (f:label($number)[. != ''], string(f:recital-position(.)))[1]"/>
    <xsl:variable name="m" as="processing-instruction()*" select="f:new-markers(., $ctx)"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="recital">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:if test="$number">
          <num>
            <xsl:call-template name="f:inlines">
              <xsl:with-param name="nodes" as="node()*" select="$number/node()"/>
              <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
            </xsl:call-template>
          </num>
        </xsl:if>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="NP[1]/TXT/node(), NP[1]/(node() except ($number, TXT)), node() except NP[1], $continued/node()"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- recitals grouped under a heading (DIV.CONSID, "A. PROCEDURE"), as divisions group articles:
       unit kind="division", eId pbl_1/dvs_A, nested under the enclosing group -->
  <xsl:template match="DIV.CONSID" mode="recital">
    <xsl:param name="ctx" as="xs:string*" tunnel="yes"/>
    <xsl:param name="groupIntro" as="element(GR.CONSID.INIT)*" select="()"/>
    <xsl:param name="division" as="xs:string?" tunnel="yes"/>
    <xsl:variable name="m" as="processing-instruction()*" select="f:new-markers(., $ctx)"/>
    <xsl:variable name="line" as="element()?" select="TITLE/TI/*[1]"/>
    <xsl:variable name="label" as="xs:string" select="((if ($line/self::NP) then f:label($line/NO.P) else f:label-after-word($line))[. != ''],
                                        string(count(preceding-sibling::DIV.CONSID) + 1))[1]"/>
    <xsl:variable name="eid" as="xs:string" select="f:eid(($division, 'pbl_1')[1], 'dvs', $label)"/>
    <xsl:variable name="unit" as="element()">
      <unit eId="{$eid}" kind="division">
        <xsl:sequence select="f:provenance($m)"/>
        <xsl:call-template name="f:title">
          <xsl:with-param name="title" as="element(TITLE)?" select="TITLE"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
        </xsl:call-template>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" as="node()*" select="$groupIntro/node()"/>
          <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
          <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
          <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
        </xsl:call-template>
        <!-- recitals and groups, and between them what the group says besides (a table, a paragraph) -->
        <xsl:for-each-group select="node() except TITLE" group-adjacent="exists(self::CONSID | self::DIV.CONSID)">
          <xsl:choose>
            <xsl:when test="current-grouping-key()">
              <xsl:apply-templates select="current-group()" mode="recital">
                <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
                <xsl:with-param name="division" as="xs:string?" select="$eid" tunnel="yes"/>
              </xsl:apply-templates>
            </xsl:when>
            <xsl:otherwise>
              <xsl:call-template name="f:blocks">
                <xsl:with-param name="nodes" as="node()*" select="current-group()"/>
                <xsl:with-param name="ctx" as="xs:string*" select="($ctx, f:ids($m))" tunnel="yes"/>
                <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
                <xsl:with-param name="base" as="xs:string?" select="$eid" tunnel="yes"/>
              </xsl:call-template>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each-group>
      </unit>
    </xsl:variable>
    <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
  </xsl:template>

  <!-- a CONSID that only wraps a group (01.01), and recitals written as a LIST: their content; opening
       words in such a CONSID, other than the recitals' own, open its group -->
  <xsl:template match="CONSID[DIV.CONSID][not(NP)] | GR.CONSID/LIST" mode="recital">
    <xsl:param name="init" as="element(GR.CONSID.INIT)?" tunnel="yes"/>
    <xsl:variable name="intro" as="element(GR.CONSID.INIT)*" select="GR.CONSID.INIT except $init"/>
    <xsl:variable name="first-group" as="element(DIV.CONSID)?" select="DIV.CONSID[1]"/>
    <xsl:for-each select="node() except GR.CONSID.INIT">
      <xsl:apply-templates select="." mode="recital">
        <xsl:with-param name="groupIntro" as="element(GR.CONSID.INIT)*" select="if (. is $first-group) then $intro else ()"/>
      </xsl:apply-templates>
    </xsl:for-each>
  </xsl:template>

  <xsl:template match="node()" mode="recital" priority="-1">
    <xsl:apply-templates select="." mode="unexpected"/>
  </xsl:template>

  <!-- an unnumbered recital's position among all the recitals of the preamble -->
  <xsl:function name="f:recital-position" as="xs:integer">
    <xsl:param name="recital" as="element()"/>
    <xsl:sequence select="count($recital/preceding::*[self::CONSID[not(DIV.CONSID)] or self::ITEM[parent::LIST/parent::GR.CONSID]]
                                [ancestor::GR.CONSID is $recital/ancestor::GR.CONSID]) + 1"/>
  </xsl:function>

  <!-- ============================================================ conclusions -->

  <xsl:template match="FINAL">
    <conclusions>
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="node() except SIGNATURE"/>
      </xsl:call-template>
      <xsl:apply-templates select="SIGNATURE"/>
    </conclusions>
  </xsl:template>

  <xsl:template match="SIGNATURE">
    <signature>
      <xsl:if test="PL.DATE">
        <placeAndDate>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" as="element()*" select="PL.DATE/P"/>
          </xsl:call-template>
        </placeAndDate>
      </xsl:if>
      <xsl:apply-templates select="SIGNATORY"/>
      <xsl:apply-templates select="node() except (PL.DATE | SIGNATORY), PL.DATE/(node() except P)" mode="unexpected"/>
    </signature>
  </xsl:template>

  <!-- the lines of a signature block: organisation, role(s), person -->
  <xsl:template match="SIGNATORY">
    <xsl:variable name="lines" as="element(P)*" select="P"/>
    <signatory>
      <xsl:if test="count($lines) ge 2">
        <organization>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" as="element()*" select="$lines[1]"/>
          </xsl:call-template>
        </organization>
      </xsl:if>
      <xsl:if test="count($lines) ge 3">
        <role>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" as="element()*" select="$lines[position() gt 1 and position() lt last()]"/>
          </xsl:call-template>
        </role>
      </xsl:if>
      <xsl:if test="$lines">
        <person>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" as="element()*" select="$lines[last()]"/>
          </xsl:call-template>
        </person>
      </xsl:if>
      <xsl:apply-templates select="node() except P" mode="unexpected"/>
    </signatory>
  </xsl:template>

  <!-- inline content without the whitespace it starts with -->
  <xsl:function name="f:trim-start" as="element()*">
    <xsl:param name="inlines" as="element()*"/>
    <xsl:variable name="first" as="element()?" select="$inlines[1]"/>
    <xsl:choose>
      <xsl:when test="$first/self::lt:t">
        <xsl:variable name="v" as="xs:string" select="replace($first/@v, '^\s+', '')"/>
        <xsl:if test="$v != ''"><t v="{$v}"/></xsl:if>
        <xsl:sequence select="$inlines[position() gt 1]"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="$inlines"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <!-- ============================================================ notes -->

  <xsl:template match="NOTE" mode="note">
    <xsl:param name="notePrefix" as="xs:string?" tunnel="yes"/>
    <note eId="{f:note-eid(., $notePrefix)}" marker="{f:note-marker(.)}" kind="{lower-case((@TYPE, 'FOOTNOTE')[1])}">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" as="node()*" select="node()"/>
        <xsl:with-param name="ctx" as="xs:string*" select="f:ids(let $v := accumulator-before('f:open') return map:keys($v) ! $v(.))" tunnel="yes"/>
        <xsl:with-param name="pending" as="processing-instruction()*" select="()" tunnel="yes"/>
        <xsl:with-param name="base" as="xs:string?" select="f:note-eid(., $notePrefix)" tunnel="yes"/>
      </xsl:call-template>
    </note>
  </xsl:template>

</xsl:stylesheet>
