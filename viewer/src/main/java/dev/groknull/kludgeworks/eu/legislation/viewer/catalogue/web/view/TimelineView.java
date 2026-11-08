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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * The versions of an act on a time axis, to scale, as the pages of the act show them. The axis
 * starts on the first day of the year of the first version. It ends on the first day of the year
 * after the year of the latest version.
 *
 * <p>A version is a mark at its first day, and the mark is a link. Versions that are too close to
 * click apart merge into one mark that lists them. They are too close if the distance from the
 * version before is less than {@link #GAP} of the axis. Each lag from {@link Act#lags()} is a band.
 * The band goes from the day the act applies to the version that includes it.
 *
 * <ul>
 *   <li>When the reader reads, from {@link #reading}, the marks link to the pages of the versions.
 *   <li>When the reader compares, from {@link #comparing}, each mark links to the comparison with
 *       the nearer end moved to it. The range between the two ends is a band.
 *   <li>While the act is being prepared, from {@link #preparing}, each mark shows the state of the
 *       text of its version, and links to nothing yet. Grey is queued, light is being built, dark
 *       is built and dashed is failed.
 *   <li>When the act is prepared, the marks of the versions that failed stay dashed. The versions
 *       that appeared since show as being prepared.
 * </ul>
 *
 * @param years a mark for each new year on the axis
 * @param marks the versions in date order. Versions that are too close are merged.
 * @param lags where the text of a version lags the law
 * @param tags what is selected, above its mark. This is READING, or FROM and TO.
 * @param band the range that is compared. Null when the reader reads.
 * @param current the value of {@code aria-current} for a selected mark: {@code page} when the
 *     reader reads, {@code true} when the reader compares
 * @param hint what a click on a mark does
 * @param steps the links to the versions on each side
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

  /**
   * The distance, in percent of the axis, below which versions merge into one mark. The value is
   * 1.6.
   */
  static final double GAP = 1.6;

  /**
   * The smallest width of a lag, in percent of the axis. With this width, a lag of a few days still
   * shows.
   */
  private static final double NARROWEST = 0.5;

  public TimelineView {
    years = List.copyOf(years);
    marks = List.copyOf(marks);
    lags = List.copyOf(lags);
    tags = List.copyOf(tags);
    steps = List.copyOf(steps);
  }

  /**
   * Returns the timeline of {@code act} as the page of {@code reading} in {@code language} shows
   * it. The timeline has the versions in that language. If the version that the reader reads is not
   * in that language, it has all versions. Each version whose text is not ready shows its state
   * from {@code states}.
   */
  public static TimelineView reading(
      Act act, Version reading, Language language, Map<Version, String> states) {
    var eli = eli(act);
    var versions = versions(act, language, List.of(reading));
    var axis = Axis.of(versions);
    var index = versions.indexOf(reading);
    return new TimelineView(
        axis.years(),
        marks(versions, axis, reading::equals, v -> Links.read(eli, v, language), states),
        lags(act, axis),
        List.of(new Tag("READING", axis.markAt(versions, reading))),
        null,
        "page",
        "Click a version to read it",
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
   * Returns the timeline of {@code act} as the comparison of {@code from} with {@code to} in {@code
   * language} shows it, on {@code today}. The timeline has the versions in that language. If one of
   * the two versions is not in that language, it has all versions. Each version whose text is not
   * ready shows its state from {@code states}. A mark links to the comparison with the nearer end
   * moved to it, as {@link #nearer} finds it.
   */
  public static TimelineView comparing(
      Act act,
      Version from,
      Version to,
      Language language,
      LocalDate today,
      Map<Version, String> states) {
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
        marks(versions, axis, v -> v.equals(from) || v.equals(to), nearer, states),
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
                    : null,
                "From is already the first version"),
            new Step(
                "both one version later ›",
                last < versions.size() - 1
                    ? Links.compare(eli, versions.get(first + 1), versions.get(last + 1), language)
                    : null,
                "To is already the latest version"),
            new Step(
                "compare “to” with the version before",
                last > 0 && first != last - 1
                    ? Links.compare(eli, versions.get(last - 1), to, language)
                    : null,
                last == 0 ? "To is the first version" : "From is already the version before"),
            new Step(
                "compare the original with the current version",
                !(from.equals(original) && to.equals(current)) && !original.equals(current)
                    ? Links.compare(eli, original, current, language)
                    : null,
                original.equals(current)
                    ? "The original is the current version"
                    : "This is already the original against the current version")));
  }

  /**
   * Returns the timeline of {@code act} while {@code preparation} runs. Each mark shows the state
   * of the text of its version.
   */
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

  /**
   * Returns the states of the texts of the versions of {@code preparation}, for the ticks after the
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

  /** Returns the state of a text, as its tick shows it. */
  private static String state(Text text) {
    return switch (text) {
      case Text.Queued _ -> "queued";
      case Text.Building _ -> "building";
      case Text.Ready _ -> "ready";
      case Text.Failed _ -> "failed";
    };
  }

  /**
   * Returns the comparison that a click on {@code version} asks for, when the comparison is from
   * {@code from} to {@code to}. The click moves the nearer end to {@code version}.
   *
   * <ul>
   *   <li>A version before From moves From.
   *   <li>A version after To moves To.
   *   <li>A version between them moves the end that is fewer days away. If the two ends are equally
   *       far, it moves From.
   * </ul>
   *
   * <p>The result is the list [from, to].
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

  /**
   * Returns the versions in {@code language}. If one of {@code shown} is not in that language,
   * returns all versions.
   */
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
      Function<Version, String> href,
      Map<Version, String> states) {
    var marks = new ArrayList<Mark>();
    for (var group : groups(versions, axis)) {
      var at = axis.at(group);
      var ticks =
          group.stream()
              .map(
                  v ->
                      new Tick(
                          at,
                          selected.test(v),
                          href.apply(v),
                          label(v, states.get(v)),
                          states.get(v)))
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

  /**
   * Returns the versions in runs. In a run, each version is closer than {@link #GAP} to the version
   * before it.
   */
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

  /**
   * Returns the label of a version. If the text of the version has a {@code state}, the label also
   * says what the state means for the reader.
   */
  private static String label(Version version, @Nullable String state) {
    return switch (state) {
      case "failed" -> label(version) + " · couldn't be prepared";
      case "queued", "building" -> label(version) + " · being prepared";
      case null, default -> label(version);
    };
  }

  /**
   * Returns the label of a version. For example, "02015R1998-20170607 · from 7 June 2017 · made by
   * 2017/815, 2017/837".
   */
  private static String label(Version version) {
    return version.celex()
        + " · from "
        + Dates.inWords(version.date())
        + " · "
        + Wording.made(version);
  }

  /** Returns the label of a merged mark. For example, "4 versions within 102 days". */
  private static String merged(List<Version> group) {
    var days = ChronoUnit.DAYS.between(group.getFirst().date(), group.getLast().date());
    return group.size() + " versions within " + days + " days";
  }

  /** Returns a CSS position on the axis. For example, {@code left: 12.34%}. */
  static String left(double at) {
    return String.format(Locale.ROOT, "left: %.2f%%", at);
  }

  /** The time axis. It holds the days from its start to its end. */
  private record Axis(LocalDate from, LocalDate to) {
    static Axis of(List<Version> versions) {
      return new Axis(
          versions.getFirst().date().withDayOfYear(1),
          versions.getLast().date().withDayOfYear(1).plusYears(1));
    }

    double at(LocalDate date) {
      return 100.0 * ChronoUnit.DAYS.between(from, date) / ChronoUnit.DAYS.between(from, to);
    }

    /**
     * Returns the position of the merged mark of {@code group}. This is the mean of its versions.
     */
    double at(List<Version> group) {
      return group.stream().mapToDouble(v -> at(v.date())).average().orElseThrow();
    }

    /** Returns the position of the mark of {@code version}, merged or not. */
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

  /** The mark of a year on the axis, at the first day of the year. */
  public record Year(int year, double at) {
    public String style() {
      return left(at);
    }
  }

  /** A mark on the axis. It stands for one version, or for several versions merged. */
  public sealed interface Mark {
    double at();

    /**
     * Returns whether this mark is selected. A mark is selected if it has the version that the
     * reader reads, or an end of the comparison.
     */
    boolean selected();

    default String style() {
      return left(at());
    }
  }

  /**
   * The mark of one version.
   *
   * @param href where a click on the mark goes. Null while the act is being prepared.
   * @param label what the version is: its CELEX, its first day and the acts that made it
   * @param state the state of the text of the version: {@code queued}, {@code building}, {@code
   *     ready} or {@code failed}. While the act is being prepared, every mark has a state. When the
   *     act is prepared, only the marks of texts that are not ready have one.
   */
  public record Tick(
      double at, boolean selected, @Nullable String href, String label, @Nullable String state)
      implements Mark {}

  /**
   * Versions that are too close to click apart, as one mark that lists them.
   *
   * @param versions the mark of each version, in date order
   * @param label how many versions there are, and over how many days
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
     * Returns the combined state of the texts of its versions.
     *
     * <ul>
     *   <li>If one of the texts is being built, returns {@code building}.
     *   <li>Else, if one is queued, returns {@code queued}.
     *   <li>Else, if one is ready, returns {@code ready}.
     *   <li>Else, if all failed, returns {@code failed}.
     *   <li>Else returns null.
     * </ul>
     */
    public @Nullable String state() {
      for (var state : List.of("building", "queued", "ready")) {
        if (versions.stream().anyMatch(tick -> state.equals(tick.state()))) {
          return state;
        }
      }
      return versions.stream().allMatch(tick -> "failed".equals(tick.state())) ? "failed" : null;
    }

    /**
     * Returns whether its list opens to the left. It does so for a mark in the right half of the
     * axis, so that the list stays on the axis.
     */
    public boolean opensLeft() {
      return at > 50;
    }
  }

  /**
   * A lag, which is where the text of a version lags the law.
   *
   * @param width how much of the axis the lag spans, in percent
   * @param text what lags, from when and until when
   */
  public record Lag(double at, double width, String text) {
    public String style() {
      return left(at) + String.format(Locale.ROOT, "; width: %.2f%%", width);
    }
  }

  /** The label of what is selected, above its mark. The label is READING, FROM or TO. */
  public record Tag(String label, double at) {
    public String style() {
      return left(at);
    }
  }

  /** The range that is compared, from the mark of From to the mark of To. */
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
