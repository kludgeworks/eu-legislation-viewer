// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The law on a day, and the text that a reader gets: the version of a day, where its text is not
 * the law ({@link Divergence}), the text that a comparison shows, and the deferred application of
 * the act. The rules are in the features "The text and the law on a day", "The text a comparison
 * shows" and "What a comparison covers".
 */
public final class LawOnADay {
  private final Act act;

  private LawOnADay(Act act) {
    this.act = act;
  }

  /** Returns what {@code act} tells of this. */
  public static LawOnADay of(Act act) {
    return new LawOnADay(act);
  }

  /**
   * Returns the version that applies on {@code date} for a reader in {@code language}. Returns
   * empty before the text of the act first applies. The returned version applies on {@code date}
   * across the act, but may lack {@code language}. When multiple versions share a day, it picks the
   * one in {@code language}, falling back to the other.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "the-text-of-a-day-is-a-version")
  public Optional<Version> versionOn(LocalDate date, Language language) {
    return act.timeline().applicableOn(date, language);
  }

  /**
   * Returns the versions of a comparison of the law on {@code from} with the law on {@code to}, in
   * {@code language}: the versions that apply on the two days ({@link #versionOn}). A comparison is
   * between the first days of these versions. Returns empty if no version applies on a day.
   */
  @BusinessRule(
      group = RuleGroup.WHAT_A_COMPARISON_COVERS,
      name = "a-comparison-is-of-two-versions")
  public Optional<Timeline.TwoVersions> versionsOn(
      LocalDate from, LocalDate to, Language language) {
    return versionOn(from, language)
        .flatMap(earlier ->
            versionOn(to, language).map(later -> new Timeline.TwoVersions(earlier, later)));
  }

  /**
   * Returns where the text that a reader gets on {@code day} is not the law on that day, as {@link
   * #divergences(Language, Map)} finds it.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "where-the-text-of-a-day-is-not-the-law")
  public List<Divergence> divergences(
      LocalDate day, Language language, Map<Version, TextTags> texts) {
    return divergences(language, texts).stream().filter(each -> each.on(day)).toList();
  }

  /**
   * Returns each period in which the text of the versions in {@code language} is not the law.
   *
   * <ul>
   *   <li>A provision of an amending act that applies, but that the text does not include yet, is a
   *       pending amendment. For example, 2017/815 applies from 1 June 2017, and the version of
   *       2015/1998 of 7 June 2017 is the first to include it.
   *   <li>A provision of an amending act that the text includes before the provision applies is a
   *       prospective amendment. For example, the version of 767/2008 of 12 January 2018 includes
   *       provisions of 2017/2226 that apply later.
   * </ul>
   *
   * <p>The act itself is not among them. While it is in force and does not apply in full, its
   * application is deferred: a state of the act, {@link #deferredApplicationOn}.
   *
   * <p>{@code texts} gives the tags of the texts in {@code language} that are built. They tell
   * which version first includes each provision. {@link Divergences} tells how. An amending act
   * that does not change the text in {@code language} is no pending amendment of that text. For
   * example, 2017/837 corrects only the Swedish and the Polish text of 2015/1998.
   */
  public List<Divergence> divergences(Language language, Map<Version, TextTags> texts) {
    return Divergences.of(act, language, texts);
  }

  /**
   * Returns the version whose text a comparison shows for {@code day}, in {@code language}.
   *
   * <ul>
   *   <li>It is the version that applies on the day.
   *   <li>If an amending act applies on the day and that version does not include it, it is the
   *       first version that includes the act. If there are two or more such acts, it is the latest
   *       of their versions. That text can include a change that does not apply on the day yet: a
   *       prospective amendment of the day says where.
   *   <li>An act that no version includes does not move the version.
   * </ul>
   *
   * <p>For example, 2017/815 applies from 1 June 2017, and the version of 2015/1998 of 7 June 2017
   * is the first to include it. The text for 3 June 2017 is the version of 7 June 2017, not the
   * version of 29 February 2016 that applies on that day.
   *
   * <p>Returns empty before the first version of the act.
   *
   * @param texts the tags of the texts in {@code language} that are built, as {@link
   *     #divergences(Language, Map)} takes them
   */
  @BusinessRule(group = RuleGroup.THE_TEXT_A_COMPARISON_SHOWS, name = "the-text-for-a-day")
  public Optional<Version> textOn(LocalDate day, Language language, Map<Version, TextTags> texts) {
    var applies = act.timeline(language).flatMap(each -> each.applicableOn(day));
    if (applies.isEmpty()) {
      return applies;
    }
    return divergences(day, language, texts).stream()
        .filter(each -> each.kind() == Divergence.Kind.PENDING)
        .map(Divergence::included)
        .filter(Objects::nonNull)
        .filter(included -> included.date().isAfter(applies.get().date()))
        .max(Comparator.comparing(Version::date))
        .or(() -> applies);
  }

