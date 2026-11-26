// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The histories of acts. A page and the API ask here, and only here, for what they show of the
 * history of an act: they give what the request has, and put the answer into their own form. The
 * answer applies the rules of the state on a day and of the history.
 *
 * <p>Each method gets the act without waiting for the Publications Office: it throws {@link
 * NotCatalogued} if the viewer looks the act up, and {@link NoSuchAct} if no act has the
 * identifier.
 */
public interface Histories {

  /**
   * Returns the history of the act that {@code act} names, by its changes to the text in {@code
   * language}. The history is on the day {@code on}, or on today if the reader gives no day. If the
   * reader gives two days that are not the same, {@code from} and {@code to}, the history is of
   * these two days, in their order.
   */
  History of(
      Eli act,
      Language language,
      @Nullable LocalDate on,
      @Nullable LocalDate from,
      @Nullable LocalDate to);

  /** Returns the history of the act with the CELEX number {@code act} on {@code day}. */
  History of(ActIdentifier act, Language language, LocalDate day);

  /**
   * Returns what the rules tell of the act with the CELEX number {@code act} for each day, by its
   * changes to the text in {@code language}. The history on one day is read from it.
   */
  ActOverTime overTime(ActIdentifier act, Language language);

  /** Returns what the rules tell of {@code act} for each day, in {@code language}. */
  ActOverTime overTime(Act act, Language language);

  /**
   * Returns the history of the occurrence {@code occurrence} of the text of {@code reading}, on
   * {@code day}. Empty if the text is not built, or if it does not have the occurrence.
   *
   * @param texts the tags of the built texts in the language, by version
   */
  Optional<ProvisionOnADay> provision(
      ActOverTime overTime,
      Version reading,
      String occurrence,
      LocalDate day,
      Map<Version, TextTags> texts);

  /**
   * Returns the occurrences of the text of {@code reading} that have a history to show, by their
   * eIds. Empty if the text is not built.
   */
  List<String> provisionsWithAHistory(
      ActOverTime overTime, Version reading, Map<Version, TextTags> texts);

  /**
   * Returns what the comparison of the texts of {@code from} and {@code to} covers.
   *
   * @param occurrence the eId of the provision that the comparison is limited to, or null
   * @param toDay the later day of the comparison
   */
  ComparedDays compared(
      ActOverTime overTime,
      Version from,
      Version to,
      @Nullable String occurrence,
      LocalDate toDay,
      Map<Version, TextTags> texts);

  /**
   * Returns {@code amending} in the history of the act that {@code act} names, on the day {@code
   * on}, or on today if the reader gives no day. Returns empty if {@code amending} is not an
   * amending act of the act in {@code language}.
   */
  Optional<AmendingHistory> amending(
      Eli act, ActIdentifier amending, Language language, @Nullable LocalDate on);
}
