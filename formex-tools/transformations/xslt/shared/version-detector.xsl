<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fmx="http://formex.publications.europa.eu/schema/formex"
    exclude-result-prefixes="fmx">
    
    <!-- 
        Formex Version Detection Utility
        
        This stylesheet provides functions to detect and convert between
        full Formex schema versions and simplified versions.
        
        Usage: Import this stylesheet and use the version detection functions
    -->
    
    <!-- Function to extract version from schema location -->
    <xsl:function name="fmx:detect-version-from-schema" as="xs:string?">
        <xsl:param name="schema-location" as="xs:string?"/>
        
        <xsl:if test="$schema-location">
            <xsl:analyze-string select="$schema-location" regex="formex-([^\.]+\.[^-]*)-.*\.xd">
                <xsl:matching-substring>
                    <xsl:value-of select="regex-group(1)"/>
                </xsl:matching-substring>
            </xsl:analyze-string>
        </xsl:if>
    </xsl:function>
    
    <!-- Function to convert full version to simple version -->
    <xsl:function name="fmx:full-to-simple-version" as="xs:string?">
        <xsl:param name="full-version" as="xs:string"/>
        
        <xsl:choose>
            <xsl:when test="$full-version = '01.0020040501'">01.00</xsl:when>
            <xsl:when test="$full-version = '01.0120040501'">01.01</xsl:when>
            <xsl:when test="$full-version = '01.0220040901'">01.02</xsl:when>
            <xsl:when test="$full-version = '02.0020050101'">02.00</xsl:when>
            <xsl:when test="$full-version = '03.0020051001'">03.00</xsl:when>
            <xsl:when test="$full-version = '04.0020061031'">04.00</xsl:when>
            <xsl:when test="$full-version = '04.cj20080212'">04.cj</xsl:when>
            <xsl:when test="$full-version = '05.0020081231'">05.00</xsl:when>
            <xsl:when test="$full-version = '05.0120091201'">05.01</xsl:when>
            <xsl:when test="$full-version = '05.1020100101'">05.10</xsl:when>
            <xsl:when test="$full-version = '05.1120100101'">05.11</xsl:when>
            <xsl:when test="$full-version = '05.2020100601'">05.20</xsl:when>
            <xsl:when test="$full-version = '05.2120110601'">05.21</xsl:when>
            <xsl:when test="$full-version = '05.2220110811'">05.22</xsl:when>
            <xsl:when test="$full-version = '05.2320111201'">05.23</xsl:when>
            <xsl:when test="$full-version = '05.2420121231'">05.24</xsl:when>
            <xsl:when test="$full-version = '05.3020130101'">05.30</xsl:when>
            <xsl:when test="$full-version = '05.5120130501'">05.51</xsl:when>
            <xsl:when test="$full-version = '05.5220130701'">05.52</xsl:when>
            <xsl:when test="$full-version = '05.5320130725'">05.53</xsl:when>
            <xsl:when test="$full-version = '05.53l20140201'">05.53l</xsl:when>
            <xsl:when test="$full-version = '05.5420140601'">05.54</xsl:when>
            <xsl:when test="$full-version = '05.5520141201'">05.55</xsl:when>
            <xsl:when test="$full-version = '05.5620160701'">05.56</xsl:when>
            <xsl:when test="$full-version = '05.5720161001'">05.57</xsl:when>
            <xsl:when test="$full-version = '05.5820161101'">05.58</xsl:when>
            <xsl:when test="$full-version = '05.5920170418'">05.59</xsl:when>
            <xsl:when test="$full-version = '06.0020210715'">06.00</xsl:when>
            <xsl:when test="$full-version = '06.0120220520'">06.01</xsl:when>
            <xsl:when test="$full-version = '06.0220230206'">06.02</xsl:when>
            <xsl:when test="$full-version = '06.02.120231031'">06.02.1</xsl:when>
            <xsl:when test="$full-version = '06.02.220241118'">06.02.2</xsl:when>
            <xsl:when test="$full-version = '06.02.320250123'">06.02.3</xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$full-version"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
    <!-- Function to get ordered version sequence -->
    <xsl:function name="fmx:get-version-sequence" as="xs:string*">
        <xsl:sequence select="(
            '01.00', '01.01', '01.02', '02.00', '03.00', '04.00', '04.cj', '05.00',
            '05.01', '05.10', '05.11', '05.20', '05.21', '05.22', '05.23', '05.24',
            '05.30', '05.51', '05.52', '05.53', '05.53l', '05.54', '05.55', '05.56',
            '05.57', '05.58', '05.59', '06.00', '06.01', '06.02', '06.02.1', '06.02.2', '06.02.3'
        )"/>
    </xsl:function>
    
    <!-- Function to get transformation path from source to target version -->
    <xsl:function name="fmx:get-transformation-path" as="xs:string*">
        <xsl:param name="from-version" as="xs:string"/>
        <xsl:param name="to-version" as="xs:string"/>
        
        <xsl:variable name="sequence" select="fmx:get-version-sequence()"/>
        <xsl:variable name="from-index" select="index-of($sequence, $from-version)[1]"/>
        <xsl:variable name="to-index" select="index-of($sequence, $to-version)[1]"/>
        
        <xsl:if test="$from-index and $to-index and $from-index lt $to-index">
            <xsl:sequence select="subsequence($sequence, $from-index + 1, $to-index - $from-index)"/>
        </xsl:if>
    </xsl:function>
    
    <!-- Function to validate if transformation path exists -->
    <xsl:function name="fmx:can-transform" as="xs:boolean">
        <xsl:param name="from-version" as="xs:string"/>
        <xsl:param name="to-version" as="xs:string"/>
        
        <xsl:variable name="sequence" select="fmx:get-version-sequence()"/>
        <xsl:variable name="from-index" select="index-of($sequence, $from-version)[1]"/>
        <xsl:variable name="to-index" select="index-of($sequence, $to-version)[1]"/>
        
        <xsl:value-of select="$from-index and $to-index and $from-index le $to-index"/>
    </xsl:function>
    
    <!-- Function to detect root element type -->
    <xsl:function name="fmx:detect-root-element" as="xs:string?">
        <xsl:param name="doc" as="document-node()"/>
        
        <xsl:variable name="root-name" select="local-name($doc/*)"/>
        
        <xsl:choose>
            <xsl:when test="$root-name = 'DOC'">doc</xsl:when>
            <xsl:when test="$root-name = 'GENERAL'">general</xsl:when>
            <xsl:when test="$root-name = 'ANNEX'">annex</xsl:when>
            <xsl:when test="$root-name = 'ACT'">act</xsl:when>
            <xsl:when test="$root-name = 'CJT'">cjt</xsl:when>
            <xsl:when test="$root-name = 'CONS.ACT'">cons-act</xsl:when>
            <xsl:when test="$root-name = 'PUBLICATION'">publication</xsl:when>
            <xsl:when test="$root-name = 'JUDGEMENT'">judgement</xsl:when>
            <xsl:when test="$root-name = 'CASE'">case</xsl:when>
            <xsl:when test="$root-name = 'CONCLUSION'">conclusion</xsl:when>
            <xsl:when test="$root-name = 'JUDGEMENT.NP'">judgement-np</xsl:when>
            <xsl:when test="$root-name = 'CORR'">corr</xsl:when>
            <xsl:when test="$root-name = 'LSEU'">lseu</xsl:when>
            <xsl:when test="$root-name = 'AGR'">agr</xsl:when>
            <xsl:when test="$root-name = 'ORDER'">order</xsl:when>
            <xsl:when test="$root-name = 'COMPETITION'">competition</xsl:when>
            <xsl:when test="$root-name = 'REPORT.HEARING'">report-hearing</xsl:when>
            <xsl:when test="$root-name = 'ORDER.NP'">order-np</xsl:when>
            <xsl:when test="$root-name = 'CONS.OTH'">cons-oth</xsl:when>
            <xsl:when test="$root-name = 'SUMMARY.JUDGEMENT'">summary-judgement</xsl:when>
            <xsl:when test="$root-name = 'OPINION'">opinion</xsl:when>
            <xsl:when test="$root-name = 'DECISION.ECR'">decision-ecr</xsl:when>
            <xsl:when test="$root-name = 'SUMMARY.PDF'">summary-pdf</xsl:when>
            <xsl:when test="$root-name = 'RULING'">ruling</xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="lower-case($root-name)"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
</xsl:stylesheet>