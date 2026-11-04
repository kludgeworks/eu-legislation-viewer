// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * An act's versions on a time axis, to scale, as the version page shows them (Design: Timeline):
 * from the first day of the first version's year to the first of the year after the latest's. A
 * version is a mark at its first day, a link to its page; versions too close to click apart (under
 * GAP of the axis from the one before) merge into one mark listing them. Each lag (Act.lags) is a
 * band from the day the act applies to the version that includes it.
 *
 * @param years a mark for each new year on the axis
 * @param marks the versions, merged where too close, in date order
 * @param lags where a version's text lags the law
 * @param reading where the version being read is
 * @param before the page of the version before the one being read; none for the first
 * @param after the page of the version after it; none for the latest
 */
public record TimelineView(
    List<Year> years,
    List<Mark> marks,
    List<Lag> lags,
    double reading,
    @Nullable String before,
    @Nullable String after) {

  /** How close, as a share of the axis in percent, versions merge: 1.6, as the design draws it. */
  static final double GAP = 1.6;

  /** The narrowest a lag is drawn, in percent of the axis, so a lag of days still shows. */
  private static final double NARROWEST = 0.5;

  public TimelineView {
    years = List.copyOf(years);
    marks = List.copyOf(marks);
    lags = List.copyOf(lags);
  }

  /**
   * {@code act}'s timeline as the page of {@code reading} in {@code language} shows it: the
   * versions in that language, or all of them if the version being read isn't in it.
   */
  public static TimelineView of(Act act, Version reading, Language language) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var versions =
        act.timeline()
            .in(language)
            .map(Timeline::versions)
            .filter(those -> those.contains(reading))
            .orElseGet(() -> act.timeline().versions());
    var axis =
        new Axis(
            versions.getFirst().date().withDayOfYear(1),
            versions.getLast().date().withDayOfYear(1).plusYears(1));
    var marks = new ArrayList<Mark>();
    var readingAt = 0.0;
    for (var group : groups(versions, axis)) {
      var at = group.stream().mapToDouble(v -> axis.at(v.date())).average().orElseThrow();
      var ticks =
          group.stream()
              .map(v -> new Tick(at, v.equals(reading), Links.read(eli, v, language), label(v)))
              .toList();
      marks.add(ticks.size() == 1 ? ticks.getFirst() : new Merged(at, ticks, merged(group)));
      if (group.contains(reading)) {
        readingAt = at;
      }
    }
    var index = versions.indexOf(reading);
    return new TimelineView(
        IntStream.rangeClosed(axis.from.getYear(), axis.to.getYear())
            .mapToObj(year -> new Year(year, axis.at(LocalDate.of(year, 1, 1))))
            .toList(),
        marks,
        act.lags().stream()
            .map(
                lag -> {
                  var from = axis.at(lag.from());
                  return new Lag(
                      from,
                      Math.max(axis.at(lag.includedIn().date()) - from, NARROWEST),
                      lagging(lag));
                })
            .toList(),
        readingAt,
        index > 0 ? Links.read(eli, versions.get(index - 1), language) : null,
        index < versions.size() - 1 ? Links.read(eli, versions.get(index + 1), language) : null);
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
    var made =
        version.integrates().isEmpty()
            ? "the act as adopted"
            : "made by "
                + version.integrates().stream().map(Wording::act).collect(Collectors.joining(", "));
    return version.celex() + " · from " + Wording.date(version.date()) + " · " + made;
  }

  /** 4 versions within 102 days. */
  private static String merged(List<Version> group) {
    var days = ChronoUnit.DAYS.between(group.getFirst().date(), group.getLast().date());
    return group.size() + " versions within " + days + " days";
  }

  /**
   * On this date, 2017/815 already applied (from 1 June 2017, Article 2 of 2017/815). No
   * consolidated version includes it until 7 June 2017.
   */
  private static String lagging(Act.Unconsolidated lag) {
    var act = Wording.act(lag.act());
    var provisions = lag.appliesFrom().clause().provisions();
    var where =
        provisions.isEmpty() ? "" : ", " + Wording.provision(provisions.getFirst()) + " of " + act;
    return "On this date, "
        + act
        + " already applied (from "
        + Wording.date(lag.from())
        + where
        + "). No consolidated version includes it until "
        + Wording.date(lag.includedIn().date())
        + ".";
  }

  /** A CSS position on the axis: left: 12.34%. */
  static String left(double at) {
    return String.format(Locale.ROOT, "left: %.2f%%", at);
  }

  /** The axis: the days from its start to its end. */
  private record Axis(LocalDate from, LocalDate to) {
    double at(LocalDate date) {
      return 100.0 * ChronoUnit.DAYS.between(from, date) / ChronoUnit.DAYS.between(from, to);
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

    /** Whether the version being read is this one, or among these. */
    boolean reading();

    default String style() {
      return left(at());
    }
  }

  /**
   * One version's mark.
   *
   * @param href its page
   * @param label what it is: its CELEX, its first day, the acts that made it
   */
  public record Tick(double at, boolean reading, String href, String label) implements Mark {}

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
    public boolean reading() {
      return versions.stream().anyMatch(Tick::reading);
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

  /** The reading tag's position. */
  public String readingStyle() {
    return left(reading);
  }
}
