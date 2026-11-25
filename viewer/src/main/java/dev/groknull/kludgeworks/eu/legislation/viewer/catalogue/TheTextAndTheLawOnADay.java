// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.WhenAnActApplies.WholeAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The feature "The text and the law on a day": one function for each of its rules, in the order of
 * the feature. A rule reads facts and gives one new fact. It does not call another rule: {@link
 * LawOverTime} applies the rules.
 *
 * <p>For each place that an amending act changes, the rules compare two dates. The place date is
 * the day that Cellar gives for the place. The version date is the first day of the first version
 * whose text newly tags the place with the amending act.
 */
public final class TheTextAndTheLawOnADay {

  private TheTextAndTheLawOnADay() {}

  /**
   * How the text differs from the law at a place, or for a whole act.
   *
   * @param kind how the text differs
   * @param applies the date from which the change applies. For an undated pending amendment, the
   *     entry into force of the act.
   * @param included the first version that includes the change, or null if no version includes it
   * @param from the first day on which the text and the law differ
   * @param until the first day on which they agree again, or null if no such day is known
   */
  public record Differs(
      Kind kind,
      ActDate applies,
      @Nullable Version included,
      LocalDate from,
      @Nullable LocalDate until) {

    /** Returns the divergence of {@code amending} at {@code places}. */
    Divergence divergence(ActIdentifier amending, List<Amendment> places) {
      return new Divergence(kind, amending, applies, included, from, until, places);
    }
  }

  /**
   * The period in which the application of an act is deferred.
   *
   * @param from its first day
   * @param inFull the date from which the act applies in full: the day after its last day
   */
  public record Deferred(LocalDate from, ActDate inFull) {

    /** Returns whether {@code day} is in the period. */
    public boolean on(LocalDate day) {
      return !day.isBefore(from) && day.isBefore(day(inFull));
    }
  }

  /**
   * The acts for which the text of a day is not the law.
   *
   * @param deferred the deferred application of the act itself, or null if it has none on the day
   * @param divergences the pending and the prospective amendments of the day, in the order of the
   *     day on which they start
   */
  public record NotTheLaw(@Nullable DeferredApplication deferred, List<Divergence> divergences) {
    public NotTheLaw {
      divergences = List.copyOf(divergences);
    }

    /** Returns the acts: {@code itself} if its application is deferred, then the amending acts. */
    public List<ActIdentifier> acts(ActIdentifier itself) {
      var acts = new ArrayList<ActIdentifier>();
      if (deferred != null) {
        acts.add(itself);
      }
      divergences.forEach(each -> acts.add(each.act()));
      return acts;
    }
  }

