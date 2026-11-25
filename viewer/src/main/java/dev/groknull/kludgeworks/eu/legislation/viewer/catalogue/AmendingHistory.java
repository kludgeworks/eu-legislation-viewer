// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One amending act in the history of an act, on a day: what the page of the amending act is about.
 * {@link Histories#amending} gives it.
 *
 * @param act the act
 * @param amending the act that amends it
 * @param language the language of the text whose changes count
 * @param day the day
 * @param today today, for the tense of the words and the links
 * @param latest the tags of the latest text of the act that is built: the changes have its order.
 *     Null if no text is built.
 * @param texts the two texts that show the wording of the changes. Null if no version integrates
 *     the amending act, or if one of the two texts is not built.
 */
public record AmendingHistory(
    Act act,
    ActIdentifier amending,
    Language language,
    LocalDate day,
    LocalDate today,
    @Nullable TextTags latest,
    @Nullable TwoTexts texts) {

  /**
   * The two texts that show the wording of the changes of an amending act, with their tags.
   *
   * @param earlier the version before the first version that integrates the act
   * @param later the first version that integrates the act
   * @param before the tags of the text of {@code earlier}
   * @param after the tags of the text of {@code later}
   */
  public record TwoTexts(Version earlier, Version later, TextTags before, TextTags after) {}

  /**
   * Returns {@code amending} in the history of {@code act} on {@code day}.
   *
   * @param built the tags of the texts of the act in {@code language} that are built
   */
  public static AmendingHistory of(
      Act act,
      ActIdentifier amending,
      Language language,
      LocalDate day,
      LocalDate today,
      Map<Version, TextTags> built) {
    var shown = TheHistoryOfAnAct.twoTextsOfAnAmendingAct(act, amending, language);
    var texts =
        shown
            .before()
            .flatMap(earlier ->
                shown
                    .integrating()
                    .flatMap(later ->
                        Optional.ofNullable(built.get(earlier))
                            .flatMap(before ->
                                Optional.ofNullable(built.get(later))
                                    .map(after -> new TwoTexts(earlier, later, before, after)))));
    return new AmendingHistory(
        act,
        amending,
        language,
        day,
        today,
        TheHistoryOfAnAct.latestBuiltText(act, language, built).orElse(null),
        texts.orElse(null));
  }
}
