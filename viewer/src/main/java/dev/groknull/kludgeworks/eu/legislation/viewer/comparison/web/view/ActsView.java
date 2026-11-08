// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.chrome.Dates;
import dev.groknull.kludgeworks.eu.legislation.comparepage.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The acts between the two versions of a comparison, as chips. They are the acts that the versions
 * after From, up to To, integrate. Each chip limits the comparison to the changes of its act, and
 * says how many differences its act made.
 *
 * @param head how many acts there are. For example, "1 act between them".
 * @param chips the chips of the acts, in the order of the versions. If the acts are counted and
 *     there are more than six chips, this list leaves out the chips of acts that made no difference
 *     here.
 * @param none the chips that {@code chips} leaves out, of acts that made no difference here
 * @param counting the address that the counts load from, until the acts are counted. Null when they
 *     are counted, or if they cannot be counted.
 * @param unknown how many differences one of the acts made, where it is not known which act. This
 *     number shows as its own chip. Null if there are none, or until the acts are counted.
 * @param hint the text that explains the counts. It says that a difference that acts made together
 *     counts in the chip of each act. Null until the acts are counted.
 */
public record ActsView(
    String head,
    List<ActsView.Chip> chips,
    List<ActsView.Chip> none,
    @Nullable String counting,
    @Nullable Integer unknown,
    @Nullable String hint) {

  /**
   * The largest number of chips that show in full. Above this number, the chips of acts that made
   * no difference here fold.
   */
  private static final int CHIPS = 6;

  public ActsView {
    chips = List.copyOf(chips);
    none = List.copyOf(none);
  }

  /**
   * Returns the acts between {@code from} and {@code to} of {@code page}. The chip of the page's
   * act, if any, is on. The chips link to the page in its view. {@code counts} gives how many
   * differences each act made. If {@code counts} is empty, the acts are not counted, and their
   * counts load from {@code counting}.
   */
  public static ActsView of(
      Act act,
      Version from,
      Version to,
      Language language,
      ComparePage page,
      Map<ActIdentifier, Integer> counts,
      @Nullable String counting) {
    var only = page.act() == null ? null : new ActIdentifier(page.act());
    var acts = act.integratedBetween(from, to);
    var chips =
        acts.stream()
            // Show a corrigendum only if it corrected the text in this language. That is, it has
            // corrections counted here, or its filter is on.
            .filter(
                each ->
                    !each.corrigendum() || each.equals(only) || counts.getOrDefault(each, 0) > 0)
            .map(
                each ->
                    new Chip(
                        each.cited(),
                        title(act, each, language),
                        counts.get(each),
                        // If the chip is on, its link shows all acts again.
                        page.href(each.equals(only) ? null : each.celex(), page.view()),
                        each.equals(only),
                        each.corrigendum()))
            .toList();
    var none =
        chips.size() > CHIPS
            ? chips.stream()
                .filter(chip -> !chip.on() && Integer.valueOf(0).equals(chip.count()))
                .toList()
            : List.<Chip>of();
    var amending = acts.stream().filter(each -> !each.corrigendum()).count();
    return new ActsView(
        amending + (amending == 1 ? " act between them" : " acts between them"),
        chips.stream().filter(chip -> !none.contains(chip)).toList(),
        none,
        counts.isEmpty() ? counting : null,
        null,
        null);
  }

  /**
   * Returns the same acts, counted.
   *
   * @param together the number of differences that acts made together
   * @param unknown the number of differences that one of the acts made, where it is not known which
   *     act
   */
  public ActsView counted(int together, int unknown) {
    var hint =
        "Number = differences that act made in this comparison; click to see only its changes."
            + (together == 0
                ? ""
                : " "
                    + together
                    + (together == 1 ? " difference was" : " differences were")
                    + " made by acts together, so "
                    + (together == 1 ? "it counts" : "they count")
                    + " in each of their chips.");
    return new ActsView(head, chips, none, counting, unknown == 0 ? null : unknown, hint);
  }

  /**
   * Returns the text that stands for the chips left out. For example, "+ 3 with no differences
   * here".
   */
  public String more() {
    return "+ " + none.size() + " with no differences here";
  }

  /**
   * Returns the hover text of the chip of an act. The text says how the act was published, then
   * when it entered into force. For example: "2026/449: Commission Implementing Regulation (EU)
   * 2026/449 of 27 February 2026 amending … · OJ L, 2026/449, 2.3.2026 · entered into force on 22
   * March 2026".
   */
  private static String title(Act act, ActIdentifier each, Language language) {
    var name = act.citation(each, language);
    return act.datesOf(each).stream()
        .filter(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE && date.date() != null)
        .map(
            date ->
                name
                    + " · entered into force on "
                    + Dates.inWords(Objects.requireNonNull(date.date())))
        .findFirst()
        .orElse(name);
  }

  /**
   * The chip of an act.
   *
   * @param label the act, by its number. For example, 2026/449.
   * @param title the hover text: the act and when it entered into force
   * @param count how many differences the act made here. Null until counted.
   * @param href the comparison limited to the changes of the act. If the chip is on, the comparison
   *     of all acts.
   * @param on whether the comparison is limited to the changes of the act
   * @param corrigendum whether the act is a corrigendum. A corrigendum corrected the text, it did
   *     not amend it.
   */
  public record Chip(
      String label,
      String title,
      @Nullable Integer count,
      String href,
      boolean on,
      boolean corrigendum) {}
}
