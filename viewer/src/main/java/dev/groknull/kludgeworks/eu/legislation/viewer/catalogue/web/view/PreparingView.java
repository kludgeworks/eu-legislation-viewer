// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import java.time.Duration;
import java.time.Instant;
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
 *   <li>the progress panel
 *   <li>what the catalogue already tells about the act
 * </ul>
 *
 * <p>The event stream pushes the progress again when it changes. {@code PreparationController}
 * sends the stream. The rest of the page does not change.
 *
 * @param asked what the reader asked for, and the statement that it opens here
 * @param self the address of the page, which the page requests again when the act is prepared
 * @param events the address of the event stream of the preparation
 * @param progress the state of the preparation
 * @param known what the catalogue tells about the act
 * @param eurLex the address of the act on EUR-Lex, in the language of the page
 */
public record PreparingView(
    String asked, String self, String events, Progress progress, List<Fact> known, String eurLex) {

  public PreparingView {
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
        Progress.of(act, preparation, now),
        known(act),
        act.id().eurLex(preparation.language()).toString());
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
   * @param running the versions that are worked on now, each with what is done to it. A version is
   *     being converted, or waits to be tried again after an attempt that failed. For example,
   *     "Version 16 of 22 · from 1 April 2023 · converting".
   * @param settled how many versions are ready or failed
   * @param size how many versions there are
   * @param counts how many versions are ready, and how many could not be made. For example, "16 of
   *     22 versions ready".
   * @param seconds how long the preparation has taken so far, in seconds. The clock on the page
   *     starts from this value.
   * @param elapsed the same time, as the page shows it. For example, 4:12.
   * @param dates when the amended provisions apply, which Cellar gave with the act. For example,
   *     "386 dates, one for each amended provision". The third step shows it, done.
   */
  public record Progress(
      @Nullable String waiting,
      List<String> running,
      int settled,
      int size,
      String counts,
      long seconds,
      String elapsed,
      String dates) {

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

    /** Returns the state of {@code preparation} of {@code act} at the time {@code now}. */
    public static Progress of(Act act, Preparation preparation, Instant now) {
      var versions = preparation.versions();
      var running = new ArrayList<String>();
      for (var i = 0; i < versions.size(); i++) {
        var version = versions.get(i);
        var line =
            "Version "
                + (i + 1)
                + " of "
                + versions.size()
                + " · from "
                + Dates.inWords(version.date());
        switch (preparation.texts().get(version)) {
          case Text.Converting _ -> running.add(line + " · converting");
          case Text.Waiting waiting when waiting.attempt() > 0 ->
              running.add(
                  line + " · attempt " + waiting.attempt() + " didn't work; trying again shortly");
          case null, default -> {
            // not worked on: waiting its turn, or settled
          }
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
          elapsed(Duration.between(preparation.started(), now)),
          dates(act.amendments().size()));
    }

    /**
     * Returns how many dates of amended provisions there are, in words. For example, "386 dates,
     * one for each amended provision".
     */
    static String dates(int amended) {
      return switch (amended) {
        case 0 -> "None: no act has amended its provisions";
        case 1 -> "1 date, for the one amended provision";
        default -> amended + " dates, one for each amended provision";
      };
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
   * A fact about the act. For example, the term "Applies" with the detail "from 1 February 2016,
   * Article 3".
   */
  public record Fact(String term, String detail) {}
}
