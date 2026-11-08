// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Marker;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The sidenote of a difference. It shows beside the note of the difference and says who made the
 * difference.
 *
 * <p>The sidenote names the acts between the two versions that made the difference. The amendment
 * markers on the unit in the later text name these acts, or the amendments of the unit in Cellar
 * name them. The markers name only the last act that changed a unit. For a deleted unit, the
 * markers name one of these acts:
 *
 * <ul>
 *   <li>the act of the deletion that keeps the unit struck
 *   <li>the act that replaced, as a whole, the unit that the deleted unit was in
 * </ul>
 *
 * <p>The sidenote shows the acts as a chain, in the order that the versions integrate them. For
 * example, "Inserted by 2019/103, then replaced by 2021/255". For each act, the sidenote says when
 * the act applies. This date comes from the dates of the act, never from the date of the version.
 * The sidenote also says what the unit was before, from the markers in the earlier text.
 *
 * <p>Acts of one version leave no text between their changes. Thus:
 *
 * <ul>
 *   <li>Where the markers name an act of a version, the sidenote follows the markers. It leaves out
 *       the other acts of that version that Cellar names.
 *   <li>The acts that the markers name together changed the unit together, with one wording. The
 *       sidenote says so. When the comparison is limited to one of these acts, the sidenote says
 *       that the change is their combined change.
 *   <li>If only Cellar names an act, the sidenote says so.
 *   <li>If neither the markers nor Cellar name an act between the two versions, the sidenote does
 *       not guess. If the acts between the versions are all of one version, it says that one of
 *       them made the difference, but not which. If not, it says that no act is named.
 * </ul>
 *
 * <p>An act can have several dates. The texts do not say from which of these dates a provision
 * applies. The amendment of the unit in Cellar chooses the date, if its day is one of the dates of
 * the act and is after the earlier version. The earlier version does not have the change. Otherwise
 * the sidenote says that the act applies in stages, and that the clause says which part applies
 * from when.
 *
 * @param made who made the difference. For example:
 *     <ul>
 *       <li>"Replaced by 2026/449"
 *       <li>"Inserted by 2019/103, then replaced by 2021/255"
 *       <li>"Replaced by 2019/103 and 2019/1583 together"
 *       <li>"Amended by an act not known: 2019/103 or 2019/1583"
 *       <li>"Deleted by an act not named", where nothing names an act between the two versions
 *     </ul>
 *
 * @param acts the acts that made the difference, in the order that the versions integrate them. The
 *     difference counts in the changes of each of these acts.
 * @param candidates the acts of which one made the difference, where it is not known which act.
 *     Empty where the act is known.
 * @param why what the reader should know about who made the difference. For example, that acts made
 *     it together, that only Cellar names the act, or that the unit is new in a unit that an act
 *     replaced as a whole. Null if there is nothing to say.
 * @param dates when each of these acts applies, with what sets the date
 * @param before what the unit was before. For example, "Before: as replaced by 2023/566". Null for
 *     an inserted unit.
 * @param published how the acts that made the difference, or may have made it, were published, one
 *     line for each act. The hover of {@code made} lists them the same way. Null where no act is
 *     named.
 */
