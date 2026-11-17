// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The history of an act on a day: its own state, its amending acts with the state of each, and its
 * corrigenda. The rules are the act's: {@link Act#amendingActs}, {@link Act#standingOn} and {@link
 * Act#corrigendaOf}. This view puts them into words.
 *
 * @param title the title of the page
 * @param day the day, in words
 * @param on the day, as the date box has it
 * @param action the address that the date form and the period form send to
 * @param today the address of the history for today. Null if the day is today.
 * @param state the state of the act itself on the day, with the date and the clause
 * @param read the address of the law on the day
 * @param rows one row for each amending act that is shown, newest first
 * @param all how many amending acts the act has
 * @param from the first day of the period that limits the rows, as the date box has it, or null
 * @param to the last day of the period, or null
 * @param showAll the address of the history without the period. Null if no period is set.
 * @param compare the address of the comparison of the law on the two days of the period. Null if
 *     the period does not have two days.
 * @param corrigenda the corrigenda of the act that correct the text in the language
 */
public record HistoryView(
    String title,
    String day,
    String on,
    String action,
    @Nullable String today,
    String state,
    String read,
    List<Row> rows,
    int all,
    @Nullable String from,
    @Nullable String to,
    @Nullable String showAll,
    @Nullable String compare,
    List<Corrigendum> corrigenda) {

  public HistoryView {
    rows = List.copyOf(rows);
    corrigenda = List.copyOf(corrigenda);
  }

  /**
   * Returns the history of {@code act} on {@code day}, in {@code language}. If {@code from} or
   * {@code to} is given, the rows are the amending acts of that period, as {@link
   * Act#amendingActs(Language, LocalDate, LocalDate)} gives them.
   */
  public static HistoryView of(
      Act act,
      LocalDate day,
      LocalDate today,
      Language language,
      @Nullable LocalDate from,
      @Nullable LocalDate to) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var past = day.isBefore(today);
    var kept = day.equals(today) ? null : day;
    var amending = act.amendingActs(language);
    var limited = from != null || to != null;
    var rows =
        (limited ? act.amendingActs(language, from, to) : amending)
            .stream().map(each -> row(act, eli, each, day, kept, past, language)).toList();
    return new HistoryView(
        act.cited(act.id()) + " · history on " + Dates.inShort(day),
        Dates.inWords(day),
        day.toString(),
        Links.history(eli, null, language),
        kept == null ? null : Links.history(eli, null, language),
        own(act, day, past),
        Links.read(eli, day, language),
        rows,
        amending.size(),
        from == null ? null : from.toString(),
        to == null ? null : to.toString(),
        limited ? Links.history(eli, kept, language) : null,
        from != null && to != null ? Links.compare(eli, from, to, language) : null,
        act.corrigendaOf(act.id(), language).stream()
            .map(each -> Corrigendum.of(act, each, language))
            .toList());
  }

  private static Row row(
      Act act,
      Eli eli,
      ActIdentifier each,
      LocalDate day,
      @Nullable LocalDate kept,
      boolean past,
      Language language) {
    var standing = act.standingOn(each, language, day);
    var whole = standing.whole();
    var counts = States.counts(standing, past);
    var changes = act.changesBy(each, language).size();
    var subject = Subject.of(act, each, language);
    return new Row(
        act.cited(each),
        subject.text(),
        subject.language(),
        act.eurLex(each, language).toString(),
        journal(act, each),
        States.label(whole, !act.datesOf(each).isEmpty(), past),
        States.tone(whole),
        States.dates(each, act.applying(each, Set.of()).orElse(null)),
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
   */
  static String own(Act act, LocalDate day, boolean past) {
    var id = act.id();
    var cited = act.cited(id);
    var applying = act.applying(id, Set.of());
    var from =
        applying
            .map(
                each ->
                    switch (each) {
                      case Applying.From one ->
                          " It applies from "
                              + Dates.inWords(one.day())
                              + States.clauseOn(act, id, one.day())
                              + ".";
                      case Applying.InStages stages ->
                          " Its first part applies from "
                              + Dates.inWords(stages.first())
                              + States.clauseOn(act, id, stages.first())
                              + ".";
                      case Applying.SetBy _, Applying.InForce _ -> "";
                    })
            .orElse("");
    return switch (act.stateOn(id, Set.of(), day)) {
      case NOT_IN_FORCE ->
          cited
              + (past ? " was not in force yet." : " is not in force yet.")
              + act.entryIntoForce(id)
                  .map(first -> " Its entry into force is " + Dates.inWords(first) + ".")
                  .orElse("");
      case APPLIES -> cited + (past ? " applied." : " applies.") + from;
      case NOT_APPLYING ->
          cited
              + (past
                  ? " was in force and did not apply yet."
                  : " is in force and does not apply yet.")
              + from;
      case NOT_KNOWN -> cited + notKnown(act, day, past);
    };
  }

  /**
   * Returns the rest of the sentence for an act whose state on {@code day} Cellar does not tell as
   * a whole. An act that applies in part says so, and from when it applies in full.
   */
  private static String notKnown(Act act, LocalDate day, boolean past) {
    var id = act.id();
    if (!act.appliesInPartOn(id, day)) {
      return (past ? " was in force." : " is in force.")
          + " The data does not give the day it applies.";
    }
    var inPart = past ? " applied in part." : " applies in part.";
    var full = act.inFullAfter(day);
    if (full.isPresent()) {
      return inPart
          + " It applies in full from "
          + Dates.inWords(full.get())
          + States.clauseOn(act, id, full.get())
          + ".";
    }
    var unknown = act.unknownDays();
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

    static Corrigendum of(Act act, ActIdentifier each, Language language) {
      var places =
          act.changesBy(each, language).stream()
              .map(change -> change.location().name())
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
