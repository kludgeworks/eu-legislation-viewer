// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amended;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline.TwoVersions;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The comparison of two texts of an act, as its fragments and the API show it. A controller asks
 * here, and only here, for what a fragment shows. It gets the {@link Ends} of the comparison from
 * its address, and then asks for an answer with them.
 */
public interface ComparedTexts {

  /**
   * Returns the ends of the comparison of the act at a canonical address ({@link Ends#of}). Empty
   * if no act has the ELI, or if the address is not canonical.
   *
   * @param only the CELEX number of the act that the address limits the comparison to, or null
   */
  Optional<Ends> ends(
      Eli act, Language language, LocalDate from, LocalDate to, @Nullable String only);

  /** Returns the ends of the comparison of the act with the CELEX number {@code act}. */
  Optional<Ends> ends(
      ActIdentifier act, Language language, LocalDate from, LocalDate to, @Nullable String only);

  /**
   * Returns the versions of the act that apply on {@code from} and on {@code to}, in {@code
   * language}: the comparison of the law on two days is at their first days. Empty if no act has
   * the number, or if no version applies on a day.
   */
  Optional<TwoVersions> applyingOn(
      ActIdentifier act, Language language, LocalDate from, LocalDate to);

  /**
   * Starts the preparation of the act of {@code ends} in their language, if it does not run, and
   * returns the ends with the preparation as it is now.
   */
  Ends prepared(Ends ends);

  /**
   * Returns the comparison of the two texts, each difference with who made it ({@link
   * Attributions#of}). Empty if either text is not built.
   *
   * @param day the later day of the comparison, to find the changes that do not apply on it yet, or
   *     null
   */
  Optional<Attributions.Compared> attributed(Ends ends, @Nullable LocalDate day);

  /**
   * Returns the differences at the occurrences {@code eIds} of the comparison. A place can have no
   * difference of its own: a chapter that the annex keeps flat. If the comparison is limited to one
   * act, the differences are then those that the act made in the place ({@link
   * TheChangesOfOneAct#chapterTheAnnexKeepsFlat}). Empty if either text is not built.
   */
  Optional<AtAPlace> at(Ends ends, List<String> eIds);

  /**
   * Returns the differences that the act of the comparison or another act of its version made,
   * where the data does not say which ({@link Alternatives}). Empty if the comparison is not
   * limited to one act, or if either text is not built.
   */
  Optional<Alternatives> either(Ends ends);

  /**
   * Returns Cellar's amendments between the two versions, at the occurrences of their texts. Empty
   * if either text is not built.
   */
  Optional<Amended> amended(Ends ends);

  /**
   * Returns the occurrences {@code eIds} of the later text, each with what last changed it. An
   * occurrence that the text does not have is left out. Empty if the later text is not built.
   */
  Optional<List<Unchanged>> unchanged(Ends ends, Collection<String> eIds);

  /**
   * Returns the occurrences of the later text from {@code first} to {@code last}, each with what
   * last changed it. Empty if the later text is not built.
   */
  Optional<Fold> unchanged(Ends ends, String first, String last);

  /**
   * The differences at a place.
   *
   * @param differences the differences
   * @param taggedBy the act of the comparison, if the place has no difference of its own and the
   *     differences are those that the text tags with the act in the place. Else null.
   */
  record AtAPlace(List<Difference> differences, @Nullable ActIdentifier taggedBy) {
    public AtAPlace {
      differences = List.copyOf(differences);
    }
  }

  /**
   * An occurrence that is the same in both texts.
   *
   * @param eId its eId
   * @param occurrence the occurrence of the later text
   * @param lastChange what last made it as it is
   */
  record Unchanged(String eId, Element occurrence, LastChange lastChange) {}

  /**
   * The occurrences of a fold of the Synopsis.
   *
   * @param occurrences the occurrences that the text has, in the order of the text
   * @param asked how many occurrences the fold names
   */
  record Fold(List<Unchanged> occurrences, int asked) {
    public Fold {
      occurrences = List.copyOf(occurrences);
    }
  }
}
