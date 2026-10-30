<?xml version="1.0" encoding="UTF-8"?>
<!-- SPDX-License-Identifier: MIT -->
<!--
  eIds, AKN4EU style: segments joined by "/", each an abbreviation plus the unit's own label,
  e.g. art_3/par_2/pnt_a, anx/pnt_1.2.2.6, pbl_1/rct_12. Labels are taken literally: brackets and
  trailing dots are dropped, whitespace becomes "-", nothing is interpreted. Units without a label
  are numbered by position (unp_2, idt_3, level_1). Letters and digits of any script are kept, so that
  labels that differ in print differ here: "ANNEX ΙII", with a Greek capital iota, isn't anx_II.
-->
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="https://kludgeworks.groknull.dev/ns/formex-converter"
    xmlns:array="http://www.w3.org/2005/xpath-functions/array"
    exclude-result-prefixes="#all">

  <!-- a visible label as an eId value: "(a)" → "a", "1.0.6." → "1.0.6", "10 a" → "10-a"; marks with no
       letter or digit ("–", "-", ".") are no label -->
  <xsl:function name="f:label" as="xs:string">
    <xsl:param name="label" as="xs:string?"/>
    <xsl:variable name="value" as="xs:string" select="
        f:spaces($label)
        => replace('^[\(\[]+|[\)\]]+$', '')
        => replace('\.+$', '')
        => replace('\s+', '-')
        => replace('[^\p{L}\p{N}._\-]', '')"/>
    <xsl:sequence select="if (matches($value, '[\p{L}\p{N}]')) then $value else ''"/>
  </xsl:function>

  <!-- the label after a leading type word: "Article 3" → "3", "ANNEX II" → "II", "PART A" → "A",
       "ANNEX" → ""; also when print left out the space: "Article13" → "13", "Article1d" → "1d". The
       first word is a type word only when a number follows; a heading without one is its own label,
       whole: "Budget of the measure", "IFRIC INTERPRETATION 7" -->
  <xsl:function name="f:label-after-word" as="xs:string">
    <xsl:param name="label" as="xs:string?"/>
    <!-- a quoted title opens with its quotation mark: ‘ANNEX I -->
    <xsl:variable name="s" as="xs:string" select="replace(f:spaces($label), '^[^\p{L}\p{N}]+', '')"/>
    <xsl:variable name="rest" as="xs:string" select="substring-after($s, ' ')"/>
    <xsl:sequence select="f:label(
        if ($rest = '') then (if (matches($s, '^\p{L}+\p{N}')) then replace($s, '^\p{L}+', '') else '')
        else if (matches($s, '^\p{L}+ ') and f:number-like($rest)) then $rest
        else $s)"/>
  </xsl:function>

  <!-- a number as labels print it: with a digit (3, 1d, 12.6.2), a Roman numeral (IV, IIa), or a
       single letter (A, a); not a word in capitals (SCHEMES, CF) -->
  <xsl:function name="f:number-like" as="xs:boolean">
    <xsl:param name="s" as="xs:string?"/>
    <xsl:sequence select="matches(string($s), '^(\S*\p{N}\S*|[IVXLCDM]+\p{Ll}?|\p{L})$')"/>
  </xsl:function>

  <!-- The kinds of division and annex, from the EU subdivision authority table (subdivision.rdf):
       their codes name the eIds (TITLE I → tis_I, CHAPTER 2 → cpt_2, Appendix III → app_III), the
       same in every language, as the table gives each kind's name in all 24. -->
  <xsl:variable name="f:subdivisions" as="document-node()" select="doc('subdivision.rdf')"/>

  <!-- the kind of subdivision a heading names, of those in $codes, in the document's language, else
       in another (a text printed in two: TITRE II in an English document), and the rest of the
       heading: ("CPT", "III") for "CHAPTER III"; empty where its first words name none (a number
       printed first, I. FEJEZET, or no type word at all) -->
  <xsl:function name="f:subdivision" as="xs:string*">
    <xsl:param name="heading" as="xs:string?"/>
    <xsl:param name="codes" as="xs:string+"/>
    <xsl:param name="context" as="node()"/>
    <xsl:variable name="lang" as="xs:string?" select="lower-case((root($context)//LG.DOC)[1])"/>
    <xsl:variable name="words" as="xs:string*" select="tokenize(replace(f:spaces($heading), '^[^\p{L}\p{N}]+', ''), ' ')"/>
    <xsl:sequence select="(
        for $concept in $f:subdivisions//*[skos:notation = $codes],
            $name in ($concept/skos:prefLabel[lang($lang)], $concept/skos:prefLabel[not(lang($lang))])
        return
          let $type := tokenize(normalize-space(replace($name, '\(.*\)', '')), ' '),
              $n := count($type)
          return
            if (count($words) ge $n and (every $i in 1 to $n satisfies f:type-word($words[$i]) = f:type-word($type[$i])))
            then array { string($concept/skos:notation), string-join(subsequence($words, $n + 1), ' ') }
            else ()
      )[1] ! array:flatten(.)"
        xmlns:skos="http://www.w3.org/2004/02/skos/core#"/>
  </xsl:function>

  <!-- the label of a heading whose first words may name its kind: the number after the name, where
       they name one of $codes in the document's language ("Appendix 1" → 1, "ANNEX II A" → II-A); else the whole heading
       ("Attachment 1", "BY 3": a first word that names no such kind is part of the label), or none for
       a single word. Without a document language, the word before a number is taken as the name. -->
  <xsl:function name="f:typed-label" as="xs:string">
    <xsl:param name="heading" as="xs:string?"/>
    <xsl:param name="codes" as="xs:string+"/>
    <xsl:param name="context" as="node()"/>
    <xsl:variable name="type" as="xs:string*" select="f:subdivision($heading, $codes, $context)"/>
    <xsl:sequence select="
        if (empty((root($context)//LG.DOC)[1])) then f:label-after-word($heading)
        else if (exists($type)) then f:label($type[2])
        else if (contains(f:spaces($heading), ' ')) then f:label($heading)
        else ''"/>
  </xsl:function>

  <!-- a type word as compared: case and hyphens aside (SUB-SECTION, subsection) -->
  <xsl:function name="f:type-word" as="xs:string">
    <xsl:param name="word" as="xs:string"/>
    <xsl:sequence select="lower-case(replace($word, '[\-‐‑]', ''))"/>
  </xsl:function>

  <!-- whitespace normalised, non-breaking spaces included ("Article&#160;1") -->
  <xsl:function name="f:spaces" as="xs:string">
    <xsl:param name="s" as="xs:string?"/>
    <xsl:sequence select="normalize-space(replace($s, '\p{Zs}', ' '))"/>
  </xsl:function>

  <!-- base/abbr_value; without a base the segment stands alone -->
  <xsl:function name="f:eid" as="xs:string">
    <xsl:param name="base" as="xs:string?"/>
    <xsl:param name="abbr" as="xs:string"/>
    <xsl:param name="value" as="xs:string?"/>
    <xsl:variable name="segment" as="xs:string" select="if ($value) then $abbr || '_' || $value else $abbr"/>
    <xsl:sequence select="if ($base) then $base || '/' || $segment else $segment"/>
  </xsl:function>

</xsl:stylesheet>
