// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a reader gets who asks for the law of an act on a day, in a language. {@link Readings} gives
 * it. A page only shows the case that it gets.
 */
public sealed interface Reading {

  /** Returns what the rules tell of the act for each day. */
  ActOverTime overTime();

  /** Returns the version that the page is of. On a day before the act, its first version. */
  Version version();

  /**
   * The day is before the first version of the act: it has no text.
   *
   * @param version the first version of the act
   */
  record BeforeTheAct(ActOverTime overTime, Version version) implements Reading {}

  /**
   * The act has no version in the language.
   *
   * @param version the version that applies on the day, in another language
   * @param law the text and the law on the day
   * @param today today, for the tense of the words and the links
   */
  record NotInTheLanguage(ActOverTime overTime, Version version, LawOnADay law, LocalDate today)
      implements Reading {}

  /**
   * The viewer prepares the act in the language.
   *
   * @param version the version that applies on the day
   * @param preparation how far the preparation is
   * @param now this instant, which tells how long the preparation takes
   */
  record Preparing(ActOverTime overTime, Version version, Preparation preparation, Instant now)
      implements Reading {}

  /**
   * The act is prepared in the language.
   *
   * @param version the version that applies on the day
   * @param preparation the texts of the act, each built or not
   * @param law the text and the law on the day
   * @param named the acts that the text of the version names and the text of the version before
   *     does not, for a version for which the Publications Office lists no acts
   * @param text the tags of the text of the version, or null if it is not built
   * @param outline the outline of the text of the version, or null if it is not built
   * @param provisions the occurrences of the text that have a history to show, by their eIds
   * @param today today, for the tense of the words and the links
   */
  record Prepared(
      ActOverTime overTime,
      Version version,
      Preparation preparation,
      LawOnADay law,
      List<ActIdentifier> named,
      @Nullable TextTags text,
      @Nullable Outline outline,
      List<String> provisions,
      LocalDate today)
      implements Reading {

    public Prepared {
      named = List.copyOf(named);
      provisions = List.copyOf(provisions);
    }
  }
}
