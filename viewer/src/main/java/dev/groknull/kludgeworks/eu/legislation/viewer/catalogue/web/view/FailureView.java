// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Trace;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Why a text couldn't be made, in its place, at the address the reader asked for (Design: When it
 * fails): one panel, with words for each kind of failure and only the actions that can help. Trying
 * again posts to the page itself, only where it may help (Failure.retryable).
 *
 * @param fault whether it is a fault, the source's or ours, rather than how things are
 * @param heading what happened: The Publications Office didn't answer
 * @param words what it means for the reader
 * @param facts which version, and what else may help: the languages it is in
 * @param retry where Try again posts: the page; none where trying again can't help
 * @param actions the links that can help, the likeliest first
 * @param reference what to quote reporting it; none where a report can't help
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
   * The page of {@code version}, at {@code self}, whose text in {@code language} couldn't be made
   * ({@code failed}).
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
   * The page of {@code version}, at {@code self}, which the Publications Office lists no text of in
   * {@code language} (Design D-64): as a text it has no Formex of, Not available in Irish, with the
   * languages it is in.
   */
  public static FailureView missing(Act act, Version version, Language language, String self) {
    return of(act, version, Failure.NO_FORMEX, null, language, self, List.of());
  }

  /**
   * The comparison of {@code from} with {@code to}, at {@code self}, where {@code failed}'s text
   * (either end's) couldn't be made: as its page's, with the comparison of the versions either side
   * of it first.
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
   * The act, at {@code self}, none of whose texts in the language could be made ({@code
   * preparation}): This act couldn't be prepared, and the first failure; Back to the results of the
   * reader's last search, if there was one ({@code results}).
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
        Action.link("Read it on EUR-Lex", Links.eurLex(act.id().celex(), preparation.language()));
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
            celex + ", from " + Wording.date(version.date()) + " · " + Wording.made(version)));
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
    // by date: the version may not be one of the language's
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
                        "Read the version before (from " + Wording.date(before.date()) + ")",
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
                        "Read the version after (from " + Wording.date(after.date()) + ")",
                        Links.read(eli, after, language))));
    actions.add(Action.link("Read this version on EUR-Lex", Links.eurLex(celex, language)));
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

  /** What to quote reporting it: 32015R1998 · eng · 7f3c, the attempt's trace (Design D-62). */
  private static String trace(Version version, Text.Failed failed, Language language) {
    return version.celex()
        + " · "
        + language.code().toLowerCase(Locale.ROOT)
        + " · "
        + Trace.of(version.celex(), language, failed.attempt());
  }

  private static Eli eli(Act act) {
    return Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
  }

  private static Optional<Timeline> timeline(Act act, Language language) {
    return act.timeline().in(language);
  }

  /**
   * A fact of the version's: Version, 02015R1998-20230401, from 1 April 2023 · made by 2023/566.
   */
  public record Fact(String term, String detail) {}

  /**
   * A link that can help.
   *
   * @param button whether it is drawn as a button, as the ways on are; a plain link, as EUR-Lex is
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
