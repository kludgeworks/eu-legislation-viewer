// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Finds where the text of the versions of an act in a language is not the law. It compares, for
 * each provision that an amending act changes, two dates:
 *
 * <ul>
 *   <li>The place date: the day from which the provision applies, as Cellar annotates the
 *       amendment.
 *   <li>The version date: the first day of the first version whose text newly tags the provision
 *       with the amending act.
 * </ul>
 *
 * <p>The rule is in the Notion page "Which date applies to a changed place":
 *
 * <ol>
 *   <li>A place date is accepted if the provision can apply from it, {@link
 *       ActDates#appliesFromDay}: if it is one of the dates of the amending act. It is rejected if
 *       it is only its entry into force, and the whole act applies from another day.
 *   <li>An accepted place date that is the version date is the date of the provision.
 *   <li>If they differ, the version date is the date of the provision if the place date was
 *       rejected, or if a version that starts on the place date includes the act but does not tag
 *       the provision.
 *   <li>Otherwise, a place date before the version date makes the provision a pending amendment
 *       between them, and a place date after the version date makes it a prospective amendment
 *       between them.
 *   <li>A provision that the amending act replaces, amends, deletes or repeals is no pending
 *       amendment if the version before the version date does not have it. The provision is not in
 *       the text yet, so the text cannot have its old words. For example, 2024/2994 replaces
 *       Article 74(1)(b) of 2013/36 from 24 December 2024. But 2024/1619 puts point (b) in, from 11
 *       January 2026.
 *   <li>A provision of an act that no version includes is a pending amendment from its accepted
 *       place date.
 * </ol>
 *
 * <p>A provision gives no answer if no version tags it while some version includes the act, or if a
 * version before the answer has no built text. An amending act whose provisions give no answer at
 * all is compared as a whole: the day from which the whole act applies, {@link
 * ActDates#appliesFrom}, against the first version that includes it. If no version includes it, and
 * Cellar does not give that day, the act is an undated pending amendment from its entry into force.
 */
final class Divergences {

  /**
   * The occurrences in all the texts that {@link #KNOWN} keeps, at most. Each is an eId of some
   * tens of characters, so the keys keep about 50 MB at most.
   */
  private static final long OCCURRENCES_KEPT = 500_000;

  /**
   * The divergences found, by what they were found from. A page finds them two to four times for
   * one request, and each request again: to find them is the most work of a page. The key is the
   * act and the texts themselves, so a change to either finds them again.
   */
  private static final Cache<Key, List<Divergence>> KNOWN =
      Caffeine.newBuilder()
          .maximumWeight(OCCURRENCES_KEPT)
          .weigher((Key key, List<Divergence> found) -> key.occurrences())
          .expireAfterAccess(Duration.ofHours(1))
          .build();

  private final Act act;
  private final Language language;
  private final List<Version> versions;
  private final Map<Version, TextTags> texts;

  /** The occurrences of each built text, made ready to find the places of the amendments. */
  private final Map<Version, TextOccurrences> occurrences = new HashMap<>();

  private Divergences(Act act, Language language, Map<Version, TextTags> texts) {
    this.act = act;
    this.language = language;
    this.versions = act.timeline(language).map(Timeline::versions).orElseGet(List::of);
    this.texts = texts;
  }

  /**
   * Returns each period in which the text of the versions of {@code act} in {@code language} is not
   * the law, in the order of the day on which they start. The act itself is not among them: its
   * deferred application is a state of the act, {@link LawOnADay#deferredApplicationOn}.
   *
   * @param texts the tags of the texts in {@code language} that are built, by version
   */
  static List<Divergence> of(Act act, Language language, Map<Version, TextTags> texts) {
    return KNOWN.get(
        new Key(act, language, Map.copyOf(texts)),
        key -> new Divergences(key.act(), key.language(), key.texts()).find());
  }

  private List<Divergence> find() {
    var found = new ArrayList<Divergence>();
    if (versions.isEmpty()) {
      return found;
    }
    var amending = new LinkedHashSet<ActIdentifier>();
    versions.forEach(each -> amending.addAll(each.integrates()));
    act.amendments().forEach(each -> amending.add(each.act()));
    amending.remove(act.id());
    var others = new ArrayList<Divergence>();
    for (var each : amending) {
      others.addAll(of(each));
    }
    others.sort(Comparator.comparing(Divergences::start));
    found.addAll(others);
    return found;
  }

  /** Returns the divergences that {@code amending} makes. */
  private List<Divergence> of(ActIdentifier amending) {
    var own = act.amendments().stream().filter(each -> each.act().equals(amending)).toList();
    var places = own.stream().filter(each -> each.changes(language)).toList();
    if (!own.isEmpty() && places.isEmpty()) {
      // It changes the text in other languages only.
      return List.of();
    }
    var groups = new LinkedHashMap<Answer, List<Amendment>>();
    var answered = false;
    for (var place : places) {
      var answer = answer(amending, place);
      if (answer.isPresent()) {
        answered = true;
        if (answer.get().kind() != null) {
          groups.computeIfAbsent(answer.get(), key -> new ArrayList<>()).add(place);
        }
      }
    }
    if (!answered) {
      return whole(amending, places);
    }
    return groups.entrySet().stream()
        .map(
            each ->
                new Divergence(
                    Objects.requireNonNull(each.getKey().kind()),
                    amending,
                    Objects.requireNonNull(each.getKey().applies()),
                    each.getKey().included(),
                    inTextOrder(each.getValue(), each.getKey().included())))
        .toList();
  }

  /**
   * Returns {@code places} in the order of the text of {@code version}, or of the latest built text
   * if {@code version} is null or has none. A place that the text does not have is last.
   */
  private List<Amendment> inTextOrder(List<Amendment> places, @Nullable Version version) {
    var of =
        Optional.ofNullable(version)
            .filter(texts::containsKey)
            .or(() -> versions.reversed().stream().filter(texts::containsKey).findFirst());
    if (of.isEmpty()) {
      return places;
    }
    var text = Objects.requireNonNull(texts.get(of.get()));
    var eIds = text.eIds();
    var occurrences = occurrencesOf(of.get(), text);
    return places.stream()
        .sorted(Comparator.comparingInt(place -> position(place, eIds, occurrences)))
        .toList();
  }

  /** Returns the occurrences of {@code text}, the text of {@code version}, made ready one time. */
  private TextOccurrences occurrencesOf(Version version, TextTags text) {
    return occurrences.computeIfAbsent(version, each -> TextOccurrences.of(text.eIds()));
  }

  /**
   * Returns where {@code place} is among {@code eIds}, or {@link Integer#MAX_VALUE} if they do not
   * have it.
   */
  private static int position(Amendment place, List<String> eIds, TextOccurrences occurrences) {
    var found = place.location().in(occurrences);
    return found.isPresent() ? eIds.indexOf(found.get().eIds().getFirst()) : Integer.MAX_VALUE;
  }

  /**
   * Returns how the text and {@code place} of {@code amending} compare. Returns empty if the texts
   * do not tell.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "a-place-date-the-act-can-apply-from")
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "the-text-agrees-with-the-law")
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "a-provision-not-in-the-text-yet")
  private Optional<Answer> answer(ActIdentifier amending, Amendment place) {
    var versionDate =
        VersionDate.of(
            amending,
            place,
            versions,
            texts,
            each -> occurrencesOf(each, Objects.requireNonNull(texts.get(each))),
            each -> includes(each, amending));
    if (versionDate.isEmpty()) {
      return Optional.empty();
    }
    var version = versionDate.get().version();
    var from = place.from();
    if (from == null || !act.dates().appliesFromDay(amending, from)) {
      // The version date stands. Without a version, the date is not known.
      return version == null ? Optional.empty() : Optional.of(Answer.AGREES);
    }
    if (version == null) {
      return Optional.of(Answer.differs(Kind.PENDING, date(amending, from), null));
    }
    var tagging = versionDate.get().tagging();
    if (from.equals(version.date())
        || versions.stream()
            .anyMatch(
                each ->
                    each.date().equals(from)
                        && includes(each, amending)
                        && !tagging.contains(each))) {
      return Optional.of(Answer.AGREES);
    }
    if (from.isAfter(version.date())) {
      return Optional.of(Answer.differs(Kind.PROSPECTIVE, date(amending, from), version));
    }
    return Optional.of(Answer.differs(Kind.PENDING, date(amending, from), version));
  }

  /**
   * Returns the divergence of {@code amending} as a whole: the day from which the whole act applies
   * against the first version that includes it. If no version includes it, the act is a pending
   * amendment from that day, once every text is built. If Cellar does not give that day either, it
   * is an undated pending amendment from the act's entry into force, at {@code places}.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "an-act-compared-as-a-whole")
  private List<Divergence> whole(ActIdentifier amending, List<Amendment> places) {
    var applies = act.dates().appliesFrom(amending);
    if (applies.isEmpty()) {
      return undated(amending, places);
    }
    var from = day(applies.get());
    var first = versions.stream().filter(each -> includes(each, amending)).findFirst();
    if (first.isPresent()) {
      return from.isBefore(first.get().date())
          ? List.of(new Divergence(Kind.PENDING, amending, applies.get(), first.get(), List.of()))
          : List.of();
    }
    return texts.keySet().containsAll(versions)
        ? List.of(new Divergence(Kind.PENDING, amending, applies.get(), null, List.of()))
        : List.of();
  }

  /**
   * Returns the divergence of {@code amending}, whose day of application Cellar does not give, if
   * no version includes it: an undated pending amendment. From its entry into force, the text can
   * lack it. Empty if a version includes it, if a text is not built, or if Cellar has no day for
   * its entry into force.
   */
  @BusinessRule(group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY, name = "undated-pending-amendment")
  private List<Divergence> undated(ActIdentifier amending, List<Amendment> places) {
    if (versions.stream().anyMatch(each -> includes(each, amending))
        || !texts.keySet().containsAll(versions)) {
      return List.of();
    }
    return act.dates().datesOf(amending).stream()
        .filter(each -> each.kind() == ActDate.Kind.ENTRY_INTO_FORCE && each.date() != null)
        .min(Comparator.comparing(Divergences::day))
        .map(
            force ->
                List.of(
                    new Divergence(Kind.UNDATED, amending, force, null, inTextOrder(places, null))))
        .orElseGet(List::of);
  }

  /**
   * Returns the date of {@code amending} that falls on {@code day}. A date of application is taken
   * before an entry into force on the same day. {@code day} must be accepted.
   */
  private ActDate date(ActIdentifier amending, LocalDate day) {
    return act.dates().datesOf(amending).stream()
        .filter(each -> day.equals(each.date()))
        .min(Comparator.comparing(each -> each.kind() == ActDate.Kind.ENTRY_INTO_FORCE))
        .orElseThrow();
  }

  /**
   * Returns whether {@code version} includes {@code amending}: the Publications Office lists it, or
   * its text tags an occurrence with it.
   */
  private boolean includes(Version version, ActIdentifier amending) {
    var text = texts.get(version);
    return version.integrates().contains(amending) || (text != null && text.tags(amending));
  }

  /** Returns the day on which a divergence starts. */
  private static LocalDate start(Divergence divergence) {
    var included = divergence.included();
    return divergence.kind() == Kind.PROSPECTIVE && included != null
        ? included.date()
        : divergence.appliesFrom();
  }

  private static LocalDate day(ActDate date) {
    return Objects.requireNonNull(date.date());
  }

  /**
   * What divergences are found from.
   *
   * @param occurrences the number of occurrences in the texts, which tells how much memory the key
   *     keeps
   */
  private record Key(Act act, Language language, Map<Version, TextTags> texts, int occurrences) {

    Key(Act act, Language language, Map<Version, TextTags> texts) {
      this(
          act, language, texts, texts.values().stream().mapToInt(text -> text.eIds().size()).sum());
    }
  }

  /**
   * How the text and a provision compare.
   *
   * @param kind how the text differs, or null if it agrees with the law
   * @param applies the date from which the provision applies, if the text differs
   * @param included the first version that includes the provision, if the text differs. Null if no
   *     version includes it.
   */
  private record Answer(
      @Nullable Kind kind, @Nullable ActDate applies, @Nullable Version included) {

    static final Answer AGREES = new Answer(null, null, null);

    static Answer differs(Kind kind, ActDate applies, @Nullable Version included) {
      return new Answer(kind, applies, included);
    }
  }
}
