// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextMarks;
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
import org.jspecify.annotations.Nullable;

/**
 * The history of one provision, as the Read page shows it under the provision: the acts that
 * changed it, with the state of each change on the day, and a link to each change. The rules are
 * the act's: {@link Act#historyOf}, {@link Act#stateOn} and {@link Act#firstWith}. This view puts
 * them into words.
 *
 * @param heading the heading. For example, "History of Article 6(2)".
 * @param count how many amending acts changed the provision, in words. Null if the data names no
 *     act.
 * @param lines one line for each change, newest act first
 * @param unknown what the page says where the data names no act. Null if it names one.
 * @param compare the address of the comparison of the wording of the provision on two days. Null if
 *     fewer than two amending acts changed it.
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
   * Returns the history of the unit {@code unit} of the text of {@code reading}, for the day {@code
   * day}.
   *
   * @param texts the marks of the built texts in {@code language}, by version. They must have the
   *     text of {@code reading}.
   * @param today the date today, which gives the tense
   */
  public static ProvisionView of(
      Act act,
      Version reading,
      String unit,
      LocalDate day,
      LocalDate today,
      Language language,
      Map<Version, TextMarks> texts) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var text = Objects.requireNonNull(texts.get(reading), "the text of the version is not built");
    var history = act.historyOf(unit, text, language);
    var past = day.isBefore(today);
    var kept = day.equals(today) ? null : day;
    var amending = act.amendingActs(language);
    var name =
        history.changes().stream()
            .filter(
                change ->
                    change
                        .location()
                        .in(text.eIds())
                        .filter(found -> !found.within() && found.eIds().equals(List.of(unit)))
                        .isPresent())
            .map(change -> change.location().name())
            .filter(each -> !each.isEmpty())
            .findFirst()
            .orElse(null);
    var lines = new ArrayList<Line>();
    for (var change : history.changes()) {
      var place = change.location().name();
      var page = href(eli, change.act(), kept, language, amending);
      lines.add(
          new Line(
              act.cited(change.act()),
              page,
              AmendingView.made(change.role()).toLowerCase(Locale.ROOT)
                  + " "
                  + (place.isEmpty() || place.equals(name) ? "it" : place)
                  + ".",
              sentence(States.note(act, change.act(), Act.days(change), day, past)),
              States.tone(act.stateOn(change.act(), Act.days(change), day)),
              amending.contains(change.act())
                  ? page
                      + "#"
                      + URLEncoder.encode(AmendingView.anchor(change), StandardCharsets.UTF_8)
                  : null));
    }
    for (var each : history.marking()) {
      lines.add(
          new Line(
              act.cited(each),
              href(eli, each, kept, language, amending),
              each.corrigendum() ? "corrected it." : "changed it.",
              each.corrigendum() ? null : sentence(States.note(act, each, Set.of(), day, past)),
              each.corrigendum() ? null : States.tone(act.stateOn(each, Set.of(), day)),
              null));
    }
    var acts = history.acts().stream().filter(each -> !each.corrigendum()).toList();
    return new ProvisionView(
        "History of " + (name == null ? "this provision" : name),
        lines.isEmpty()
            ? null
            : acts.size() == 1
                ? "1 amending act changed it"
                : acts.size() + " amending acts changed it",
        lines,
        lines.isEmpty() ? unknown(act, unit, language, texts) : null,
        acts.size() < 2 ? null : compare(act, acts, reading, unit, language));
  }

  /** Returns {@code note} with a full stop at its end, if it has none. */
  private static @Nullable String sentence(@Nullable String note) {
    return note == null || note.endsWith(".") ? note : note + ".";
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
      Act act, String unit, Language language, Map<Version, TextMarks> texts) {
    return act.firstWith(unit, language, texts)
        .filter(first -> first.kind() != Version.Kind.ORIGINAL)
        .map(
            first ->
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
   * Returns the address of the comparison that shows the wording of the unit before the first of
   * {@code acts} and after the last. It starts at the version before the first version that
   * integrates one of the acts. It ends at the last version that integrates one of them, or at
   * {@code reading} if that is later. Null if no version integrates an act, or if no version is
   * before it.
   */
  private static @Nullable String compare(
      Act act, List<ActIdentifier> acts, Version reading, String unit, Language language) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var integrating =
        acts.stream()
            .flatMap(each -> act.integrating(each, language).stream())
            .sorted(Comparator.comparing(Version::date))
            .toList();
    if (integrating.isEmpty()) {
      return null;
    }
    var from = act.timeline(language).flatMap(each -> each.predecessor(integrating.getFirst()));
    var last = integrating.getLast();
    var to = last.date().isAfter(reading.date()) ? last : reading;
    return from.filter(each -> each.date().isBefore(to.date()))
        .map(
            each ->
                Links.compare(eli, each.date(), to.date(), language)
                    + "#"
                    + URLEncoder.encode(unit, StandardCharsets.UTF_8))
        .orElse(null);
  }

  /**
   * Returns the units of the text of {@code reading} that have a history to show, by their eIds:
   * the units with a change of their own, and the units that the data names no act for. The text
   * shows a link at each of them that has no ▼ mark. A unit with a ▼ mark opens its history from
   * the mark.
   */
  public static List<String> units(
      Act act, Version reading, Language language, Map<Version, TextMarks> texts) {
    var text = texts.get(reading);
    if (text == null) {
      return List.of();
    }
    var units = new ArrayList<>(act.changedUnits(text.eIds(), language).keySet());
    units.addAll(act.unattributed(reading, language, texts));
    return units;
  }

  /**
   * One change of the provision.
   *
   * @param act the act, as cited
   * @param href the address of the page of the act. Null if the act has no page.
   * @param words what the act did. For example, "replaced it." or "replaced Article 6."
   * @param note the state of the change on the day, with the date and the clause. Null where the
   *     change applies and the data gives no day for it.
   * @param tone "warn" where the change does not apply on the day, "open" where the data does not
   *     tell, "off" where the act is not in force yet. Null where the change applies.
   * @param change the address of the change on the page of its act. Null if the data does not give
   *     the change.
   */
  public record Line(
      String act,
      @Nullable String href,
      String words,
      @Nullable String note,
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
