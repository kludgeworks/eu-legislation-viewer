// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ProvisionOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The history of one provision, as the Read page shows it under the provision: the acts that
 * changed it, with the state of each change on the day, and a link to each change. {@link
 * ProvisionOnADay} has what the rules give. This view puts it into words.
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
   * Returns the history of {@code provision} in words.
   *
   * @param today the date today, which gives the tense
   */
  public static ProvisionView of(ProvisionOnADay provision, LocalDate today) {
    var name = provision.name();
    var lines = lines(provision, today, each -> true);
    var acts = provision.amendingActs();
    return new ProvisionView(
        "History of " + (name == null ? "this provision" : name),
        lines.isEmpty()
            ? null
            : acts.size() == 1
                ? "1 amending act changed it"
                : acts.size() + " amending acts changed it",
        lines,
        lines.isEmpty() ? unknown(provision) : null,
        acts.size() < 2 ? null : compare(provision));
  }

  /**
   * Returns the changes that {@code acts} made to {@code provision}, oldest act first, with the
   * state of each change on its day. The Compare page of one provision lists them as the steps
   * between its two days.
   */
  static List<Line> steps(ProvisionOnADay provision, LocalDate today, List<ActIdentifier> acts) {
    return lines(provision, today, acts::contains).reversed();
  }

  /** Returns one line for each change of {@code provision} by an act that {@code kept} accepts. */
  private static List<Line> lines(
      ProvisionOnADay provision, LocalDate today, Predicate<ActIdentifier> kept) {
    var act = provision.overTime().act();
    var language = provision.overTime().language();
    var day = provision.day();
    var name = provision.name();
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var past = day.isBefore(today);
    var on = day.equals(today) ? null : day;
    var amending =
        provision.overTime().amendingActs().stream().map(ActOverTime.Amending::act).toList();
    var lines = new ArrayList<Line>();
    for (var each : provision.changes()) {
      var change = each.change();
      if (!kept.test(change.act())) {
        continue;
      }
      var place = change.place().words();
      var page = href(eli, change.act(), on, language, amending);
      lines.add(
          new Line(
              act.cited(change.act()),
              page,
              AmendingView.made(change.role()).toLowerCase(Locale.ROOT)
                  + " "
                  + (place.isEmpty() || place.equals(name) ? "it" : place)
                  + ".",
              sentence(
                  StateWording.statement(
                      act,
                      change.act(),
                      each.appliesFrom(),
                      each.state().state(),
                      each.whole(),
                      past)),
              StateWording.tone(each.state().state()),
              amending.contains(change.act())
                  ? page
                      + "#"
                      + URLEncoder.encode(AmendingView.anchor(change), StandardCharsets.UTF_8)
                  : null));
    }
    for (var each : provision.tagging()) {
      if (!kept.test(each.act())) {
        continue;
      }
      var corrigendum = each.act().corrigendum();
      lines.add(
          new Line(
              act.cited(each.act()),
              href(eli, each.act(), on, language, amending),
              corrigendum ? "corrected it." : "changed it.",
              corrigendum
                  ? null
                  : sentence(
                      StateWording.statement(
                          act,
                          each.act(),
                          each.appliesFrom(),
                          each.state().state(),
                          each.whole(),
                          past)),
              corrigendum ? null : StateWording.tone(each.state().state()),
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
  private static Unknown unknown(ProvisionOnADay provision) {
    var language = provision.overTime().language();
    return Optional.ofNullable(provision.firstText())
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
   * Returns the address of the comparison that shows the wording of the provision before the first
   * of its amending acts and after the last. It starts at the version before the first version that
   * integrates one of the acts. It ends at the last version that integrates one of them, or at
   * {@code reading} if that is later. Null if no version integrates an act, or if no version is
   * before it.
   */
  private static @Nullable String compare(ProvisionOnADay provision) {
    var reading = provision.reading();
    var act = provision.overTime().act();
    var language = provision.overTime().language();
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var integrating = provision.integrating();
    if (integrating.isEmpty()) {
      return null;
    }
    var from = act.timeline(language).flatMap(each -> each.predecessor(integrating.getFirst()));
    var last = integrating.getLast();
    var to = last.date().isAfter(reading.date()) ? last : reading;
    return from.filter(each -> each.date().isBefore(to.date()))
        .map(each ->
            new ComparePage(Links.compare(eli, language), each.date(), to.date(), null, null)
                .href(provision.occurrence()))
        .orElse(null);
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
