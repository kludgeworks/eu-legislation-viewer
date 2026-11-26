// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location.Found;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The rule of the feature "Who made a change" (comparison) that tells which of Cellar's amendments
 * count for two texts of an act. It is one function of facts: {@link Act#amended} applies it. The
 * other rules of the feature need a comparison, and are not here.
 */
public final class WhoMadeAChange {

  private WhoMadeAChange() {}

  /**
   * An amendment counts if an act between the two texts made it, and if it changes the text in the
   * language. An amendment that applies only after the later text does not count. It is a later
   * part of the act, and the later text does not have that part. An amendment that applies before
   * the earlier text counts: a version can lag the law by weeks, so its date does not tell what the
   * version already contains.
   *
   * <p>An amendment that counts is on one of the two texts:
   *
   * <ul>
   *   <li>A deletion or a repeal is on the earlier text, else on the later text. The later text has
   *       the place of a deletion where it keeps the deleted occurrence, and the earlier text does
   *       not have it where it came before the occurrence. For example, 2021/255 repeals point
   *       6.8.5.5 of 2015/1998, which came in after the text of 15 November 2015: in the comparison
   *       of that text with the text of 15 March 2021, the repeal is at the later text's kept
   *       deletion.
   *   <li>An addition or a replacement is on the later text.
   * </ul>
   *
   * @param amendment the amendment
   * @param madeBetweenTheTexts whether a version after the earlier text, up to and including the
   *     later text, integrates the act of the amendment
   * @param language the language of the two texts
   * @param later the date of the later text
   * @param inTheEarlierText where the location of the amendment is in the earlier text. Null if
   *     that text does not have it.
   * @param inTheLaterText the same for the later text
   * @return the eIds of the occurrences that the amendment is at, or empty if it does not count. An
   *     amendment that counts and that neither text has is at no occurrence.
   */
  @BusinessRule(
      group = RuleGroup.WHO_MADE_A_CHANGE,
      name = "cellars-amendments-that-count",
      revision = 2)
  public static Optional<List<String>> cellarsAmendmentsThatCount(
      Amendment amendment,
      boolean madeBetweenTheTexts,
      Language language,
      LocalDate later,
      @Nullable Found inTheEarlierText,
      @Nullable Found inTheLaterText) {
    var applies = amendment.from();
    if (!madeBetweenTheTexts
        || !amendment.changes(language)
        || (applies != null && applies.isAfter(later))) {
      return Optional.empty();
    }
    var found =
        amendment.role().removes() && inTheEarlierText != null ? inTheEarlierText : inTheLaterText;
    return Optional.of(found == null ? List.of() : found.eIds());
  }
}
