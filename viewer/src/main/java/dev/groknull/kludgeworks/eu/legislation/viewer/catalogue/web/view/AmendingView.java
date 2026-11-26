// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.AmendingHistory;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The page of one amending act: what it changed in an act, and the state of each change on a day.
 * {@link AmendingHistory} has what the rules give. This view applies no rule: it puts the history
 * into words.
 *
 * @param title the title of the page
 * @param heading the heading, for example "What 2022/1190 changed in 2018/1862"
 * @param subject the title of the amending act after its date, or null if Cellar has no title
 * @param subjectLanguage the ISO code of the language of the title
 * @param journalHref the address of the amending act on EUR-Lex
 * @param journal its Official Journal reference
 * @param day the day, in words
 * @param picker the day control. The page takes one day only.
 * @param history the address of the history of the act, on the day
 * @param base how the amended act is cited
 * @param state how the amending act stands on the day, as sentences
 * @param dates its dates, with the clause that sets them
 * @param clause a link to the article that sets the day from which the act applies, where Cellar
 *     does not know the day. Null in each other case.
 * @param groups its changes in groups, by the day from which they apply. Each is a link that limits
 *     the page. The first is all of its changes. Empty if the changes are in one group only: a
 *     limit would change nothing.
 * @param sequences its changes, in the order of the text, limited to the group that is on. Changes
 *     that follow each other and have the same state are one sequence, so the page says the state
 *     one time.
 * @param ordered whether the changes are in the order of the text. They are in the order of the
 *     data while no text of the act is built.
 * @param open whether the wording of each change shows when the page opens. It does if the page has
 *     few changes. If not, the reader opens the wording of a change.
 * @param either the address of the changes that the amending act or another act of its version
 *     made, where the data does not say which. Null if the texts are not built, or if the act is
 *     the only act of its version.
 * @param corrigenda the corrigenda of the amending act that correct the text in the language
 */
