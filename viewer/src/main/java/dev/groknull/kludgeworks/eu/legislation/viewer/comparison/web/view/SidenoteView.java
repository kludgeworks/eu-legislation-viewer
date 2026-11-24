// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Applicability;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributed;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.LastChange;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The sidenote of a difference. It shows beside the difference and says who made the difference.
 *
 * <p>The rules are the domain's: {@link Attributed#of} finds the acts that made the difference, in
 * their steps, and from when each act's change applies ({@link Applicability}). This class puts
 * them into words. For example, "Inserted by 2019/103, then replaced by 2021/255", with a date line
 * for each act. Where no act is named, the sidenote does not guess: if the acts between the two
 * versions are all of one version, it says that one of them made the difference, but not which. If
 * not, it says that no act is named.
 *
 * @param made who made the difference. For example:
 *     <ul>
 *       <li>"Replaced by 2026/449"
 *       <li>"Inserted by 2019/103, then replaced by 2021/255"
 *       <li>"Replaced by 2019/103 and 2019/1583 together"
 *       <li>"The data does not say which act amended this. It was 2019/103 or 2019/1583."
 *       <li>"The data does not say which act deleted this.", where nothing names an act between the
 *           two versions
 *     </ul>
 *
 * @param attribution who made the difference: the acts that made it, or the acts of which one made
 *     it
 * @param dates when each of these acts applies, with what sets the date
 * @param published how the acts that made the difference, or may have made it, were published, one
 *     line for each act. The hover of {@code made} lists them the same way. Null where no act is
 *     named.
 * @param prospective what the sidenote says where a change of the difference does not apply on the
 *     later day of the comparison yet, a prospective amendment. For example, "2022/1190 did not
 *     apply yet on 1 August 2022". The difference then has an amber bar. Null where each change
 *     applies.
 */
