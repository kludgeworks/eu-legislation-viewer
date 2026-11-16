// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One entry of the warning on the Reading card: why the text is not the law on the day asked.
 *
 * <ul>
 *   <li>A late text: "Not included: 2017/815", "Applied from 1 June 2017. Included from 7 June
 *       2017.", and a link to the comparison that shows what the act changed. If no version
 *       includes the act yet, the link goes to the act on EUR-Lex.
 *   <li>An early text: "In force. Does not apply yet.", "Applies from 25 May 2018 · Article 99(2)
 *       of 2016/679.", and a link to the first day on which it applies.
 *   <li>An act in force that no version includes, whose day of application Cellar does not give:
 *       "Date not confirmed: 2021/1133", "In force from 2 August 2021. No version includes it. It
 *       applies from a date set in Article 6 of 2021/1133, and Cellar does not give the day.", and
 *       a link to the act on EUR-Lex.
 *   <li>Text of an amending act that is included before it applies: "Included before it applies:
 *       2017/2226", "Applies from 11 October 2019. Included from 12 January 2018.", and a link to
 *       the comparison that shows what the act changed.
 * </ul>
 *
 * <p>Where the text differs in some provisions only, the detail names them. For example, "In point
 * 4.1.3.3 and point 4.1.3.4." It names three at most.
 *
 * @param title what differs
 * @param detail from when the act applies, and from when the text includes it
 * @param href the address of the one link
 * @param link the text of the link
 * @param external whether the link leaves the viewer, for EUR-Lex
 */
public record DivergenceView(
    String title, String detail, String href, String link, boolean external) {

  /** How many provisions the detail names. */
  private static final int SHOWN = 3;

  /**
   * Returns the entry for {@code divergence} of {@code act} on {@code asked}, a day on which {@code
   * version} gives the text, in {@code language}.
   *
   * @param today the date today, which tells "applied" from "applies"
   */
  public static DivergenceView of(
      Act act,
      Divergence divergence,
      Version version,
      LocalDate asked,
      Language language,
      LocalDate today) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    return switch (divergence.kind()) {
      case LATE -> late(act, divergence, version, eli, language, today);
      case EARLY ->
          divergence.whole(act.id())
              ? early(act, divergence, eli, asked, language)
              : included(act, divergence, eli, language);
      case UNCONFIRMED -> unconfirmed(act, divergence, language);
    };
  }

  private static DivergenceView late(
      Act act, Divergence late, Version version, Eli eli, Language language, LocalDate today) {
    var title = "Not included: " + act.cited(late.act());
    var from = late.appliesFrom();
    var applies = (from.isAfter(today) ? "Applies from " : "Applied from ") + Dates.inWords(from);
    var included = late.included();
    if (included == null) {
      return new DivergenceView(
          title,
          applies + ". No version includes it yet." + places(late),
          act.eurLex(late.act(), language).toString(),
          "Official Journal",
          true);
    }
    return new DivergenceView(
        title,
        applies + ". Included from " + Dates.inWords(included.date()) + "." + places(late),
        Links.compare(eli, version.date(), included.date(), language, late.act(), null),
        "See its changes in a comparison",
        false);
  }

  private static DivergenceView unconfirmed(Act act, Divergence unconfirmed, Language language) {
    var applies = MarksView.applies(act, unconfirmed.act());
    return new DivergenceView(
        "Date not confirmed: " + act.cited(unconfirmed.act()),
        "In force from "
            + Dates.inWords(unconfirmed.appliesFrom())
            + ". No version includes it."
            + (applies == null ? "" : " It " + applies + ", and Cellar does not give the day.")
            + places(unconfirmed),
        act.eurLex(unconfirmed.act(), language).toString(),
        "Official Journal",
        true);
  }

  private static DivergenceView early(
      Act act, Divergence early, Eli eli, LocalDate asked, Language language) {
    var from = early.appliesFrom();
    var inPart = act.appliesInPartOn(early.act(), asked);
    var clause =
        early.applies().clause().provisions().stream()
            .findFirst()
            .map(provision -> " · " + provision.cited() + " of " + act.cited(early.act()))
            .orElse("");
    return new DivergenceView(
        inPart ? "In force. Applies in part only." : "In force. Does not apply yet.",
        (inPart ? "Applies in full from " : "Applies from ") + Dates.inWords(from) + clause + ".",
        Links.read(eli, from, language),
        "Go to " + Dates.inWords(from) + ", the first day it applies" + (inPart ? " in full" : ""),
        false);
  }

  /**
   * Returns the entry for text of an amending act that a version includes before it applies. The
   * link goes to the comparison of that version with the version before it, for the act.
   */
  private static DivergenceView included(Act act, Divergence early, Eli eli, Language language) {
    var included = Objects.requireNonNull(early.included());
    var from = early.appliesFrom();
    var before = act.timeline(language).flatMap(timeline -> timeline.predecessor(included));
    return new DivergenceView(
        "Included before it applies: " + act.cited(early.act()),
        "Applies from "
            + Dates.inWords(from)
            + ". Included from "
            + Dates.inWords(included.date())
            + "."
            + places(early),
        before
            .map(
                each ->
                    Links.compare(eli, each.date(), included.date(), language, early.act(), null))
            .orElseGet(() -> Links.read(eli, from, language)),
        before.isPresent() ? "See its changes in a comparison" : "Go to " + Dates.inWords(from),
        false);
  }

  /**
   * Returns the provisions of {@code divergence}, as " In point 4.1.3.3 and point 4.1.3.4.", or an
   * empty string where the whole act differs. It names three at most, and counts the others.
   */
  private static String places(Divergence divergence) {
    var names =
        divergence.places().stream().map(place -> place.location().name()).distinct().toList();
    if (names.isEmpty()) {
      return "";
    }
    var shown = names.subList(0, Math.min(names.size(), SHOWN));
    var more = names.size() - shown.size();
    var list =
        more > 0
            ? String.join(", ", shown) + " and " + more + " more"
            : shown.size() == 1
                ? shown.getFirst()
                : String.join(", ", shown.subList(0, shown.size() - 1)) + " and " + shown.getLast();
    return " In " + list + ".";
  }
}
