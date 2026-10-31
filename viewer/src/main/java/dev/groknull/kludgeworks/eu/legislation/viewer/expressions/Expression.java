// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.time.LocalDate;
import java.util.List;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/**
 * One language version of an act: its original text, or one consolidated version.
 *
 * @param act The CELEX number of the act this text belongs to: the act itself for its original text
 *     and its consolidated versions (32015R1998), the act it corrects for a corrigendum (32009R0631
 *     for corrigendum 32009R0631R(01)). The document's own number is source/@celex.
 * @param kind Original OJ text, corrigendum or consolidated version.
 * @param version A consolidated version's date, the first day it applies to, as its CELEX number
 *     has it (02015R1998-20260322); absent for an original text or a corrigendum.
 * @param lang The language of this text, e.g. en.
 * @param formexVersion The Formex schema version of the source, e.g. 06.02.1.
 * @param converter The version of the converter that produced this document (its version.txt).
 * @param source Which document this is, and where it was published.
 * @param lifecycle For a consolidated version: its period and the acts it integrates.
 * @param preface The long title.
 * @param preamble Citations and recitals, between the title and the enacting terms.
 * @param body The enacting terms: the articles.
 * @param conclusions The closing formula and the signature.
 * @param annexes The annexes, in order.
 * @param notes The footnotes, collected from wherever they occur in the text.
 */
