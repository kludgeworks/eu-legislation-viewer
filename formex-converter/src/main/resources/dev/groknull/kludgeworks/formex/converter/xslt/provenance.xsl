<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Amendment markers. A consolidation marks each amended passage with a pair of processing
  instructions, <?CLG.MDFO ID="O…" ACTION="REPLACED" ACTIVE.DOC="32026R0449" ACTIVE.LOC="…"?>
  … <?CLG.MDFC IDREF="O…"?>. They are milestones: they may open in one element and close in another.

  The accumulator f:open holds the markers open at any node. From it:
  - a marker that is open before an element starts and still open after it ends COVERS the
    element: it becomes <provenance> on the unit or block made from it (or <deleted> around it);
  - a marker that opens or closes inside a block becomes an inline <changed> / <deleted> span,
    split at element boundaries into pieces that share the marker id.
  $ctx (tunnel) holds the ids of the markers already attributed to an enclosing unit or block.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:fn="http://www.w3.org/2005/xpath-functions"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all">

  <xsl:accumulator name="f:open" as="map(xs:string, processing-instruction())" initial-value="map{}">
    <xsl:accumulator-rule match="processing-instruction('CLG.MDFO')"
                          select="map:put($value, f:pi-attr(., 'ID'), .)"/>
    <xsl:accumulator-rule match="processing-instruction('CLG.MDFC')"
                          select="map:remove($value, f:pi-attr(., 'IDREF'))"/>
  </xsl:accumulator>

  <!-- the value of one pseudo-attribute in a processing instruction: ID="O1" or NO='4'. A large
       consolidation asks its markers millions of times, so each marker is parsed once: its
       attributes are the value f:marker-attributes takes at it, which Saxon keeps per node -->
  <xsl:function name="f:pi-attr" as="xs:string?">
    <xsl:param name="pi" as="processing-instruction()"/>
    <xsl:param name="name" as="xs:string"/>
    <xsl:sequence select="(if (name($pi) = ('CLG.MDFO', 'CLG.MDFC')) then $pi ! accumulator-before('f:marker-attributes')
                           else f:pi-attributes($pi))($name)"/>
  </xsl:function>

  <xsl:accumulator name="f:marker-attributes" as="map(xs:string, xs:string)" initial-value="map{}">
    <xsl:accumulator-rule match="processing-instruction('CLG.MDFO') | processing-instruction('CLG.MDFC')"
                          select="f:pi-attributes(.)"/>
  </xsl:accumulator>

  <!-- all the pseudo-attributes of a processing instruction, the first of each name -->
  <xsl:function name="f:pi-attributes" as="map(xs:string, xs:string)">
    <xsl:param name="pi" as="processing-instruction()"/>
    <xsl:variable name="m" as="element()" select="analyze-string(string($pi), '(^|\s)([^\s=]+)=([&quot;''])([^&quot;'']*)\3')"/>
    <xsl:sequence select="map:merge($m/fn:match ! map{string(fn:group[@nr = 2]): string(fn:group[@nr = 4])}, map{'duplicates': 'use-first'})"/>
  </xsl:function>

  <!-- markers covering $e (open before it starts, still open after it ends), outermost first -->
  <xsl:function name="f:covering" as="processing-instruction()*">
    <xsl:param name="e" as="node()"/>
    <xsl:variable name="before" as="map(xs:string, processing-instruction())" select="$e ! accumulator-before('f:open')"/>
    <xsl:variable name="after" as="map(xs:string, processing-instruction())" select="$e ! accumulator-after('f:open')"/>
    <xsl:sequence select="(map:keys($before)[map:contains($after, .)] ! $before(.))/self::node()"/>
  </xsl:function>

  <!-- markers covering the whole of a run of sibling nodes -->
  <xsl:function name="f:covering-run" as="processing-instruction()*">
    <xsl:param name="nodes" as="node()+"/>
    <xsl:variable name="before" as="map(xs:string, processing-instruction())" select="$nodes[1] ! accumulator-before('f:open')"/>
    <xsl:variable name="after" as="map(xs:string, processing-instruction())" select="$nodes[last()] ! accumulator-after('f:open')"/>
    <xsl:sequence select="(map:keys($before)[map:contains($after, .)] ! $before(.))/self::node()"/>
  </xsl:function>

  <!-- markers covering $e that aren't attributed yet: by the ids f:open keys them by -->
  <xsl:function name="f:new-markers" as="processing-instruction()*">
    <xsl:param name="e" as="node()"/>
    <xsl:param name="ctx" as="xs:string*"/>
    <xsl:variable name="before" as="map(xs:string, processing-instruction())" select="$e ! accumulator-before('f:open')"/>
    <xsl:variable name="after" as="map(xs:string, processing-instruction())" select="$e ! accumulator-after('f:open')"/>
    <xsl:sequence select="(map:keys($before)[map:contains($after, .)][not(. = $ctx)] ! $before(.))/self::node()"/>
  </xsl:function>

  <xsl:function name="f:ids" as="xs:string*">
    <xsl:param name="markers" as="processing-instruction()*"/>
    <xsl:sequence select="$markers ! f:pi-attr(., 'ID')"/>
  </xsl:function>

  <!-- the outermost marker that is open over $n and not yet attributed; '' if none -->
  <xsl:function name="f:outer-marker" as="xs:string">
    <xsl:param name="n" as="node()"/>
    <xsl:param name="ctx" as="xs:string*"/>
    <xsl:variable name="open" as="processing-instruction()*" select="
        if ($n instance of text())
        then (let $v := $n ! accumulator-before('f:open') return (map:keys($v)[not(. = $ctx)] ! $v(.))/self::node())
        else f:new-markers($n, $ctx)"/>
    <xsl:sequence select="(f:ids($open), '')[1]"/>
  </xsl:function>

  <xsl:function name="f:is-deletion" as="xs:boolean">
    <xsl:param name="marker" as="processing-instruction()"/>
    <xsl:sequence select="f:action($marker) = 'deleted'"/>
  </xsl:function>

  <!-- REPLACED, INSERTED, DELETED; older consolidations also write REPEALED (a deletion) and ADDED -->
  <xsl:function name="f:action" as="xs:string">
    <xsl:param name="marker" as="processing-instruction()"/>
    <xsl:variable name="action" as="xs:string?" select="f:pi-attr($marker, 'ACTION')"/>
    <xsl:variable name="known" as="xs:string?" select="map{'REPLACED': 'replaced', 'INSERTED': 'inserted', 'ADDED': 'inserted',
                                                         'DELETED': 'deleted', 'REPEALED': 'deleted'}($action)"/>
    <xsl:sequence select="if (exists($known)) then $known else f:unknown($marker, 'amendment marker ACTION=' || $action)"/>
  </xsl:function>

  <!-- a CELEX number; consolidations made with Formex 01.02 to 04.00 write the year with two
       digits (398D0441 for 31998D0441, 302R1534 for 32002R1534) -->
  <xsl:function name="f:celex" as="xs:string">
    <xsl:param name="celex" as="xs:string?"/>
    <xsl:variable name="c" as="xs:string" select="normalize-space($celex)"/>
    <xsl:sequence select="
        if (matches($c, '^[0-9][0-9]{2}[A-Z]'))
        then substring($c, 1, 1) || (if (xs:integer(substring($c, 2, 2)) gt 50) then '19' else '20') || substring($c, 2)
        else $c"/>
  </xsl:function>

  <xsl:function name="f:amendment-attributes" as="attribute()*">
    <xsl:param name="marker" as="processing-instruction()"/>
    <xsl:attribute name="amendingAct" select="f:celex(f:pi-attr($marker, 'ACTIVE.DOC'))"/>
    <xsl:attribute name="action" select="f:action($marker)"/>
    <xsl:if test="f:pi-attr($marker, 'ACTIVE.LOC')">
      <xsl:attribute name="location" select="f:pi-attr($marker, 'ACTIVE.LOC')"/>
    </xsl:if>
    <xsl:attribute name="marker" select="f:pi-attr($marker, 'ID')"/>
  </xsl:function>

  <!-- <provenance> for the markers that keep the content live -->
  <xsl:function name="f:provenance" as="element()*">
    <xsl:param name="markers" as="processing-instruction()*"/>
    <xsl:for-each select="$markers[not(f:is-deletion(.))]">
      <provenance>
        <xsl:sequence select="f:amendment-attributes(.)"/>
      </provenance>
    </xsl:for-each>
  </xsl:function>

  <!-- content an amendment deleted, but the consolidation keeps, goes inside <deleted> -->
  <xsl:function name="f:wrap-deleted" as="element()*">
    <xsl:param name="markers" as="processing-instruction()*"/>
    <xsl:param name="content" as="element()*"/>
    <xsl:choose>
      <xsl:when test="exists($content) and $markers[f:is-deletion(.)]">
        <deleted>
          <xsl:for-each select="$markers[f:is-deletion(.)]">
            <provenance>
              <xsl:sequence select="f:amendment-attributes(.)"/>
            </provenance>
          </xsl:for-each>
          <xsl:sequence select="$content"/>
        </deleted>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="$content"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

</xsl:stylesheet>
