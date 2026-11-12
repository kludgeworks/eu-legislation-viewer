// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The versions of an act on a book ruler, as the pages of the act show them. The ruler is a map of
 * when and how often the act changed, not a scale of time.
 *
 * <ul>
 *   <li>It spans the years from the act's adoption to today, or to a version after today.
 *   <li>Each year is a segment as wide as the number of its marks, plus one, so that a year with
 *       one version stays wide enough to click. Within a year, the marks are spaced evenly in date
 *       order. A run of years with no mark folds into one break.
 *   <li>Every version is a dot. A version after today is a dashed dot. Adoption is an open dot, and
 *       today a dashed line.
 *   <li>The days the reader chose are balls on the ruler, with their kicker and date below. A day
 *       between two dots sits between them, in proportion to the days.
 * </ul>
 *
 * <p>When the reader reads, from {@link #reading}, each dot links to the law on its version's first
 * day. When the reader compares, from {@link #comparing}, each dot links to the comparison with the
 * nearer end moved to it, and the range between the two days is a band. While the act is being
 * prepared, from {@link #preparing}, each dot shows the state of its version's text and links to
 * nothing yet.
 *
 * @param years the years on the ruler, each at its start
 * @param breaks the runs of years with no version, each folded to one break
 * @param adopted the mark of the act's adoption. Null if the date is not known.
 * @param today the dashed line at today
 * @param dots the versions, in date order
 * @param balls the days the reader chose: THE LAW ON, or FROM and TO
 * @param band the range that is compared. Null when the reader reads.
 * @param current the value of {@code aria-current} for a selected dot: {@code page} when the reader
 *     reads, {@code true} when the reader compares
 * @param steps the links to the versions on each side
 */
public record TimelineView(
    List<Year> years,
    List<Break> breaks,
    @Nullable Point adopted,
    Point today,
    List<Dot> dots,
    List<Ball> balls,
    @Nullable Band band,
    String current,
    List<Step> steps) {

  /** How wide a ball's label is, in percent of the ruler, for telling whether two labels meet. */
  static final double LABEL = 11;

  /** The width, in percent of the ruler, below which a year is labelled with two digits: ’04. */
  static final double NARROW = 3.5;

  public TimelineView {
    years = List.copyOf(years);
    breaks = List.copyOf(breaks);
    dots = List.copyOf(dots);
    balls = List.copyOf(balls);
    steps = List.copyOf(steps);
  }

  /**
   * Returns the timeline of {@code act} as the page of the law on {@code day}, which {@code
   * reading} gives, shows it in {@code language}, on {@code today}. The timeline has the versions
   * in that language. If {@code reading} is not in that language, it has all versions. Each version
   * whose text is not ready shows its state from {@code states}.
   */
  public static TimelineView reading(
      Act act,
      Version reading,
      LocalDate day,
      Language language,
      LocalDate today,
      Map<Version, String> states) {
    var eli = eli(act);
    var versions = versions(act, language, List.of(reading));
    var ruler = Ruler.of(act, versions, today);
    var index = versions.indexOf(reading);
    return ruler.view(
        dots(versions, ruler, today, reading::equals, v -> Links.read(eli, v, language), states),
        balls(List.of(new Ball("THE LAW ON", Dates.inShort(day), ruler.at(day), false, false))),
        null,
        "page",
        List.of(
            new Step(
                "‹ version before",
                index > 0 ? Links.read(eli, versions.get(index - 1), language) : null,
                "This is the first version"),
            new Step(
                "version after ›",
                index < versions.size() - 1
                    ? Links.read(eli, versions.get(index + 1), language)
                    : null,
                "This is the latest version")));
  }

  /**
   * Returns the timeline of {@code act} as the comparison of the law on {@code fromDay}, which
   * {@code from} gives, with the law on {@code toDay}, which {@code to} gives, shows it in {@code
   * language}, on {@code today}. The timeline has the versions in that language. If one of the two
   * versions is not in that language, it has all versions. Each version whose text is not ready
   * shows its state from {@code states}. A dot links to the comparison with the nearer end moved to
   * its first day, as {@link #nearer} finds it. Every link keeps the comparison's {@code view}.
   */
  public static TimelineView comparing(
      Act act,
      Version from,
      LocalDate fromDay,
      Version to,
      LocalDate toDay,
      Language language,
      LocalDate today,
      Map<Version, String> states,
      @Nullable String view) {
    var eli = eli(act);
    var versions = versions(act, language, List.of(from, to));
    var ruler = Ruler.of(act, versions, today);
    Function<Ends, String> compare =
        ends -> Links.compare(eli, ends.from(), ends.to(), language, null, view);
    var first = versions.indexOf(from);
    var last = versions.indexOf(to);
    var current =
        act.timeline()
            .in(language)
            .flatMap(t -> t.applicableOn(today))
            .filter(versions::contains)
            .orElseGet(versions::getLast);
    var original = versions.getFirst();
    var at = ruler.at(fromDay);
    var until = ruler.at(toDay);
    return ruler.view(
        dots(
            versions,
            ruler,
            today,
            v -> v.equals(from) || v.equals(to),
            v -> compare.apply(nearer(v.date(), fromDay, toDay)),
            states),
        balls(
            List.of(
                new Ball("FROM", Dates.inShort(fromDay), at, true, false),
                new Ball("TO", Dates.inShort(toDay), until, false, false))),
        new Band(at, until),
        "true",
        List.of(
            new Step(
                "‹ both one version earlier",
                first > 0
                    ? compare.apply(days(versions.get(first - 1), versions.get(last - 1)))
                    : null,
                "From is already the first version"),
            new Step(
                "both one version later ›",
                last < versions.size() - 1
                    ? compare.apply(days(versions.get(first + 1), versions.get(last + 1)))
                    : null,
                "To is already the latest version"),
            new Step(
                "compare “to” with the version before",
                last > 0 && first != last - 1
                    ? compare.apply(new Ends(versions.get(last - 1).date(), toDay))
                    : null,
                last == 0 ? "To is the first version" : "From is already the version before"),
            new Step(
                "compare the original with the current version",
                !(from.equals(original) && to.equals(current)) && !original.equals(current)
                    ? compare.apply(days(original, current))
                    : null,
                original.equals(current)
                    ? "The original is the current version"
                    : "This is already the original against the current version")));
  }

  /**
   * Returns the timeline of {@code act} while {@code preparation} prepares it, on {@code today}.
   * Each dot shows the state of the text of its version, and links to nothing yet.
   */
  public static TimelineView preparing(Act act, Preparation preparation, LocalDate today) {
    var versions = preparation.versions();
    var ruler = Ruler.of(act, versions, today);
    var dots =
        versions.stream()
            .map(
                v -> {
                  var state = state(Objects.requireNonNull(preparation.texts().get(v)));
                  return ruler.dot(v, today, false, null, label(v, versions, state), state);
                })
            .toList();
    return ruler.view(dots, List.of(), null, "page", List.of());
  }

  /**
   * Returns the timeline of {@code act} as the page of a day before its first version shows it, in
   * {@code language}, on {@code today}. No version is selected, and there is no ball. The step
   * after goes to the first version.
   */
  public static TimelineView before(Act act, Language language, LocalDate today) {
    var eli = eli(act);
    var versions = versions(act, language, List.of());
    var ruler = Ruler.of(act, versions, today);
    return ruler.view(
        dots(versions, ruler, today, v -> false, v -> Links.read(eli, v, language), Map.of()),
        List.of(),
        null,
        "page",
        List.of(
            new Step("‹ version before", null, "There is no version before this day"),
            new Step(
                "version after ›",
                Links.read(eli, versions.getFirst(), language),
                "There is no version")));
  }

  /**
   * Returns the states of the texts of the versions of {@code preparation}, for the dots after the
   * act is prepared. Only the texts that are not ready are in the map. These are the texts that
   * failed, or that are being prepared because they arrived later.
   */
  public static Map<Version, String> states(Preparation preparation) {
    var states = new HashMap<Version, String>();
    preparation
        .texts()
        .forEach(
            (version, text) -> {
              if (!(text instanceof Text.Ready)) {
                states.put(version, state(text));
              }
            });
    return states;
  }

  /** Returns the state of a text, as its dot shows it. */
  private static String state(Text text) {
    return switch (text) {
      case Text.Queued _ -> "queued";
      case Text.Building _ -> "building";
      case Text.Ready _ -> "ready";
      case Text.Failed _ -> "failed";
    };
  }

  /**
   * Returns the days of the comparison that a click on the dot of a version that starts on {@code
   * day} asks for, when the comparison is from {@code from} to {@code to}. The click moves the
   * nearer end to {@code day}.
   *
   * <ul>
   *   <li>A day before From moves From.
   *   <li>A day after To moves To.
   *   <li>A day between them moves the end that is fewer days away. If the two ends are equally
   *       far, it moves From.
   * </ul>
   */
  static Ends nearer(LocalDate day, LocalDate from, LocalDate to) {
    if (day.isBefore(from)) {
      return new Ends(day, to);
    }
    if (day.isAfter(to)) {
      return new Ends(from, day);
    }
    return ChronoUnit.DAYS.between(from, day) <= ChronoUnit.DAYS.between(day, to)
        ? new Ends(day, to)
        : new Ends(from, day);
  }

  private static Ends days(Version from, Version to) {
    return new Ends(from.date(), to.date());
  }

  /** The two days of a comparison. */
  record Ends(LocalDate from, LocalDate to) {}

  private static Eli eli(Act act) {
    return Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
  }

  /**
   * Returns the versions in {@code language}. If one of {@code shown} is not in that language,
   * returns all versions.
   */
  /**
   * Returns the days that a new version of {@code act} starts in {@code language}, as the date
   * picker rings them: ISO dates in order, separated by spaces. If the act has no versions in that
   * language, they are the days of all its versions.
   */
  public static String starts(Act act, Language language) {
    return versions(act, language, List.of()).stream()
        .map(version -> version.date().toString())
        .collect(Collectors.joining(" "));
  }

  private static List<Version> versions(Act act, Language language, List<Version> shown) {
    return act.timeline()
        .in(language)
        .map(Timeline::versions)
        .filter(those -> those.containsAll(shown))
        .orElseGet(() -> act.timeline().versions());
  }

  private static List<Dot> dots(
      List<Version> versions,
      Ruler ruler,
      LocalDate today,
      Predicate<Version> selected,
      Function<Version, String> href,
      Map<Version, String> states) {
    return versions.stream()
        .map(
            v -> {
              var state = states.get(v);
              return ruler.dot(
                  v, today, selected.test(v), href.apply(v), label(v, versions, state), state);
            })
        .toList();
  }

  /**
   * Returns the balls with their labels placed so that they do not meet. A label ends at its ball
   * if it asks to, and near the right edge; otherwise it starts at it. Near the left edge it always
   * starts at it. If a label would meet the one before, it goes below it.
   */
  static List<Ball> balls(List<Ball> balls) {
    var placed = new ArrayList<Ball>();
    for (var ball : balls) {
      var end = ball.at() > 100 - LABEL || (ball.end() && ball.at() >= LABEL);
      var start = end ? ball.at() - LABEL : ball.at();
      var stacked =
          !placed.isEmpty()
              && placed.getLast().labelStart() + LABEL > start
              && start + LABEL > placed.getLast().labelStart();
      placed.add(new Ball(ball.kicker(), ball.day(), ball.at(), end, stacked));
    }
    return placed;
  }

  /**
   * Returns the label of the dot of a version, as its hover says it. For example, "7 June 2017 ·
   * made by 2017/815, 2017/837 · version 9". If the text of the version has a {@code state}, the
   * label also says what the state means for the reader.
   */
  private static String label(Version version, List<Version> versions, @Nullable String state) {
    var label =
        Dates.inWords(version.date())
            + " · "
            + Wording.made(version)
            + " · version "
            + (versions.indexOf(version) + 1);
    return switch (state) {
      case "failed" -> label + " · couldn't be prepared";
      case "queued", "building" -> label + " · being prepared";
      case null, default -> label;
    };
  }

  /** Returns a CSS position on the ruler. For example, {@code left: 12.34%}. */
  static String left(double at) {
    return String.format(Locale.ROOT, "left: %.2f%%", at);
  }

  /**
   * The book ruler: where each day is, in percent of its width. It is a list of anchors, days at
   * known places: the start of each year, each mark in it, and the end of each break. A day between
   * two anchors is between them in proportion to the days.
   */
  private static final class Ruler {
    private final List<LocalDate> days = new ArrayList<>();
    private final List<Double> places = new ArrayList<>();
    private final List<Year> years = new ArrayList<>();
    private final List<Break> breaks = new ArrayList<>();
    private final @Nullable Point adopted;
    private final Point today;

    private Ruler(Act act, List<Version> versions, LocalDate today) {
      var adoption = act.dateOfDocument();
      var marks = new TreeSet<LocalDate>();
      versions.forEach(v -> marks.add(v.date()));
      marks.add(today);
      if (adoption != null) {
        marks.add(adoption);
      }
      var first = marks.first().getYear();
      var last = marks.last().getYear();
      // Lay the segments out in units, then turn them into percent.
      var segments = new ArrayList<Segment>();
      var unit = 0.0;
      for (var year = first; year <= last; year++) {
        var start = LocalDate.of(year, 1, 1);
        var in = marks.subSet(start, start.plusYears(1));
        if (in.isEmpty()) {
          var end = year;
          while (end < last && marks.subSet(start, LocalDate.of(end + 2, 1, 1)).isEmpty()) {
            end++;
          }
          anchor(start, unit);
          segments.add(new Segment(year, end, unit, 1, false));
          unit += 1;
          anchor(LocalDate.of(end + 1, 1, 1), unit);
          year = end;
        } else {
          anchor(start, unit);
          segments.add(new Segment(year, year, unit, in.size() + 1, true));
          var i = 1;
          for (var mark : in) {
            anchor(mark, unit + i++);
          }
          unit += in.size() + 1;
          anchor(start.plusYears(1), unit);
        }
      }
      var total = unit;
      places.replaceAll(place -> 100 * place / total);
      for (var segment : segments) {
        var at = 100 * segment.start() / total;
        var width = 100 * segment.width() / total;
        if (segment.marked()) {
          years.add(
              new Year(
                  segment.from(),
                  width < NARROW
                      ? "’" + String.format(Locale.ROOT, "%02d", segment.from() % 100)
                      : String.valueOf(segment.from()),
                  at));
        } else {
          breaks.add(
              new Break(at + width / 2, span(segment.from(), segment.to()) + ": no versions"));
        }
      }
      this.adopted =
          adoption == null
              ? null
              : new Point(
                  at(adoption),
                  "Adopted "
                      + Dates.inWords(adoption)
                      + " · in force "
                      + Dates.inWords(versions.getFirst().date()));
      this.today = new Point(at(today), "Today, " + Dates.inWords(today));
    }

    static Ruler of(Act act, List<Version> versions, LocalDate today) {
      return new Ruler(act, versions, today);
    }

    private void anchor(LocalDate day, double unit) {
      days.add(day);
      places.add(unit);
    }

    /** Returns where {@code day} is on the ruler, in percent. */
    double at(LocalDate day) {
      if (!day.isAfter(days.getFirst())) {
        return places.getFirst();
      }
      for (var i = 1; i < days.size(); i++) {
        var after = days.get(i);
        if (!day.isAfter(after)) {
          var before = days.get(i - 1);
          var span = ChronoUnit.DAYS.between(before, after);
          var share = span == 0 ? 0 : (double) ChronoUnit.DAYS.between(before, day) / span;
          return places.get(i - 1) + share * (places.get(i) - places.get(i - 1));
        }
      }
      return places.getLast();
    }

    Dot dot(
        Version version,
        LocalDate today,
        boolean selected,
        @Nullable String href,
        String label,
        @Nullable String state) {
      return new Dot(
          at(version.date()), selected, href, label, state, version.date().isAfter(today));
    }

    TimelineView view(
        List<Dot> dots, List<Ball> balls, @Nullable Band band, String current, List<Step> steps) {
      return new TimelineView(years, breaks, adopted, today, dots, balls, band, current, steps);
    }

    /**
     * A year with marks, or a run of years without, laid out in units.
     *
     * @param from its first year
     * @param to its last year
     * @param start where it starts, in units
     * @param width how wide it is, in units
     * @param marked whether it is a year with marks
     */
    private record Segment(int from, int to, double start, double width, boolean marked) {}

    /** Returns a run of years as a break's hover says it. For example, "2009–12" or "2009". */
    private static String span(int from, int to) {
      if (from == to) {
        return String.valueOf(from);
      }
      return from / 100 == to / 100
          ? from + "–" + String.format(Locale.ROOT, "%02d", to % 100)
          : from + "–" + to;
    }
  }

  /**
   * A year on the ruler, at its start.
   *
   * @param year the year
   * @param label the year as the ruler shows it: "2016", or "’16" where the year is narrow
   * @param at where the year starts, in percent
   */
  public record Year(int year, String label, double at) {
    public String style() {
      return left(at);
    }
  }

  /**
   * A run of years with no version, folded to one break.
   *
   * @param at the middle of the break, in percent
   * @param label what its hover says. For example, "2009–12: no versions".
   */
  public record Break(double at, String label) {
    public String style() {
      return left(at);
    }
  }

  /**
   * A day marked on the ruler that is not a version: the act's adoption, or today.
   *
   * @param label what its hover says. For example, "Adopted 5 November 2015 · in force 15 November
   *     2015".
   */
  public record Point(double at, String label) {
    public String style() {
      return left(at);
    }
  }

  /**
   * The dot of a version.
   *
   * @param selected whether the version gives the law on a day the reader chose
   * @param href where a click on the dot goes. Null while the act is being prepared.
   * @param label what its hover says: the version's first day, the acts that made it and its number
   * @param state the state of the text of the version: {@code queued}, {@code building}, {@code
   *     ready} or {@code failed}. While the act is being prepared, every dot has a state. When the
   *     act is prepared, only the dots of texts that are not ready have one.
   * @param future whether the version starts after today. Its dot is dashed.
   */
  public record Dot(
      double at,
      boolean selected,
      @Nullable String href,
      String label,
      @Nullable String state,
      boolean future) {
    public String style() {
      return left(at);
    }
  }

  /**
   * A day the reader chose, as a ball on the ruler with its kicker and date below.
   *
   * @param kicker THE LAW ON, FROM or TO
   * @param day the day, short. For example, "14 Feb 2022".
   * @param at where the day is, in percent
   * @param end whether the label ends at the ball. Otherwise it starts at it.
   * @param stacked whether the label goes below the label before, because they would meet
   */
  public record Ball(String kicker, String day, double at, boolean end, boolean stacked) {
    public String style() {
      return left(at);
    }

    /** Returns where its label starts, in percent. */
    double labelStart() {
      return end ? at - LABEL : at;
    }
  }

  /** The range that is compared, from the ball of From to the ball of To. */
  public record Band(double from, double to) {
    public String style() {
      return left(from) + String.format(Locale.ROOT, "; width: %.2f%%", to - from);
    }
  }

  /**
   * A link to the versions on one side.
   *
   * @param href where the link goes. Null where there is nothing on that side.
   * @param nowhere why there is nothing on that side, as the hover of the greyed step says
   */
  public record Step(String label, @Nullable String href, String nowhere) {}
}