@AggregateRoot
public record Expression(
    ActIdentifier act,
    Kind kind,
    @Nullable LocalDate version,
    String lang,
    String formexVersion,
    String converter,
    Source source,
    @Nullable Lifecycle lifecycle,
    @Nullable Preface preface,
    @Nullable Preamble preamble,
    List<Block> body,
    @Nullable Conclusions conclusions,
    List<Block> annexes,
    List<Note> notes) {

  public Expression {
    body = List.copyOf(body);
    annexes = List.copyOf(annexes);
    notes = List.copyOf(notes);
  }

  /** Its identity: its document (source/@celex) and its language. */
  @Identity
  public ExpressionIdentifier id() {
    return new ExpressionIdentifier(source.celex(), Language.ofIso(lang));
  }

  /**
   * Whether a document is an act's original text, a corrigendum to it or one of its consolidated
   * versions.
   */
  public enum Kind {
    /** The text as published in the Official Journal. */
    ORIGINAL,
    /** A consolidated version: the act with its amendments integrated, valid from a date. */
    CONSOLIDATED,
    /**
     * A corrigendum: corrections to a published text, each a unit of kind correction with its old
     * and new wording.
     */
    CORRIGENDUM
  }

  /**
   * What comes before the preamble: the long title.
   *
   * @param longTitle The long title.
   */
  public record Preface(List<Block> longTitle) {

    public Preface {
      longTitle = List.copyOf(longTitle);
    }
  }

  /**
   * The preamble: the acting entity, citations, recitals and the enacting formula, in source order.
   *
   * @param eId Always pbl_1.
   */
  public record Preamble(EId eId, List<PreambleContent> content) {

    public Preamble {
      content = List.copyOf(content);
    }
  }

  /**
   * The closing block: final formula and signature.
   *
   * @param signatures The signatures: one, one per party to an agreement, or none (consolidated
   *     versions).
   */
  public record Conclusions(List<Block> content, List<Signature> signatures) {

    public Conclusions {
      content = List.copyOf(content);
      signatures = List.copyOf(signatures);
    }
  }

  /**
   * Place, date and signatories.
   *
   * @param placeAndDate "Done at Brussels, 5 November 2015.": the date is typed, the place stays
   *     text (Formex does not mark it, and recognising it would mean parsing the wording of 24
   *     languages).
   * @param signatories One signatory; acts of Parliament and Council have two.
   */
  public record Signature(List<Inline> placeAndDate, List<Signatory> signatories) {

    public Signature {
      placeAndDate = List.copyOf(placeAndDate);
      signatories = List.copyOf(signatories);
    }
  }

  /**
   * A signatory, its lines split by position.
   *
   * @param organization The first line: "For the Commission".
   * @param role The lines in between: "The President".
   * @param person The last line: the name.
   */
  public record Signatory(
      @Nullable Lines organization, @Nullable Lines role, @Nullable Lines person) {}

  /**
   * An Official Journal publication.
   *
   * @param series The OJ series: L or C.
   * @param number The issue number, without leading zeros; absent for documents published on their
   *     own (from October 2023), which have a document number instead.
   * @param date The publication date.
   * @param page The first page.
   * @param document The document's number in the Official Journal: for a document published on its
   *     own (from October 2023), its number in the series, 2026/449 (cited OJ L, 2026/449); before,
   *     for a C-series document, the number printed above its title, without brackets, 2022/C
   *     155/03 (Formex NO.DOC.C).
   */
  public record Publication(
      String series,
      @Nullable String number,
      @Nullable LocalDate date,
      @Nullable String page,
      @Nullable String document) {}

  /**
   * A consolidated version's period and the acts it integrates (Formex INFO.CONSLEG, GR.MOD.ACT,
   * GR.CORRIG). What its dates mean in law (entry into force, application) comes from Cellar, not
   * from here.
   *
   * @param events An act this version integrates, in the order the source lists them.
   * @param end The last day this version applies to; absent while it is the latest.
   * @param produced When the Publications Office produced the consolidation.
   * @param sequence The production sequence number of the consolidation, e.g. 023.001.0.
   */
  public record Lifecycle(
      List<EventRef> events,
      @Nullable LocalDate end,
      @Nullable LocalDate produced,
      @Nullable String sequence) {

    public Lifecycle {
      events = List.copyOf(events);
    }
  }

  /**
   * An act a consolidated version integrates.
   *
   * @param type Amendment or corrigendum.
   * @param act The CELEX number of the amending act or corrigendum.
   * @param date The date of that act; for a corrigendum, which has no date of its own, the day it
   *     was published.
   */
  public record EventRef(Type type, ActIdentifier act, @Nullable LocalDate date) {

    /** What kind of act a consolidated version integrates. */
    public enum Type {
      /** An amending act. */
      AMENDMENT,
      /** A corrigendum, correcting an error in the published text. */
      CORRIGENDUM
    }
  }

  /**
   * Which document this is, and where it was published.
   *
   * @param celex The CELEX number of this document: 02015R1998-20260322 for a consolidated version,
   *     32015R1998 for the original.
   * @param eli This document's ELI, when its source carries it (from Formex 06.x): the act's
   *     original text's, or the corrigendum's own.
   * @param publication Where the act was published in the Official Journal.
   */
  public record Source(String celex, @Nullable String eli, @Nullable Publication publication) {}

  /**
   * A footnote, referred to from the text by noteRef.
   *
   * @param eId note_N by position in the document; in another file of an OJ act's package, that
   *     file's sequence number: note_anx01000301-1 in an annex, note_inc000402-1 in an included
   *     document, note_att01000301-1 in an attached document.
   * @param marker The marker as printed: the source's own number if it gives one, * for star
   *     numbering, else the running number.
   * @param kind Where the note belongs.
   */
  public record Note(EId eId, String marker, Kind kind, List<Block> content) {

    public Note {
      content = List.copyOf(content);
    }

    /** Kinds of footnote. */
    public enum Kind {
      /** A footnote to the text; numbered through the document. */
      FOOTNOTE,
      /** A note to a table; numbered within the table. */
      TABLE,
      /** A note inside quoted text, belonging to the quoted act. */
      QUOTATION,
      /** A note printed at the end of the document. */
      ENDNOTE,
      /** A note whose marker the source sets by hand. */
      MANUAL,
      /** A note to a title (Formex TYPE TITLE). */
      TITLE,
      /**
       * A note that belongs to a chapter of the document (Formex TYPE CHAPTER, 05.00 and later).
       */
      CHAPTER
    }
  }
}
