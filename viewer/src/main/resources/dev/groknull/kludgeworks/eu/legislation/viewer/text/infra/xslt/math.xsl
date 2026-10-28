<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  Formulas → Presentation MathML. Formex FORMULA (in running text) and FORMULA.S (displayed) hold
  expressions built from:
    EXPR @TYPE           a group, with delimiters: BRACKET (), SQBRACKET [], BRACE {}, BAR ||, NONE
    IND, EXPONENT        subscript and superscript of what precedes them (before 05.50, IND LOC
                         also places it above or below, and an empty OVER is a fraction bar)
    OP.MATH, OP.CMP      empty milestones whose TYPE names the operator (PLUS, MULT, LE, AP …)
    FRACTION             DIVIDEND over DIVISOR
    SUM, PRODUCT, INTEGRAL  the sign, with OVER and UNDER as its limits
    ROOT, OVERLINE, VECTOR  root (DEGREE for an nth root), bar, arrow
    FUNCTION             the function sign ƒ, with OVER and UNDER as its limits
    BAR                  a single vertical bar
    FMT.VALUE            a formatted number
  Text is split into numbers (mn), names (mi) and other characters (mo).
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
    xmlns:f="https://kludgeworks.groknull.dev/ns/normalise"
    xmlns:m="http://www.w3.org/1998/Math/MathML"
    xmlns="https://kludgeworks.groknull.dev/ns/legal-text/1"
    exclude-result-prefixes="#all" expand-text="yes">

  <xsl:mode name="math" on-no-match="fail" on-multiple-match="fail"/>

  <!-- a formula in running text; TYPE="OUTLINE" is displayed on its own line -->
  <xsl:template match="FORMULA" mode="inline">
    <math>
      <xsl:sequence select="f:math-root(., if (@TYPE = 'OUTLINE') then 'block' else 'inline')"/>
    </math>
  </xsl:template>

  <!-- a displayed formula met inside running text (TXT) -->
  <xsl:template match="FORMULA.S" mode="inline">
    <math>
      <xsl:sequence select="f:math-root(., 'block')"/>
    </math>
  </xsl:template>

  <!-- a displayed formula -->
  <xsl:template match="FORMULA.S" mode="block">
    <math>
      <xsl:sequence select="f:math-root(., 'block')"/>
    </math>
  </xsl:template>

  <xsl:function name="f:math-root" as="element(m:math)">
    <xsl:param name="formula" as="element()"/>
    <xsl:param name="display" as="xs:string"/>
    <m:math display="{$display}">
      <m:mrow>
        <xsl:sequence select="f:math($formula/node())"/>
      </m:mrow>
    </m:math>
  </xsl:function>

  <!-- a sequence of nodes; IND, EXPONENT, a standalone OVER or UNDER, and HT SUB/SUP attach to the
       item before them. Before Formex 05.50, IND says where it goes (LOC="SUB|SUP|OVER|UNDER") and an
       empty OVER is a fraction bar between the item before and the item after it. -->
  <xsl:function name="f:math" as="element()*">
    <xsl:param name="nodes" as="node()*"/>
    <xsl:iterate select="$nodes[not(self::processing-instruction() or self::comment())]">
      <xsl:param name="acc" as="element()*" select="()"/>
      <xsl:param name="dividend" as="element()?" select="()"/>
      <xsl:on-completion select="$acc, $dividend"/>
      <xsl:variable name="script" as="xs:string" select="
          if (self::IND) then (map{'SUP': 'sup', 'OVER': 'over', 'UNDER': 'under'}(string(@LOC)), 'sub')[1]
          else if (self::HT[@TYPE = 'SUB']) then 'sub'
          else if (self::EXPONENT or self::HT[@TYPE = 'SUP']) then 'sup'
          else if (self::OVER[node()]) then 'over'
          else if (self::UNDER) then 'under'
          else ''"/>
      <xsl:choose>
        <xsl:when test="self::OVER[not(node())]">
          <xsl:next-iteration>
            <xsl:with-param name="acc" select="$acc[position() lt last()]"/>
            <xsl:with-param name="dividend" select="($acc[last()], f:empty-row())[1]"/>
          </xsl:next-iteration>
        </xsl:when>
        <xsl:when test="self::text()[not(normalize-space())] and $dividend">
          <xsl:next-iteration/>
        </xsl:when>
        <xsl:when test="$script != ''">
          <xsl:variable name="base" as="element()" select="($acc[last()], f:empty-row())[1]"/>
          <xsl:variable name="script-row" as="element()">
            <m:mrow>
              <xsl:sequence select="f:math(node())"/>
            </m:mrow>
          </xsl:variable>
          <xsl:next-iteration>
            <xsl:with-param name="acc" select="$acc[position() lt last()], f:attach($base, $script, $script-row)"/>
          </xsl:next-iteration>
        </xsl:when>
        <xsl:otherwise>
          <xsl:variable name="items" as="element()*">
            <xsl:apply-templates select="." mode="math"/>
          </xsl:variable>
          <xsl:next-iteration>
            <xsl:with-param name="acc" as="element()*">
              <xsl:sequence select="$acc"/>
              <xsl:choose>
                <xsl:when test="$dividend and exists($items)">
                  <m:mfrac><xsl:sequence select="$dividend, $items[1]"/></m:mfrac>
                  <xsl:sequence select="$items[position() gt 1]"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:sequence select="$items"/>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:with-param>
            <xsl:with-param name="dividend" select="if (exists($items)) then () else $dividend"/>
          </xsl:next-iteration>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:iterate>
  </xsl:function>

  <xsl:function name="f:empty-row" as="element(m:mrow)">
    <m:mrow/>
  </xsl:function>

  <!-- base + script; a subscript and a superscript become msubsup, limits below and above munderover -->
  <xsl:function name="f:attach" as="element()">
    <xsl:param name="base" as="element()"/>
    <xsl:param name="script" as="xs:string"/>
    <xsl:param name="row" as="element()"/>
    <xsl:choose>
      <xsl:when test="$script = 'sup' and $base/self::m:msub">
        <m:msubsup>
          <xsl:sequence select="$base/*, $row"/>
        </m:msubsup>
      </xsl:when>
      <xsl:when test="$script = 'over' and $base/self::m:munder">
        <m:munderover>
          <xsl:sequence select="$base/*, $row"/>
        </m:munderover>
      </xsl:when>
      <xsl:when test="$script = 'under' and $base/self::m:mover[not(@accent)]">
        <m:munderover>
          <xsl:sequence select="$base/*[1], $row, $base/*[2]"/>
        </m:munderover>
      </xsl:when>
      <xsl:otherwise>
        <xsl:element name="{map{'sub': 'msub', 'sup': 'msup', 'over': 'mover', 'under': 'munder'}($script)}"
                     namespace="http://www.w3.org/1998/Math/MathML">
          <xsl:sequence select="$base, $row"/>
        </xsl:element>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <!-- ============================================================ tokens -->

  <xsl:template match="text()" mode="math">
    <xsl:analyze-string select="." regex="(\d+(?:[.,]\d+)*)|(\p{{L}}+)|(\s+)|(.)">
      <xsl:matching-substring>
        <xsl:choose>
          <xsl:when test="regex-group(1)"><m:mn>{.}</m:mn></xsl:when>
          <xsl:when test="regex-group(2)"><m:mi>{.}</m:mi></xsl:when>
          <xsl:when test="regex-group(3)"/>
          <xsl:otherwise><m:mo>{.}</m:mo></xsl:otherwise>
        </xsl:choose>
      </xsl:matching-substring>
    </xsl:analyze-string>
  </xsl:template>

  <xsl:template match="FMT.VALUE" mode="math">
    <m:mn>{normalize-space(.)}</m:mn>
  </xsl:template>

  <!-- the signs the Formex manual gives for each TYPE -->
  <xsl:variable name="f:operators" as="map(xs:string, xs:string)" select="map{
      'PLUS': '+', 'MINUS': '−', 'MULT': '∙', 'DIV': '/', 'PLUSMINUS': '±', 'CARTPROD': '×',
      'EQ': '=', 'NE': '≠', 'LT': '&lt;', 'GT': '&gt;', 'LE': '≤', 'GE': '≥', 'AP': '≈', 'EQV': '≡'}"/>

  <xsl:template match="OP.MATH | OP.CMP" mode="math">
    <xsl:variable name="op" as="xs:string?" select="$f:operators(string(@TYPE))"/>
    <xsl:choose>
      <xsl:when test="$op"><m:mo>{$op}</m:mo></xsl:when>
      <xsl:otherwise><xsl:sequence select="f:unknown(., local-name() || ' TYPE=' || @TYPE)"/></xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- typefaces inside a formula: bold and italic as mathvariant on the tokens -->
  <xsl:template match="HT" mode="math">
    <xsl:variable name="variant" as="xs:string?" select="map{'BOLD': 'bold', 'ITALIC': 'italic'}(string(@TYPE))"/>
    <xsl:for-each select="f:math(node())">
      <xsl:choose>
        <xsl:when test="$variant and (self::m:mi or self::m:mn or self::m:mo)">
          <xsl:copy>
            <xsl:attribute name="mathvariant" select="$variant"/>
            <xsl:sequence select="@*, node()"/>
          </xsl:copy>
        </xsl:when>
        <xsl:otherwise>
          <xsl:sequence select="."/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
  </xsl:template>

  <xsl:template match="FT" mode="math">
    <xsl:sequence select="f:math(node())"/>
  </xsl:template>

  <xsl:template match="QUOT.START | QUOT.END" mode="math">
    <m:mo>{f:quote-char(.)}</m:mo>
  </xsl:template>

  <!-- a footnote reference inside a formula: kept as text, the note itself is collected as usual -->
  <xsl:template match="NOTE" mode="math">
    <m:msup><m:mrow/><m:mtext>({f:note-marker(if (@NOTE.REF) then root(.)//NOTE[@NOTE.ID = current()/@NOTE.REF][1] else .)})</m:mtext></m:msup>
  </xsl:template>

  <!-- ============================================================ structures -->

  <xsl:variable name="f:fences" as="map(xs:string, xs:string+)" select="map{
      'BRACKET': ('(', ')'), 'SQBRACKET': ('[', ']'), 'BRACE': ('{', '}'), 'BAR': ('|', '|')}"/>

  <xsl:template match="EXPR" mode="math">
    <xsl:variable name="fence" as="xs:string*" select="$f:fences(string(@TYPE))"/>
    <m:mrow>
      <xsl:if test="exists($fence)"><m:mo fence="true">{$fence[1]}</m:mo></xsl:if>
      <xsl:sequence select="f:math(node())"/>
      <xsl:if test="exists($fence)"><m:mo fence="true">{$fence[2]}</m:mo></xsl:if>
    </m:mrow>
  </xsl:template>

  <xsl:template match="FRACTION" mode="math">
    <m:mfrac>
      <m:mrow><xsl:sequence select="f:math(DIVIDEND/node())"/></m:mrow>
      <m:mrow><xsl:sequence select="f:math(DIVISOR/node())"/></m:mrow>
    </m:mfrac>
  </xsl:template>

  <!-- ∑ ∏ ∫ with their limits -->
  <!-- a single vertical bar (absolute values are EXPR TYPE="BAR") -->
  <xsl:template match="BAR" mode="math">
    <m:mo>|</m:mo>
  </xsl:template>

  <!-- ∑ ∏ ∫ and the function sign ƒ, with their limits -->
  <xsl:template match="SUM | PRODUCT | INTEGRAL | FUNCTION" mode="math">
    <xsl:variable name="sign" as="element()"><xsl:choose>
      <xsl:when test="self::FUNCTION"><m:mi>ƒ</m:mi></xsl:when>
      <xsl:otherwise><m:mo largeop="true">{map{'SUM': '∑', 'PRODUCT': '∏', 'INTEGRAL': '∫'}(local-name())}</m:mo></xsl:otherwise>
    </xsl:choose></xsl:variable>
    <xsl:variable name="under" as="element()"><m:mrow><xsl:sequence select="f:math(UNDER/node())"/></m:mrow></xsl:variable>
    <xsl:variable name="over" as="element()"><m:mrow><xsl:sequence select="f:math(OVER/node())"/></m:mrow></xsl:variable>
    <xsl:choose>
      <xsl:when test="UNDER and OVER"><m:munderover><xsl:sequence select="$sign, $under, $over"/></m:munderover></xsl:when>
      <xsl:when test="UNDER"><m:munder><xsl:sequence select="$sign, $under"/></m:munder></xsl:when>
      <xsl:when test="OVER"><m:mover><xsl:sequence select="$sign, $over"/></m:mover></xsl:when>
      <xsl:otherwise><xsl:sequence select="$sign"/></xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- a square root, or with DEGREE (05.50+) an nth root -->
  <xsl:template match="ROOT" mode="math">
    <xsl:choose>
      <xsl:when test="DEGREE">
        <m:mroot>
          <m:mrow><xsl:sequence select="f:math(node() except DEGREE)"/></m:mrow>
          <m:mrow><xsl:sequence select="f:math(DEGREE/node())"/></m:mrow>
        </m:mroot>
      </xsl:when>
      <xsl:otherwise>
        <m:msqrt><xsl:sequence select="f:math(node())"/></m:msqrt>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="OVERLINE" mode="math">
    <m:mover accent="true"><m:mrow><xsl:sequence select="f:math(node())"/></m:mrow><m:mo>¯</m:mo></m:mover>
  </xsl:template>

  <xsl:template match="VECTOR" mode="math">
    <m:mover accent="true"><m:mrow><xsl:sequence select="f:math(node())"/></m:mrow><m:mo>→</m:mo></m:mover>
  </xsl:template>

  <xsl:template match="*" mode="math" priority="-1">
    <xsl:sequence select="f:unknown(., 'in a formula')"/>
  </xsl:template>

</xsl:stylesheet>
