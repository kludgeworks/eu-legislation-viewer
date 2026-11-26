// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline.TwoDays;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What a reader gets who asks to compare the law of an act on two days, in a language. {@link
 * ComparedLaws} gives it. A page only shows the case that it gets.
 */
public sealed interface ComparedLaw {

  /** Returns the two days of the comparison ({@link Timeline#twoDays}). */
  TwoDays days();

  /**
   * The reader did not give the two days of the comparison, or gave From after To. The comparison
   * is at {@code days}.
   */
  record Elsewhere(TwoDays days) implements ComparedLaw {}

  /**
   * From is a day before the first version of the act: that day has no text.
   *
   * @param overTime what the rules tell of the act for each day
   * @param afterFirst the day on which a comparison from the first version can end
   */
  record BeforeTheAct(ActOverTime overTime, TwoDays days, LocalDate afterFirst)
      implements ComparedLaw {}

  /**
   * The viewer prepares the act in the language.
   *
   * @param overTime what the rules tell of the act for each day
   * @param preparation how far the preparation is
   * @param now this instant, which tells how long the preparation takes
   */
  record Preparing(ActOverTime overTime, TwoDays days, Preparation preparation, Instant now)
      implements ComparedLaw {}

  /**
   * The act is prepared in the language.
   *
   * @param overTime what the rules tell of the act for each day
   * @param preparation the texts of the act, each built or not
   * @param earlier the version whose text shows the law on the earlier day
   * @param later the version whose text shows the law on the later day
   * @param compared what the comparison covers
   * @param today today, for the tense of the words and the links
   */
  record Prepared(
      ActOverTime overTime,
      TwoDays days,
      Preparation preparation,
      Version earlier,
      Version later,
      ComparedDays compared,
      LocalDate today)
      implements ComparedLaw {}
}