public record SidenoteView(
    @Nullable String made,
    List<ActIdentifier> acts,
    List<ActIdentifier> candidates,
    @Nullable String why,
    List<Applies> dates,
    @Nullable String before,
    @Nullable String published) {

  public SidenoteView {
    acts = List.copyOf(acts);
    candidates = List.copyOf(candidates);
    dates = List.copyOf(dates);
  }

  /**
   * Returns whether only corrigenda made the difference. If so, the difference is a correction, not
   * an amendment, and the marks show it differently.
   */
  public boolean corrected() {
    return !acts.isEmpty() && acts.stream().allMatch(ActIdentifier::corrigendum);
  }

  /**
   * Returns the sidenote of {@code difference}, for a comparison that is not limited to the changes
   * of one act.
   */
  public static SidenoteView of(
      Act act,
      Version from,
      Version to,
      Difference difference,
      List<Amendment> amendments,
      Language language) {
    return of(act, from, to, difference, amendments, null, language);
  }

  /**
   * Returns the sidenote of {@code difference}, in the comparison of the version {@code from} of
   * {@code act} with the version {@code to}.
   *
   * @param amendments the amendments of the unit in Cellar, by the acts between the two versions.
   *     {@code Amended.of} finds them.
   * @param only the act whose changes the comparison is limited to, if any
   * @param language the language of the links
   */
  public static SidenoteView of(
      Act act,
      Version from,
      Version to,
      Difference difference,
      List<Amendment> amendments,
      @Nullable ActIdentifier only,
      Language language) {
    var between = act.integratedBetween(from, to);
    var marked = acts(difference.markers(), between::contains);
    var markedVersions =
        marked.keySet().stream()
            .map(each -> version(act, from, to, each))
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
    var made = new LinkedHashMap<>(marked);
    var named = new LinkedHashMap<ActIdentifier, Amendment>();
    for (var amendment : amendments) {
      var each = amendment.act();
      // Leave out the act if the markers name an act of the same version. The markers take
      // priority.
      if (between.contains(each)
          && !made.containsKey(each)
          && version(act, from, to, each).filter(markedVersions::contains).isEmpty()) {
        made.put(each, action(amendment.role()));
        named.put(each, amendment);
      }
    }
    var before =
        difference.kind() == Kind.INSERTED
            ? null
            : before(acts(difference.earlierMarkers(), a -> true));
    if (made.isEmpty()) {
      return unknown(act, from, to, difference, only, before, language);
    }
    var steps = steps(act, from, to, made.keySet(), marked.keySet(), together(difference, between));
    var dates = new ArrayList<Applies>();
    for (var each : steps.stream().flatMap(List::stream).toList()) {
      if (!each.corrigendum()) {
        var days =
            amendments.stream()
                .filter(amendment -> amendment.act().equals(each))
                .map(Amendment::from)
                .filter(Objects::nonNull)
                // A day that is not after the earlier text is stale, because the earlier text
                // does not have the change. The points of 2019/103 are an example.
                .filter(day -> day.isAfter(from.date()))
                .collect(Collectors.toSet());
        applies(act, each, days, language).ifPresent(dates::add);
      }
    }
    var chain = new ArrayList<String>();
    var why = new ArrayList<String>();
    var newIn = newIn(difference);
    newIn.ifPresent(why::add);
    for (var i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      var first = step.getFirst();
      // An inserted unit was inserted in the first step. A deleted unit was deleted in the last
      // step. If words were deleted from a unit that is still there, the unit was amended.
      var kind =
          i == 0 && difference.kind() == Kind.INSERTED
              ? Kind.INSERTED
              : i == steps.size() - 1 && difference.kind() == Kind.DELETED
                  ? Kind.DELETED
                  : Kind.CHANGED;
      var verb = verb(kind, first, Objects.requireNonNull(made.get(first)));
      verb = i == 0 ? verb : verb.toLowerCase(Locale.ROOT);
      if (step.size() == 1) {
        chain.add(verb + " by " + names(step));
      } else if (only != null && step.contains(only)) {
        var others = step.stream().filter(each -> !each.equals(only)).toList();
        chain.add(verb + " by " + only.cited() + ", together with " + names(others));
        why.add(combined(only, others, difference, newIn.isPresent()));
      } else {
        chain.add(verb + " by " + names(step) + " together");
        why.add(together(step, difference, newIn.isPresent()));
      }
    }
    if (marked.isEmpty()) {
      // One sentence for each place, naming all the acts that the data names for it
      var byPlace = new LinkedHashMap<String, List<ActIdentifier>>();
      named.forEach(
          (each, amendment) ->
              byPlace
                  .computeIfAbsent(amendment.location().name(), place -> new ArrayList<>())
                  .add(each));
      byPlace.forEach(
          (place, acts) ->
              why.add(
                  "The text has no marker here; the Publications Office’s data names "
                      + names(acts)
                      + " for "
                      + place
                      + "."));
    }
    return new SidenoteView(
        String.join(", then ", chain),
        steps.stream().flatMap(List::stream).toList(),
        List.of(),
        // Each step of acts together says why their change can't be split: say it once
        why.isEmpty() ? null : String.join(" ", new LinkedHashSet<>(why)),
        dates,
        before,
        act.citations(steps.stream().flatMap(List::stream).toList(), language));
  }

  /**
   * Returns the sidenote of a difference for which no act is named. If the acts between the two
   * versions are all of one version, the sidenote says that one of them made it, but not which. If
   * not, the sidenote does not say who made it.
   */
  private static SidenoteView unknown(
      Act act,
      Version from,
      Version to,
      Difference difference,
      @Nullable ActIdentifier only,
      @Nullable String before,
      Language language) {
    var candidates =
        act.integratedBetween(from, to).stream().filter(each -> !each.corrigendum()).toList();
    var versions =
        candidates.stream()
            .map(each -> version(act, from, to, each))
            .flatMap(Optional::stream)
            .distinct()
            .count();
    if (candidates.size() < 2 || versions != 1) {
      return new SidenoteView(
          verb(difference) + " by an act not named",
          List.of(),
          List.of(),
          "Neither the text’s markers nor the Publications Office’s data name an act between"
              + " these versions for it.",
          List.of(),
          before,
          null);
    }
    var ordered =
        version(act, from, to, candidates.getFirst()).orElseThrow().integrates().stream()
            .filter(candidates::contains)
            .toList();
    var names = ordered.stream().map(ActIdentifier::cited).toList();
    var verb = verb(difference);
    var dates = new ArrayList<Applies>();
    for (var each : ordered) {
      applies(act, each, Set.of(), language).ifPresent(dates::add);
    }
    return new SidenoteView(
        verb
            + " by an act not known: "
            + String.join(", ", names.subList(0, names.size() - 1))
            + " or "
            + names.getLast(),
        List.of(),
        ordered,
        only != null && ordered.contains(only)
            ? "May be "
                + only.cited()
                + "’s change: neither the text’s marker nor the Publications Office’s data says"
                + " which act made it."
            : "Neither the text’s marker nor the Publications Office’s data says which of them"
                + " made it.",
        dates,
        before,
        act.citations(ordered, language));
  }

  /**
   * Returns what happened to {@code difference}, as the first word of the made line: "Inserted",
   * "Deleted" or "Amended".
   */
  private static String verb(Difference difference) {
    return switch (difference.kind()) {
      case INSERTED -> "Inserted";
      case DELETED -> "Deleted";
      case CHANGED -> "Amended";
    };
  }

  /**
   * Returns the text that says that the acts of {@code step} made the difference together. The text
   * always says "There is one wording, so the change can’t be split between them." Before it, the
   * text says what each act did, if their markers say they did different things. For example,
   * "2019/1583 replaced the point, and 2019/103 replaced section 11.1 as a whole." It does not say
   * this if {@code newIn} is true, because the unit is new in a unit that the acts replaced as a
   * whole, and that text says so already.
   */
  private static String together(List<ActIdentifier> step, Difference difference, boolean newIn) {
    var parts = new ArrayList<String>();
    var done = new HashSet<String>();
    for (var marker : difference.markers()) {
      var each = new ActIdentifier(marker.act());
      if (step.contains(each)) {
        var what =
            marker.action()
                + " "
                + (marker.in() == null
                    ? "the " + noun(difference)
                    : unit(marker.in(), difference) + " as a whole");
        done.add(what);
        parts.add(each.cited() + " " + what);
      }
    }
    var split = "There is one wording, so the change can’t be split between them.";
    return newIn || done.size() < 2 ? split : String.join(", and ", parts) + ". " + split;
  }

  /**
   * Returns the text that says that the unit is new in, or not in, a unit that was replaced as a
   * whole. The acts that its markers name replaced that unit.
   *
   * <ul>
   *   <li>For an inserted unit, for example: "2019/103 replaced section 11.1 as a whole; this point
   *       is new in it."
   *   <li>For a deleted unit, for example: "2017/815 replaced Part 10 as a whole; this point is not
   *       in it."
   *   <li>For a changed unit, returns empty.
   * </ul>
   */
  private static Optional<String> newIn(Difference difference) {
    if (difference.kind() == Kind.CHANGED) {
      return Optional.empty();
    }
    var whole =
        difference.markers().stream()
            .filter(marker -> marker.in() != null && marker.action().equals("replaced"))
            .toList();
    if (whole.isEmpty()) {
      return Optional.empty();
    }
    var acts = whole.stream().map(marker -> new ActIdentifier(marker.act())).distinct().toList();
    return Optional.of(
        names(acts)
            + " replaced "
            + unit(Objects.requireNonNull(whole.getFirst().in()), difference)
            + " as a whole; this "
            + noun(difference)
            + (difference.kind() == Kind.INSERTED ? " is new in it." : " is not in it."));
  }

  /**
   * Returns the text that says that the change shown is the combined change of the acts, when the
   * comparison is limited to the changes of {@code only}. For example, "Together with 2019/103.
   * Both acts changed this point in the same version, and the wording between their changes exists
   * in no version…". If {@code newIn} is true, the text says only that the wording between their
   * changes exists in no version. This is because the unit is new in a unit that the acts replaced
   * as a whole, and that text says the rest already.
   */
  private static String combined(
      ActIdentifier only, List<ActIdentifier> others, Difference difference, boolean newIn) {
    var acts = others.size() == 1 ? "Both acts" : "All " + (others.size() + 1) + " acts";
    return "Together with "
        + names(others)
        + (newIn
            ? ": the wording between their changes"
            : ". "
                + acts
                + " changed this "
                + noun(difference)
                + " in the same version, and the"
                + " wording between their changes")
        + " exists in no version, so this is their combined change, not "
        + only.cited()
        + "’s part alone.";
  }

  /**
   * Returns the name of the unit of the difference, as a sentence uses it. For example, "point" or
   * "article". Other units are a "provision".
   */
  private static String noun(Difference difference) {
    var kind = difference.unit().kind();
    return switch (kind) {
      case "point", "article", "paragraph", "subparagraph", "indent", "annex", "recital" -> kind;
      // A numbered point that holds other points. For example, 11.1.2.
      case "level" -> difference.unit().num().matches("\\d.*") ? "point" : "provision";
      default -> "provision";
    };
  }

  /**
   * Returns the unit {@code eId} that {@code difference} is in, as a sentence cites it. For
   * example:
   *
   * <ul>
   *   <li>"section 11.1", if the unit is the section of the difference in the outline
   *   <li>"point 11.1"
   *   <li>"Article 5"
   * </ul>
   */
  private static String unit(String eId, Difference difference) {
    var last = eId.substring(eId.lastIndexOf('/') + 1);
    var number = last.substring(last.indexOf('_') + 1);
    if (eId.equals(difference.section()) && last.startsWith("pnt_")) {
      return "section " + number;
    }
    if (last.startsWith("art_")) {
      return "Article " + number;
    }
    if (last.startsWith("anx")) {
      return last.contains("_") ? "Annex " + number : "the annex";
    }
    if (last.startsWith("level_PART-")) {
      return "Part " + last.substring("level_PART-".length());
    }
    if (last.startsWith("level_ATTACHMENT-")) {
      return "Appendix " + last.substring("level_ATTACHMENT-".length());
    }
    return (last.startsWith("par_") ? "paragraph " : "point ") + number;
  }

  /**
   * Returns the first version after {@code from}, up to {@code to}, that integrates {@code
   * amending}.
   */
  private static Optional<Version> version(
      Act act, Version from, Version to, ActIdentifier amending) {
    return act.timeline().versions().stream()
        .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
        .filter(v -> v.integrates().contains(amending))
        .findFirst();
  }

  /**
   * Returns the acts that {@code markers} name and that {@code kept} accepts, in order. Each act
   * comes with the action of its first marker.
   */
  private static Map<ActIdentifier, String> acts(
      List<Marker> markers, Predicate<ActIdentifier> kept) {
    var acts = new LinkedHashMap<ActIdentifier, String>();
    for (var marker : markers) {
      var act = new ActIdentifier(marker.act());
      if (kept.test(act)) {
        acts.putIfAbsent(act, marker.action());
      }
    }
    return acts;
  }

  /**
   * Returns the acts in {@code acts} in the order that the versions after {@code from}, up to
   * {@code to}, first integrate them. There is one step for each version, which holds the acts of
   * that version.
   */
  private static List<List<ActIdentifier>> steps(
      Act act,
      Version from,
      Version to,
      Collection<ActIdentifier> acts,
      Collection<ActIdentifier> marked,
      List<Set<ActIdentifier>> together) {
    var between =
        act.timeline().versions().stream()
            .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
            .toList();
    // The version of each act is the first version that has it. An act that applies in stages is
    // in several versions.
    var at = new HashMap<ActIdentifier, Version>();
    for (var each : acts) {
      if (!marked.contains(each)) {
        between.stream()
            .filter(v -> v.integrates().contains(each))
            .findFirst()
            .ifPresent(version -> at.put(each, version));
      }
    }
    // An act that the markers name was the last act to change the unit. Thus it is in no version
    // before the versions of the other acts. If it changed the unit together with other acts, it is
    // in the first version that has all of them.
    var floor = at.values().stream().map(Version::date).max(Comparator.naturalOrder()).orElse(null);
    for (var each : marked) {
      var with =
          together.stream()
              .filter(group -> group.contains(each))
              .findFirst()
              .orElseGet(() -> Set.of(each));
      var after = between.stream().filter(v -> floor == null || !v.date().isBefore(floor)).toList();
      after.stream()
          .filter(v -> v.integrates().containsAll(with))
          .findFirst()
          .or(() -> after.stream().filter(v -> v.integrates().contains(each)).findFirst())
          .or(() -> between.stream().filter(v -> v.integrates().contains(each)).findFirst())
          .ifPresent(version -> at.put(each, version));
    }
    var steps = new ArrayList<List<ActIdentifier>>();
    for (var version : between) {
      // Keep the order that the version gives the acts.
      var step =
          version.integrates().stream()
              .filter(each -> acts.contains(each) && version.equals(at.get(each)))
              .toList();
      if (!step.isEmpty()) {
        steps.add(step);
      }
    }
    return steps;
  }

  /**
   * Returns the acts between the two versions that replaced the unit together, by its markers. Two
   * acts that replaced the same unit as a whole did so at the same time. The unit can be the unit
   * itself or the unit it is in. If one act replaced the unit after the other, only its own marker
   * would be there. Each set has two or more acts.
   */
  private static List<Set<ActIdentifier>> together(
      Difference difference, Collection<ActIdentifier> between) {
    var byUnit = new LinkedHashMap<Optional<String>, Set<ActIdentifier>>();
    for (var marker : difference.markers()) {
      var each = new ActIdentifier(marker.act());
      if (marker.action().equals("replaced") && between.contains(each)) {
        byUnit
            .computeIfAbsent(Optional.ofNullable(marker.in()), in -> new LinkedHashSet<>())
            .add(each);
      }
    }
    return byUnit.values().stream().filter(acts -> acts.size() > 1).toList();
  }

  /**
   * Returns what the role of an amendment did, as the action of a marker says it: "replaced",
   * "inserted" or "deleted".
   */
  private static String action(Amendment.Role role) {
    return switch (role) {
      case REPLACEMENT -> "replaced";
      case DELETION, REPEAL -> "deleted";
      default -> "inserted";
    };
  }

  /**
   * Returns the numbers of the acts as a list in words. For example, "2019/103 and 2019/1583", or
   * "2017/815, 2019/103 and 2019/1583".
   */
  private static String names(Collection<ActIdentifier> acts) {
    var names = acts.stream().map(ActIdentifier::cited).toList();
    return names.size() == 1
        ? names.getFirst()
        : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
  }

  /**
   * Returns what {@code act} did to the unit, as a difference of {@code kind} shows it.
   *
   * <ul>
   *   <li>"Inserted", "Deleted", "Replaced" or "Renumbered"
   *   <li>"Amended", for words that the act inserted or deleted in a unit that is still there
   *   <li>"Corrected", for a corrigendum
   * </ul>
   */
  private static String verb(Kind kind, ActIdentifier act, String action) {
    if (act.corrigendum()) {
      return "Corrected";
    }
    return switch (kind) {
      case INSERTED -> "Inserted";
      case DELETED -> "Deleted";
      case CHANGED ->
          switch (action) {
            case "replaced" -> "Replaced";
            case "renumbered" -> "Renumbered";
            default -> "Amended";
          };
    };
  }

  /**
   * Returns what the unit was before, from the acts that the markers of the earlier text name. For
   * example, "Before: as replaced by 2023/566". If the markers name no act, returns "Before:
   * original act".
   */
  private static String before(Map<ActIdentifier, String> acts) {
    if (acts.isEmpty()) {
      return "Before: original act";
    }
    var first = acts.entrySet().iterator().next();
    var done =
        first.getKey().corrigendum()
            ? "corrected"
            : switch (first.getValue()) {
              case "replaced", "inserted", "deleted", "renumbered" -> first.getValue();
              default -> "amended";
            };
    return "Before: as " + done + " by " + names(acts.keySet());
  }

  /**
   * Returns when {@code amending} applies, from its own dates.
   *
   * <ul>
   *   <li>If it has one date of application, it applies from that date. If it has no date of
   *       application, it applies from its date of entry into force.
   *   <li>If it has several dates, it applies from the date that the amendments of the unit in
   *       Cellar have. {@code days} holds these dates.
   *   <li>If it has several dates or applies in part, and Cellar does not choose a date, it applies
   *       in stages.
   *   <li>If Cellar has no day for its date of application, it applies from a date that its clause
   *       sets.
   *   <li>If Cellar has no dates of the act, returns empty.
   * </ul>
   */
  private static Optional<Applies> applies(
      Act act, ActIdentifier amending, Set<LocalDate> days, Language language) {
    var dates = act.datesOf(amending);
    if (dates.isEmpty()) {
      return Optional.empty();
    }
    var name = amending.cited();
    var dated = dates.stream().filter(date -> date.date() != null).toList();
    var application = dates.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE);
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    // Cellar has only a placeholder for a date of application, or no dates with a day.
    if (dated.isEmpty() || (applying.isEmpty() && application.findAny().isPresent())) {
      var undated = dates.stream().filter(date -> date.date() == null).toList();
      var set = applies(act, name + " applies from a date set in", undated, amending, language, "");
      // There is nothing to say if no clause sets the date.
      return set.clause() == null ? Optional.empty() : Optional.of(set);
    }
    // Use the date of the act that Cellar has for the unit.
    if (days.size() == 1) {
      var day = days.iterator().next();
      var on = dated.stream().filter(date -> day.equals(date.date())).toList();
      if (!on.isEmpty()) {
        return Optional.of(
            applies(
                act,
                name
                    + " applies from "
                    + Dates.inWords(day)
                    + (on.stream().allMatch(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE)
                        ? ", its entry into force"
                        : ""),
                on,
                amending,
                language,
                ""));
      }
    }
    var applies = days(applying.isEmpty() ? dated : applying);
    if (applies.size() == 1 && applying.stream().noneMatch(ActDate::partial)) {
      var day = Dates.inWords(applies.getFirst());
      return Optional.of(
          applies(
              act,
              name + " applies from " + day + (applying.isEmpty() ? ", its entry into force" : ""),
              applying.isEmpty() ? dated : applying,
              amending,
              language,
              ""));
    }
    var stages = new ArrayList<String>();
    for (var day : days(dated)) {
      var on = dated.stream().filter(date -> day.equals(date.date())).toList();
      var from = "from " + Dates.inWords(day);
      stages.add(
          on.stream().allMatch(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE)
              ? from + ", its entry into force"
              : on.stream().allMatch(ActDate::partial) ? "in part " + from : from);
    }
    return Optional.of(
        applies(
            act,
            name + " applies in stages: " + String.join("; ", stages),
            dated,
            amending,
            language,
            " says which part from when"));
  }

  private static Applies applies(
      Act act,
      String when,
      List<ActDate> dates,
      ActIdentifier amending,
      Language language,
      String after) {
    var provisions = new LinkedHashSet<ActDate.Provision>();
    var amendedBy = new LinkedHashSet<ActIdentifier>();
    for (var date : dates) {
      provisions.addAll(date.clause().provisions());
      amendedBy.addAll(date.clause().amendedBy());
    }
    var clause =
        provisions.isEmpty()
            ? null
            : new Link(
                provisions.stream().map(ActDate.Provision::cited).collect(Collectors.joining(", "))
                    + " of "
                    + amending.cited(),
                act.eurLex(amending, language).toString());
    return new Applies(
        when,
        when.endsWith(" in") ? " " : " · ",
        clause,
        amendedBy.stream()
            .map(each -> new Link(each.cited(), act.eurLex(each, language).toString()))
            .toList(),
        clause == null ? "" : after);
  }

  /** Returns the distinct days of {@code dates}, in order. */
  private static List<LocalDate> days(List<ActDate> dates) {
    return dates.stream().map(ActDate::date).filter(Objects::nonNull).distinct().sorted().toList();
  }

  /**
   * When an act applies, as the sidenote says it. For example, "applies from 1 October 2026 ·
   * Article 2 of 2026/449, as amended by 2020/910".
   *
   * @param when when the act applies. For example, "applies from 1 October 2026", or "2026/449
   *     applies in stages: from 22 March 2026, its entry into force; in part from 1 October 2026".
   * @param between what goes between {@code when} and the clause. This is a dot, or a space after
   *     "set in".
   * @param clause the clause that sets the date, linked to EUR-Lex. For example, "Article 2 of
   *     2026/449". Null where Cellar does not say.
   * @param amendedBy the acts that changed the clause since, linked to EUR-Lex. For example,
   *     2020/910.
   * @param after what follows the clause. For stages, this is "says which part from when".
   */
  public record Applies(
      String when, String between, @Nullable Link clause, List<Link> amendedBy, String after) {
    public Applies {
      amendedBy = List.copyOf(amendedBy);
    }
  }

  /** A link, with its words and its address. */
  public record Link(String label, String href) {}

  /**
   * Returns the history of a provision that is the same in both versions. The Synopsis shows it
   * beside the provision.
   *
   * <p>The history names the act that the versions of {@code act} integrated last, among the acts
   * that the amendment markers in {@code to} name. The markers are those of the provision, or those
   * of the unit that an act put in its form as a whole. {@link Marker} has them. Corrigenda are
   * left out. The history says what the act did and when it applies.
   *
   * <p>If no act changed the provision, the history says "As in the original act". It then applies
   * from the dates of the act, or else from the first version of the act.
   */
  public static History history(Act act, Version to, List<Marker> markers, Language language) {
    @Nullable Marker last = null;
    @Nullable LocalDate integrated = null;
    for (var marker : markers) {
      var each = new ActIdentifier(marker.act());
      if (each.corrigendum()) {
        continue;
      }
      var on =
          act.timeline().versions().stream()
              .filter(version -> !version.date().isAfter(to.date()))
              .filter(version -> version.integrates().contains(each))
              .map(Version::date)
              .min(LocalDate::compareTo);
      if (on.isPresent() && (integrated == null || on.get().isAfter(integrated))) {
        last = marker;
        integrated = on.get();
      }
    }
    if (last == null) {
      var first = act.timeline().versions().getFirst();
      return new History(
          "As in the original act",
          applies(act, act.id(), Set.of(), language)
              .map(applies -> unnamed(applies, act.id()))
              .orElseGet(
                  () ->
                      new Applies(
                          "applies from " + Dates.inWords(first.date()), "", null, List.of(), "")));
    }
    var each = new ActIdentifier(last.act());
    return new History(
        "Last changed by " + each.cited() + " (" + last.action() + ")",
        applies(act, each, Set.of(), language).map(applies -> unnamed(applies, each)).orElse(null));
  }

  /**
   * Returns {@code applies} without the act at the start of its date line, because the line before
   * names the act already. For example, "Last changed by 2019/103 (replaced) · applies from 1
   * February 2019".
   */
  private static Applies unnamed(Applies applies, ActIdentifier act) {
    var name = act.cited() + " ";
    return applies.when().startsWith(name)
        ? new Applies(
            applies.when().substring(name.length()),
            applies.between(),
            applies.clause(),
            applies.amendedBy(),
            applies.after())
        : applies;
  }

  /**
   * The history of a provision, as the Synopsis shows it beside a provision that is the same in
   * both versions.
   *
   * @param said what last made the provision as it is. For example, "Last changed by 2019/103
   *     (replaced)" or "As in the original act".
   * @param applies when that change applies, with what sets the date. Null where nothing says.
   */
  public record History(String said, @Nullable Applies applies) {}
}