public record SidenoteView(
    @Nullable String made,
    Attribution attribution,
    List<Applies> dates,
    @Nullable String published,
    @Nullable String prospective) {

  public SidenoteView {
    dates = List.copyOf(dates);
  }

  /**
   * Returns the sidenote of {@code attributed}, a difference with who made it ({@link
   * Attributed#of}).
   *
   * @param only the act whose changes the comparison is limited to, if any
   * @param today the day of the page. It gives the tense of a change that does not apply yet.
   * @param language the language of the links
   */
  public static SidenoteView of(
      Act act,
      Attributed attributed,
      @Nullable ActIdentifier only,
      LocalDate today,
      Language language) {
    var sidenote = said(act, attributed, only, language);
    var notApplying = attributed.notApplying();
    return notApplying == null ? sidenote : sidenote.notApplying(act, notApplying, today);
  }

  /**
   * Returns this sidenote with the words for a change that does not apply on the later day of the
   * comparison yet. {@code today} gives the tense.
   */
  private SidenoteView notApplying(Act act, Attributed.NotApplying notApplying, LocalDate today) {
    var day = notApplying.day();
    return new SidenoteView(
        made,
        attribution,
        dates,
        published,
        act.cited(notApplying.act())
            + (day.isBefore(today) ? " did not apply yet on " : " does not apply yet on ")
            + Dates.inWords(day));
  }

  private static SidenoteView said(
      Act act, Attributed attributed, @Nullable ActIdentifier only, Language language) {
    var appeared = attributed.appeared();
    if (appeared != null) {
      return appeared(act, appeared, attributed.difference());
    }
    var attribution = attributed.attribution();
    var dates =
        attributed.applies().stream()
            .map(applicability -> applies(act, applicability, language))
            .toList();
    if (attribution.steps().isEmpty()) {
      return unknown(act, attribution, dates, attributed.difference(), language);
    }
    var chain = new ArrayList<String>();
    var steps = attribution.steps();
    for (var i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      var verb = i == 0 ? verb(step.modification()) : step.modification().word();
      var acts = step.acts();
      if (acts.size() == 1) {
        chain.add(verb + " by " + names(act, acts));
      } else if (only != null && acts.contains(only)) {
        var others = acts.stream().filter(each -> !each.equals(only)).toList();
        chain.add(verb + " by " + act.cited(only) + ", together with " + names(act, others));
      } else {
        chain.add(verb + " by " + names(act, acts) + " together");
      }
    }
    return new SidenoteView(
        String.join(", then ", chain),
        attribution,
        dates,
        act.citations(attribution.acts(), language),
        null);
  }

  /**
   * Returns the sidenote of a difference for which no act is named. Where {@code attribution} has
   * the acts of which one made it ({@link Attribution#unnamed}), it names them, but not which one
   * made it. If not, it does not say who made it.
   */
  private static SidenoteView unknown(
      Act act,
      Attribution attribution,
      List<Applies> dates,
      Difference difference,
      Language language) {
    var candidates = attribution.candidates();
    if (candidates.isEmpty()) {
      return new SidenoteView(unsaid(difference), Attribution.NONE, List.of(), null, null);
    }
    var names = candidates.stream().map(act::cited).toList();
    return new SidenoteView(
        unsaid(difference)
            + " It was "
            + String.join(", ", names.subList(0, names.size() - 1))
            + " or "
            + names.getLast()
            + ".",
        attribution,
        dates,
        act.citations(candidates, language),
        null);
  }

  /**
   * Returns the sidenote of {@code difference}, which no tag and no amendment in Cellar attributes,
   * where {@code appeared} is the text where the change first appears. It names that text and what
   * it consolidates, and no act as the one that made the change. The page says "text", not
   * "version". For example, "Deleted in the text of 22 March 2026, which consolidates 2026/449 and
   * Corrigendum 1 to 2026/449". If the text consolidates no act, it names the text only.
   */
  private static SidenoteView appeared(
      Act act, Attributed.Appeared appeared, Difference difference) {
    var did = verb(Modification.of(difference.kind()));
    var consolidates = appeared.consolidates();
    return new SidenoteView(
        did
            + " in the text of "
            + Dates.inWords(appeared.version().date())
            + (consolidates.isEmpty() ? "" : ", which consolidates " + names(act, consolidates)),
        Attribution.NONE,
        List.of(),
        null,
        null);
  }

  /**
   * Returns the words for a difference for which the data names no act: "The data does not say
   * which act deleted this."
   */
  private static String unsaid(Difference difference) {
    return "The data does not say which act "
        + Modification.of(difference.kind()).word()
        + " this.";
  }

  /**
   * Returns the numbers of the acts as a list in words. For example, "2019/103 and 2019/1583", or
   * "2017/815, 2019/103 and 2019/1583".
   */
  private static String names(Act act, Collection<ActIdentifier> acts) {
    var names = acts.stream().map(act::cited).toList();
    return names.size() == 1
        ? names.getFirst()
        : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
  }

  /**
   * Returns {@code modification} as the first word of a sidenote. For example, "Replaced" or
   * "Corrected".
   */
  static String verb(Modification modification) {
    var word = modification.word();
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }

  /**
   * Returns {@code applicability} as the date line of the sidenote says it. For example, "2017/815
   * · OJ L 122, 13.5.2017 · applies from 1 June 2017 · Article 2 of 2017/815", or "Corrigendum 4 to
   * 2017/815 corrects 2017/815 from 1 June 2017 · first in the text of 1 January 2026".
   */
  private static Applies applies(Act act, Applicability applicability, Language language) {
    return switch (applicability) {
      case Applicability.Corrects corrects ->
          new Applies(
              corrects.act().cited()
                  + " corrects "
                  + act.cited(corrects.corrected())
                  + (corrects.from() == null ? "" : " from " + Dates.inWords(corrects.from()))
                  + (corrects.firstIn() == null
                      ? ""
                      : " · first in the text of " + Dates.inWords(corrects.firstIn())),
              "",
              null,
              List.of(),
              List.of(),
              "");
      case Applicability.Applies applies -> {
        var name = named(act, applies.act());
        yield switch (applies.applying()) {
          case Applying.SetBy _ ->
              applies(act, name + " applies from a date set in", applies, language, "");
          case Applying.From from ->
              applies(
                  act,
                  name
                      + " applies from "
                      + Dates.inWords(from.day())
                      + (from.entryIntoForce() ? ", its entry into force" : ""),
                  applies,
                  language,
                  "");
          case Applying.InForce inForce ->
              applies(
                  act,
                  name + " in force from " + Dates.inWords(inForce.day()),
                  applies,
                  language,
                  "");
          case Applying.FromMoreThanOneDay parts ->
              applies(
                  act,
                  name
                      + " applies from more than one day: "
                      + parts.starts().stream()
                          .map(SidenoteView::part)
                          .collect(Collectors.joining("; ")),
                  applies,
                  language,
                  " says which part from when");
        };
      }
    };
  }

  /** Returns a part in words. For example, "a part from 1 October 2026". */
  private static String part(Applying.Start part) {
    var from = "from " + Dates.inWords(part.day());
    return part.entryIntoForce()
        ? from + ", its entry into force"
        : part.forPart() ? "a part " + from : from;
  }

  private static Applies applies(
      Act act, String when, Applicability.Applies applies, Language language, String after) {
    var amending = applies.act();
    var clause =
        applies.clause().isEmpty()
            ? null
            : new Link(
                applies.clause().stream()
                        .map(ActDate.Provision::cited)
                        .collect(Collectors.joining(", "))
                    + " of "
                    + act.cited(amending),
                act.eurLex(amending, language).toString(),
                null);
    return new Applies(
        when,
        when.endsWith(" in") ? " " : " · ",
        clause,
        links(act, applies.amendedBy(), language),
        links(act, applies.setBy(), language),
        clause == null ? "" : after);
  }

  /**
   * Returns {@code acts} as links to EUR-Lex, each named as the pages name it, with its citation
   * for the hover. For example, "2023/201", and "2023/201: Commission Implementing Decision (EU)
   * 2023/201 of 30 January 2023 … · OJ L 27, 31.1.2023".
   */
  private static List<Link> links(Act act, List<ActIdentifier> acts, Language language) {
    return acts.stream()
        .map(each ->
            new Link(
                act.cited(each),
                act.eurLex(each, language).toString(),
                act.citation(each, language)))
        .toList();
  }

  /**
   * Returns how a date line names {@code amending}: its number, then its OJ reference where the
   * catalogue has it. For example, "2026/449 · OJ L, 2026/449, 2.3.2026 ·".
   */
  private static String named(Act act, ActIdentifier amending) {
    return act.cited(amending)
        + act.publication(amending)
            .map(Publication::journal)
            .map(journal -> " · " + journal.reference() + " ·")
            .orElse("");
  }

  /**
   * When an act applies, as the sidenote says it. For example, "applies from 31 December 2021 ·
   * Article 2 of 2019/103, as amended by 2020/910", or "applies from 7 March 2023 · Article 79.5 of
   * 2018/1862, on the day set by 2023/201".
   *
   * @param when when the act applies. For example, "applies from 1 October 2026", or "2026/449
   *     applies from more than one day: from 22 March 2026, its entry into force; a part from 1
   *     October 2026".
   * @param between what goes between {@code when} and the clause. This is a dot, or a space after
   *     "set in".
   * @param clause the clause that sets the date, linked to EUR-Lex. For example, "Article 2 of
   *     2026/449". Null where Cellar does not say.
   * @param amendedBy the later acts that amended the clause, linked to EUR-Lex. For example,
   *     2020/910.
   * @param setBy the later acts that set the day that the clause left to them, linked to EUR-Lex.
   *     For example, 2023/201.
   * @param after what follows the clause. For parts, this is "says which part from when".
   */
  public record Applies(
      String when,
      String between,
      @Nullable Link clause,
      List<Link> amendedBy,
      List<Link> setBy,
      String after) {
    public Applies {
      amendedBy = List.copyOf(amendedBy);
      setBy = List.copyOf(setBy);
    }
  }

  /**
   * A link, with its words, its address, and the words of its hover.
   *
   * @param title the words of the hover, for example how the act was published. Null for none.
   */
  public record Link(String label, String href, @Nullable String title) {}

  /**
   * Returns the history of a provision that is the same in both texts, as the Synopsis shows it
   * beside the provision: what last made it as it is, {@code last} ({@link LastChange#of}), in
   * words.
   */
  public static History history(Act act, LastChange last, Language language) {
    return switch (last) {
      case LastChange.Original(var applicability) ->
          new History(
              "As in the original act",
              applicability == null
                  ? null
                  : unnamed(applies(act, applicability, language), act, act.id()));
      case LastChange.ByAct(var each, var modification, var applicability) ->
          new History(
              "Last changed by " + act.cited(each) + " (" + modification.word() + ")",
              applicability == null
                  ? null
                  : unnamed(applies(act, applicability, language), act, each));
    };
  }

  /**
   * Returns {@code applies} without the act at the start of its date line, because the line before
   * names the act already. For example, "Last changed by 2019/103 (replaced) · applies from 1
   * February 2019".
   */
  private static Applies unnamed(Applies applies, Act act, ActIdentifier named) {
    var name = named(act, named) + " ";
    return applies.when().startsWith(name)
        ? new Applies(
            applies.when().substring(name.length()),
            applies.between(),
            applies.clause(),
            applies.amendedBy(),
            applies.setBy(),
            applies.after())
        : applies;
  }

  /**
   * The history of a provision, as the Synopsis shows it beside a provision that is the same in
   * both versions.
   *
   * @param said what last made the provision as it is. For example, "Last changed by 2019/103
   *     (replaced)" or "As in the original act".
   * @param applies when that change applies, with what sets the date. Null where nothing says.
   */
  public record History(String said, @Nullable Applies applies) {}
}
