// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Anything inside running text. */
public sealed interface Inline {

  /**
   * Plain text, with its characters exactly as in the source, including non-breaking spaces.
   *
   * @param value The text.
   */
  record Text(String value) implements Inline {}

  /**
   * Text set in a typeface (Formex HT).
   *
   * @param kind Which typeface.
   */
  record Emphasis(Kind kind, List<Inline> content) implements Inline {

    public Emphasis {
      content = List.copyOf(content);
    }

    /** Typefaces (Formex HT/@TYPE). */
    public enum Kind {
      /** Bold. */
      B,
      /** Italic. */
      I,
      /** Underlined. */
      U,
      /** Superscript: m². */
      SUP,
      /** Subscript. */
      SUB,
      /** Upper case; surnames in signatures. */
      UC,
      /** Small capitals (Formex 05.59 and later). */
      SC,
      /** Letter-spaced. */
      EXPANDED,
      /** Struck through. */
      STRIKE,
      /** Enclosed in a box. */
      BOX
    }
  }

  /**
   * A date in the text (Formex DATE).
   *
   * @param date The date; absent when the source's value is not a date (Formex DATE
   *     ISO="19931911"): the wording is all there is.
   * @param asWritten The date as printed: "5 November 2015".
   */
  record DateRef(@Nullable LocalDate date, String asWritten) implements Inline {}

  /**
   * Quoted text (Formex QUOT.START and QUOT.END in the same element). A pair that crosses elements
   * stays as plain quotation marks.
   *
   * @param startQuote The opening mark, e.g. ‘.
   * @param endQuote The closing mark, e.g. ’.
   */
  record Quote(String startQuote, String endQuote, List<Inline> content) implements Inline {

    public Quote {
      content = List.copyOf(content);
    }
  }

  /**
   * Where a footnote is referred to; the note itself is in notes.
   *
   * @param marker The marker as printed.
   */
  record NoteRef(EId note, String marker) implements Inline {}

  /**
   * A reference to another document.
   *
   * @param kind How the target is given.
   * @param href The target's URI, for kind uri.
   * @param series For kind oj: the OJ series.
   * @param number For kind oj: the issue or document number.
   * @param date For kind oj: the publication date.
   * @param page For kind oj: the first page.
   */
  record Ref(
      Kind kind,
      @Nullable String href,
      @Nullable String series,
      @Nullable String number,
      @Nullable LocalDate date,
      @Nullable String page,
      List<Inline> content)
      implements Inline {

    public Ref {
      content = List.copyOf(content);
    }

    /** How a reference gives its target. */
    public enum Kind {
      /**
       * An Official Journal reference, as data (Formex REF.DOC.OJ); the renderer builds the link.
       */
      OJ,
      /** A URI, usually the ELI (Formex REF.DOC@URI, LINK; 06.x). */
      URI
    }
  }

  /** A line break (Formex BR, 05.59 and later; joins the lines of a heading). */
  record Eol() implements Inline {}

  /** A formula in running text (Formex FORMULA), as MathML. */
  record MathInline(String mathml) implements Inline {}

  /**
   * Text in another language than the document's (Formex LV): a term quoted in its original
   * language, or the multilingual closing formulas of an agreement.
   *
   * @param lang The language of the text, e.g. fr.
   */
  record Span(String lang, List<Inline> content) implements Inline {

    public Span {
      content = List.copyOf(content);
    }
  }

  /**
   * An image inside running text or a heading (Formex INCL.ELEMENT TYPE="TIFF" there).
   *
   * @param src The image file in the act's Cellar manifestation.
   * @param content What the image shows (Formex INCL.ELEMENT CONTENT).
   */
  record InlineImage(String src, @Nullable ImageContent content) implements Inline {}

  /**
   * A text-level amendment: the words an amending act inserted or replaced inside a paragraph (an
   * amendment marker that opens or closes inside text). Inside a num, it shows a renumbering.
   */
  record Changed(Provenance provenance, List<Inline> content) implements Inline {

    public Changed {
      content = List.copyOf(content);
    }
  }

  /** Words an amendment deleted but the consolidation keeps. Never live text. */
  record DeletedInline(Provenance provenance, List<Inline> content) implements Inline {

    public DeletedInline {
      content = List.copyOf(content);
    }
  }

  /** In the long title: the type of act, "Commission Implementing Regulation". */
  record DocType(List<Inline> content) implements Inline {

    public DocType {
      content = List.copyOf(content);
    }
  }

  /** In the long title: the number, "(EU) 2015/1998". */
  record DocNumber(List<Inline> content) implements Inline {

    public DocNumber {
      content = List.copyOf(content);
    }
  }

  /**
   * In the long title: the date of the act.
   *
   * @param date The date; absent when the source's value is not a date (Formex DATE
   *     ISO="19931911"): the wording is all there is.
   * @param asWritten The date as printed: "5 November 2015".
   */
  record DocDate(@Nullable LocalDate date, String asWritten) implements Inline {}

  /** In the long title: what the act is about, "laying down detailed measures …". */
  record DocPurpose(List<Inline> content) implements Inline {

    public DocPurpose {
      content = List.copyOf(content);
    }
  }
}
