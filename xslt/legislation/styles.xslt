<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Template to generate all CSS styles -->
    <xsl:template name="generate-styles">
        <style>
            body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            line-height: 1.5;
            }
            .title {
            text-align: center;
            margin-bottom: 30px;
            }
            .consideration {
            margin-left: 40px;
            margin-bottom: 20px;
            }
            .consid-number {
            float: left;
            margin-left: -40px;
            }
            .article {
            margin-bottom: 20px;
            }
            .article-title {
            font-style: italic;
            text-align: center;
            margin-bottom: 10px;
            }
            /* Text formatting styles for all HT TYPE values */
            .italic {
            font-style: italic;
            }
            .bold {
            font-weight: bold;
            }
            .uppercase {
            text-transform: uppercase;
            }
            .smallcaps {
            font-variant: small-caps;
            }
            .underline {
            text-decoration: underline;
            }
            .strikethrough {
            text-decoration: line-through;
            }
            .box {
            border: 1px solid black;
            padding: 2px 4px;
            display: inline-block;
            }
            .expanded {
            letter-spacing: 0.2em;
            }
            .normal {
            font-style: normal;
            font-weight: normal;
            text-transform: none;
            }
            .subscript {
            vertical-align: sub;
            font-size: smaller;
            }
            .superscript {
            vertical-align: super;
            font-size: smaller;
            }
            .signature {
            margin-top: 30px;
            text-align: right;
            }
            .footnote-ref {
            vertical-align: super;
            font-size: smaller;
            }
            .annex-title {
            text-align: center;
            }
            .footnotes-section {
            margin-top: 30px;
            border-top: 1px solid #ccc;
            padding-top: 10px;
            font-size: 0.9em;
            }
            .footnote-item {
            margin-bottom: 8px;
            text-indent: -20px;
            padding-left: 20px;
            }
            .footnote-item p {
            margin: 0;
            display: inline;
            }
            .footnote-number {
            vertical-align: super;
            font-size: smaller;
            margin-right: 5px;
            }
            .section-number {
            font-weight: bold;
            margin-right: 12px;
            }
            .section {
            margin-bottom: 15px;
            }
            .section-title {
            margin-top: 20px;
            margin-bottom: 10px;
            }
            /* Improve list display */
            .alpha-list {
            list-style-type: none;
            padding-left: 20px;
            margin-top: 5px;
            }
            .alpha-list li {
            margin-bottom: 5px;
            text-indent: -20px;
            padding-left: 20px;
            }
            .dash-list {
            list-style-type: none;
            padding-left: 20px;
            }
            .dash-list li {
            margin-bottom: 5px;
            text-indent: -15px;
            padding-left: 15px;
            }
            .dash-list li p {
            margin: 0;
            display: inline;
            }
            .dash-list li:before {
            content: "— ";
            }
            .attachment-heading {
            font-weight: bold;
            margin-top: 30px;
            margin-bottom: 15px;
            }
            .sub-heading {
            font-weight: bold;
            margin-top: 15px;
            margin-bottom: 10px;
            }
            /* No margins for level classes */
            .level-1 { }
            .level-2 { }
            .level-3 { }
            .level-4 { }
            .level-5 { }
            /* To handle nested lists better */
            .nested-content {
            margin-left: 20px;
            }
            /* Definition list styles */
            .definition-list {
            margin: 15px 0;
            padding-left: 10px;
            }
            .definition-list dt {
            font-weight: bold;
            margin-top: 10px;
            margin-bottom: 5px;
            }
            .definition-list dd {
            margin-left: 20px;
            margin-bottom: 10px;
            }
            .prefix {
            font-weight: normal;
            }
        </style>
    </xsl:template>

</xsl:stylesheet>