  /**
   * For a day, the reader gets the newest version that starts on or before it, in the language. If
   * two versions share that day, it is the one in {@code language}, else the other. The reader gets
   * no version before the first version of the act.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "the-text-of-a-day-is-a-version")
  public static Optional<Version> versionOfADay(
      Timeline timeline, LocalDate day, Language language) {
    return timeline.applicableOn(day, language);
  }

  /**
   * The version date of a change is the first day of the first version whose text newly tags the
   * changed place with the amending act. An occurrence tags the place if it is an occurrence of the
   * place, or an occurrence in it. If no version tags it, it is the first version that tags an
   * occurrence around the place: 2022/1190 deletes Article 48(9) of 2018/1862, and the text of 1
   * August 2022 keeps the paragraph and tags Article 48. It is the version that starts on the place
   * date instead, if that version also newly tags the place.
   *
   * @param amending the amending act
   * @param place the change
   * @param versions the versions of the act in the language, in order
   * @param texts the tags of the texts that are built, by version
   * @param occurrences the occurrences of the text of a version, made ready one time
   * @param includes whether a version includes {@code amending}
   * @return the version date. It has no version if no version includes {@code amending}. Empty if a
   *     version before the answer has no built text, or if no version tags the place while some
   *     version includes the act.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "the-version-date-of-a-change")
  static Optional<VersionDate> versionDateOfAChange(
      ActIdentifier amending,
      Amendment place,
      List<Version> versions,
      Map<Version, TextTags> texts,
      Function<Version, TextOccurrences> occurrences,
      Predicate<Version> includes) {
    Version first = null;
    Version around = null;
    var newly = new ArrayList<Version>();
    var tagging = new HashSet<Version>();
    Set<String> before = Set.of();
    for (var version : versions) {
      var text = texts.get(version);
      if (text == null) {
        if (first == null) {
          return Optional.empty();
        }
        continue;
      }
      var found =
          place
              .location()
              .in(occurrences.apply(version))
              .map(Location.Found::eIds)
              .orElseGet(List::of);
      if (found.isEmpty()) {
        continue;
      }
      var of = Set.copyOf(found);
      var holders = holders(found);
      var now = new HashSet<String>();
      for (var tag : text.tagged(amending)) {
        if (inOneOf(tag, of)) {
          now.add(tag);
        } else if (around == null && holders.contains(tag)) {
          around = version;
        }
      }
      if (!now.isEmpty()) {
        tagging.add(version);
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
      return Optional.of(new VersionDate(onTheDay.get(), tagging));
    }
    if (first != null || around != null) {
      return Optional.of(new VersionDate(first != null ? first : around, tagging));
    }
    return versions.stream().anyMatch(includes)
        ? Optional.empty()
        : Optional.of(new VersionDate(null, tagging));
  }

  /**
   * A place date counts only if the act can apply from it: it is one of the dates of the act. It
   * does not count if it is only the entry into force of the act, and the whole act applies from
   * another day.
   *
   * @param placeDate the day that Cellar gives for the place, or null if it gives none
   * @param canApplyFrom the days that a provision of the act can apply from
   * @param dates the dates of the act
   * @return the date of the act that falls on the place date, if the place date counts. A date of
   *     application is taken before an entry into force on the same day.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "a-place-date-the-act-can-apply-from")
  public static Optional<ActDate> aPlaceDateTheActCanApplyFrom(
      @Nullable LocalDate placeDate, Set<LocalDate> canApplyFrom, List<ActDate> dates) {
    if (placeDate == null || !canApplyFrom.contains(placeDate)) {
      return Optional.empty();
    }
    return dates.stream()
        .filter(each -> placeDate.equals(each.date()))
        .min(Comparator.comparing(each -> each.kind() == ActDate.Kind.ENTRY_INTO_FORCE));
  }

  /**
   * The text agrees with the law at a place if the place date counts and is the version date. The
   * version date stands if the place date does not count. The text also agrees if a version that
   * starts on the place date includes the act and does not tag the place.
   *
   * @param placeDate the place date, if it counts ({@link #aPlaceDateTheActCanApplyFrom}), else
   *     null
   * @param versionDate the version whose first day is the version date, or null if no version
   *     includes the act
   * @param includedOnThePlaceDate whether a version that starts on the place date includes the act
   *     and does not tag the place
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "the-text-agrees-with-the-law")
  public static boolean theTextAgreesWithTheLaw(
      @Nullable ActDate placeDate, @Nullable Version versionDate, boolean includedOnThePlaceDate) {
    if (versionDate == null) {
      return false;
    }
    return placeDate == null || includedOnThePlaceDate || day(placeDate).equals(versionDate.date());
  }

  /**
   * A place date before the version date makes a pending amendment. The text and the law differ
   * from the place date to the day before the version date. A place of an act that no version
   * includes is pending from its place date. This is also true for a provision that the act adds,
   * and that the version before the version date does not have.
   *
   * @param placeDate the place date, if it counts, else null
   * @param versionDate the version whose first day is the version date, or null if no version
   *     includes the act
   * @param includedOnThePlaceDate whether a version that starts on the place date includes the act
   *     and does not tag the place
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "pending-amendment",
      revision = 2)
  public static Optional<Differs> pendingAmendment(
      @Nullable ActDate placeDate, @Nullable Version versionDate, boolean includedOnThePlaceDate) {
    if (placeDate == null) {
      return Optional.empty();
    }
    var from = day(placeDate);
    if (versionDate == null) {
      return Optional.of(new Differs(Kind.PENDING, placeDate, null, from, null));
    }
    return includedOnThePlaceDate || !from.isBefore(versionDate.date())
        ? Optional.empty()
        : Optional.of(new Differs(Kind.PENDING, placeDate, versionDate, from, versionDate.date()));
  }

  /**
   * A place date after the version date makes a prospective amendment. The text and the law differ
   * from the version date to the day before the place date.
   *
   * @param placeDate the place date, if it counts, else null
   * @param versionDate the version whose first day is the version date, or null if no version
   *     includes the act
   * @param includedOnThePlaceDate whether a version that starts on the place date includes the act
   *     and does not tag the place
   */
  @BusinessRule(group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY, name = "prospective-amendment")
  public static Optional<Differs> prospectiveAmendment(
      @Nullable ActDate placeDate, @Nullable Version versionDate, boolean includedOnThePlaceDate) {
    if (placeDate == null || versionDate == null || includedOnThePlaceDate) {
      return Optional.empty();
    }
    var applies = day(placeDate);
    return applies.isAfter(versionDate.date())
        ? Optional.of(
            new Differs(Kind.PROSPECTIVE, placeDate, versionDate, versionDate.date(), applies))
        : Optional.empty();
  }

