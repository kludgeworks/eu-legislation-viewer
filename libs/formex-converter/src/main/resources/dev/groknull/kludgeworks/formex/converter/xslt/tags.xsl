<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Tags. A consolidation tags each amended passage with a pair of processing
  instructions, <?CLG.MDFO ID="O…" ACTION="REPLACED" ACTIVE.DOC="32026R0449" ACTIVE.LOC="…"?>
  … <?CLG.MDFC IDREF="O…"?>. They are milestones: they may open in one element and close in another.

  The accumulator f:open holds the tags open at any node. From it:
  - a tag that is open before an element starts and still open after it ends COVERS the
    element: it becomes <tag> on the occurrence or block made from it (or <deleted> around it);
  - a tag that opens or closes inside a block becomes an inline <changed> / <deleted> span,
    split at element boundaries into pieces that share the tag id.
  $ctx (tunnel) holds the ids of the tags already attributed to an enclosing occurrence or block.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:fn="http://www.w3.org/2005/xpath-functions"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns:lt="https://kludgeworks.groknull.dev/ns/legal-text/1"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all">

  <xsl:accumulator name="f:open" as="map(xs:string, processing-instruction())" initial-value="map{}">
    <xsl:accumulator-rule match="processing-instruction('CLG.MDFO')"
                          select="map:put($value, f:pi-attr(., 'ID'), .)"/>
    <xsl:accumulator-rule match="processing-instruction('CLG.MDFC')"
                          select="map:remove($value, f:pi-attr(., 'IDREF'))"/>
  </xsl:accumulator>

  <!-- the value of one pseudo-attribute in a processing instruction: ID="O1" or NO='4'. A large
       consolidation asks its tags millions of times, so each tag is parsed once: its
       attributes are the value f:tag-attributes takes at it, which Saxon keeps per node -->
  <xsl:function name="f:pi-attr" as="xs:string?">
    <xsl:param name="pi" as="processing-instruction()"/>
    <xsl:param name="name" as="xs:string"/>
    <xsl:sequence select="(if (name($pi) = ('CLG.MDFO', 'CLG.MDFC')) then $pi ! accumulator-before('f:tag-attributes')
                           else f:pi-attributes($pi))($name)"/>
  </xsl:function>

  <xsl:accumulator name="f:tag-attributes" as="map(xs:string, xs:string)" initial-value="map{}">
    <xsl:accumulator-rule match="processing-instruction('CLG.MDFO') | processing-instruction('CLG.MDFC')"
                          select="f:pi-attributes(.)"/>
  </xsl:accumulator>

  <!-- all the pseudo-attributes of a processing instruction, the first of each name -->
  <xsl:function name="f:pi-attributes" as="map(xs:string, xs:string)">
    <xsl:param name="pi" as="processing-instruction()"/>
    <xsl:variable name="m" as="element()" select="analyze-string(string($pi), '([^\s=&quot;'']+)=([&quot;''])([^&quot;'']*)\2')"/>
    <xsl:sequence select="map:merge($m/fn:match ! map{string(fn:group[@nr = 1]): string(fn:group[@nr = 3])}, map{'duplicates': 'use-first'})"/>
  </xsl:function>

  <!-- tags covering $e (open before it starts, still open after it ends), outermost first -->
  <xsl:function name="f:covering" as="processing-instruction()*">
    <xsl:param name="e" as="node()"/>
    <xsl:variable name="before" as="map(xs:string, processing-instruction())" select="$e ! accumulator-before('f:open')"/>
    <xsl:variable name="after" as="map(xs:string, processing-instruction())" select="$e ! accumulator-after('f:open')"/>
    <xsl:sequence select="(map:keys($before)[map:contains($after, .)] ! $before(.))/self::node()"/>
  </xsl:function>

  <!-- tags covering the whole of a run of nodes, from its first node that isn't whitespace to its
       last: whitespace says nothing of an amendment, and a run may end in whitespace from elsewhere
       (<ITEM><?CLG.MDFO?> <NP>…<TXT>… acquiree).<?CLG.MDFC?> However, …</TXT></NP></ITEM>: the
       ITEM's space, still inside the tag, after its text) -->
  <xsl:function name="f:covering-run" as="processing-instruction()*">
    <xsl:param name="nodes" as="node()+"/>
    <xsl:variable name="ends" as="node()+" select="let $text := $nodes[not(self::text()[not(normalize-space())])]
                                                    return if (exists($text)) then ($text[1], $text[last()]) else ($nodes[1], $nodes[last()])"/>
    <xsl:variable name="before" as="map(xs:string, processing-instruction())" select="$ends[1] ! accumulator-before('f:open')"/>
    <xsl:variable name="after" as="map(xs:string, processing-instruction())" select="$ends[last()] ! accumulator-after('f:open')"/>
    <xsl:sequence select="(map:keys($before)[map:contains($after, .)] ! $before(.))/self::node()"/>
  </xsl:function>

  <!-- tags covering $e that aren't attributed yet: by the ids f:open keys them by -->
  <xsl:function name="f:new-tags" as="processing-instruction()*">
    <xsl:param name="e" as="node()"/>
    <xsl:param name="ctx" as="xs:string*"/>
    <xsl:variable name="before" as="map(xs:string, processing-instruction())" select="$e ! accumulator-before('f:open')"/>
    <xsl:variable name="after" as="map(xs:string, processing-instruction())" select="$e ! accumulator-after('f:open')"/>
    <xsl:sequence select="(map:keys($before)[map:contains($after, .)][not(. = $ctx)] ! $before(.))/self::node()"/>
  </xsl:function>

  <xsl:function name="f:ids" as="xs:string*">
    <xsl:param name="tags" as="processing-instruction()*"/>
    <xsl:sequence select="$tags ! f:pi-attr(., 'ID')"/>
  </xsl:function>

  <!-- the outermost tag that is open over $n and not yet attributed; '' if none -->
  <xsl:function name="f:outer-tag" as="xs:string">
    <xsl:param name="n" as="node()"/>
    <xsl:param name="ctx" as="xs:string*"/>
    <xsl:variable name="open" as="processing-instruction()*" select="
        if ($n instance of text())
        then (let $v := $n ! accumulator-before('f:open') return (map:keys($v)[not(. = $ctx)] ! $v(.))/self::node())
        else f:new-tags($n, $ctx)"/>
    <xsl:sequence select="(f:ids($open), '')[1]"/>
  </xsl:function>

  <xsl:function name="f:is-deletion" as="xs:boolean">
    <xsl:param name="tag" as="processing-instruction()"/>
    <xsl:sequence select="f:action($tag) = 'deleted'"/>
  </xsl:function>

  <!-- REPLACED, INSERTED, DELETED, RENUMBERED; older consolidations also write REPEALED (a deletion) and
       ADDED -->
  <xsl:function name="f:action" as="xs:string">
    <xsl:param name="tag" as="processing-instruction()"/>
    <xsl:variable name="action" as="xs:string?" select="f:pi-attr($tag, 'ACTION')"/>
    <xsl:variable name="known" as="xs:string?" select="if (exists($action)) then map{'REPLACED': 'replaced', 'INSERTED': 'inserted', 'ADDED': 'inserted',
                                                         'DELETED': 'deleted', 'REPEALED': 'deleted', 'RENUMBERED': 'renumbered'}($action) else ()"/>
    <xsl:sequence select="if (exists($known)) then $known else f:unknown($tag, 'tag ACTION=' || $action)"/>
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
    <xsl:param name="tag" as="processing-instruction()"/>
    <xsl:attribute name="amendingAct" select="f:celex(f:pi-attr($tag, 'ACTIVE.DOC'))"/>
    <xsl:attribute name="action" select="f:action($tag)"/>
    <xsl:if test="f:pi-attr($tag, 'ACTIVE.LOC')">
      <xsl:attribute name="location" select="f:pi-attr($tag, 'ACTIVE.LOC')"/>
    </xsl:if>
    <xsl:attribute name="tagId" select="f:pi-attr($tag, 'ID')"/>
  </xsl:function>

  <!-- <tag> for the tags that keep the content live -->
  <xsl:function name="f:tags" as="element()*">
    <xsl:param name="tags" as="processing-instruction()*"/>
    <xsl:for-each select="$tags[not(f:is-deletion(.))]">
      <tag>
        <xsl:sequence select="f:amendment-attributes(.)"/>
      </tag>
    </xsl:for-each>
  </xsl:function>

  <!-- the deletions open over $n: it is in text an amendment deleted (a footnote in it is in force no
       longer, nor numbered) -->
  <xsl:function name="f:deletions" as="processing-instruction()*">
    <xsl:param name="n" as="node()"/>
    <xsl:variable name="open" as="map(xs:string, processing-instruction())" select="$n ! accumulator-before('f:open')"/>
    <xsl:sequence select="(map:keys($open) ! $open(.))[f:is-deletion(.)]/self::node()"/>
  </xsl:function>

  <!-- content an amendment deleted, but the consolidation keeps, goes inside <deleted> -->
  <xsl:function name="f:wrap-deleted" as="element()*">
    <xsl:param name="tags" as="processing-instruction()*"/>
    <xsl:param name="content" as="element()*"/>
    <xsl:choose>
      <xsl:when test="exists($content) and $tags[f:is-deletion(.)]">
        <deleted>
          <xsl:for-each select="$tags[f:is-deletion(.)]">
            <tag>
              <xsl:sequence select="f:amendment-attributes(.)"/>
            </tag>
          </xsl:for-each>
          <xsl:sequence select="$content"/>
        </deleted>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="$content"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <!-- One deletion, one <deleted>. The occurrences of a passage an amendment deleted are wrapped one by
       one, as they are made; here a run of them, side by side, deleted by the same tags, becomes
       one, as EUR-Lex shows one tag for it (Attachment 6-D of 02015R1998-20260322: 13 occurrences, one
       deletion by 32022R1174). Deleted words in running text are left as they are. -->
  <xsl:mode name="f:deletions" on-no-match="shallow-copy" on-multiple-match="fail"/>

  <xsl:template match="*[lt:deleted/lt:tag]" mode="f:deletions">
    <xsl:copy>
      <xsl:apply-templates select="@*" mode="#current"/>
      <xsl:for-each-group select="node()" group-adjacent="if (self::lt:deleted/lt:tag) then lt:tag/@tagId else generate-id()" composite="yes">
        <xsl:choose>
          <xsl:when test="self::lt:deleted/lt:tag">
            <xsl:copy>
              <xsl:apply-templates select="@*, lt:tag, current-group()/(node() except lt:tag)" mode="#current"/>
            </xsl:copy>
          </xsl:when>
          <xsl:otherwise>
            <xsl:apply-templates select="current-group()" mode="#current"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:for-each-group>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
