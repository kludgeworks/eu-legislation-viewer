// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The page of an act while the act is being prepared in its language. The page shows:
 *
 * <ul>
 *   <li>what the reader asked for. It opens at this address when it is ready.
 *   <li>the progress panel, with the steps as they happened
 *   <li>what the catalogue already tells about the act
 * </ul>
 *
 * <p>The event stream pushes the progress and the steps again when they change. {@code
 * PreparationController} sends the stream. The rest of the page does not change.
 *
 * @param asked what the reader asked for, and the statement that it opens here
 * @param self the address of the page, which the page requests again when the act is prepared
 * @param events the address of the event stream of the preparation
 * @param progress the state of the preparation
 * @param steps what was done, as it happened
 * @param known what the catalogue tells about the act
 * @param eurLex the address of the act on EUR-Lex, in the language of the page
 */
public record PreparingView(
    String asked,
    String self,
    String events,
    Progress progress,
    List<Step> steps,
    List<Fact> known,
    String eurLex) {

  /**
   * The time zone of the time of a step. It is Brussels, the same as for the dates on the pages.
   */
  private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  public PreparingView {
    steps = List.copyOf(steps);
    known = List.copyOf(known);
  }

  /**
   * Returns the page of {@code act} while {@code preparation} prepares it.
   *
   * @param asked what the reader asked for
   * @param self the address of the page
   * @param now the time now
   */
  public static PreparingView of(
      Act act, Preparation preparation, String asked, String self, Instant now) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    return new PreparingView(
        asked + "; it opens here, at this address, as soon as the act is ready.",
        self,
        Links.prepare(eli, preparation.language()),
        Progress.of(preparation, now),
        steps(preparation),
        known(act),
        act.id().eurLex(preparation.language()).toString());
  }

  /**
   * Returns the steps of the preparation, in the words that the reader sees. The steps are in the
   * order they were taken.
   */
  public static List<Step> steps(Preparation preparation) {
    return preparation.steps().stream()
        .map(step -> new Step(TIME.format(step.at().atZone(BRUSSELS)), step(step)))
        .toList();
  }

  /** Returns the text of a step. For example, "version 02015R1998-20230401 converted (34 s)". */
  private static String step(Preparation.Step step) {
    var version = "version " + step.celex();
    return switch (step.kind()) {
      case STARTED ->
          step.attempt() > 1
              ? "converting " + version + " again, attempt " + step.attempt()
              : "converting " + version;
      case BUILT ->
          version
              + " converted"
              + (step.took() == null ? "" : " (" + Math.max(step.took().toSeconds(), 1) + " s)");
      case DEFERRED -> version + " didn't convert; it will be tried again";
      case FAILED ->
          version
              + " couldn't be converted: "
              + (step.failure() == null ? "something went wrong" : Wording.failure(step.failure()));
    };
  }

  /**
   * Returns what is known about the act at once. This is when it was adopted, when it is in force,
   * applies and takes effect, and which acts amended it.
   */
  private static List<Fact> known(Act act) {
    var known = new ArrayList<Fact>();
    if (act.dateOfDocument() != null) {
      known.add(new Fact("Adopted", Dates.inWords(act.dateOfDocument())));
    }
    var own = act.datesOf(act.id());
    for (var kind : ActDate.Kind.values()) {
      var dates =
          own.stream()
              .filter(date -> date.kind() == kind)
              .map(PreparingView::date)
              .collect(Collectors.joining("; "));
      if (!dates.isEmpty()) {
        known.add(
            new Fact(
                switch (kind) {
                  case ENTRY_INTO_FORCE -> "In force";
                  case APPLICATION -> "Applies";
                  case TAKING_EFFECT -> "Takes effect";
                },
                dates));
      }
    }
    var amending = new LinkedHashSet<ActIdentifier>();
    for (var version : act.timeline().versions()) {
      version.integrates().stream().filter(each -> !each.corrigendum()).forEach(amending::add);
    }
    if (!amending.isEmpty()) {
      known.add(
          new Fact(
              "Amended by",
              amending.size() == 1
                  ? amending.getFirst().cited()
                  : amending.size()
                      + " acts, "
                      + amending.getFirst().cited()
                      + " to "
                      + amending.getLast().cited()));
    }
    return known;
  }

  /**
   * Returns a date of the act and the provisions it is in. For example:
   *
   * <ul>
   *   <li>"from 1 February 2016, Article 3"
   *   <li>"in part from …"
   *   <li>"20 days after its publication, Article 2"
   * </ul>
   */
  private static String date(ActDate date) {
    var clause = date.clause();
    var when = date.date() != null ? "from " + Dates.inWords(date.date()) : relative(clause);
    var where =
        clause.provisions().stream()
            .map(ActDate.Provision::cited)
            .collect(Collectors.joining(", "));
    return (date.partial() ? "in part " : "") + when + (where.isEmpty() ? "" : ", " + where);
  }

  /**
   * Returns a date that the clause sets relative to a day. For example, "20 days after its
   * publication". If the clause sets no day, returns "on a date not yet known".
   */
  private static String relative(ActDate.Clause clause) {
    if (clause.from() == null) {
      return "on a date not yet known";
    }
    var day =
        switch (clause.from()) {
          case PUBLICATION -> "its publication";
          case NOTIFICATION -> "its notification";
          case DOCUMENT -> "its adoption";
        };
    var days = clause.days();
    return days == null ? "on " + day : days + (days == 1 ? " day" : " days") + " after " + day;
  }

  /**
   * The state of the preparation, as the event stream pushes it.
   *
   * @param waiting why nothing has started yet, with how many versions are ahead. Null when
   *     something has started. Beside it, the page shows one of {@link #ASIDES} at a time, for the
   *     eye only.
   * @param running the versions that are being built now. For example, "Version 16 of 22 ·
   *     02015R1998-20230401".
   * @param settled how many versions are built or failed
   * @param size how many versions there are
   * @param counts how many versions are ready, and how many could not be made. For example, "16 of
   *     22 versions ready".
   * @param seconds how long the preparation has taken so far, in seconds. The clock on the page
   *     starts from this value.
   * @param elapsed the same time, as the page shows it. For example, 4:12.
   */
  public record Progress(
      @Nullable String waiting,
      List<String> running,
      int settled,
      int size,
      String counts,
      long seconds,
      String elapsed) {

    /**
     * The light lines that show beside the wait, one at a time. The page changes the line every few
     * seconds, in {@code clock.js}. The lines are hidden from screen readers, which read the plain
     * line.
     */
    public static final List<String> ASIDES =
        List.of(
            "Taking a number at the Publications Office…",
            "Our turn applies from a date to be determined…",
            "Notwithstanding the above, still waiting…",
            "Without prejudice to the queue…",
            "Pending entry into force…",
            "Consolidating our patience…");

    public Progress {
      running = List.copyOf(running);
    }

    /** Returns the asides as the script of the page reads them, one on each line. */
    public String asides() {
      return String.join("\n", ASIDES);
    }

    /** Returns the state of {@code preparation} at the time {@code now}. */
    public static Progress of(Preparation preparation, Instant now) {
      var versions = preparation.versions();
      var running = new ArrayList<String>();
      for (var i = 0; i < versions.size(); i++) {
        var version = versions.get(i);
        if (preparation.texts().get(version) instanceof Text.Building) {
          running.add(running(version, i + 1, versions.size()));
        }
      }
      var settled = preparation.ready() + preparation.failed();
      var waiting = settled == 0 && running.isEmpty() ? waiting(preparation.ahead()) : null;
      var size = preparation.size();
      var counts =
          preparation.ready()
              + " of "
              + size
              + (size == 1 ? " version" : " versions")
              + " ready"
              + (preparation.failed() == 0
                  ? ""
                  : ", " + preparation.failed() + " couldn't be made");
      return new Progress(
          waiting,
          running,
          settled,
          size,
          counts,
          Math.max(Duration.between(preparation.started(), now).toSeconds(), 0),
          elapsed(Duration.between(preparation.started(), now)));
    }

    private static String running(Version version, int index, int size) {
      return "Version " + index + " of " + size + " · " + version.celex();
    }

    /**
     * Returns, in plain words, why nothing has started. For example, "About 40 versions of other
     * acts are ahead. Nothing's wrong; this one starts by itself." From 10 versions ahead, the
     * number is rounded to the nearest 10.
     */
    static String waiting(int ahead) {
      var calm = "Nothing's wrong; this one starts by itself.";
      if (ahead == 0) {
        return calm;
      }
      if (ahead == 1) {
        return "1 version of another act is ahead. " + calm;
      }
      return (ahead < 10 ? String.valueOf(ahead) : "About " + Math.round(ahead / 10.0) * 10)
          + " versions of other acts are ahead. "
          + calm;
    }

    /**
     * Returns the elapsed time as m:ss, for example 4:12. From one hour, it is h:mm:ss, for example
     * 1:04:12.
     */
    static String elapsed(Duration elapsed) {
      var seconds = Math.max(elapsed.toSeconds(), 0);
      return seconds >= 3600
          ? String.format(
              Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
          : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
  }

  /**
   * A step of the preparation, as it happened.
   *
   * @param time when the step happened, in Brussels time. For example, 10:42:13.
   * @param text what happened. For example, "version 02015R1998-20230401 converted (34 s)".
   */
  public record Step(String time, String text) {}

  /**
   * A fact about the act. For example, the term "Applies" with the detail "from 1 February 2016,
   * Article 3".
   */
  public record Fact(String term, String detail) {}

  /**
   * Returns the receipt that shows when the act is prepared. For example, "Prepared · 22 versions
   * in English · took 1:12".
   */
  public static String receipt(Preparation preparation, Language language) {
    var size = preparation.size();
    var versions =
        preparation.failed() == 0
            ? size + (size == 1 ? " version" : " versions")
            : preparation.ready() + " of " + size + " versions";
    return "Prepared · "
        + versions
        + " in "
        + Wording.language(language)
        + preparation.took().map(took -> " · took " + Progress.elapsed(took)).orElse("");
  }
}
