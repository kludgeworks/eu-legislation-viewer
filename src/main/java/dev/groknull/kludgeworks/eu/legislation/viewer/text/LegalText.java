// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One language version of an act, as converted from Formex: its original text, a corrigendum or one
 * consolidated version.
 */
public record LegalText(
    String act,
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

  public LegalText {
    body = List.copyOf(body);
    annexes = List.copyOf(annexes);
    notes = List.copyOf(notes);
  }

  /** Original OJ text, corrigendum or consolidated version. */
  public enum Kind {
    ORIGINAL,
    CONSOLIDATED,
    CORRIGENDUM
  }
}