  /**
   * The deferred application of an act starts on its entry into force, or on its first version if
   * that day is later. It ends on the day before the whole act applies. For example, 2016/679 from
   * 24 May 2016 to 24 May 2018.
   *
   * @param firstVersion the first day of the first version of the act in the language, or null if
   *     it has none
   * @param entryIntoForce the entry into force of the act, or null if Cellar does not give it
   * @param appliesFrom from which day the whole act applies
   * @return the period. Empty if the act has no version, if it applies in full from the first day
   *     of the period, or if Cellar does not give the day from which it applies in full.
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "deferred-application-of-the-act")
  public static Optional<Deferred> deferredApplicationOfTheAct(
      @Nullable LocalDate firstVersion, @Nullable LocalDate entryIntoForce, WholeAct appliesFrom) {
    if (firstVersion == null) {
      return Optional.empty();
    }
    var from =
        entryIntoForce != null && firstVersion.isBefore(entryIntoForce)
            ? entryIntoForce
            : firstVersion;
    return appliesFrom
        .from()
        .filter(inFull -> inFull.date() != null && inFull.date().isAfter(from))
        .map(inFull -> new Deferred(from, inFull));
  }

  /**
   * The viewer compares an act as a whole if no provision of the act gives an answer. It compares
   * the day from which the whole act applies with the first version that includes the act. If no
   * version includes it, the act is a pending amendment from that day, once every text is built.
   *
   * @param aProvisionGivesAnAnswer whether a provision of the act gives an answer
   * @param appliesFrom from which day the whole act applies
   * @param firstIncluding the first version that includes the act, or null if none does
   * @param everyTextBuilt whether the viewer built the text of each version
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "an-act-compared-as-a-whole")
  public static Optional<Differs> anActComparedAsAWhole(
      boolean aProvisionGivesAnAnswer,
      WholeAct appliesFrom,
      @Nullable Version firstIncluding,
      boolean everyTextBuilt) {
    var applies = appliesFrom.from();
    if (aProvisionGivesAnAnswer || applies.isEmpty()) {
      return Optional.empty();
    }
    var from = day(applies.get());
    if (firstIncluding != null) {
      return from.isBefore(firstIncluding.date())
          ? Optional.of(
              new Differs(Kind.PENDING, applies.get(), firstIncluding, from, firstIncluding.date()))
          : Optional.empty();
    }
    return everyTextBuilt
        ? Optional.of(new Differs(Kind.PENDING, applies.get(), null, from, null))
        : Optional.empty();
  }

  /**
   * An act is an undated pending amendment from its entry into force if the act is in force, no
   * version includes it, and Cellar does not give the day from which it applies. The viewer can
   * tell this only after it builds the text of every version.
   *
   * @param aProvisionGivesAnAnswer whether a provision of the act gives an answer
   * @param appliesFrom from which day the whole act applies
   * @param firstIncluding the first version that includes the act, or null if none does
   * @param everyTextBuilt whether the viewer built the text of each version
   * @param dates the dates of the act
   */
  @BusinessRule(group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY, name = "undated-pending-amendment")
  public static Optional<Differs> undatedPendingAmendment(
      boolean aProvisionGivesAnAnswer,
      WholeAct appliesFrom,
      @Nullable Version firstIncluding,
      boolean everyTextBuilt,
      List<ActDate> dates) {
    if (aProvisionGivesAnAnswer
        || appliesFrom.from().isPresent()
        || firstIncluding != null
        || !everyTextBuilt) {
      return Optional.empty();
    }
    return dates.stream()
        .filter(each -> each.kind() == ActDate.Kind.ENTRY_INTO_FORCE && each.date() != null)
        .min(Comparator.comparing(TheTextAndTheLawOnADay::day))
        .map(force -> new Differs(Kind.UNDATED, force, null, day(force), null));
  }

  /**
   * The acts that made the text of a version in a language are the acts and corrigenda that the
   * Publications Office lists for the version, and that change its text in that language. An act
   * changes the text in a language unless each of its amendments is for other languages only. A
   * corrigendum counts only if the Publications Office publishes it in that language. For example,
   * the version of 2015/1998 of 12 April 2022 lists 2022/463, which changes only the German text:
   * in English no act made that text.
   *
   * @return the acts, in the order of the Publications Office
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "the-acts-that-made-a-text-in-the-language")
  public static List<ActIdentifier> theActsThatMadeATextInTheLanguage(
      Act act, Version version, Language language) {
    return version.integrates().stream()
        .filter(each ->
            each.corrigendum()
                ? act.publication(each).flatMap(p -> p.title(language)).isPresent()
                : TheHistoryOfAnAct.inLanguage(act, each, language))
        .toList();
  }

  /**
   * The text of a day is not the law for the act itself, while its application is deferred, and for
   * each prospective and each pending amendment of the day, dated or undated. No other rule decides
   * this.
   *
   * @param deferred the deferred application of the act on the day, or null if it has none
   * @param divergences each divergence of the act
   * @param day the day
   */
  @BusinessRule(
      group = RuleGroup.THE_TEXT_AND_THE_LAW_ON_A_DAY,
      name = "where-the-text-of-a-day-is-not-the-law")
  public static NotTheLaw whereTheTextOfADayIsNotTheLaw(
      @Nullable DeferredApplication deferred, List<Divergence> divergences, LocalDate day) {
    return new NotTheLaw(deferred, divergences.stream().filter(each -> each.on(day)).toList());
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

  private static LocalDate day(ActDate date) {
    return Objects.requireNonNull(date.date());
  }
}
