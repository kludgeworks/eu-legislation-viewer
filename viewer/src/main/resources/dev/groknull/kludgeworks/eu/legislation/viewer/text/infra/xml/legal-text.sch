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

  <!-- units, quoted structures and notes that are in force: not inside a deleted placeholder -->
  <xsl:key name="live-eid" match="(lt:unit | lt:quotedStructure | lt:note)[not(ancestor::lt:deleted)]" use="@eId"/>
  <xsl:key name="note" match="lt:note" use="@eId"/>
  <xsl:key name="marker" match="lt:*[@marker][@amendingAct]" use="@marker"/>

  <sch:pattern id="eids">
    <sch:title>eIds identify units</sch:title>
    <!-- a deleted placeholder keeps its old eId, which a later insertion may reuse -->
    <sch:rule context="(lt:unit | lt:quotedStructure | lt:note)[not(ancestor::lt:deleted)]">
      <sch:assert id="unique-live-eid" test="count(key('live-eid', @eId)) = 1">
        The eId <sch:value-of select="@eId"/> belongs to <sch:value-of select="count(key('live-eid', @eId))"/>
        units in force; it must belong to one.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <sch:pattern id="references">
    <sch:title>References resolve</sch:title>
    <sch:rule context="lt:noteRef">
      <sch:assert id="note-exists" test="exists(key('note', substring-after(@href, '#')))">
        <sch:value-of select="@href"/> refers to no note.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

  <sch:pattern id="amendments">
    <sch:title>An amendment marker means one amendment</sch:title>
    <!-- a marker split at element boundaries becomes several pieces that share its id -->
    <sch:rule context="lt:*[@marker][@amendingAct]">
      <sch:assert id="marker-consistent"
                  test="every $piece in key('marker', @marker)
                        satisfies $piece/@amendingAct = @amendingAct and $piece/@action = @action">
        The pieces of marker <sch:value-of select="@marker"/> disagree on the amending act or action.
      </sch:assert>
    </sch:rule>
  </sch:pattern>

</sch:schema>
