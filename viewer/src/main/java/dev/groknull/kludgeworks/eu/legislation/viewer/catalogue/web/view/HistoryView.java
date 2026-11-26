// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.History;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheHistoryOfAnAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.LegalState;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The history of an act on a day, or between two days: its own state, its amending acts with the
 * state of each, and its corrigenda. The rules are the act's: {@link
 * TheHistoryOfAnAct#amendingActs}, {@link TheHistoryOfAnAct#amendingActsBetweenTwoDays}, {@link
 * TheStateOnADay#howAnAmendingActStands} and {@link TheHistoryOfAnAct#corrigendaOfAnAct}. This view
 * puts them into words.
 *
 * <p>With one day, the rows are all the amending acts. With two days, the rows are the amending
 * acts between the days, and the state of each is its state on the second day. The comparison of
 * the two days shows the changes of the same acts.
 *
 * @param title the title of the page
 * @param day the day, in words. With two days, it is the second day.
 * @param picker the day control. With two days, it goes to this page.
 * @param today the address of the history for today. Null if the page is for today only.
 * @param state the state of the act itself on the day, with the date and the clause. Null with two
 *     days.
 * @param read the address of the law on the day
 * @param between the two days in words, and how many amending acts changed the act between them.
 *     For example, "From 2 July 2020 to 15 March 2021." and "2 amending acts changed 2015/1998
 *     between these days." Null with one day.
 * @param compare the address of the comparison of the law on the two days. Null with one day, and
 *     where a day is before the first text of the act.
 * @param heading the heading of the rows
 * @param rows one row for each amending act that is shown, newest first
 * @param all how many amending acts the act has
 * @param showAll the address of the history with all the amending acts, on the second day. Null
 *     with one day.
 * @param corrigenda the corrigenda of the act that correct the text in the language
 */
