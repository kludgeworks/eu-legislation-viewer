// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One language version of an act: its original text, or one consolidated version.
 *
 * @param act The CELEX number of the act, e.g. 32015R1998.
 * @param kind Original OJ text or consolidated version.
 * @param version A consolidated version's start date; absent for the original text.
 * @param lang The language of this text, e.g. en.
 * @param formexVersion The Formex schema version of the source, e.g. 06.02.1.
 * @param converter The version of the converter that produced this document. A new converter
 *     invalidates cached texts.
 * @param source Which document this is, and where it was published.
 * @param lifecycle For a consolidated version: its period and the acts it integrates.
 * @param preface The long title.
 * @param preamble Citations and recitals, between the title and the enacting terms.
 * @param body The enacting terms: the articles.
 * @param conclusions The closing formula and the signature.
 * @param annexes The annexes, in order.
 * @param notes The footnotes, collected from wherever they occur in the text.
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

  /** Whether a document is an act's original text or a consolidated version. */
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
}
