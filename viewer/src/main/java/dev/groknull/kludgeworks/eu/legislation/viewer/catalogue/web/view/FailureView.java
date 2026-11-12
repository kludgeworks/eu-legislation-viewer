// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * A panel that says why a text could not be made. The panel shows in place of the text, at the
 * address that the reader asked for. It has words for each kind of failure, and only the actions
 * that can help. "Try again" posts to the page itself. It shows only where trying again may help,
 * as {@link Failure#retryable()} tells.
 *
 * @param fault whether the failure is a fault of the source or of the viewer, rather than how
 *     things are
 * @param heading what happened. For example, "The Publications Office didn't answer".
 * @param words what the failure means for the reader
 * @param facts which version failed, and other facts that may help. For example, the languages that
 *     the version is in.
 * @param retry the address that "Try again" posts to, which is the page. Null where trying again
 *     cannot help.
 * @param actions the links that can help, the most likely first
 * @param reference the reference to quote in a report. Null where a report cannot help.
 */
public record FailureView(
    boolean fault,
    String heading,
    String words,
    List<Fact> facts,
    @Nullable String retry,
    List<Action> actions,
    @Nullable String reference) {

  public FailureView {
    facts = List.copyOf(facts);
    actions = List.copyOf(actions);
  }

  /**
   * Returns the panel for the page of {@code version}, at {@code self}. The text of the version in
   * {@code language} could not be made, as {@code failed} tells.
   */
  public static FailureView reading(
      Act act, Version version, Text.Failed failed, Language language, String self) {
    return of(
        act,
        version,
        failed.failure(),
        trace(version, failed, language),
        language,
        self,
        List.of());
  }

  /**
   * Returns the panel for the page of {@code version}, at {@code self}, when the Publications
   * Office lists no text of it in {@code language}. The panel is the same as for a text that has no
   * Formex. For example, it says "Not available in Irish" and gives the languages that the version
   * is in.
   */
  public static FailureView missing(Act act, Version version, Language language, String self) {
    return of(act, version, Failure.NO_FORMEX, null, language, self, List.of());
  }

  /**
   * Returns the panel for the comparison of {@code from} with {@code to}, at {@code self}, when the
   * text of {@code failed} could not be made. {@code failed} is one of the two ends. The panel is
   * the same as for the page of {@code failed}. Its first action is the comparison of the versions
   * on each side of {@code failed}, if both exist.
   */
  public static FailureView comparing(
      Act act,
      Version from,
      Version to,
      Version failed,
      Text.Failed text,
      Language language,
      String self) {
    var eli = eli(act);
    var timeline = timeline(act, language);
    var earlier =
        failed.equals(from) ? timeline.flatMap(t -> t.predecessor(from)) : Optional.of(from);
    var later = failed.equals(to) ? timeline.flatMap(t -> t.successor(to)) : Optional.of(to);
    var instead = new ArrayList<Action>();
    if (earlier.isPresent() && later.isPresent()) {
      instead.add(
          Action.button(
              "Compare the versions on either side instead",
              Links.compare(eli, earlier.get(), later.get(), language)));
    }
    return of(act, failed, text.failure(), trace(failed, text, language), language, self, instead);
  }

  /**
   * Returns the panel for the law on {@code asked}, a day before the first version of {@code act},
   * in {@code language}. Nothing moves the day: the panel says that the act didn't exist yet, or
   * wasn't in force yet if it was adopted by then, and when it was adopted and came into force. Its
   * actions are the act as it first came into force, and the act in the Official Journal.
   */
  public static FailureView before(Act act, LocalDate asked, Language language) {
    var eli = eli(act);
    var first = act.timeline().versions().getFirst().date();
    var adopted = act.dateOfDocument();
    var cited = act.id().cited();
    return new FailureView(
        false,
        cited
            + (adopted != null && !asked.isBefore(adopted)
                ? " wasn’t in force yet on "
                : " didn’t exist yet on ")
            + Dates.inWords(asked),
        (adopted == null ? "It" : "It was adopted on " + Dates.inWords(adopted) + " and")
            + " came into force on "
            + Dates.inWords(first)
            + ".",
        List.of(),
        null,
        List.of(
            Action.button("Read it as it first came into force", Links.read(eli, first, language)),
            Action.link("The act in the Official Journal ↗", act.id().eurLex(language).toString())),
        null);
  }

  /**
   * Returns the panel for the act, at {@code self}, when none of its texts in the language could be
   * made, as {@code preparation} tells. The panel says "This act couldn't be prepared" and gives
   * the first failure. If the reader made a search, {@code results} gives it, and the panel links
   * back to its results.
   */
  public static FailureView act(
      Act act, Preparation preparation, String self, Chrome.@Nullable Results results) {
    var failures =
        preparation.texts().values().stream()
            .map(text -> text instanceof Text.Failed failed ? failed.failure() : null)
            .filter(Objects::nonNull)
            .toList();
    var size = preparation.size();
    var eurLex =
        Action.link("Read it on EUR-Lex", act.id().eurLex(preparation.language()).toString());
    return new FailureView(
        true,
        "This act couldn't be prepared",
        (size == 1 ? "Its only version" : "None of its " + size + " versions")
            + " could be converted. The first failure: "
            + (failures.isEmpty() ? "something went wrong" : Wording.failure(failures.getFirst()))
            + ".",
        List.of(),
        failures.stream().anyMatch(Failure::retryable) ? self : null,
        results == null
            ? List.of(eurLex)
            : List.of(
                eurLex,
                Action.link("Back to the results for “" + results.words() + "”", results.href())),
        null);
  }

  private static FailureView of(
      Act act,
      Version version,
      Failure failure,
      @Nullable String trace,
      Language language,
      String self,
      List<Action> first) {
    var eli = eli(act);
    var celex = version.celex();
    var actions = new ArrayList<>(first);
    var facts = new ArrayList<Fact>();
    facts.add(
        new Fact(
            "Version",
            celex + ", from " + Dates.inWords(version.date()) + " · " + Wording.made(version)));
    if (failure == Failure.NO_FORMEX) {
      var others = version.languages().stream().filter(each -> each != language).toList();
      if (!others.isEmpty()) {
        facts.add(
            new Fact(
                "Exists in",
                others.stream().map(Wording::language).collect(Collectors.joining(" · "))));
        var other = others.contains(Language.ENG) ? Language.ENG : others.getFirst();
        actions.add(
            Action.button(
                "Read it in " + Wording.language(other), Links.read(eli, version, other)));
      }
    }
    var timeline = timeline(act, language);
    // Find the versions before and after by date. The version may not be in the timeline of the
    // language.
    timeline
        .flatMap(
            t ->
                t.versions().stream()
                    .filter(each -> each.date().isBefore(version.date()))
                    .reduce((earlier, later) -> later))
        .ifPresent(
            before ->
                actions.add(
                    Action.button(
                        "Read the version before (from " + Dates.inWords(before.date()) + ")",
                        Links.read(eli, before, language))));
    timeline
        .flatMap(
            t ->
                t.versions().stream()
                    .filter(each -> each.date().isAfter(version.date()))
                    .findFirst())
        .ifPresent(
            after ->
                actions.add(
                    Action.button(
                        "Read the version after (from " + Dates.inWords(after.date()) + ")",
                        Links.read(eli, after, language))));
    actions.add(Action.link("Read this version on EUR-Lex", version.eurLex(language).toString()));
    var reference = trace;
    return switch (failure) {
      case UNAVAILABLE ->
          new FailureView(
              true,
              "The Publications Office didn't answer",
              "We asked it for version "
                  + celex
                  + " several times and got no answer. This is usually temporary.",
              facts,
              self,
              actions,
              null);
      case NO_FORMEX ->
          new FailureView(
              false,
              "Not available in " + Wording.language(language),
              "The Publications Office has no file of this version in "
                  + Wording.language(language)
                  + ", so it can't be shown here. Nothing is machine-translated.",
              facts,
              null,
              actions,
              null);
      case MISFILED ->
          new FailureView(
              true,
              "The source file is for another document",
              "The Publications Office's catalogue lists a file under "
                  + celex
                  + ", but its contents are another document's. This is an error at the source,"
                  + " not something you or we can fix here.",
              facts,
              null,
              actions,
              reference);
      case UNSUPPORTED ->
          new FailureView(
              false,
              "This version uses something the viewer can't show yet",
              "Part of version "
                  + celex
                  + " uses a structure the viewer doesn't handle yet. The other versions aren't"
                  + " affected.",
              facts,
              null,
              actions,
              reference);
      case OUT_OF_SCOPE ->
          new FailureView(
              false,
              "This document isn't a legal act the viewer shows",
              "Version "
                  + celex
                  + " is a kind of document the viewer doesn't show, such as a notice.",
              facts,
              null,
              actions,
              null);
      case ERROR ->
          new FailureView(
              true,
              "Something went wrong on our side",
              "Preparing version "
                  + celex
                  + " failed because of a fault in the viewer, not the source.",
              facts,
              self,
              actions,
              reference);
    };
  }

  /**
   * The reference that a reader quotes in a report, for example {@code 32015R1998 · eng ·
   * 4bf92f3577b34da6a3ce929d0e0e4736}. The last part is the trace id of the attempt that failed. It
   * is not there if no trace was recorded.
   */
  private static String trace(Version version, Text.Failed failed, Language language) {
    var reference = version.celex() + " · " + language.code().toLowerCase(Locale.ROOT);
    return failed.trace() == null ? reference : reference + " · " + failed.trace();
  }

  private static Eli eli(Act act) {
    return Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
  }

  private static Optional<Timeline> timeline(Act act, Language language) {
    return act.timeline().in(language);
  }

  /**
   * A fact about the version. For example, the term "Version" with the detail "02015R1998-20230401,
   * from 1 April 2023 · made by 2023/566".
   */
  public record Fact(String term, String detail) {}

  /**
   * A link that can help.
   *
   * @param button whether the link shows as a button. The ways to continue in the viewer are
   *     buttons. The links to EUR-Lex are plain links.
   */
  public record Action(String label, String href, boolean button) {
    static Action button(String label, String href) {
      return new Action(label, href, true);
    }

    static Action link(String label, String href) {
      return new Action(label, href, false);
    }
  }
}
