// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.util.List;

/** A footnote, table note or other note, referred to by {@link NoteRef}s. */
public record Note(EId eId, String marker, Kind kind, List<Block> content) {

  public Note {
    content = List.copyOf(content);
  }

  /** What kind of note. */
  public enum Kind {
    FOOTNOTE,
    TABLE,
    QUOTATION,
    ENDNOTE,
    MANUAL,
    TITLE,
    CHAPTER
  }
}
