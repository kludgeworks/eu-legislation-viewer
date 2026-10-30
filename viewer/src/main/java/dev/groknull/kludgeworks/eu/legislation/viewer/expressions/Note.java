// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import java.util.List;

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
    /** A note that belongs to a chapter of the document (Formex TYPE CHAPTER, 05.00 and later). */
    CHAPTER
  }
}
