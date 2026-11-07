// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
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
 * Who made a difference, as its sidenote says it beside its note (Design: Mode: Compare, Sidenote):
 * the acts between the two versions that the amendment markers on the unit in the later text or
 * Cellar's amendments of it name (the markers name only the last act to change a unit; a deleted
 * one's, the deletion it is kept struck under or the unit it was in replaced whole), as a chain in
 * the order the versions integrate them: Inserted by 2019/103, then replaced by 2021/255. Each with
 * when it applies, from its own dates (never the version's); and what the unit was before, by the
 * markers in the earlier text.
 *
 * <p>Acts of one version (Design: Acts sharing a version) leave no text between their changes:
 * where the markers name an act of a version, Cellar's other acts of it are left out (the markers
 * are followed); the acts the markers name together changed it together, one wording, which the
 * sidenote says, and, limited to one of them, that the change is theirs combined. An act only
 * Cellar names is said to be. Where neither names an act between them, and those acts are all of
 * one version, it was one of them, not known which; across versions, that no act is named, rather
 * than guess.
 *
 * <p>Which of an act's dates a provision applies from, when it has several, isn't in the texts:
 * Cellar's amendment of the unit chooses it, where its day is one of them and after the earlier
 * version, which hasn't the change (Research: where a provision's date comes from); otherwise the
 * sidenote says the act applies in stages, and that the clause says which part from when.
 *
 * @param made who made it: Replaced by 2026/449; Inserted by 2019/103, then replaced by 2021/255;
 *     Replaced by 2019/103 and 2019/1583 together; Amended by an act not known: 2019/103 or
 *     2019/1583; Deleted by an act not named, where nothing names one between the two versions
 * @param acts the acts that made it, in the order the versions integrate them: whose changes it
 *     counts in
 * @param candidates the acts one of which made it, not known which; none where it is known
 * @param why what the reader should know of who made it: that acts made it together, that only
 *     Cellar names the act, that it is new in a unit an act replaced whole; none if nothing
 * @param dates when each of those acts applies, with what sets it
 * @param before what the unit was before: Before: as replaced by 2023/566; none for an inserted one
 * @param published how the acts that made it, or might have, were published, a line each, as made's
 *     hover lists them (Design D-42); none where no act is named
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
   * Whether corrigenda alone made it: a correction, not an amendment, which the marks show apart
   * (Design D-31).
   */
  public boolean corrected() {
    return !acts.isEmpty() && acts.stream().allMatch(Wording::corrigendum);
  }

  /** The same, the comparison not limited to an act's changes. */
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
   * The sidenote of {@code difference}, in the comparison of {@code act}'s version {@code from}
   * with {@code to}, with Cellar's {@code amendments} of its unit by the acts between them
   * (Amended.of), limited to the changes of {@code only}, if any, its links in {@code language}.
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
      // the markers name an act of its version: theirs is followed
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
      if (!Wording.corrigendum(each)) {
        var days =
            amendments.stream()
                .filter(amendment -> amendment.act().equals(each))
                .map(Amendment::from)
                .filter(Objects::nonNull)
                // a day the earlier text, without the change, is from is stale (2019/103's points)
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
      // an inserted unit was inserted first; a deleted one, deleted last (a unit still there had
      // words deleted from it: amended)
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
        chain.add(verb + " by " + Wording.act(only) + ", together with " + names(others));
        why.add(combined(only, others, difference, newIn.isPresent()));
      } else {
        chain.add(verb + " by " + names(step) + " together");
        why.add(together(step, difference, newIn.isPresent()));
      }
    }
    if (marked.isEmpty()) {
      named.forEach(
          (each, amendment) ->
              why.add(
                  "The text has no marker here; the Publications Office’s data names "
                      + Wording.act(each)
                      + " for "
                      + amendment.location().name()
                      + "."));
    }
    return new SidenoteView(
        String.join(", then ", chain),
        steps.stream().flatMap(List::stream).toList(),
        List.of(),
        why.isEmpty() ? null : String.join(" ", why),
        dates,
        before,
        Wording.published(act, steps.stream().flatMap(List::stream).toList(), language));
  }

  /**
   * The sidenote of a difference no act is named for: one of the acts between the two versions, not
   * known which, where they are all of one version; nothing of who made it, where they aren't.
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
        act.integratedBetween(from, to).stream()
            .filter(each -> !Wording.corrigendum(each))
            .toList();
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
    var names = ordered.stream().map(Wording::act).toList();
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
                + Wording.act(only)
                + "’s change: neither the text’s marker nor the Publications Office’s data says"
                + " which act made it."
            : "Neither the text’s marker nor the Publications Office’s data says which of them"
                + " made it.",
        dates,
        before,
        Wording.published(act, ordered, language));
  }

  /** What happened to {@code difference}, as a made line starts: Inserted, Deleted, Amended. */
  private static String verb(Difference difference) {
    return switch (difference.kind()) {
      case INSERTED -> "Inserted";
      case DELETED -> "Deleted";
      case CHANGED -> "Amended";
    };
  }

  /**
   * That {@code step}'s acts made it together: 2019/1583 replaced the point, and 2019/103 replaced
   * section 11.1 as a whole, where their markers say they did different things (but for one new in
   * a unit they replaced whole, which says so already: {@code newIn}). There is one wording, so the
   * change can’t be split between them.
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
        parts.add(Wording.act(each) + " " + what);
      }
    }
    var split = "There is one wording, so the change can’t be split between them.";
    return newIn || done.size() < 2 ? split : String.join(", and ", parts) + ". " + split;
  }

  /**
   * That an inserted unit is new in the unit the acts its markers name replaced whole: 2019/103
   * replaced section 11.1 as a whole; this point is new in it; that a deleted one is not in it
   * (2017/815 replaced Part 10 as a whole; this point is not in it). None for a changed one.
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
   * That, limited to {@code only}'s changes, the change shown is theirs combined: Together with
   * 2019/103. Both acts changed this point in the same version, and the wording between their
   * changes exists in no version…; for one new in a unit they replaced whole ({@code newIn}), which
   * says so already, the wording only.
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
        + Wording.act(only)
        + "’s part alone.";
  }

  /** What the difference's unit is, as a sentence calls it: point, article; provision. */
  private static String noun(Difference difference) {
    var kind = difference.unit().kind();
    return switch (kind) {
      case "point", "article", "paragraph", "subparagraph", "indent", "annex", "recital" -> kind;
      // a numbered point that holds others: 11.1.2
      case "level" -> difference.unit().num().matches("\\d.*") ? "point" : "provision";
      default -> "provision";
    };
  }

  /**
   * The unit {@code eId} that {@code difference} is in, as a sentence cites it: section 11.1, the
   * section it is in by the outline; point 11.1; Article 5.
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

  /** The version after {@code from}, up to {@code to}, that first integrates {@code amending}. */
  private static Optional<Version> version(
      Act act, Version from, Version to, ActIdentifier amending) {
    return act.timeline().versions().stream()
        .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
        .filter(v -> v.integrates().contains(amending))
        .findFirst();
  }

  /**
   * The acts {@code markers} name that {@code kept} keeps, in order, each with what it did first.
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
   * The acts in {@code acts} in the order the versions after {@code from}, up to {@code to}, first
   * integrate them: one step for each version, its acts together.
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
    // each act's version: the first that has it (an act that applies in stages is in several)
    var at = new HashMap<ActIdentifier, Version>();
    for (var each : acts) {
      if (!marked.contains(each)) {
        between.stream()
            .filter(v -> v.integrates().contains(each))
            .findFirst()
            .ifPresent(version -> at.put(each, version));
      }
    }
    // an act the markers name was the last to change it: in none before the others'; with those it
    // changed it together, in the first that has them all
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
      // in the version's order of them
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
   * The acts between the two versions that replaced the unit together, by its markers: two that
   * replaced the same unit whole (its own, or the one it is in) did so at once, as an act that
   * replaced it after another would have left only its own marker. Each set of two or more.
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

  /** What an amendment's role did, as a marker's action says it: replaced, inserted, deleted. */
  private static String action(Amendment.Role role) {
    return switch (role) {
      case REPLACEMENT -> "replaced";
      case DELETION, REPEAL -> "deleted";
      default -> "inserted";
    };
  }

  /** 2019/103 and 2019/1583; 2017/815, 2019/103 and 2019/1583. */
  private static String names(Collection<ActIdentifier> acts) {
    var names = acts.stream().map(Wording::act).toList();
    return names.size() == 1
        ? names.getFirst()
        : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
  }

  /**
   * What {@code act} did to the unit, as a difference of {@code kind} shows it: Inserted, Deleted,
   * Replaced, Renumbered; Amended, for words it inserted or deleted in a unit that is still there;
   * Corrected, for a corrigendum.
   */
  private static String verb(Kind kind, ActIdentifier act, String action) {
    if (Wording.corrigendum(act)) {
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
   * What the unit was before, by the acts the earlier text's markers name: Before: as replaced by
   * 2023/566; Before: original act, where none does.
   */
  private static String before(Map<ActIdentifier, String> acts) {
    if (acts.isEmpty()) {
      return "Before: original act";
    }
    var first = acts.entrySet().iterator().next();
    var done =
        Wording.corrigendum(first.getKey())
            ? "corrected"
            : switch (first.getValue()) {
              case "replaced", "inserted", "deleted", "renumbered" -> first.getValue();
              default -> "amended";
            };
    return "Before: as " + done + " by " + names(acts.keySet());
  }

  /**
   * When {@code amending} applies, from its own dates: from its one date of application, or of
   * entry into force if it has none; from the one of them Cellar's amendments of the unit have
   * ({@code days}), where it has several; in stages, where it has several or applies in part and
   * Cellar doesn't choose; from a date its clause sets, where Cellar has none; nothing, where
   * Cellar has no dates of it at all.
   */
  private static Optional<Applies> applies(
      Act act, ActIdentifier amending, Set<LocalDate> days, Language language) {
    var dates = act.datesOf(amending);
    if (dates.isEmpty()) {
      return Optional.empty();
    }
    var name = Wording.act(amending);
    var dated = dates.stream().filter(date -> date.date() != null).toList();
    var application = dates.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE);
    var applying =
        dated.stream().filter(date -> date.kind() != ActDate.Kind.ENTRY_INTO_FORCE).toList();
    // a date of application Cellar has only a placeholder for, or no dates with a day at all
    if (dated.isEmpty() || (applying.isEmpty() && application.findAny().isPresent())) {
      var undated = dates.stream().filter(date -> date.date() == null).toList();
      var set = applies(act, name + " applies from a date set in", undated, amending, language, "");
      // nothing to say where no clause says where
      return set.clause() == null ? Optional.empty() : Optional.of(set);
    }
    // the one of its dates Cellar has for the unit
    if (days.size() == 1) {
      var day = days.iterator().next();
      var on = dated.stream().filter(date -> day.equals(date.date())).toList();
      if (!on.isEmpty()) {
        return Optional.of(
            applies(
                act,
                name
                    + " applies from "
                    + Wording.date(day)
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
      var day = Wording.date(applies.getFirst());
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
      var from = "from " + Wording.date(day);
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
                provisions.stream().map(Wording::provision).collect(Collectors.joining(", "))
                    + " of "
                    + Wording.act(amending),
                Links.amending(act, amending, language));
    return new Applies(
        when,
        when.endsWith(" in") ? " " : " · ",
        clause,
        amendedBy.stream()
            .map(each -> new Link(Wording.act(each), Links.amending(act, each, language)))
            .toList(),
        clause == null ? "" : after);
  }

  /** The distinct days of {@code dates}, in order. */
  private static List<LocalDate> days(List<ActDate> dates) {
    return dates.stream().map(ActDate::date).filter(Objects::nonNull).distinct().sorted().toList();
  }

  /**
   * When an act applies, as the sidenote says it: applies from 1 October 2026 · Article 2 of
   * 2026/449, as amended by 2020/910.
   *
   * @param when when: applies from 1 October 2026; 2026/449 applies in stages: from 22 March 2026,
   *     its entry into force; in part from 1 October 2026
   * @param between what goes between when and the clause: a dot, or a space after set in
   * @param clause what sets it, on EUR-Lex: Article 2 of 2026/449; none where Cellar doesn't say
   * @param amendedBy the acts that changed it since, on EUR-Lex: 2020/910
   * @param after what follows the clause: says which part from when, for stages
   */
  public record Applies(
      String when, String between, @Nullable Link clause, List<Link> amendedBy, String after) {
    public Applies {
      amendedBy = List.copyOf(amendedBy);
    }
  }

  /** A link: its words and where it goes. */
  public record Link(String label, String href) {}

  /**
   * What a provision the same in both versions last was, as the Synopsis says beside it (Design:
   * Synopsis history column): the act among its amendment markers in {@code to} (its own, or those
   * of the unit an act put in its form whole, Difference.Marker) that {@code act}'s versions
   * integrated last, its corrigenda left out, with what it did and when it applies; as in the
   * original act where none did, applying from the act's first version.
   */
  public static History history(Act act, Version to, List<Marker> markers, Language language) {
    @Nullable Marker last = null;
    @Nullable LocalDate integrated = null;
    for (var marker : markers) {
      var each = new ActIdentifier(marker.act());
      if (Wording.corrigendum(each)) {
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
                          "applies from " + Wording.date(first.date()), "", null, List.of(), "")));
    }
    var each = new ActIdentifier(last.act());
    return new History(
        "Last changed by " + Wording.act(each) + " (" + last.action() + ")",
        applies(act, each, Set.of(), language).map(applies -> unnamed(applies, each)).orElse(null));
  }

  /**
   * {@code applies} without the act its date line starts with, where the line before names it
   * already: Last changed by 2019/103 (replaced) · applies from 1 February 2019.
   */
  private static Applies unnamed(Applies applies, ActIdentifier act) {
    var name = Wording.act(act) + " ";
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
   * A provision's history, as the Synopsis has it beside one the same in both versions.
   *
   * @param said what last made it as it is: Last changed by 2019/103 (replaced); As in the original
   *     act
   * @param applies when that applies, with what sets it; none where nothing says
   */
  public record History(String said, @Nullable Applies applies) {}
}