public record AmendingView(
    String title,
    String heading,
    @Nullable String subject,
    @Nullable String subjectLanguage,
    String journalHref,
    String journal,
    String day,
    DayView picker,
    String history,
    String base,
    String state,
    String dates,
    @Nullable Clause clause,
    List<Group> groups,
    List<Sequence> sequences,
    boolean ordered,
    boolean open,
    @Nullable String either,
    List<HistoryView.Corrigendum> corrigenda) {

  /** The page shows the wording of each change when it opens, up to this many changes. */
  private static final int OPEN = 25;

  /** The value of the parameter for the changes whose day of application the data does not give. */
  public static final String NOT_KNOWN = "unknown";

  public AmendingView {
    groups = List.copyOf(groups);
    sequences = List.copyOf(sequences);
    corrigenda = List.copyOf(corrigenda);
  }

  /**
   * Returns the page of the amending act of {@code history}. If {@code group} is given, the page
   * shows only the changes that apply from that day: a day, or {@link #NOT_KNOWN}.
   */
  public static AmendingView of(AmendingHistory history, @Nullable String group) {
    var act = history.act();
    var amending = history.amending();
    var language = history.language();
    var day = history.day();
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var past = day.isBefore(history.today());
    var kept = day.equals(history.today()) ? null : day;
    var page = Links.amending(eli, amending, kept, language);
    var subject = HistoryView.Subject.of(act, amending, language);
    var cited = act.cited(amending);
    var texts = history.texts();
    var compare =
        texts == null
            ? null
            : new ComparePage(
                Links.compare(eli, language),
                texts.earlier().date(),
                texts.later().date(),
                amending.celex(),
                null);
    var all = history.changes();
    var shown = all.stream().filter(change -> group == null || group.equals(key(change))).toList();
    return new AmendingView(
        cited + " in " + act.cited(act.id()) + " · on " + Dates.inShort(day),
        "What " + cited + " changed in " + act.cited(act.id()),
        subject.text(),
        subject.language(),
        act.eurLex(amending, language).toString(),
        HistoryView.journal(act, amending),
        Dates.inWords(day),
        DayView.of(
            history.overTime(),
            day,
            null,
            Links.amending(eli, amending, null, language),
            null,
            null),
        Links.history(eli, kept, language),
        act.cited(act.id()),
        cited
            + " "
            + StateWording.phrase(history.stands().state(), past)
            + "."
            + (all.isEmpty()
                ? " The data lists no changed provisions."
                : " It changed "
                    + (all.size() == 1 ? "1 provision." : all.size() + " provisions.")),
        Wording.dates(amending, history.applying()),
        clause(history),
        groups(history, past, page, group),
        sequences(shown.stream().map(change -> change(history, change, past, compare)).toList()),
        history.latest() != null,
        shown.size() <= OPEN,
        compare == null || history.otherActs().isEmpty() ? null : compare.either(),
        history.corrigenda().stream()
            .map(each -> HistoryView.Corrigendum.of(act, each, language))
            .toList());
  }

  /**
   * Returns a link to the article of {@code amending} that sets the day from which it applies.
   * Returns null if Cellar knows the day, or if it names no article. The address is the text of the
   * act on EUR-Lex, at the anchor of the article: "art_" and its number.
   */
  private static @Nullable Clause clause(AmendingHistory history) {
    var act = history.act();
    var amending = history.amending();
    var language = history.language();
    return Optional.ofNullable(history.applying())
        .flatMap(applying ->
            switch (applying) {
              case Applying.SetBy set ->
                  set.dates().stream()
                      .flatMap(date -> date.clause().provisions().stream())
                      .filter(each -> each.type() == ActDate.Provision.Type.ARTICLE)
                      .filter(each -> !article(each).isEmpty())
                      .findFirst()
                      .map(each ->
                          new Clause(
                              "Read " + each.cited(),
                              act.eurLex(amending, language) + "#art_" + article(each)));
              case Applying.From _, Applying.FromMoreThanOneDay _, Applying.InForce _ ->
                  Optional.<Clause>empty();
            })
        .orElse(null);
  }

  /** Returns the number of the article of {@code provision}: 2 for Article 2.2(f). */
  private static String article(ActDate.Provision provision) {
    var number = provision.number() == null ? "" : provision.number();
    var end = 0;
    while (end < number.length() && Character.isDigit(number.charAt(end))) {
      end++;
    }
    return number.substring(0, end);
  }

  /** Returns the label of the group of the changes that apply from {@code from}. */
  private static String label(State state, LocalDate from, boolean past) {
    var day = Dates.inWords(from);
    return switch (state) {
      case APPLIES -> (past ? "Applied from " : "Applies from ") + day;
      case NOT_APPLYING -> (past ? "Did not apply yet, from " : "Does not apply yet, from ") + day;
      case NOT_IN_FORCE, NO_LONGER_IN_FORCE, IN_PART, NOT_KNOWN -> "From " + day;
    };
  }

  /**
   * Returns {@code changes} as sequences: changes that follow each other and have the same
   * statement are one sequence. The order of the changes stays.
   */
  private static List<Sequence> sequences(List<Change> changes) {
    var sequences = new ArrayList<Sequence>();
    for (var change : changes) {
      var last = sequences.isEmpty() ? null : sequences.getLast();
      if (last != null
          && Objects.equals(last.statement(), change.statement())
          && Objects.equals(last.tone(), change.tone())) {
        var joined = new ArrayList<>(last.changes());
        joined.add(change);
        sequences.set(sequences.size() - 1, new Sequence(last.statement(), last.tone(), joined));
      } else {
        sequences.add(new Sequence(change.statement(), change.tone(), List.of(change)));
      }
    }
    return sequences;
  }

  /** Returns the group of {@code change}: the day from which it applies, or {@link #NOT_KNOWN}. */
  private static String key(AmendingHistory.Change change) {
    return Optional.ofNullable(change.appliesFrom()).map(LocalDate::toString).orElse(NOT_KNOWN);
  }

  private static List<Group> groups(
      AmendingHistory history, boolean past, String page, @Nullable String on) {
    if (history.parts().size() < 2) {
      return List.of();
    }
    var separator = page.contains("?") ? "&" : "?";
    var groups = new ArrayList<Group>();
    groups.add(new Group("All changes", history.changes().size(), page, on == null, null));
    for (var part : history.parts()) {
      var from = part.from();
      var state = part.state();
      var key = from == null ? NOT_KNOWN : from.toString();
      groups.add(
          new Group(
              from == null || state == null ? "Day not known" : label(state.state(), from, past),
              part.changes(),
              page + separator + "applies=" + key,
              key.equals(on),
              state == null ? StateWording.UNKNOWN : StateWording.tone(state.state())));
    }
    return groups;
  }

  private static Change change(
      AmendingHistory history,
      AmendingHistory.Change of,
      boolean past,
      @Nullable ComparePage compare) {
    var change = of.change();
    var name = change.place().words();
    return new Change(
        name.isEmpty() ? change.location().code() : name,
        made(change.role()),
        StateWording.statement(
            history.act(),
            history.amending(),
            of.appliesFrom(),
            of.state().state(),
            history.applying(),
            past),
        StateWording.tone(of.state().state()),
        anchor(change),
        compare == null || of.occurrences().isEmpty() ? null : compare.place(of.occurrences()));
  }

  /** Returns what an act did to a provision, as one word. For example, "Replaced". */
  static String made(Amendment.Role role) {
    return switch (role) {
      case REPLACEMENT -> "Replaced";
      case ADDITION -> "Inserted";
      case DELETION -> "Deleted";
      case REPEAL -> "Repealed";
      case AMENDMENT -> "Amended";
      case EXTENSION, SUSPENSION, DEFERRAL, OTHER -> "Changed";
    };
  }

  /**
   * Returns the anchor of {@code change} on the page of its act. It is the place in Cellar's codes,
   * with a line for each space. For example, "at-AR_6_PA_2". The history of a provision links to
   * it.
   */
  static String anchor(Amendment change) {
    return "at-" + change.location().code().replace(' ', '_');
  }

  /**
   * The changes that apply from the same day, as a link that limits the page.
   *
   * @param label what the group is, for example "Does not apply yet, from 15 October 2026"
   * @param count how many changes are in it
   * @param href the address of the page, limited to the group
   * @param on whether the page is limited to this group now
   * @param tone as {@link Change#tone}
   */
  public record Group(String label, int count, String href, boolean on, @Nullable String tone) {}

  /**
   * A link to the article that sets a day.
   *
   * @param label the words of the link, for example "Read Article 3"
   * @param href the address of the article on EUR-Lex
   */
  public record Clause(String label, String href) {}

  /**
   * One change of the amending act.
   *
   * @param place the provision, for example "Article 3, point 8"
   * @param made what the act did to it, for example "Replaced"
   * @param statement its state on the day, with the date and the clause. Null where the data gives
   *     no day for a change that applies.
   * @param tone "warn" where the change does not apply on the day, "open" where Cellar does not
   *     tell, "off" where the act is not in force yet. Null where the change applies.
   * @param anchor the anchor of the change on the page, {@link #anchor}
   * @param wording the address of the wording of the change: the differences at its place between
   *     the text before the act and the first text with it. Null if the texts are not built, or if
   *     neither text has the place.
   */
  public record Change(
      String place,
      String made,
      @Nullable String statement,
      @Nullable String tone,
      String anchor,
      @Nullable String wording) {}

  /**
   * Changes that follow each other in the text and have the same state.
   *
   * @param statement their state on the day, with the date and the clause, said one time for the
   *     sequence
   * @param tone as {@link Change#tone}
   * @param changes the changes, in the order of the text
   */
  public record Sequence(@Nullable String statement, @Nullable String tone, List<Change> changes) {
    public Sequence {
      changes = List.copyOf(changes);
    }

    /** Returns whether a change of the sequence has its wording to show. */
    public boolean worded() {
      return changes.stream().anyMatch(change -> change.wording() != null);
    }

    /** Returns how many provisions the sequence has, in words. For example, "17 provisions". */
    public String size() {
      return changes.size() == 1 ? "1 provision" : changes.size() + " provisions";
    }
  }
}