public record HistoryView(
    String title,
    String day,
    DayView picker,
    @Nullable String today,
    @Nullable String state,
    String read,
    @Nullable Between between,
    @Nullable String compare,
    String heading,
    List<Row> rows,
    int all,
    @Nullable String showAll,
    List<Corrigendum> corrigenda) {

  public HistoryView {
    rows = List.copyOf(rows);
    corrigenda = List.copyOf(corrigenda);
  }

  /**
   * The two days of a history, in words.
   *
   * @param days the days: "From 2 July 2020 to 15 March 2021."
   * @param said how many amending acts changed the act between them, or that the act has no text on
   *     the first day
   */
  public record Between(String days, String said) {}

  /**
   * Returns {@code history} as the page shows it. With two days, the rows are the amending acts
   * between them.
   */
  public static HistoryView of(History history) {
    var today = history.today();
    var act = history.act();
    var day = history.day();
    var first = history.first();
    var language = history.language();
    var between = history.between();
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var past = day.isBefore(today);
    var kept = day.equals(today) ? null : day;
    var cited = act.cited(act.id());
    var state = history.state();
    return new HistoryView(
        cited
            + (first == null
                ? " · history on " + Dates.inShort(day)
                : " · " + Dates.inShort(first) + " → " + Dates.inShort(day)),
        Dates.inWords(day),
        DayView.of(
            history.overTime(),
            day,
            first,
            Links.history(eli, null, language),
            Links.history(eli, null, language),
            null),
        kept == null && first == null ? null : Links.history(eli, null, language),
        state == null ? null : own(act, state, day, past),
        Links.read(eli, day, language),
        first == null || between == null
            ? null
            : new Between(
                "From " + Dates.inWords(first) + " to " + Dates.inWords(day) + ".",
                between.textOnTheFirstDay()
                    ? said(between.acts().size(), cited)
                    : cited + " has no text on " + Dates.inWords(first) + "."),
        first == null || between == null || !between.textOnTheFirstDay()
            ? null
            : Links.compare(eli, first, day, language),
        first == null
            ? "Amending acts, newest first"
            : "Amending acts between these days, newest first",
        history.shown().stream().map(each -> row(act, eli, each, kept, past, language)).toList(),
        history.amendingActs().size(),
        first == null ? null : Links.history(eli, kept, language),
        history.corrigenda().stream().map(each -> Corrigendum.of(act, each, language)).toList());
  }

  private static String said(int acts, String cited) {
    return switch (acts) {
      case 0 -> "No amending act changed " + cited + " between these days.";
      case 1 -> "1 amending act changed " + cited + " between these days.";
      default -> acts + " amending acts changed " + cited + " between these days.";
    };
  }

  private static Row row(
      Act act,
      Eli eli,
      History.AmendingAct amending,
      @Nullable LocalDate kept,
      boolean past,
      Language language) {
    var each = amending.act();
    var stands = amending.stands();
    var counts =
        StateWording.counts(stands.applies(), stands.notApplying(), stands.notKnown(), past);
    var changes = amending.changes().size();
    var subject = Subject.of(act, each, language);
    return new Row(
        act.cited(each),
        subject.text(),
        subject.language(),
        act.eurLex(each, language).toString(),
        journal(act, each),
        StateWording.label(stands.state(), amending.applying() != null, past),
        StateWording.tone(stands.state()),
        Wording.dates(each, amending.applying()),
        counts.isEmpty() ? null : counts,
        changes == 0 ? "Its changes" : changes == 1 ? "1 provision" : changes + " provisions",
        Links.amending(eli, each, kept, language));
  }

  /**
   * Returns the Official Journal reference of {@code each}, or "EUR-Lex" if Cellar does not say
   * where the Journal published it.
   */
  static String journal(Act act, ActIdentifier each) {
    return act.publication(each)
        .map(Publication::journal)
        .map(Publication.Journal::reference)
        .orElse("EUR-Lex");
  }

  /**
   * Returns the state of {@code act} itself on {@code day}, as one or two sentences. For example,
   * "2018/1862 applied in part. It applies in full from 7 March 2023 · Article 79 of 2018/1862."
   * The words come from {@code state} and the facts that it has.
   */
  static String own(Act act, LegalState state, LocalDate day, boolean past) {
    var id = act.id();
    var cited = act.cited(id);
    return switch (state) {
      case LegalState.NoLongerInForce ended ->
          cited
              + (past ? " was no longer in force." : " is no longer in force.")
              + " Its end of validity is "
              + Dates.inWords(ended.endOfValidity())
              + "."
              + (ended.repealedBy().isEmpty()
                  ? ""
                  : " "
                      + ended.repealedBy().stream()
                          .map(act::cited)
                          .collect(Collectors.joining(" and "))
                      + " repealed it.");
      case LegalState.NotInForce before ->
          cited
              + (past ? " was not in force yet." : " is not in force yet.")
              + " Its entry into force is "
              + Dates.inWords(before.entryIntoForce())
              + ".";
      case LegalState.Applies applies ->
          cited + (past ? " applied." : " applies.") + from(act, applies.applying());
      case LegalState.NotApplying not ->
          cited
              + (past
                  ? " was in force and did not apply yet."
                  : " is in force and does not apply yet.")
              + from(act, not.applying());
      case LegalState.NotKnown _ -> cited + notKnown(act, day, past);
    };
  }

  /** Returns the sentence that says from when {@code act} applies, or nothing. */
  private static String from(Act act, Applying applying) {
    var id = act.id();
    return switch (applying) {
      case Applying.From one ->
          " It applies from "
              + Dates.inWords(one.day())
              + Wording.clauseOn(act, id, one.day())
              + ".";
      case Applying.FromMoreThanOneDay parts ->
          " Its first part applies from "
              + Dates.inWords(parts.first())
              + Wording.clauseOn(act, id, parts.first())
              + ".";
      case Applying.SetBy _, Applying.InForce _ -> "";
    };
  }

  /**
   * Returns the rest of the sentence for an act whose state on {@code day} Cellar does not tell as
   * a whole. An act that applies in part says so, and from when it applies in full.
   */
  private static String notKnown(Act act, LocalDate day, boolean past) {
    var id = act.id();
    if (!act.dates().appliesInPartOn(id, day)) {
      return (past ? " was in force." : " is in force.")
          + " The data does not give the day from which it applies.";
    }
    var inPart = past ? " applied in part." : " applies in part.";
    var full = act.dates().inFullAfter(day);
    if (full.isPresent()) {
      return inPart
          + " It applies in full from "
          + Dates.inWords(full.get())
          + Wording.clauseOn(act, id, full.get())
          + ".";
    }
    var unknown = act.dates().unknownDays();
    return unknown.isEmpty()
        ? inPart
        : inPart
            + " Other parts apply from dates set in "
            + unknown.stream().map(ActDate.Provision::cited).collect(Collectors.joining(", "))
            + ".";
  }

  /**
   * A row of the history: one amending act.
   *
   * @param cited how the act is cited, for example "2022/1190"
   * @param subject the title of the act after its date, or null if Cellar has no title
   * @param subjectLanguage the ISO code of the language of the title
   * @param journalHref the address of the act on EUR-Lex
   * @param journal its Official Journal reference
   * @param state how it stands on the day, for example "In force, did not apply yet"
   * @param tone "warn" where a change does not apply on the day, "open" where Cellar does not tell,
   *     "off" where the act is not in force yet. Null where the act applies.
   * @param dates its dates, with the clause that sets them
   * @param counts how many of its changes are in each state, where they differ. If not, null.
   * @param changes the label of the link to its changes, for example "17 provisions"
   * @param href the address of the page of this act
   */
  public record Row(
      String cited,
      @Nullable String subject,
      @Nullable String subjectLanguage,
      String journalHref,
      String journal,
      String state,
      @Nullable String tone,
      String dates,
      @Nullable String counts,
      String changes,
      String href) {}

  /**
   * A corrigendum in the list of corrigenda.
   *
   * @param cited how it is cited, for example "Corrigendum 1 to 2018/1862"
   * @param published when the Official Journal published it, for example "Published on 23 May
   *     2018". Null if Cellar does not say.
   * @param journalHref its address on EUR-Lex
   * @param journal its Official Journal reference
   * @param places the provisions that it corrects, or null if Cellar lists none
   */
  public record Corrigendum(
      String cited,
      @Nullable String published,
      String journalHref,
      String journal,
      @Nullable String places) {

    /** Returns {@code corrigendum} of {@code act}, with the places that it corrects. */
    static Corrigendum of(Act act, History.Corrigendum corrigendum, Language language) {
      var each = corrigendum.act();
      var places =
          corrigendum.changes().stream()
              .map(change -> change.place().words())
              .filter(name -> !name.isEmpty())
              .distinct()
              .collect(Collectors.joining(", "));
      return new Corrigendum(
          each.cited(),
          act.publication(each)
              .map(Publication::journal)
              .map(journal -> "Published on " + Dates.inWords(journal.published()))
              .orElse(null),
          act.eurLex(each, language).toString(),
          HistoryView.journal(act, each),
          places.isEmpty() ? null : places);
    }
  }

  /**
   * The title of an act after its date, as a row shows it under the number.
   *
   * @param text the title, or null if Cellar has none
   * @param language the ISO code of its language, or null without a title
   */
  record Subject(@Nullable String text, @Nullable String language) {

    /**
     * Returns the subject of {@code each}, in {@code language} if Cellar has its title in it, else
     * in English. An English title is cut after its date. For example, "amending Regulation (EU)
     * 2018/1862 as regards the entry of information alerts".
     */
    static Subject of(Act act, ActIdentifier each, Language language) {
      var publication = act.publication(each);
      var titled =
          publication.flatMap(p -> p.title(language)).isPresent() ? language : Language.ENG;
      var title = publication.flatMap(p -> p.title(titled));
      if (title.isEmpty()) {
        return new Subject(null, null);
      }
      var dated = Wording.DATED.matcher(title.get());
      return new Subject(
          titled == Language.ENG && dated.matches() ? dated.group(2) : title.get(), titled.iso());
    }
  }
}
