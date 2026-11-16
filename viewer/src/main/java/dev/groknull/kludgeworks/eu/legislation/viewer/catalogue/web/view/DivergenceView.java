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
 * </ul>
 *
 * @param title what differs
 * @param detail from when the act applies, and from when the text includes it
 * @param href the address of the one link
 * @param link the text of the link
 * @param external whether the link leaves the viewer, for EUR-Lex
 */
public record DivergenceView(
    String title, String detail, String href, String link, boolean external) {

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
      case EARLY -> early(act, divergence, eli, asked, language);
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
          applies + ". No version includes it yet.",
          act.eurLex(late.act(), language).toString(),
          "Official Journal",
          true);
    }
    return new DivergenceView(
        title,
        applies + ". Included from " + Dates.inWords(included.date()) + ".",
        Links.compare(eli, version.date(), included.date(), language, late.act(), null),
        "See its changes in a comparison",
        false);
  }

  private static DivergenceView early(
      Act act, Divergence early, Eli eli, LocalDate asked, Language language) {
    var from = early.appliesFrom();
    var inPart =
        act.datesOf(early.act()).stream()
            .anyMatch(date -> date.partial() && date.date() != null && !date.date().isAfter(asked));
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
}
