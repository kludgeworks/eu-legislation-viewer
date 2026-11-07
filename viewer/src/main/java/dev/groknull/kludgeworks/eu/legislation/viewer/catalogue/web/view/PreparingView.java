// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

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
 * An act's page while the act is being prepared in its language (Design: Preparing an act): what
 * the reader asked for, which opens at this address once it is ready; the progress panel, its steps
 * as they happened, and what is already known about the act, from the catalogue. The stream pushes
 * the progress and the steps again as they change (PreparationController); the rest stays.
 *
 * @param asked what the reader asked for, and that it opens here
 * @param self the page's address, asked again once the act is prepared
 * @param events the stream of the preparation
 * @param progress how it stands
 * @param steps what was done, as it happened
 * @param known what the catalogue says of the act
 * @param eurLex the act on EUR-Lex, in the page's language
 */
public record PreparingView(
    String asked,
    String self,
    String events,
    Progress progress,
    List<Step> steps,
    List<Fact> known,
    String eurLex) {

  /** Where a step's time is told: Brussels, as the pages' dates are. */
  private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  public PreparingView {
    steps = List.copyOf(steps);
    known = List.copyOf(known);
  }

  /**
   * The page of {@code act} being prepared ({@code preparation}), asked for as {@code asked}, at
   * {@code self}, on {@code now}.
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
        Links.eurLex(act.id().celex(), preparation.language()));
  }

  /** The preparation's steps, as a reader is told them, in the order they were taken. */
  public static List<Step> steps(Preparation preparation) {
    return preparation.steps().stream()
        .map(step -> new Step(TIME.format(step.at().atZone(BRUSSELS)), step(step)))
        .toList();
  }

  /** version 02015R1998-20230401 converted (34 s). */
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

  /** What is known of the act at once: adopted, in force, applies, takes effect, amended by. */
  private static List<Fact> known(Act act) {
    var known = new ArrayList<Fact>();
    if (act.dateOfDocument() != null) {
      known.add(new Fact("Adopted", Wording.date(act.dateOfDocument())));
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
      version.integrates().stream()
          .filter(each -> !Wording.corrigendum(each))
          .forEach(amending::add);
    }
    if (!amending.isEmpty()) {
      known.add(
          new Fact(
              "Amended by",
              amending.size() == 1
                  ? Wording.act(amending.getFirst())
                  : amending.size()
                      + " acts, "
                      + Wording.act(amending.getFirst())
                      + " to "
                      + Wording.act(amending.getLast())));
    }
    return known;
  }

  /** from 1 February 2016, Article 3; in part from …; 20 days after its publication, Article 2. */
  private static String date(ActDate date) {
    var clause = date.clause();
    var when = date.date() != null ? "from " + Wording.date(date.date()) : relative(clause);
    var where =
        clause.provisions().stream().map(Wording::provision).collect(Collectors.joining(", "));
    return (date.partial() ? "in part " : "") + when + (where.isEmpty() ? "" : ", " + where);
  }

  /** A date the clause sets from a day: 20 days after its publication. */
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
   * How the preparation stands, as the stream pushes it.
   *
   * @param waiting while nothing has started: why, with how many versions are ahead; none once it
   *     has. Beside it, one of {@link #ASIDES} at a time, for the eye alone
   * @param running the versions being built now: Version 16 of 22 · 02015R1998-20230401
   * @param settled how many versions are built or failed
   * @param size how many versions there are
   * @param counts 16 of 22 versions ready, and how many couldn't be made
   * @param seconds how long it has taken so far, in seconds, for the clock the page keeps
   * @param elapsed the same as the page shows it: 4:12
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
     * The light lines beside the wait, one at a time, changed every few seconds by the page
     * (clock.js); hidden from screen readers, which have the plain line.
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

    /** The asides, as the page's script reads them: one per line. */
    public String asides() {
      return String.join("\n", ASIDES);
    }

    /** {@code preparation}'s progress, on {@code now}. */
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
     * Why nothing has started, plainly (Design D-59): About 40 versions of other acts are ahead.
     * Nothing's wrong; this one starts by itself.
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

    /** 4:12, or 1:04:12 past an hour. */
    static String elapsed(Duration elapsed) {
      var seconds = Math.max(elapsed.toSeconds(), 0);
      return seconds >= 3600
          ? String.format(
              Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
          : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
  }

  /**
   * A step as it happened.
   *
   * @param time when, in Brussels: 10:42:13
   * @param text what: version 02015R1998-20230401 converted (34 s)
   */
  public record Step(String time, String text) {}

  /** A fact of the act's: Applies, from 1 February 2016, Article 3. */
  public record Fact(String term, String detail) {}

  /** The receipt once it is prepared: ✓ Prepared · 22 versions in English · took 1:12. */
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
