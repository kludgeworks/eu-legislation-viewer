// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheTextAndTheLawOnADay.Deferred;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheTextAndTheLawOnADay.Differs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
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
 * The text and the law of an act over time, in a language: where the text of its versions is not
 * the law, and when. {@link #of} applies the rules of the feature "The text and the law on a day"
 * one time. A day is then a lookup, {@link #on}. {@link Laws} gives it.
 *
 * @param act the act
 * @param language the language of the texts
 * @param texts the tags of the texts in the language that are built, by version
 * @param divergences each period in which the text of the versions is not the law, in the order of
 *     the day on which they start. The act itself is not among them.
 * @param deferred the period in which the application of the act itself is deferred, or null if it
 *     has none
 */
public record LawOverTime(
    Act act,
    Language language,
    Map<Version, TextTags> texts,
    List<Divergence> divergences,
    @Nullable Deferred deferred) {

  public LawOverTime {
    texts = Map.copyOf(texts);
    divergences = List.copyOf(divergences);
  }

  /**
   * Returns the text and the law of {@code act} over time, in {@code language}.
   *
   * <p>An amending act that does not change the text in {@code language} makes no divergence. For
   * example, 2017/837 corrects only the Swedish and the Polish text of 2015/1998.
   *
   * @param texts the tags of the texts in {@code language} that are built, by version. They tell
   *     which version first includes each provision.
   */
  public static LawOverTime of(Act act, Language language, Map<Version, TextTags> texts) {
    var versions = act.timeline(language).map(Timeline::versions).orElseGet(List::of);
    var deferred =
        TheTextAndTheLawOnADay.deferredApplicationOfTheAct(
            versions.stream().findFirst().map(Version::date).orElse(null),
            act.dates().entryIntoForce(act.id()).orElse(null),
            DatedAct.of(act, act.id()).appliesFrom());
    var found = new ArrayList<Divergence>();
    if (!versions.isEmpty()) {
      var amending = new LinkedHashSet<ActIdentifier>();
      versions.forEach(each -> amending.addAll(each.integrates()));
      act.amendments().forEach(each -> amending.add(each.act()));
      amending.remove(act.id());
      var finder = new Finder(act, language, versions, texts);
      amending.forEach(each -> found.addAll(finder.of(each)));
      found.sort(Comparator.comparing(Divergence::from));
    }
    return new LawOverTime(act, language, texts, found, deferred.orElse(null));
  }

  /** Returns the text and the law on {@code day}. */
  public LawOnADay on(LocalDate day) {
    var version = act.timeline().applicableOn(day, language);
    var notTheLaw =
        TheTextAndTheLawOnADay.whereTheTextOfADayIsNotTheLaw(
            Optional.ofNullable(deferred)
                .filter(each -> each.on(day))
                .map(each ->
                    new DeferredApplication(
                        each.inFull(), act.dates().appliesInPartOn(act.id(), day)))
                .orElse(null),
            divergences,
            day);
    return new LawOnADay(
        day,
        version.orElse(null),
        version
            .map(each ->
                TheTextAndTheLawOnADay.theActsThatMadeATextInTheLanguage(act, each, language))
            .orElseGet(List::of),
        TheTextAComparisonShows.theTextForADay(
                act.timeline(language).flatMap(each -> each.applicableOn(day)).orElse(null),
                notTheLaw.divergences())
            .orElse(null),
        notTheLaw);
  }

  /**
   * Returns the occurrences of the text of {@code version} that have a change that does not apply
   * on {@code day} yet. Empty if the text of {@code version} is not built.
   */
  public Prospective changesThatDoNotApplyYet(Version version, LocalDate day) {
    return TheTextAComparisonShows.changesThatDoNotApplyYet(
        day, texts.get(version), on(day).divergences());
  }

  /** Applies the rules to each amending act. It keeps the occurrences of each text ready. */
  private static final class Finder {
    private final Act act;
    private final Language language;
    private final List<Version> versions;
    private final Map<Version, TextTags> texts;
    private final Map<Version, TextOccurrences> occurrences = new HashMap<>();

    Finder(Act act, Language language, List<Version> versions, Map<Version, TextTags> texts) {
      this.act = act;
      this.language = language;
      this.versions = versions;
      this.texts = texts;
    }

    /** Returns the divergences that {@code amending} makes. */
    List<Divergence> of(ActIdentifier amending) {
      var own = act.amendments().stream().filter(each -> each.act().equals(amending)).toList();
      var places = own.stream().filter(each -> each.changes(language)).toList();
      if (!own.isEmpty() && places.isEmpty()) {
        // It changes the text in other languages only.
        return List.of();
      }
      var dated = DatedAct.of(act, amending);
      var groups = new LinkedHashMap<Differs, List<Amendment>>();
      var answered = false;
      for (var place : places) {
        var versionDate =
            TheTextAndTheLawOnADay.versionDateOfAChange(
                amending,
                place,
                versions,
                texts,
                each -> occurrencesOf(each, Objects.requireNonNull(texts.get(each))),
                each -> includes(each, amending));
        if (versionDate.isEmpty()) {
          continue;
        }
        var version = versionDate.get().version();
        var placeDate =
            TheTextAndTheLawOnADay.aPlaceDateTheActCanApplyFrom(
                    place.from(), dated.canApplyFrom(), dated.dates())
                .orElse(null);
        var differs =
            TheTextAndTheLawOnADay.pendingAmendment(placeDate, version)
                .or(() -> TheTextAndTheLawOnADay.prospectiveAmendment(placeDate, version));
        answered |=
            differs.isPresent()
                || TheTextAndTheLawOnADay.theTextAgreesWithTheLaw(placeDate, version);
        differs.ifPresent(
            each -> groups.computeIfAbsent(each, key -> new ArrayList<>()).add(place));
      }
      var first = versions.stream().filter(each -> includes(each, amending)).findFirst();
      var everyTextBuilt = texts.keySet().containsAll(versions);
      var found = new ArrayList<Divergence>();
      groups.forEach((differs, at) ->
          found.add(differs.divergence(amending, inTextOrder(at, differs.included()))));
      TheTextAndTheLawOnADay.anActComparedAsAWhole(
              answered, dated.appliesFrom(), first.orElse(null), everyTextBuilt)
          .ifPresent(differs -> found.add(differs.divergence(amending, List.of())));
      TheTextAndTheLawOnADay.undatedPendingAmendment(
              answered, dated.appliesFrom(), first.orElse(null), everyTextBuilt, dated.dates())
          .ifPresent(differs -> found.add(differs.divergence(amending, inTextOrder(places, null))));
      return found;
    }

    /**
     * Returns {@code places} in the order of the text of {@code version}, or of the latest built
     * text if {@code version} is null or has none. A place that the text does not have is last.
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
      var ready = occurrencesOf(of.get(), text);
      return places.stream()
          .sorted(Comparator.comparingInt(place -> position(place, eIds, ready)))
          .toList();
    }

    /** Returns the occurrences of {@code text}, the text of {@code version}, made ready once. */
    private TextOccurrences occurrencesOf(Version version, TextTags text) {
      return occurrences.computeIfAbsent(version, each -> TextOccurrences.of(text.eIds()));
    }

    /**
     * Returns where {@code place} is among {@code eIds}, or {@link Integer#MAX_VALUE} if they do
     * not have it.
     */
    private static int position(Amendment place, List<String> eIds, TextOccurrences occurrences) {
      var found = place.location().in(occurrences);
      return found.isPresent() ? eIds.indexOf(found.get().eIds().getFirst()) : Integer.MAX_VALUE;
    }

    /**
     * Returns whether {@code version} includes {@code amending}: the Publications Office lists it,
     * or its text tags an occurrence with it.
     */
    private boolean includes(Version version, ActIdentifier amending) {
      var text = texts.get(version);
      return version.integrates().contains(amending) || (text != null && text.tags(amending));
    }
  }
}
