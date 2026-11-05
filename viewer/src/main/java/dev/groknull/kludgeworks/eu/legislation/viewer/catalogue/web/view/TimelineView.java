// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * An act's versions on a time axis, to scale, as its pages show them (Design: Timeline): from the
 * first day of the first version's year to the first of the year after the latest's. A version is a
 * mark at its first day, a link; versions too close to click apart (under GAP of the axis from the
 * one before) merge into one mark listing them. Each lag (Act.lags) is a band from the day the act
 * applies to the version that includes it. Reading (reading), the marks link to the versions'
 * pages; comparing (comparing), each links to the comparison with the nearer end moved to it, and
 * the range between the two is a band. While the act is prepared (preparing), the marks are each
 * version's text as it stands, links to nothing yet: grey queued, light being built, dark built,
 * dashed failed (Design: Preparing an act).
 *
 * @param years a mark for each new year on the axis
 * @param marks the versions, merged where too close, in date order
 * @param lags where a version's text lags the law
 * @param tags what is selected, above its mark: READING; FROM and TO
 * @param band the range compared; none when reading
 * @param current what a selected mark is, as aria-current has it: page reading, true comparing
 * @param hint what clicking a mark does
 * @param steps links to the versions either side
 */
public record TimelineView(
    List<Year> years,
    List<Mark> marks,
    List<Lag> lags,
    List<Tag> tags,
    @Nullable Band band,
    String current,
    String hint,
    List<Step> steps) {

  /** How close, as a share of the axis in percent, versions merge: 1.6, as the design draws it. */
  static final double GAP = 1.6;

  /** The narrowest a lag is drawn, in percent of the axis, so a lag of days still shows. */
  private static final double NARROWEST = 0.5;

  public TimelineView {
    years = List.copyOf(years);
    marks = List.copyOf(marks);
    lags = List.copyOf(lags);
    tags = List.copyOf(tags);
    steps = List.copyOf(steps);
  }

  /**
   * {@code act}'s timeline as the page of {@code reading} in {@code language} shows it: the
   * versions in that language, or all of them if the version being read isn't in it.
   */
  public static TimelineView reading(Act act, Version reading, Language language) {
    var eli = eli(act);
    var versions = versions(act, language, List.of(reading));
    var axis = Axis.of(versions);
    var index = versions.indexOf(reading);
    return new TimelineView(
        axis.years(),
        marks(versions, axis, reading::equals, v -> Links.read(eli, v, language)),
        lags(act, axis),
        List.of(new Tag("READING", axis.markAt(versions, reading))),
        null,
        "page",
        "Click a version to read it",
        List.of(
            new Step(
                "‹ version before",
                index > 0 ? Links.read(eli, versions.get(index - 1), language) : null),
            new Step(
                "version after ›",
                index < versions.size() - 1
                    ? Links.read(eli, versions.get(index + 1), language)
                    : null)));
  }

  /**
   * {@code act}'s timeline as the comparison of {@code from} with {@code to} in {@code language}
   * shows it, on {@code today}: the versions in that language, or all of them if either isn't in
   * it. A mark links to the comparison with the nearer end moved to it (nearer).
   */
  public static TimelineView comparing(
      Act act, Version from, Version to, Language language, LocalDate today) {
    var eli = eli(act);
    var versions = versions(act, language, List.of(from, to));
    var axis = Axis.of(versions);
    var at = axis.markAt(versions, from);
    var until = axis.markAt(versions, to);
    Function<Version, String> nearer =
        v -> {
          var ends = nearer(v, from, to);
          return Links.compare(eli, ends.getFirst(), ends.getLast(), language);
        };
    var first = versions.indexOf(from);
    var last = versions.indexOf(to);
    var current = act.versionOn(today).filter(versions::contains).orElseGet(versions::getLast);
    var original = versions.getFirst();
    return new TimelineView(
        axis.years(),
        marks(versions, axis, v -> v.equals(from) || v.equals(to), nearer),
        lags(act, axis),
        at == until
            ? List.of(new Tag(from.equals(to) ? "FROM = TO" : "FROM · TO", at))
            : List.of(new Tag("FROM", at), new Tag("TO", until)),
        new Band(at, until),
        "true",
        "Click a version to move the nearer end",
        List.of(
            new Step(
                "‹ both one version earlier",
                first > 0
                    ? Links.compare(eli, versions.get(first - 1), versions.get(last - 1), language)
                    : null),
            new Step(
                "both one version later ›",
                last < versions.size() - 1
                    ? Links.compare(eli, versions.get(first + 1), versions.get(last + 1), language)
                    : null),
            new Step(
                "compare “to” with the version before",
                last > 0 && first != last - 1
                    ? Links.compare(eli, versions.get(last - 1), to, language)
                    : null),
            new Step(
                "compare the original with the current version",
                !(from.equals(original) && to.equals(current)) && !original.equals(current)
                    ? Links.compare(eli, original, current, language)
                    : null)));
  }

  /** {@code act}'s timeline while {@code preparation} is under way: each version's text's state. */
  public static TimelineView preparing(Act act, Preparation preparation) {
    var versions = preparation.versions();
    var axis = Axis.of(versions);
    var marks = new ArrayList<Mark>();
    for (var group : groups(versions, axis)) {
      var at = axis.at(group);
      var ticks =
          group.stream()
              .map(
                  v ->
                      new Tick(
                          at,
                          false,
                          null,
                          label(v),
                          state(Objects.requireNonNull(preparation.texts().get(v)))))
              .toList();
      marks.add(ticks.size() == 1 ? ticks.getFirst() : new Merged(at, ticks, merged(group)));
    }
    return new TimelineView(
        axis.years(),
        marks,
        lags(act, axis),
        List.of(),
        null,
        "page",
        "Being prepared: the versions open once it is",
        List.of());
  }

  /** A text's state, as a tick is drawn. */
  private static String state(Text text) {
    return switch (text) {
      case Text.Queued _ -> "queued";
      case Text.Building _ -> "building";
      case Text.Ready _ -> "ready";
      case Text.Failed _ -> "failed";
    };
  }

  /**
   * The comparison clicking {@code version} asks for, from {@code from} to {@code to}: the nearer
   * end moved to it (one before From moves From, one after To moves To, one between moves the end
   * fewer days away, From on a tie), as [from, to].
   */
  static List<Version> nearer(Version version, Version from, Version to) {
    var date = version.date();
    if (date.isBefore(from.date())) {
      return List.of(version, to);
    }
    if (date.isAfter(to.date())) {
      return List.of(from, version);
    }
    return ChronoUnit.DAYS.between(from.date(), date) <= ChronoUnit.DAYS.between(date, to.date())
        ? List.of(version, to)
        : List.of(from, version);
  }

  private static Eli eli(Act act) {
    return Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
  }

  /** The versions in {@code language}, or all of them if any of {@code shown} isn't in it. */
  private static List<Version> versions(Act act, Language language, List<Version> shown) {
    return act.timeline()
        .in(language)
        .map(Timeline::versions)
        .filter(those -> those.containsAll(shown))
        .orElseGet(() -> act.timeline().versions());
  }

  private static List<Mark> marks(
      List<Version> versions,
      Axis axis,
      Predicate<Version> selected,
      Function<Version, String> href) {
    var marks = new ArrayList<Mark>();
    for (var group : groups(versions, axis)) {
      var at = axis.at(group);
      var ticks =
          group.stream()
              .map(v -> new Tick(at, selected.test(v), href.apply(v), label(v), null))
              .toList();
      marks.add(ticks.size() == 1 ? ticks.getFirst() : new Merged(at, ticks, merged(group)));
    }
    return marks;
  }

  private static List<Lag> lags(Act act, Axis axis) {
    return act.lags().stream()
        .map(
            lag -> {
              var from = axis.at(lag.from());
              return new Lag(
                  from,
                  Math.max(axis.at(lag.includedIn().date()) - from, NARROWEST),
                  Wording.lagOn(lag));
            })
        .toList();
  }

  /** The versions in runs, each version closer than GAP to the one before it in its run. */
  private static List<List<Version>> groups(List<Version> versions, Axis axis) {
    var groups = new ArrayList<List<Version>>();
    for (var version : versions) {
      var last = groups.isEmpty() ? null : groups.getLast();
      if (last != null && axis.at(version.date()) - axis.at(last.getLast().date()) < GAP) {
        last.add(version);
      } else {
        groups.add(new ArrayList<>(List.of(version)));
      }
    }
    return groups;
  }

  /** 02015R1998-20170607 · from 7 June 2017 · made by 2017/815, 2017/837. */
  private static String label(Version version) {
    return version.celex()
        + " · from "
        + Wording.date(version.date())
        + " · "
        + Wording.made(version);
  }

  /** 4 versions within 102 days. */
  private static String merged(List<Version> group) {
    var days = ChronoUnit.DAYS.between(group.getFirst().date(), group.getLast().date());
    return group.size() + " versions within " + days + " days";
  }

  /** A CSS position on the axis: left: 12.34%. */
  static String left(double at) {
    return String.format(Locale.ROOT, "left: %.2f%%", at);
  }

  /** The axis: the days from its start to its end. */
  private record Axis(LocalDate from, LocalDate to) {
    static Axis of(List<Version> versions) {
      return new Axis(
          versions.getFirst().date().withDayOfYear(1),
          versions.getLast().date().withDayOfYear(1).plusYears(1));
    }

    double at(LocalDate date) {
      return 100.0 * ChronoUnit.DAYS.between(from, date) / ChronoUnit.DAYS.between(from, to);
    }

    /** Where a merged mark of {@code group} is: the middle of its versions. */
    double at(List<Version> group) {
      return group.stream().mapToDouble(v -> at(v.date())).average().orElseThrow();
    }

    /** Where {@code version}'s mark is, merged or not. */
    double markAt(List<Version> versions, Version version) {
      return at(
          groups(versions, this).stream()
              .filter(g -> g.contains(version))
              .findFirst()
              .orElseThrow());
    }

    List<Year> years() {
      return IntStream.rangeClosed(from.getYear(), to.getYear())
          .mapToObj(year -> new Year(year, at(LocalDate.of(year, 1, 1))))
          .toList();
    }
  }

  /** A year's mark on the axis, at its first day. */
  public record Year(int year, double at) {
    public String style() {
      return left(at);
    }
  }

  /** A mark on the axis: one version, or several merged. */
  public sealed interface Mark {
    double at();

    /** Whether the version being read is this one, or among these; or an end compared. */
    boolean selected();

    default String style() {
      return left(at());
    }
  }

  /**
   * One version's mark.
   *
   * @param href where clicking it goes; none while the act is prepared
   * @param label what it is: its CELEX, its first day, the acts that made it
   * @param state while the act is prepared, its text's: queued, building, ready, failed
   */
  public record Tick(
      double at, boolean selected, @Nullable String href, String label, @Nullable String state)
      implements Mark {}

  /**
   * Versions too close to click apart, as one mark that lists them.
   *
   * @param versions each version's own mark, in date order
   * @param label how many, and over how many days
   */
  public record Merged(double at, List<Tick> versions, String label) implements Mark {
    public Merged {
      versions = List.copyOf(versions);
    }

    @Override
    public boolean selected() {
      return versions.stream().anyMatch(Tick::selected);
    }

    /**
     * While the act is prepared, how its versions' texts stand together: being built if any is,
     * else queued if any is, else built if any is, else failed; none otherwise.
     */
    public @Nullable String state() {
      for (var state : List.of("building", "queued", "ready", "failed")) {
        if (versions.stream().anyMatch(tick -> state.equals(tick.state()))) {
          return state;
        }
      }
      return null;
    }

    /**
     * Whether its list opens leftwards, from a mark in the axis's right half, so it stays on it.
     */
    public boolean opensLeft() {
      return at > 50;
    }
  }

  /**
   * A lag: where a version's text lags the law.
   *
   * @param width how much of the axis it spans, in percent
   * @param text what lags, from when and until when
   */
  public record Lag(double at, double width, String text) {
    public String style() {
      return left(at) + String.format(Locale.ROOT, "; width: %.2f%%", width);
    }
  }

  /** What is selected (READING, FROM, TO), above its mark. */
  public record Tag(String label, double at) {
    public String style() {
      return left(at);
    }
  }

  /** The range compared, from From's mark to To's. */
  public record Band(double from, double to) {
    public String style() {
      return left(from) + String.format(Locale.ROOT, "; width: %.2f%%", to - from);
    }
  }

  /**
   * A link to versions either side.
   *
   * @param href where it goes; none where there is nothing that way
   */
  public record Step(String label, @Nullable String href) {}
}
