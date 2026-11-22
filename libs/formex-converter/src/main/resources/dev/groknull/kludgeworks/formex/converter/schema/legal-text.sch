<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  legal-text 1.0: the rules legal-text.xsd can't express. XSD 1.0 checks the structure; its
  identity constraints can't leave out deleted placeholders or match "#note_1" against "note_1".
  A legal text is valid when it is valid against both. Run with any ISO Schematron processor
  with the XSLT 3.0 binding (SchXslt 2 in the tests).
-->
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    queryBinding="xslt3">

  <sch:title>legal-text 1.0 rules</sch:title>

  <sch:ns prefix="lt" uri="https://kludgeworks.groknull.dev/ns/legal-text/1"/>

  <!-- occurrences, quoted structures and footnotes that are in force: not inside a deleted placeholder -->
  <xsl:key name="live-eid" match="(lt:occurrence | lt:quotedStructure | lt:footnote)[not(ancestor::lt:deleted)]" use="@eId"/>
  <xsl:key name="footnote" match="lt:footnote" use="@eId"/>
  <xsl:key name="tag" match="lt:*[@tagId][@amendingAct]" use="@tagId"/>

  <sch:pattern id="eids">
    <sch:title>eIds identify occurrences</sch:title>
    <!-- a deleted placeholder keeps its old eId, which a later insertion may reuse; occurrences whose label
         the published text repeats under one parent share theirs, each marked ambiguous -->
    <sch:rule context="(lt:occurrence | lt:quotedStructure | lt:footnote)[not(ancestor::lt:deleted)]">
      <sch:assert id="unique-live-eid" test="count(key('live-eid', @eId)) = 1 or (every $u in key('live-eid', @eId) satisfies $u/@ambiguous = 'true')">
        The eId <sch:value-of select="@eId"/> belongs to <sch:value-of select="count(key('live-eid', @eId))"/>
        occurrences in force; it must belong to one, unless all are marked ambiguous.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <sch:pattern id="references">
    <sch:title>References resolve</sch:title>
    <sch:rule context="lt:footnoteRef">
      <sch:assert id="footnote-exists" test="exists(key('footnote', substring-after(@href, '#')))">
        <sch:value-of select="@href"/> refers to no footnote.
      </sch:assert>
      <sch:assert id="footnote-marker" test="empty(key('footnote', substring-after(@href, '#'))) or key('footnote', substring-after(@href, '#'))/@marker = @marker">
        The reference to <sch:value-of select="@href"/> shows <sch:value-of select="@marker"/>, the footnote another marker.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <sch:pattern id="kinds">
    <sch:title>What the schema documents per kind holds</sch:title>
    <sch:rule context="lt:occurrence[@depth or @box]">
      <sch:assert id="levels-only" test="@kind = 'level'">depth and box belong to levels; <sch:value-of select="@eId"/> is a <sch:value-of select="@kind"/>.</sch:assert>
    </sch:rule>
    <sch:rule context="lt:occurrence[@eurlexAnchor]">
      <sch:assert id="annexes-only" test="@kind = 'annex'">eurlexAnchor belongs to annexes; <sch:value-of select="@eId"/> is a <sch:value-of select="@kind"/>.</sch:assert>
    </sch:rule>
    <sch:rule context="lt:ref">
      <sch:assert id="ref-by-kind" test="if (@kind = 'oj') then not(@href) else not(@series | @number | @date | @page)">
        A reference of kind <sch:value-of select="@kind"/> has the attributes of the other kind.
      </sch:assert>
    </sch:rule>
    <sch:rule context="lt:old | lt:new">
      <sch:assert id="correction-sides" test="ancestor::lt:occurrence[1]/@kind = 'correction'">old and new are the sides of a correction.</sch:assert>
    </sch:rule>
  </sch:pattern>

  <sch:pattern id="deletions">
    <sch:title>Deleted content says so, and only it</sch:title>
    <sch:rule context="lt:tag">
      <sch:assert id="deletion-in-deleted" test="(@modification = 'deleted') = exists(parent::lt:deleted)">
        A deletion is the tag of a deleted placeholder, and a deleted placeholder's tag is its deletion
        (tag <sch:value-of select="@tagId"/>).
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <sch:pattern id="versions">
    <sch:title>A document says once which version it is</sch:title>
    <sch:rule context="lt:legalText">
      <sch:assert id="version-of-consolidation" test="(@kind = 'consolidated') = exists(@version) and (@kind = 'consolidated') = exists(lt:lifecycle)">
        A consolidated version has a version date and a lifecycle, and only a consolidated version has them.
      </sch:assert>
      <sch:assert id="version-in-celex" test="not(@kind = 'consolidated' and @version) or replace(lt:source/@celex, '^.*-([0-9]{4})([0-9]{2})([0-9]{2})$', '$1-$2-$3') = @version">
        The version date <sch:value-of select="@version"/> isn't the date of <sch:value-of select="lt:source/@celex"/>.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <sch:pattern id="amendments">
    <sch:title>A tag means one amendment</sch:title>
    <!-- a tag split at element boundaries becomes several pieces that share its id -->
    <sch:rule context="lt:*[@tagId][@amendingAct]">
      <sch:assert id="tag-consistent"
                  test="every $piece in key('tag', @tagId)
                        satisfies $piece/@amendingAct = @amendingAct and $piece/@modification = @modification">
        The pieces of tag <sch:value-of select="@tagId"/> disagree on the amending act or modification.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

</sch:schema>
