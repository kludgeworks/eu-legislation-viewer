// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActHistory;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ProvisionHistory;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The history of one provision, as the Read page shows it under the provision: the acts that
 * changed it, with the state of each change on the day, and a link to each change. The rules are
 * the act's: {@link ProvisionHistory#of}, {@link ActDates#stateOn} and {@link
 * ProvisionHistory#firstWith}. This view puts them into words.
 *
 * @param heading the heading. For example, "History of Article 6(2)".
 * @param count how many amending acts changed the provision, in words. Null if the data names no
 *     act.
 * @param lines one line for each change, newest act first
 * @param unknown what the page says where the data names no act. Null if it names one.
 * @param compare the address of the Compare page of the provision, for two days. Null if fewer than
 *     two amending acts changed it.
 */
public record ProvisionView(
    String heading,
    @Nullable String count,
    List<Line> lines,
    @Nullable Unknown unknown,
    @Nullable String compare) {

  public ProvisionView {
    lines = List.copyOf(lines);
  }

  /**
   * Returns the history of the occurrence {@code occurrence} of the text of {@code reading}, for
   * the day {@code day}.
   *
   * @param texts the tags of the built texts in {@code language}, by version. They must have the
   *     text of {@code reading}.
   * @param today the date today, which gives the tense
   */
  public static ProvisionView of(
      Act act,
      Version reading,
      String occurrence,
      LocalDate day,
      LocalDate today,
      Language language,
      Map<Version, TextTags> texts) {
    var text = Objects.requireNonNull(texts.get(reading), "the text of the version is not built");
    var history = ProvisionHistory.of(act, occurrence, text, language);
    var name = name(history, occurrence, text);
    var lines = lines(act, history, name, day, today, language, each -> true);
    var acts = history.acts().stream().filter(each -> !each.corrigendum()).toList();
    return new ProvisionView(
        "History of " + (name == null ? "this provision" : name),
        lines.isEmpty()
            ? null
            : acts.size() == 1
                ? "1 amending act changed it"
                : acts.size() + " amending acts changed it",
        lines,
        lines.isEmpty() ? unknown(act, occurrence, language, texts) : null,
        acts.size() < 2 ? null : compare(act, acts, reading, occurrence, language));
  }

  /**
   * Returns the name of the occurrence {@code occurrence} of {@code text}, as a change of the
   * provision names it. For example, "Article 6(2)". Null if no change names it.
   */
  static @Nullable String name(Act act, String occurrence, TextTags text, Language language) {
    return name(ProvisionHistory.of(act, occurrence, text, language), occurrence, text);
  }

  private static @Nullable String name(ProvisionHistory history, String occurrence, TextTags text) {
    return history.changes().stream()
        .filter(change ->
            change
                .location()
                .in(text.eIds())
                .filter(found -> !found.within() && found.eIds().equals(List.of(occurrence)))
                .isPresent())
        .map(change -> change.location().place().words())
        .filter(each -> !each.isEmpty())
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns the changes that {@code acts} made to the occurrence {@code occurrence} of {@code
   * text}, oldest act first, with the state of each change on {@code day}. The Compare page of one
   * provision lists them as the steps between its two days.
   */
  static List<Line> steps(
      Act act,
      String occurrence,
      TextTags text,
      LocalDate day,
      LocalDate today,
      Language language,
      List<ActIdentifier> acts) {
    var history = ProvisionHistory.of(act, occurrence, text, language);
    return lines(
            act, history, name(history, occurrence, text), day, today, language, acts::contains)
        .reversed();
  }

  /** Returns one line for each change of {@code history} by an act that {@code kept} accepts. */
  private static List<Line> lines(
      Act act,
      ProvisionHistory history,
      @Nullable String name,
      LocalDate day,
      LocalDate today,
      Language language,
      Predicate<ActIdentifier> kept) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var past = day.isBefore(today);
    var on = day.equals(today) ? null : day;
    var amending = ActHistory.of(act).amendingActs(language);
    var lines = new ArrayList<Line>();
    for (var change : history.changes()) {
      if (!kept.test(change.act())) {
        continue;
      }
      var place = change.location().place().words();
      var page = href(eli, change.act(), on, language, amending);
      lines.add(
          new Line(
              act.cited(change.act()),
              page,
              AmendingView.made(change.role()).toLowerCase(Locale.ROOT)
                  + " "
                  + (place.isEmpty() || place.equals(name) ? "it" : place)
                  + ".",
              sentence(StateWording.statement(act, change.act(), ActDates.days(change), day, past)),
              StateWording.tone(act.dates().stateOn(change.act(), ActDates.days(change), day)),
              amending.contains(change.act())
                  ? page
                      + "#"
                      + URLEncoder.encode(AmendingView.anchor(change), StandardCharsets.UTF_8)
                  : null));
    }
    for (var each : history.tagging()) {
      if (!kept.test(each)) {
        continue;
      }
      lines.add(
          new Line(
              act.cited(each),
              href(eli, each, on, language, amending),
              each.corrigendum() ? "corrected it." : "changed it.",
              each.corrigendum()
                  ? null
                  : sentence(StateWording.statement(act, each, Set.of(), day, past)),
              each.corrigendum()
                  ? null
                  : StateWording.tone(act.dates().stateOn(each, Set.of(), day)),
              null));
    }
    return lines;
  }

  /** Returns {@code statement} with a full stop at its end, if it has none. */
  private static @Nullable String sentence(@Nullable String statement) {
    return statement == null || statement.endsWith(".") ? statement : statement + ".";
  }

  /**
   * Returns the address of the page that shows what {@code each} did: the page of an amending act,
   * or the list of the corrigenda in the history. Null for any other act.
   */
  private static @Nullable String href(
      Eli eli,
      ActIdentifier each,
      @Nullable LocalDate kept,
      Language language,
      List<ActIdentifier> amending) {
    if (amending.contains(each)) {
      return Links.amending(eli, each, kept, language);
    }
    return each.corrigendum() ? Links.history(eli, kept, language) + "#corrigenda" : null;
  }

  /**
   * Returns what the page says of a provision that no act of the data changed: the first text that
   * has it.
   */
  private static Unknown unknown(
      Act act, String occurrence, Language language, Map<Version, TextTags> texts) {
    return ProvisionHistory.firstWith(act, occurrence, language, texts)
        .filter(first -> first.kind() != Version.Kind.ORIGINAL)
        .map(first ->
            new Unknown(
                "The data does not say which act inserted this provision. It first appears in"
                    + " the text of "
                    + Dates.inWords(first.date())
                    + ".",
                "The text of " + Dates.inWords(first.date()),
                first.eurLex(language).toString()))
        .orElseGet(
            () -> new Unknown("The data names no act that changed this provision.", null, null));
  }

  /**
   * Returns the address of the comparison that shows the wording of the occurrence before the first
   * of {@code acts} and after the last. It starts at the version before the first version that
   * integrates one of the acts. It ends at the last version that integrates one of them, or at
   * {@code reading} if that is later. Null if no version integrates an act, or if no version is
   * before it.
   */
  private static @Nullable String compare(
      Act act, List<ActIdentifier> acts, Version reading, String occurrence, Language language) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var integrating =
        acts.stream()
            .flatMap(each -> ActHistory.of(act).integrating(each, language).stream())
            .sorted(Comparator.comparing(Version::date))
            .toList();
    if (integrating.isEmpty()) {
      return null;
    }
    var from = act.timeline(language).flatMap(each -> each.predecessor(integrating.getFirst()));
    var last = integrating.getLast();
    var to = last.date().isAfter(reading.date()) ? last : reading;
    return from.filter(each -> each.date().isBefore(to.date()))
        .map(each ->
            new ComparePage(Links.compare(eli, language), each.date(), to.date(), null, null)
                .href(occurrence))
        .orElse(null);
  }

  /**
   * Returns the occurrences of the text of {@code reading} that have a history to show, by their
   * eIds: the occurrences with a change of their own, and the occurrences that the data names no
   * act for. The text shows a link at each of them that has no ▼ tag. An occurrence with a ▼ tag
   * opens its history from the tag.
   */
  public static List<String> occurrences(
      Act act, Version reading, Language language, Map<Version, TextTags> texts) {
    var text = texts.get(reading);
    if (text == null) {
      return List.of();
    }
    var occurrences =
        new ArrayList<>(ProvisionHistory.changedOccurrences(act, text.eIds(), language).keySet());
    occurrences.addAll(ProvisionHistory.unattributed(act, reading, language, texts));
    return occurrences;
  }

  /**
   * One change of the provision.
   *
   * @param act the act, as cited
   * @param href the address of the page of the act. Null if the act has no page.
   * @param words what the act did. For example, "replaced it." or "replaced Article 6."
   * @param statement the state of the change on the day, with the date and the clause. Null where
   *     the change applies and the data gives no day for it.
   * @param tone "warn" where the change does not apply on the day, "open" where the data does not
   *     tell, "off" where the act is not in force yet. Null where the change applies.
   * @param change the address of the change on the page of its act. Null if the data does not give
   *     the change.
   */
  public record Line(
      String act,
      @Nullable String href,
      String words,
      @Nullable String statement,
      @Nullable String tone,
      @Nullable String change) {}

  /**
   * What the page says where the data names no act for the provision.
   *
   * @param words the sentences
   * @param link the words of the link to the first text that has the provision. Null if no built
   *     text has it, or if the act as published has it.
   * @param href the address of that text on EUR-Lex
   */
  public record Unknown(String words, @Nullable String link, @Nullable String href) {}
}