  /**
   * Returns the occurrences of the text of {@code version} that have a change that does not apply
   * on {@code day} yet. They are the occurrences of each prospective amendment of the day, {@link
   * Divergence#in}. A comparison gives the difference at such an occurrence an amber bar.
   *
   * <p>Empty if the text of {@code version} is not built.
   *
   * @param texts the tags of the texts in {@code language} that are built, as {@link
   *     #divergences(Language, Map)} takes them
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_A_COMPARISON_SHOWS,
      name = "changes-that-do-not-apply-yet")
  public Prospective prospective(
      Version version, LocalDate day, Language language, Map<Version, TextTags> texts) {
    var byOccurrence = new LinkedHashMap<String, ActIdentifier>();
    var text = texts.get(version);
    if (text != null) {
      for (var divergence : divergences(day, language, texts)) {
        if (divergence.kind() == Divergence.Kind.PROSPECTIVE) {
          divergence
              .in(text.eIds())
              .forEach(occurrence -> byOccurrence.putIfAbsent(occurrence.eId(), divergence.act()));
        }
      }
    }
    return new Prospective(day, byOccurrence);
  }

  /**
   * Returns the acts and corrigenda that Cellar lists for {@code version} and that change its text
   * in {@code language}, in Cellar's order. An act changes the text in a language unless each of
   * its amendments is for other languages only ({@link ActHistory#inLanguage}). A corrigendum is
   * published only in the languages that it corrects, so one without a title in {@code language} is
   * left out. For example, the version of 2015/1998 of 12 April 2022 lists 2022/463, which changes
   * only the German text: in English it lists none.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "the-acts-that-made-a-text-in-the-language")
  public List<ActIdentifier> changingIn(Version version, Language language) {
    return version.integrates().stream()
        .filter(each ->
            each.corrigendum()
                ? act.publication(each).flatMap(p -> p.title(language)).isPresent()
                : ActHistory.of(act).inLanguage(each, language))
        .toList();
  }

  /**
   * Returns the deferred application of the act on {@code day}, in {@code language}: the act is in
   * force, but it applies in full only from a later day. The reader gets a text on such a day: it
   * is on or after the first version of the act in {@code language}, and on or after its entry into
   * force. The act applies in full after both. For example, 2016/679 on 1 March 2017, but not on 4
   * May 2016, before its entry into force, and 2018/1862 on 1 August 2022, which applies in part on
   * that day.
   *
   * <p>Returns empty on a day without a text, on or after the day from which the act applies in
   * full, and where Cellar does not give that day.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "deferred-application-of-the-act")
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "where-the-text-of-a-day-is-not-the-law")
  public Optional<DeferredApplication> deferredApplicationOn(LocalDate day, Language language) {
    var first =
        act.timeline(language).map(Timeline::versions).orElseGet(List::of).stream()
            .findFirst()
            .map(Version::date);
    var inForce = act.dates().entryIntoForce(act.id());
    var from = first.map(date -> inForce.filter(date::isBefore).orElse(date));
    return act.dates()
        .appliesFrom(act.id())
        .filter(inFull -> inFull.date() != null)
        .filter(inFull ->
            from.isPresent()
                && Objects.requireNonNull(inFull.date()).isAfter(from.get())
                && !day.isBefore(from.get())
                && day.isBefore(inFull.date()))
        .map(inFull -> new DeferredApplication(inFull, act.dates().appliesInPartOn(act.id(), day)));
  }
}
