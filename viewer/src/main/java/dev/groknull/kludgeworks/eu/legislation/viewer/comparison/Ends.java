// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The two ends of a comparison, from its canonical address: the versions that start on its two
 * days, From not after To. {@link #of} is the one place that finds them.
 *
 * <p>A comparison can be limited to the changes of one act. That act is between the two versions,
 * and the comparison is then of the two texts of its changes ({@link Inclusion}).
 *
 * @param act the act
 * @param versions the versions of the act in the language, with the acts that each includes
 * @param from the version that starts on the earlier day
 * @param to the version that starts on the later day
 * @param only the act that the comparison is limited to, or null
 * @param inclusion the two texts of the changes of {@code only}, or null if the comparison is not
 *     limited to one act
 */
public record Ends(
    Act act,
    Versions versions,
    Version from,
    Version to,
    @Nullable ActIdentifier only,
    @Nullable Inclusion inclusion) {

  /**
   * Returns the ends of the comparison of {@code act} at a canonical address.
   *
   * @param act the act
   * @param language the language
   * @param from the earlier day of the address
   * @param to the later day of the address
   * @param only the CELEX number of the act that the address limits the comparison to, or null
   * @param preparation the preparation of the act in the language
   * @param tags the tags of the texts in the language that are built, by version
   * @return the ends. Empty if the act has no version in the language, if a day is not the first
   *     day of a version, if From is after To, or if {@code only} is not an act between the two
   *     versions.
   */
  public static Optional<Ends> of(
      Act act,
      Language language,
      LocalDate from,
      LocalDate to,
      @Nullable String only,
      Preparation preparation,
      Map<Version, TextTags> tags) {
    var timeline = act.timeline().in(language);
    if (timeline.isEmpty()) {
      return Optional.empty();
    }
    var two = timeline.get().versionsStartingOn(from, to);
    if (two.isEmpty()) {
      return Optional.empty();
    }
    var versions =
        new Versions(language, timeline.get(), preparation, act.inclusions(language, tags));
    var earlier = two.get().from();
    var later = two.get().to();
    if (only == null) {
      return Optional.of(new Ends(act, versions, earlier, later, null, null));
    }
    return versions
        .included()
        .oneBetween(earlier, later, only)
        .flatMap(one ->
            TheChangesOfOneAct.oneActIsOneStep(
                    timeline.get(), versions.included(), earlier, later, one)
                .map(inclusion -> new Ends(act, versions, earlier, later, one, inclusion)));
  }

  /** Returns the language of the texts. */
  public Language language() {
    return versions.language();
  }

  /** Returns the versions of the act in the language. */
  public Timeline timeline() {
    return versions.timeline();
  }

  /** Returns the preparation of the act in the language. */
  public Preparation preparation() {
    return versions.preparation();
  }

  /** Returns the acts that each version includes. */
  public Inclusions included() {
    return versions.included();
  }

  /** The two texts of the comparison, as the viewer has them: built, not built yet, or failed. */
  public sealed interface TwoTexts {}

  /**
   * Both texts are built.
   *
   * @param earlier the version of the earlier text
   * @param later the version of the later text
   * @param before the earlier text
   * @param after the later text
   */
  public record Built(Version earlier, Version later, TextId before, TextId after)
      implements TwoTexts {}

  /** A text is not built yet, and no text failed. */
  public record NotBuiltYet() implements TwoTexts {}

  /** The viewer could not build a text, or the preparation does not have one. */
  public record Failed() implements TwoTexts {}

  /** Returns the two texts that the comparison compares, as the preparation has them. */
  public TwoTexts texts() {
    var before = preparation().texts().get(earlier());
    var after = preparation().texts().get(later());
    if (before instanceof Text.Ready(var first) && after instanceof Text.Ready(var second)) {
      return new Built(earlier(), later(), first, second);
    }
    return before == null
            || after == null
            || before instanceof Text.Failed
            || after instanceof Text.Failed
        ? new Failed()
        : new NotBuiltYet();
  }

  /**
   * Returns the version of the earlier text that the comparison compares: the text just before the
   * changes of {@link #only}, or {@link #from}.
   */
  public Version earlier() {
    return inclusion == null ? from : inclusion.before();
  }

  /**
   * Returns the version of the later text that the comparison compares: the first text with the
   * changes of {@link #only}, or {@link #to}.
   */
  public Version later() {
    return inclusion == null ? to : inclusion.first();
  }
}
