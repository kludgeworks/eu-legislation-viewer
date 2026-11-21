// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Finds where the text of the versions of an act in a language is not the law. It compares, for
 * each provision that an amending act changes, two dates:
 *
 * <ul>
 *   <li>The place date: the day from which the provision applies, as Cellar annotates the
 *       amendment.
 *   <li>The version date: the first day of the first version whose text newly marks the provision
 *       with the amending act.
 * </ul>
 *
 * <p>The rule is in the Notion page "Which date applies to a changed place":
 *
 * <ol>
 *   <li>A place date is accepted if the provision can apply from it, {@link Act#appliesFromDay}: if
 *       it is one of the dates of the amending act. It is rejected if it is only its entry into
 *       force, and the whole act applies from another day.
 *   <li>An accepted place date that is the version date is the date of the provision.
 *   <li>If they differ, the version date is the date of the provision if the place date was
 *       rejected, or if a version that starts on the place date includes the act but does not mark
 *       the provision.
 *   <li>Otherwise, a place date before the version date makes the text late between them, and a
 *       place date after the version date makes the text early between them.
 *   <li>A provision that the amending act replaces, amends, deletes or repeals does not make the
 *       text late if the version before the version date does not have it. The provision is not in
 *       the text yet, so the text cannot have its old words. For example, 2024/2994 replaces
 *       Article 74(1)(b) of 2013/36 from 24 December 2024. But 2024/1619 puts point (b) in, from 11
 *       January 2026.
 *   <li>A provision of an act that no version includes makes the text late from its accepted place
 *       date.
 * </ol>
 *
 * <p>A provision gives no answer if no version marks it while some version includes the act, or if
 * a version before the answer has no built text. An amending act whose provisions give no answer at
 * all is compared as a whole: the day from which the whole act applies, {@link Act#appliesFrom},
 * against the first version that includes it. If no version includes it, and Cellar does not give
 * that day, the date of the text is not confirmed from the act's entry into force.
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
  private final Map<Version, TextMarks> texts;

  /** The occurrences of each built text, made ready to find the places of the amendments. */
  private final Map<Version, Location.Occurrences> occurrences = new HashMap<>();

  private Divergences(Act act, Language language, Map<Version, TextMarks> texts) {
    this.act = act;
    this.language = language;
    this.versions = act.timeline(language).map(Timeline::versions).orElseGet(List::of);
    this.texts = texts;
  }

  /**
   * Returns each period in which the text of the versions of {@code act} in {@code language} is not
   * the law. The early text of the act itself is first. The others are in the order of the day on
   * which they start.
   *
   * @param texts the marks of the texts in {@code language} that are built, by version
   */
  static List<Divergence> of(Act act, Language language, Map<Version, TextMarks> texts) {
    return KNOWN.get(
        new Key(act, language, Map.copyOf(texts)),
        key -> new Divergences(key.act(), key.language(), key.texts()).find());
  }

  private List<Divergence> find() {
    var found = new ArrayList<Divergence>();
    if (versions.isEmpty()) {
      return found;
    }
    var first = versions.getFirst();
    act.appliesFrom(act.id())
        .filter(from -> day(from).isAfter(first.date()))
        .ifPresent(from -> found.add(new Divergence(Kind.EARLY, act.id(), from, first, List.of())));
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
  private Location.Occurrences occurrencesOf(Version version, TextMarks text) {
    return occurrences.computeIfAbsent(version, each -> Location.occurrences(text.eIds()));
  }

  /**
   * Returns where {@code place} is among {@code eIds}, or {@link Integer#MAX_VALUE} if they do not
   * have it.
   */
  private static int position(
      Amendment place, List<String> eIds, Location.Occurrences occurrences) {
    var found = place.location().in(occurrences);
    return found.isPresent() ? eIds.indexOf(found.get().eIds().getFirst()) : Integer.MAX_VALUE;
  }

  /**
   * Returns how the text and {@code place} of {@code amending} compare. Returns empty if the texts
   * do not tell.
   */
  private Optional<Answer> answer(ActIdentifier amending, Amendment place) {
    var marked = firstMarking(amending, place);
    if (marked.isEmpty()) {
      return Optional.empty();
    }
    var version = marked.get().version();
    var from = place.from();
    if (from == null || !act.appliesFromDay(amending, from)) {
      // The version date stands. Without a version, the date is not confirmed.
      return version == null ? Optional.empty() : Optional.of(Answer.AGREES);
    }
    if (version == null) {
      return Optional.of(Answer.differs(Kind.LATE, date(amending, from), null));
    }
    var marking = marked.get().marking();
    if (from.equals(version.date())
        || versions.stream()
            .anyMatch(
                each ->
                    each.date().equals(from)
                        && includes(each, amending)
                        && !marking.contains(each))) {
      return Optional.of(Answer.AGREES);
    }
    if (from.isAfter(version.date())) {
      return Optional.of(Answer.differs(Kind.EARLY, date(amending, from), version));
    }
    if (place.role().changesExisting()) {
      var before = versions.indexOf(version) - 1;
      var text = before < 0 ? null : texts.get(versions.get(before));
      if (text == null) {
        return Optional.empty();
      }
      if (place.location().in(occurrencesOf(versions.get(before), text)).isEmpty()) {
        // The provision is not in the text yet: an act that applies later puts it in.
        return Optional.of(Answer.AGREES);
      }
    }
    return Optional.of(Answer.differs(Kind.LATE, date(amending, from), version));
  }

  /**
   * Returns the first version whose text newly marks {@code place} with {@code amending}. An
   * occurrence marks the place if it is an occurrence of the place, or an occurrence in it. If no
   * version marks it, it is the first version that marks an occurrence around the place. It is the
   * version that starts on the place date instead, if that version also newly marks the place.
   *
   * <p>A deletion or a repeal leaves no occurrence where the provision stood. The consolidation
   * keeps its mark there, in the occurrence around it, with a line of dashes: point (d) of Article
   * 1(2) of 2014/65, deleted by 2019/2177. So in a text that no longer has the occurrence of such a
   * place, the occurrence around where it stood marks the place, by a mark on that occurrence
   * itself.
   *
   * <p>Returns a {@link Marked} without a version if no version includes {@code amending}. Returns
   * empty if a version before the answer has no built text, or if no version marks the place while
   * some version includes the act.
   */
  private Optional<Marked> firstMarking(ActIdentifier amending, Amendment place) {
    Version first = null;
    Version around = null;
    var newly = new ArrayList<Version>();
    var marking = new HashSet<Version>();
    Set<String> before = Set.of();
    for (var version : versions) {
      var text = texts.get(version);
      if (text == null) {
        if (first == null) {
          return Optional.empty();
        }
        continue;
      }
      var occurrences =
          place
              .location()
              .in(occurrencesOf(version, text))
              .map(Location.Found::eIds)
              .orElseGet(List::of);
      var gone = occurrences.isEmpty() && place.role().removes();
      if (gone) {
        occurrences =
            place
                .location()
                .around(occurrencesOf(version, text))
                .map(Location.Found::eIds)
                .orElseGet(List::of);
      }
      if (occurrences.isEmpty()) {
        continue;
      }
      var found = Set.copyOf(occurrences);
      var holders = holders(occurrences);
      var now = new HashSet<String>();
      for (var mark : text.marked(amending)) {
        if (gone ? found.contains(mark) : inOneOf(mark, found)) {
          now.add(mark);
        } else if (around == null && holders.contains(mark)) {
          around = version;
        }
      }
      if (!now.isEmpty()) {
        marking.add(version);
      }
      if (!before.containsAll(now)) {
        newly.add(version);
        if (first == null) {
          first = version;
        }
      }
      before = now;
    }
    var from = place.from();
    var onTheDay = newly.stream().filter(each -> each.date().equals(from)).findFirst();
    if (onTheDay.isPresent()) {
      return Optional.of(new Marked(onTheDay.get(), marking));
    }
    if (first != null || around != null) {
      return Optional.of(new Marked(first != null ? first : around, marking));
    }
    var included = versions.stream().anyMatch(each -> includes(each, amending));
    return included ? Optional.empty() : Optional.of(new Marked(null, marking));
  }

  /** Returns whether {@code eId} is one of {@code occurrences}, or an occurrence in one of them. */
  private static boolean inOneOf(String eId, Set<String> occurrences) {
    if (occurrences.contains(eId)) {
      return true;
    }
    for (var slash = eId.lastIndexOf('/'); slash > 0; slash = eId.lastIndexOf('/', slash - 1)) {
      if (occurrences.contains(eId.substring(0, slash))) {
        return true;
      }
    }
    return false;
  }

  /** Returns the eIds of the occurrences that hold one of {@code occurrences}. */
  private static Set<String> holders(List<String> occurrences) {
    var holders = new HashSet<String>();
    for (var occurrence : occurrences) {
      for (var slash = occurrence.indexOf('/');
          slash > 0;
          slash = occurrence.indexOf('/', slash + 1)) {
        holders.add(occurrence.substring(0, slash));
      }
    }
    return holders;
  }

  /**
   * Returns the divergence of {@code amending} as a whole: the day from which the whole act applies
   * against the first version that includes it. If no version includes it, the text is late from
   * that day, once every text is built. If Cellar does not give that day either, the date of the
   * text is not confirmed from the act's entry into force, at {@code places}.
   */
  private List<Divergence> whole(ActIdentifier amending, List<Amendment> places) {
    var applies = act.appliesFrom(amending);
    if (applies.isEmpty()) {
      return unconfirmed(amending, places);
    }
    var from = day(applies.get());
    var first = versions.stream().filter(each -> includes(each, amending)).findFirst();
    if (first.isPresent()) {
      return from.isBefore(first.get().date())
          ? List.of(new Divergence(Kind.LATE, amending, applies.get(), first.get(), List.of()))
          : List.of();
    }
    return texts.keySet().containsAll(versions)
        ? List.of(new Divergence(Kind.LATE, amending, applies.get(), null, List.of()))
        : List.of();
  }

  /**
   * Returns the divergence of {@code amending}, whose day of application Cellar does not give, if
   * no version includes it: from its entry into force, the text can lack it. Empty if a version
   * includes it, if a text is not built, or if Cellar has no day for its entry into force.
   */
  private List<Divergence> unconfirmed(ActIdentifier amending, List<Amendment> places) {
    if (versions.stream().anyMatch(each -> includes(each, amending))
        || !texts.keySet().containsAll(versions)) {
      return List.of();
    }
    return act.datesOf(amending).stream()
        .filter(each -> each.kind() == ActDate.Kind.ENTRY_INTO_FORCE && each.date() != null)
        .min(Comparator.comparing(Divergences::day))
        .map(
            force ->
                List.of(
                    new Divergence(
                        Kind.UNCONFIRMED, amending, force, null, inTextOrder(places, null))))
        .orElseGet(List::of);
  }

  /**
   * Returns the date of {@code amending} that falls on {@code day}. A date of application is taken
   * before an entry into force on the same day. {@code day} must be accepted.
   */
  private ActDate date(ActIdentifier amending, LocalDate day) {
    return act.datesOf(amending).stream()
        .filter(each -> day.equals(each.date()))
        .min(Comparator.comparing(each -> each.kind() == ActDate.Kind.ENTRY_INTO_FORCE))
        .orElseThrow();
  }

  /**
   * Returns whether {@code version} includes {@code amending}: the Publications Office lists it, or
   * its text marks an occurrence with it.
   */
  private boolean includes(Version version, ActIdentifier amending) {
    var text = texts.get(version);
    return version.integrates().contains(amending) || (text != null && text.marks(amending));
  }

  /** Returns the day on which a divergence starts. */
  private static LocalDate start(Divergence divergence) {
    var included = divergence.included();
    return divergence.kind() == Kind.EARLY && included != null
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
  private record Key(Act act, Language language, Map<Version, TextMarks> texts, int occurrences) {

    Key(Act act, Language language, Map<Version, TextMarks> texts) {
      this(
          act, language, texts, texts.values().stream().mapToInt(text -> text.eIds().size()).sum());
    }
  }

  /**
   * The first version that marks a provision.
   *
   * @param version the version, or null if no version includes the amending act
   * @param marking every version whose text marks the provision
   */
  private record Marked(@Nullable Version version, Set<Version> marking) {}

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
