// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A part of the hierarchy of the text: article, paragraph, point, annex, annex level and so on. One
 * element for every level; what it is comes from kind. Annex levels are flat: point 1.2.2.6 is a
 * sibling of 1.2.2, as in AKN4EU.
 *
 * @param eId The unit's identifier, e.g. art_3/par_2/pnt_a or anx/pnt_1.2.2.6.
 * @param kind What the unit is.
 * @param provenance The amending acts that put this unit in its current form, outermost first.
 * @param num The label as printed: "Article 3", "(a)", "1.2.2.6", "ANNEX II".
 * @param heading The heading; several lines are joined by eol.
 * @param subheading A second heading under the first.
 * @param depth Levels only: the nesting depth in the source (Formex LEVEL), for indentation.
 * @param box Levels only: the text is printed in a box.
 * @param ambiguous The published text gives this label twice under the same parent (Annex XI points
 *     1., 2., 2.): the units keep the label and share their eId, as published, and each is marked.
 *     A reference to the label can't tell them apart.
 * @param eurlexAnchor Annexes only: the number EUR-Lex's HTML gives the annex in its anchor,
 *     #anx_N, so that a reader can link to it there
 *     (https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX:32015R1998#anx_1). EUR-Lex
 *     counts the files an annex is split into, where this text joins them, so it can't be worked
 *     out from the units.
 */
public record Unit(
    EId eId,
    Kind kind,
    List<Provenance> provenance,
    List<Inline> num,
    @Nullable Lines heading,
    @Nullable Lines subheading,
    List<Block> content,
    @Nullable Integer depth,
    boolean box,
    boolean ambiguous,
    @Nullable Integer eurlexAnchor)
    implements Block {

  public Unit {
    provenance = List.copyOf(provenance);
    num = List.copyOf(num);
    content = List.copyOf(content);
  }

  /** The kinds of unit (AKN4EU names). */
  public enum Kind {
    /**
     * A grouping of articles: part, title, chapter or section (Formex DIVISION). Its num is the
     * label line ("CHAPTER III"). Its eId names the kind its heading gives, by the code of the EU
     * subdivision authority table, in any of the 24 languages, then the label after the type word:
     * TITLE I is tis_I, CHAPTER 2 cpt_2, SECTION 1 sct_1, PART TWO prt_TWO; a heading that names no
     * kind the table has gives division_. A division is nested under the one that encloses it.
     * Recitals are grouped the same way (Formex DIV.CONSID, "A. PROCEDURE"): eId pbl_1/division_A;
     * the recitals inside keep preamble-wide eIds (pbl_1/rct_12), as articles keep act-wide ones.
     */
    DIVISION,
    /**
     * A titled part of an article, found in agreements (Formex SUBDIV, "(1) Objectives"). Its num
     * and heading come from its title; its eId is subdivision_ and its number, under the article.
     */
    SUBDIVISION,
    /** An article; eId art_N, act-wide even inside divisions, as in AKN4EU and ELI. */
    ARTICLE,
    /** A paragraph of an article: numbered (par_N), or unnumbered (unp_N). */
    PARAGRAPH,
    /** A subparagraph of a numbered paragraph; sub_N by position. */
    SUBPARAGRAPH,
    /**
     * A numbered item of a list; pnt_ and its label, or idt_N by position where the label is only a
     * mark (a dash written as its number).
     */
    POINT,
    /** An unnumbered item of a list, such as a dash; idt_N by position. */
    INDENT,
    /** An entry of a definition list: its prefix as num, its term as heading. */
    DEFINITION,
    /**
     * A section or point of an annex: numbered (pnt_ and its number), labelled (level_ and a label
     * line such as ATTACHMENT-6-A) or unnumbered (level_N by position).
     */
    LEVEL,
    /**
     * An annex; anx, or anx_ and its label. An appendix (an annex inside an annex, Appendix III) is
     * app_ and its label, or app.
     */
    ANNEX,
    /**
     * One correction of a corrigendum: where (a paragraph: "On page 5, Article 3(2):"), then the
     * old and the new wording; eId cor_N by position.
     */
    CORRECTION,
    /**
     * A document attached to the act that is not one of its annexes: the agreement a decision
     * concludes, a protocol, a draft decision (Formex package entries DOC.SUB.PUB other than
     * ANNEX), or a related document of a consolidation (nested CONS.DOC.GEN of TYPE ACT, GENERAL or
     * OTH.ACT). As AKN's attachment: eId att_N by position, its title as heading, its own text,
     * articles (att_1/art_1), annexes (att_1/anx) and attachments under it.
     */
    ATTACHMENT,
    /** A letter of an exchange of letters attached to an agreement; eId let_N by position. */
    LETTER,
    /** A citation of the preamble. */
    CITATION,
    /** A recital of the preamble. */
    RECITAL
  }
}
