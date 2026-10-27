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

  The output validates against legal-text.xsd. An element the normaliser doesn't know stops the
  transformation (f:unknown), so a new Formex construct shows up as a failing test, not as lost text.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:f="https://kludgeworks.groknull.dev/ns/normalise"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:include href="provenance.xsl"/>
  <xsl:include href="eid.xsl"/>
  <xsl:include href="inline.xsl"/>
  <xsl:include href="units.xsl"/>
  <xsl:include href="math.xsl"/>

  <xsl:output method="xml" encoding="UTF-8" indent="no"/>

  <!-- the CELEX number of an OJ act; Formex doesn't carry it (consolidations do) -->
  <xsl:param name="celex" as="xs:string?"/>
  <xsl:param name="normaliser" as="xs:string" select="'1.0.0'"/>

  <xsl:variable name="f:namespace" select="'https://kludgeworks.groknull.dev/ns/legal-text/1'"/>
  <xsl:variable name="f:schema" select="'https://kludgeworks.groknull.dev/schema/legal-text/1.0/legal-text.xsd'"/>

  <xsl:mode on-no-match="fail" use-accumulators="#all"/>
  <xsl:mode name="document" on-no-match="fail"/>

  <xsl:template match="/">
    <xsl:apply-templates select="*" mode="document"/>
  </xsl:template>

  <!-- the same, when the root element itself is the input -->
  <xsl:template match="/DOC | /ACT | /AGR | /CORR | /CONS.ACT | /CONS.OTH | /*">
    <xsl:apply-templates select="." mode="document"/>
  </xsl:template>

  <!-- ============================================================ inputs -->

  <!-- an OJ act's package: the main document, then its annexes in the order the package lists them -->
  <xsl:template match="DOC" mode="document">
    <xsl:variable name="main" select="doc(resolve-uri(FMX/DOC.MAIN.PUB/REF.PHYS/@FILE, base-uri(.)))/*"/>
    <xsl:variable name="annexes" select="FMX/DOC.SUB.PUB[@TYPE = 'ANNEX']/REF.PHYS/doc(resolve-uri(@FILE, base-uri(.)))/ANNEX"/>
    <!-- a package of something other than a legal act is refused like the document itself -->
    <xsl:if test="not($main/(self::ACT | self::AGR | self::CORR | self::CONS.ACT | self::CONS.OTH))">
      <xsl:apply-templates select="$main" mode="document"/>
    </xsl:if>
    <xsl:call-template name="f:legal-text">
      <xsl:with-param name="act" select="$main"/>
      <xsl:with-param name="annexes" select="$annexes"/>
      <xsl:with-param name="package" select="."/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="ACT | AGR | CORR | CONS.ACT | CONS.OTH" mode="document">
    <xsl:call-template name="f:legal-text">
      <xsl:with-param name="act" select="."/>
    </xsl:call-template>
  </xsl:template>

  <!-- notices (GENERAL), case law, the OJ's tables of contents (PUBLICATION) and the like -->
  <xsl:template match="*" mode="document">
    <xsl:message terminate="yes" error-code="f:out-of-scope">Not a legal act: Formex {local-name()} is out of scope</xsl:message>
  </xsl:template>

  <!-- ============================================================ document -->

  <xsl:template name="f:legal-text">
    <xsl:param name="act" as="element()"/>
    <xsl:param name="annexes" as="element(ANNEX)*" select="()"/>
    <xsl:param name="package" as="element(DOC)?" select="()"/>
    <xsl:variable name="consolidated" select="exists($act/(self::CONS.ACT | self::CONS.OTH))"/>
    <!-- where the bibliographic data is, and where the text is -->
    <xsl:variable name="doc" select="($act/CONS.DOC, $act/CONS.OTH.DOC, $act)[1]"/>
    <xsl:variable name="content" select="($doc/CONS.DOC.GEN, $doc)[1]"/>
    <xsl:variable name="info" select="$act/INFO.CONSLEG"/>
    <xsl:variable name="act-celex" select="
        if ($consolidated) then '3' || $info/@CONSLEG.REF
        else ($celex[normalize-space()], error(xs:QName('f:celex'), 'An OJ act needs the $celex parameter'))[1]"/>
    <legalText act="{$act-celex}" kind="{if ($consolidated) then 'consolidated' else if ($act/self::CORR) then 'corrigendum' else 'original'}">
      <xsl:attribute name="xsi:schemaLocation" select="$f:namespace || ' ' || $f:schema"/>
      <xsl:if test="$consolidated">
        <xsl:attribute name="version" select="f:date($info/@START.DATE)"/>
      </xsl:if>
      <xsl:attribute name="lang" select="lower-case(($doc/BIB.INSTANCE/LG.DOC, $content/BIB.INSTANCE/LG.DOC)[1])"/>
      <xsl:attribute name="formexVersion" select="f:formex-version($act)"/>
      <xsl:attribute name="normaliser" select="$normaliser"/>

      <source celex="{if ($consolidated) then '0' || $info/@CONSLEG.REF || '-' || $info/@START.DATE else $act-celex}">
        <xsl:if test="$doc/BIB.INSTANCE/NO.ELI">
          <xsl:attribute name="eli" select="normalize-space($doc/BIB.INSTANCE/NO.ELI)"/>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="$consolidated">
            <xsl:variable name="bib" select="$doc/FAM.COMP/BIB.DATA/BIB.INSTANCE.CONS"/>
            <xsl:sequence select="f:publication($bib/DOCUMENT.REF.CONS/COLL, $bib/DOCUMENT.REF.CONS/NO.OJ,
                                                $bib/DATE/@ISO, $bib/DOCUMENT.REF.CONS/PAGE.FIRST)"/>
          </xsl:when>
          <xsl:when test="$package">
            <xsl:sequence select="f:publication($package/PUBLICATION.REF/COLL, $package/PUBLICATION.REF/NO.OJ,
                                                $package/PUBLICATION.REF/DATE/@ISO, $package/FMX/DOC.MAIN.PUB/PAGE.FIRST)"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:variable name="ref" select="$doc/BIB.INSTANCE/DOCUMENT.REF"/>
            <xsl:sequence select="f:publication($ref/COLL, $ref/NO.OJ, (), $ref/PAGE.FIRST)"/>
          </xsl:otherwise>
        </xsl:choose>
      </source>

      <xsl:if test="$consolidated">
        <lifecycle>
          <xsl:attribute name="start" select="f:date($info/@START.DATE)"/>
          <xsl:if test="$info/@END.DATE != '99999999'">
            <xsl:attribute name="end" select="f:date($info/@END.DATE)"/>
          </xsl:if>
          <xsl:if test="$info/@CONSLEG.DATE">
            <xsl:attribute name="produced" select="f:date($info/@CONSLEG.DATE)"/>
          </xsl:if>
          <xsl:if test="$info/@PROD.SEQ">
            <xsl:attribute name="sequence" select="$info/@PROD.SEQ"/>
          </xsl:if>
          <xsl:for-each-group select="$doc/FAM.COMP//(MOD.ACT | CORRIG)/BIB.DATA" group-by="normalize-space(NO.CELEX)">
            <eventRef type="{if (parent::CORRIG) then 'corrigendum' else 'amendment'}" act="{f:celex(current-grouping-key())}">
              <xsl:if test="DATE/@ISO">
                <xsl:attribute name="date" select="f:date(DATE/@ISO)"/>
              </xsl:if>
            </eventRef>
          </xsl:for-each-group>
        </lifecycle>
      </xsl:if>

      <xsl:apply-templates select="$content/TITLE"/>
      <xsl:apply-templates select="$content/PREAMBLE"/>

      <body>
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="$content/ENACTING.TERMS/node(), $content/CONTENTS/(node() except CONS.ANNEX),
                                               $content/CONTENTS.CORR/CORRECTION"/>
          <xsl:with-param name="ctx" select="()" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
          <xsl:with-param name="base" select="()" tunnel="yes"/>
        </xsl:call-template>
      </body>

      <xsl:apply-templates select="$content/FINAL"/>

      <xsl:variable name="groups" select="f:annex-groups(($content/CONS.ANNEX, $content/CONTENTS/CONS.ANNEX, $annexes))"/>
      <xsl:variable name="annex-units" as="element()*">
        <xsl:for-each select="$groups">
          <xsl:variable name="fragments" select="?*"/>
          <xsl:apply-templates select="$fragments[1]" mode="block">
            <xsl:with-param name="fragments" select="$fragments"/>
            <xsl:with-param name="annexCount" select="count($groups)"/>
            <xsl:with-param name="annexPosition" select="position()"/>
            <xsl:with-param name="anchor" select="
                if ($fragments[1]/self::ANNEX) then index-of(($annexes ! generate-id()), generate-id($fragments[1]))
                else count($fragments[1]/preceding::CONS.ANNEX) + 1"/>
            <xsl:with-param name="ctx" select="()" tunnel="yes"/>
            <xsl:with-param name="pending" select="()" tunnel="yes"/>
            <xsl:with-param name="base" select="()" tunnel="yes"/>
          </xsl:apply-templates>
        </xsl:for-each>
      </xsl:variable>
      <xsl:variable name="letters" as="element()*">
        <xsl:apply-templates select="$content/COLL.LETTERS/LETTER" mode="block">
          <xsl:with-param name="ctx" select="()" tunnel="yes"/>
          <xsl:with-param name="pending" select="()" tunnel="yes"/>
          <xsl:with-param name="base" select="()" tunnel="yes"/>
        </xsl:apply-templates>
      </xsl:variable>
      <xsl:if test="$annex-units or $letters">
        <annexes>
          <xsl:sequence select="$annex-units, $letters"/>
        </annexes>
      </xsl:if>

      <xsl:variable name="notes" select="($act, $annexes)//NOTE[@NOTE.ID]"/>
      <xsl:if test="$notes">
        <notes>
          <xsl:apply-templates select="$notes" mode="note"/>
        </notes>
      </xsl:if>
    </legalText>
  </xsl:template>

  <xsl:function name="f:formex-version" as="xs:string">
    <xsl:param name="e" as="element()"/>
    <xsl:sequence select="replace($e/@xsi:noNamespaceSchemaLocation, '^.*formex-([^-/]+)-\d+\.xd$', '$1')"/>
  </xsl:function>

  <xsl:function name="f:publication" as="element()?">
    <xsl:param name="series" as="xs:string?"/>
    <xsl:param name="number" as="xs:string?"/>
    <xsl:param name="date" as="xs:string?"/>
    <xsl:param name="page" as="xs:string?"/>
    <xsl:if test="normalize-space($series)">
      <publication series="{normalize-space($series)}">
        <xsl:if test="normalize-space($number)">
          <xsl:attribute name="number" select="replace(normalize-space($number), '^0+', '')"/>
        </xsl:if>
        <xsl:if test="normalize-space($date)">
          <xsl:attribute name="date" select="f:date($date)"/>
        </xsl:if>
        <xsl:if test="normalize-space($page)">
          <xsl:attribute name="page" select="normalize-space($page)"/>
        </xsl:if>
      </publication>
    </xsl:if>
  </xsl:function>

  <!-- ============================================================ preface -->

  <!-- the long title, split by position as AKN4EU does: type and number, date, purpose -->
  <xsl:template match="TITLE">
    <preface>
      <longTitle>
        <xsl:for-each select="TI/P">
          <p>
            <xsl:choose>
              <xsl:when test="position() = 1 and node()[1][self::HT]">
                <docType>
                  <xsl:call-template name="f:inlines">
                    <xsl:with-param name="nodes" select="node()[1]"/>
                  </xsl:call-template>
                </docType>
                <xsl:variable name="rest" select="node()[position() gt 1]"/>
                <xsl:if test="matches(string-join($rest ! string()), '^\s')">
                  <t v=" "/>
                </xsl:if>
                <docNumber>
                  <t v="{normalize-space(string-join($rest ! string()))}"/>
                </docNumber>
              </xsl:when>
              <xsl:when test="position() = 2 and count(DATE) = 1">
                <xsl:for-each select="node()">
                  <xsl:choose>
                    <xsl:when test="self::DATE">
                      <docDate date="{f:date(@ISO)}" asWritten="{string(.)}"/>
                    </xsl:when>
                    <xsl:when test="self::text()">
                      <t v="{.}"/>
                    </xsl:when>
                  </xsl:choose>
                </xsl:for-each>
              </xsl:when>
              <xsl:when test="position() = 3">
                <docPurpose>
                  <xsl:call-template name="f:inlines">
                    <xsl:with-param name="nodes" select="node()"/>
                  </xsl:call-template>
                </docPurpose>
              </xsl:when>
              <xsl:otherwise>
                <xsl:call-template name="f:inlines">
                  <xsl:with-param name="nodes" select="node()"/>
                </xsl:call-template>
              </xsl:otherwise>
            </xsl:choose>
          </p>
        </xsl:for-each>
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

  <!-- text of a preamble outside formulas, citations and recitals -->
  <xsl:template match="PREAMBLE/(P | NP | LIST | TBL)">
    <xsl:call-template name="f:blocks">
      <xsl:with-param name="nodes" select="."/>
    </xsl:call-template>
  </xsl:template>

  <xsl:template match="PREAMBLE.INIT | PREAMBLE.FINAL">
    <xsl:if test="normalize-space()">
      <formula name="{if (self::PREAMBLE.INIT) then 'actingEntity' else 'enactingFormula'}">
        <xsl:call-template name="f:blocks">
          <xsl:with-param name="nodes" select="node()"/>
        </xsl:call-template>
      </formula>
    </xsl:if>
  </xsl:template>

  <xsl:template match="GR.VISA">
    <citations>
      <xsl:if test="GR.VISA.INIT">
        <intro>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" select="GR.VISA.INIT/node()"/>
          </xsl:call-template>
        </intro>
      </xsl:if>
      <xsl:for-each select="VISA">
        <xsl:variable name="m" select="f:new-markers(., ())"/>
        <xsl:variable name="unit" as="element()">
          <unit eId="pbl_1/cit_{position()}" kind="citation">
            <xsl:sequence select="f:provenance($m)"/>
            <xsl:call-template name="f:blocks">
              <xsl:with-param name="nodes" select="node()"/>
              <xsl:with-param name="base" select="'pbl_1/cit_' || position()" tunnel="yes"/>
              <xsl:with-param name="ctx" select="f:ids($m)" tunnel="yes"/>
            </xsl:call-template>
          </unit>
        </xsl:variable>
        <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
      </xsl:for-each>
    </citations>
  </xsl:template>

  <xsl:template match="GR.CONSID">
    <recitals>
      <xsl:if test="GR.CONSID.INIT">
        <intro>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" select="GR.CONSID.INIT/node()"/>
          </xsl:call-template>
        </intro>
      </xsl:if>
      <xsl:for-each select="CONSID">
        <xsl:variable name="eid" select="'pbl_1/rct_' || (f:label(NP/NO.P)[. != ''], string(position()))[1]"/>
        <xsl:variable name="m" select="f:new-markers(., ())"/>
        <xsl:variable name="unit" as="element()">
        <unit eId="{$eid}" kind="recital">
          <xsl:sequence select="f:provenance($m)"/>
          <xsl:if test="NP/NO.P">
            <num>
              <xsl:call-template name="f:inlines">
                <xsl:with-param name="nodes" select="NP/NO.P/node()"/>
              </xsl:call-template>
            </num>
          </xsl:if>
          <xsl:call-template name="f:blocks">
            <xsl:with-param name="nodes" select="NP/TXT/node(), NP/(node() except (NO.P, TXT)), node() except NP"/>
            <xsl:with-param name="base" select="$eid" tunnel="yes"/>
            <xsl:with-param name="ctx" select="f:ids($m)" tunnel="yes"/>
          </xsl:call-template>
        </unit>
        </xsl:variable>
        <xsl:sequence select="f:wrap-deleted($m, $unit)"/>
      </xsl:for-each>
    </recitals>
  </xsl:template>

  <!-- ============================================================ conclusions -->

  <xsl:template match="FINAL">
    <conclusions>
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" select="P"/>
      </xsl:call-template>
      <xsl:apply-templates select="SIGNATURE"/>
    </conclusions>
  </xsl:template>

  <xsl:template match="SIGNATURE">
    <signature>
      <xsl:if test="PL.DATE">
        <placeAndDate>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" select="PL.DATE/P"/>
          </xsl:call-template>
        </placeAndDate>
      </xsl:if>
      <xsl:apply-templates select="SIGNATORY"/>
    </signature>
  </xsl:template>

  <!-- the lines of a signature block: organisation, role(s), person -->
  <xsl:template match="SIGNATORY">
    <xsl:variable name="lines" select="P"/>
    <signatory>
      <xsl:if test="count($lines) ge 2">
        <organization>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" select="$lines[1]"/>
          </xsl:call-template>
        </organization>
      </xsl:if>
      <xsl:if test="count($lines) ge 3">
        <role>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" select="$lines[position() gt 1 and position() lt last()]"/>
          </xsl:call-template>
        </role>
      </xsl:if>
      <xsl:if test="$lines">
        <person>
          <xsl:call-template name="f:lines">
            <xsl:with-param name="lines" select="$lines[last()]"/>
          </xsl:call-template>
        </person>
      </xsl:if>
    </signatory>
  </xsl:template>

  <!-- ============================================================ notes -->

  <xsl:template match="NOTE" mode="note">
    <note eId="{f:note-eid(.)}" marker="{f:note-marker(.)}" kind="{lower-case((@TYPE, 'FOOTNOTE')[1])}">
      <xsl:call-template name="f:blocks">
        <xsl:with-param name="nodes" select="node()"/>
        <xsl:with-param name="ctx" select="f:ids(let $v := accumulator-before('f:open') return map:keys($v) ! $v(.))" tunnel="yes"/>
        <xsl:with-param name="pending" select="()" tunnel="yes"/>
        <xsl:with-param name="base" select="f:note-eid(.)" tunnel="yes"/>
      </xsl:call-template>
    </note>
  </xsl:template>

</xsl:stylesheet>
