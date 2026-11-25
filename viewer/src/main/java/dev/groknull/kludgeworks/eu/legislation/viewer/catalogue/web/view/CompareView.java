// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActOverTime;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LawOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheHistoryOfAnAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The head of the Compare page: the two days of the comparison, and the amending acts between them.
 * The page shows days only. It does not name the versions that give the texts.
 *
 * <p>The head shows:
 *
 * <ul>
 *   <li>a heading: "767/2008 on two days", or "Article 6(2) of 767/2008 on two days" where the page
 *       is limited to one provision
 *   <li>the day control, with the two days
 *   <li>one line with the amending acts between the two days: "From 2 August 2021 to 17 November
 *       2026. 2 amending acts changed Article 6(2) between these days: 2021/1152 and 2024/1356."
 *       Each act is a link to its page.
 * </ul>
 *
 * <p>The acts are the amending acts that the text of the To day includes and the text of the From
 * day does not, {@link Act#integratedBetween} and {@link TheHistoryOfAnAct#amendingActs}. For one
 * provision, they are those of them that changed the provision, {@link
 * TheHistoryOfAnAct#historyOfAProvision}. The line leaves out the corrigenda, and the acts that
 * change only the text in other languages.
 *
 * <p>For one provision, the page also lists the steps between the two days, oldest first: one line
 * for each change, as the history of the provision on Read has it ({@link ProvisionView}). A link
 * opens the comparison of the whole act on the same two days.
 *
 * @param heading the heading of the page
 * @param days the two days, in words: "From 2 August 2021 to 17 November 2026."
 * @param picker the day control, with the two days. It keeps the provision that the page is limited
 *     to. With one day, it goes to the history of the act.
 * @param said how many amending acts changed the act or the provision between the days. Null where
 *     a day is before the first text of the act.
 * @param acts the amending acts, in the order that the texts include them
 * @param whole the address of the comparison of the whole act on the two days. Null where the page
 *     shows the whole act.
 * @param steps the changes of the provision between the two days, oldest first. Empty for the whole
 *     act.
 */
public record CompareView(
    String heading,
    String days,
    DayView picker,
    @Nullable String said,
    List<Link> acts,
    @Nullable String whole,
    List<ProvisionView.Line> steps) {

  public CompareView {
    acts = List.copyOf(acts);
    steps = List.copyOf(steps);
  }

  /**
   * Returns the head of the comparison of the law on {@code fromDay} with the law on {@code toDay},
   * for {@code act}, on {@code today}. {@code from} and {@code to} are the versions whose texts
   * show the law on the two days, {@link LawOnADay#shown}.
   *
   * @param occurrence the eId of the provision that the page is limited to, or null
   * @param tags the tags of the built texts in {@code language}, by version
   */
  public static CompareView of(
      Act act,
      Version from,
      LocalDate fromDay,
      Version to,
      LocalDate toDay,
      @Nullable String occurrence,
      Language language,
      LocalDate today,
      Map<Version, TextTags> tags) {
    var eli = eli(act);
    var amending = ActOverTime.amendingActsOf(act, language);
    var between =
        act.inclusions(language, tags).between(from, to).stream()
            .filter(amending::contains)
            .toList();
    var text = occurrence == null ? null : tags.get(to);
    var name =
        text == null || occurrence == null
            ? null
            : ProvisionView.name(act, occurrence, text, language);
    var changed =
        text == null || occurrence == null
            ? between
            : TheHistoryOfAnAct.actsOfAProvisionBetweenTwoDays(
                    TheHistoryOfAnAct.historyOfAProvision(
                        act,
                        language,
                        occurrence,
                        text,
                        TheHistoryOfAnAct.orderOfTheAmendingActs(act, language)),
                    between)
                .reversed();
    var kept = toDay.equals(today) ? null : toDay;
    var subject = occurrence == null ? act.id().cited() : name == null ? "this provision" : name;
    return new CompareView(
        heading(act, occurrence, name),
        days(fromDay, toDay),
        DayView.of(
            act,
            toDay,
            fromDay,
            Links.history(eli, null, language),
            Links.compare(eli, language),
            occurrence,
            language),
        said(changed.size(), subject),
        links(act, changed, kept, language),
        occurrence == null ? null : Links.compare(eli, fromDay, toDay, language),
        text == null || occurrence == null
            ? List.of()
            : ProvisionView.steps(act, occurrence, text, toDay, today, language, changed));
  }

  /**
   * Returns the head of a comparison whose From, {@code fromDay}, is a day before the first text of
   * {@code act}. It has the days and no acts: a panel in place of the differences says why.
   */
  public static CompareView before(Act act, LocalDate fromDay, LocalDate toDay, Language language) {
    return new CompareView(
        heading(act, null, null),
        days(fromDay, toDay),
        DayView.of(
            act,
            toDay,
            fromDay,
            Links.history(eli(act), null, language),
            Links.compare(eli(act), language),
            null,
            language),
        null,
        List.of(),
        null,
        List.of());
  }

  /** Returns the acts of the line, each with what comes after it. */
  private static List<Link> links(
      Act act, List<ActIdentifier> acts, @Nullable LocalDate kept, Language language) {
    var links = new ArrayList<Link>();
    for (var i = 0; i < acts.size(); i++) {
      var each = acts.get(i);
      links.add(
          new Link(
              act.cited(each),
              Links.amending(eli(act), each, kept, language),
              i == acts.size() - 1 ? "." : i == acts.size() - 2 ? " and" : ","));
    }
    return links;
  }

  private static String heading(Act act, @Nullable String occurrence, @Nullable String name) {
    var cited = act.id().cited();
    return (occurrence == null ? cited : (name == null ? "A provision" : name) + " of " + cited)
        + " on two days";
  }

  private static String days(LocalDate fromDay, LocalDate toDay) {
    return "From " + Dates.inWords(fromDay) + " to " + Dates.inWords(toDay) + ".";
  }

  private static String said(int acts, String subject) {
    return switch (acts) {
      case 0 -> "No amending act changed " + subject + " between these days.";
      case 1 -> "1 amending act changed " + subject + " between these days:";
      default -> acts + " amending acts changed " + subject + " between these days:";
    };
  }

  private static Eli eli(Act act) {
    return Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
  }

  /**
   * An amending act between the two days.
   *
   * @param act the act, as cited
   * @param href the address of the page of the act
   * @param after what comes after the act in the line: a comma, " and", or a full stop
   */
  public record Link(String act, String href, String after) {}
}
