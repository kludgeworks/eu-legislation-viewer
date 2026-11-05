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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Who made a difference, as its sidenote says it beside its note (Design: Mode: Compare, Sidenote):
 * the acts between the two versions that the amendment markers on the unit in the later text name,
 * or else, where none does (a deletion, which leaves none; a change the text doesn't mark), that
 * Cellar's amendments of the unit name; each with when it applies, from its own dates (never the
 * version's); and what the unit was before, by the markers in the earlier text. Where neither names
 * an act between them, it says nothing of who made it rather than guess.
 *
 * <p>Which of an act's dates a provision applies from, when it has several, isn't in the texts:
 * Cellar's amendment of the unit chooses it, where its day is one of them and after the earlier
 * version, which hasn't the change (Research: where a provision's date comes from); otherwise the
 * sidenote says the act applies in stages, and that the clause says which part from when.
 *
 * @param made who made it: Replaced by 2026/449; none where no marker names an act between the two
 *     versions
 * @param dates when each of those acts applies, with what sets it
 * @param before what the unit was before: Before: as replaced by 2023/566; none for an inserted one
 */
public record SidenoteView(@Nullable String made, List<Applies> dates, @Nullable String before) {

  public SidenoteView {
    dates = List.copyOf(dates);
  }

  /**
   * The sidenote of {@code difference}, in the comparison of {@code act}'s version {@code from}
   * with {@code to}, with Cellar's {@code amendments} of its unit by the acts between them
   * (Amended.of), its links in {@code language}.
   */
  public static SidenoteView of(
      Act act,
      Version from,
      Version to,
      Difference difference,
      List<Amendment> amendments,
      Language language) {
    var between = act.integratedBetween(from, to);
    var made = acts(difference.markers(), between::contains);
    if (made.isEmpty()) {
      for (var amendment : amendments) {
        if (between.contains(amendment.act())) {
          made.putIfAbsent(amendment.act(), action(amendment.role()));
        }
      }
    }
    var before =
        difference.kind() == Kind.INSERTED
            ? null
            : before(acts(difference.earlierMarkers(), a -> true));
    if (made.isEmpty()) {
      return new SidenoteView(null, List.of(), before);
    }
    var first = made.entrySet().iterator().next();
    var names = names(made.keySet());
    var dates = new ArrayList<Applies>();
    for (var each : made.keySet()) {
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
    return new SidenoteView(
        verb(difference.kind(), first.getKey(), first.getValue())
            + " by "
            + names
            + (made.size() > 1 ? " together" : ""),
        dates,
        before);
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
   * Replaced, Renumbered; Amended, for words it inserted in a unit that was there; Corrected, for a
   * corrigendum.
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
            case "deleted" -> "Deleted";
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
      var set = applies("applies from a date set in", undated, amending, language, "");
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
                "applies from "
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
              "applies from " + day + (applying.isEmpty() ? ", its entry into force" : ""),
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
            name + " applies in stages: " + String.join("; ", stages),
            dated,
            amending,
            language,
            " says which part from when"));
  }

  private static Applies applies(
      String when, List<ActDate> dates, ActIdentifier amending, Language language, String after) {
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
                Links.eurLex(amending.celex(), language));
    return new Applies(
        when,
        when.endsWith(" in") ? " " : " · ",
        clause,
        amendedBy.stream()
            .map(each -> new Link(Wording.act(each), Links.eurLex(each.celex(), language)))
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
}
