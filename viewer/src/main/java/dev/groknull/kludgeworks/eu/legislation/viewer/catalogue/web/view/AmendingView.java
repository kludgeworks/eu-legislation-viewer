// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The page of one amending act: what it changed in an act, and the state of each change on a day.
 * The rules are the act's: {@link Act#changesBy}, {@link Act#stateOn} and {@link
 * Act#changeAppliesFrom}. This view puts them into words.
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
   * Returns the page of {@code amending} for {@code act} on {@code day}, in {@code language}.
   *
   * @param group the day of application that limits the page, or {@link #NOT_KNOWN}. Null for all
   *     changes.
   * @param text the tags of the latest built text of the act, which give the order of the text.
   *     Null if no text is built.
   * @param step the two texts that show the wording of the changes. Null if they are not built, or
   *     if no version integrates the amending act.
   */
  public static AmendingView of(
      Act act,
      ActIdentifier amending,
      LocalDate day,
      LocalDate today,
      Language language,
      @Nullable String group,
      @Nullable TextTags text,
      @Nullable Step step) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var past = day.isBefore(today);
    var kept = day.equals(today) ? null : day;
    var all = act.changesBy(amending, language);
    var inOrder = text == null ? all : Act.inTextOrder(all, text.eIds());
    var page = Links.amending(eli, amending, kept, language);
    var standing = act.standingOn(amending, language, day);
    var subject = HistoryView.Subject.of(act, amending, language);
    var cited = act.cited(amending);
    var compare =
        step == null
            ? null
            : new ComparePage(
                Links.compare(eli, language), step.from(), step.to(), amending.celex(), null);
    var shown =
        inOrder.stream()
            .filter(change -> group == null || group.equals(key(act, amending, change)))
            .toList();
    return new AmendingView(
        cited + " in " + act.cited(act.id()) + " · on " + Dates.inShort(day),
        "What " + cited + " changed in " + act.cited(act.id()),
        subject.text(),
        subject.language(),
        act.eurLex(amending, language).toString(),
        HistoryView.journal(act, amending),
        Dates.inWords(day),
        DayView.of(
            act, day, null, Links.amending(eli, amending, null, language), null, null, language),
        Links.history(eli, kept, language),
        act.cited(act.id()),
        cited
            + " "
            + StateWording.phrase(standing.state(), past)
            + "."
            + (all.isEmpty()
                ? " The data lists no changed provisions."
                : " It changed "
                    + (all.size() == 1 ? "1 provision." : all.size() + " provisions.")),
        Wording.dates(amending, act.applying(amending, Set.of()).orElse(null)),
        clause(act, amending, language),
        groups(act, amending, all, day, past, page, group),
        sequences(
            shown.stream()
                .map(change -> change(act, amending, change, day, past, compare, step))
                .toList()),
        text != null,
        shown.size() <= OPEN,
        compare == null || act.integratedWith(amending, language).isEmpty()
            ? null
            : compare.either(),
        act.corrigendaOf(amending, language).stream()
            .map(each -> HistoryView.Corrigendum.of(act, each, language))
            .toList());
  }

  /** Returns the group of {@code change}: the day from which it applies, or {@link #NOT_KNOWN}. */
  private static String key(Act act, ActIdentifier amending, Amendment change) {
    return act.changeAppliesFrom(amending, Act.days(change))
        .map(LocalDate::toString)
        .orElse(NOT_KNOWN);
  }

  private static List<Group> groups(
      Act act,
      ActIdentifier amending,
      List<Amendment> all,
      LocalDate day,
      boolean past,
      String page,
      @Nullable String on) {
    Map<Optional<LocalDate>, Integer> counts =
        new TreeMap<>(
            Comparator.comparing(
                (Optional<LocalDate> each) -> each.orElse(LocalDate.MAX),
                Comparator.naturalOrder()));
    all.forEach(
        change -> counts.merge(act.changeAppliesFrom(amending, Act.days(change)), 1, Integer::sum));
    if (counts.size() < 2) {
      return List.of();
    }
    var separator = page.contains("?") ? "&" : "?";
    var groups = new ArrayList<Group>();
    groups.add(new Group("All changes", all.size(), page, on == null, null));
    counts.forEach(
        (from, count) -> {
          var key = from.map(LocalDate::toString).orElse(NOT_KNOWN);
          var state = from.map(each -> act.stateOn(amending, Set.of(each), day));
          groups.add(
              new Group(
                  from.map(each -> label(state.orElseThrow(), each, past)).orElse("Day not known"),
                  count,
                  page + separator + "applies=" + key,
                  key.equals(on),
                  state.isPresent() ? StateWording.tone(state.get()) : StateWording.UNKNOWN));
        });
    return groups;
  }

  /**
   * Returns a link to the article of {@code amending} that sets the day from which it applies.
   * Returns null if Cellar knows the day, or if it names no article. The address is the text of the
   * act on EUR-Lex, at the anchor of the article: "art_" and its number.
   */
  private static @Nullable Clause clause(Act act, ActIdentifier amending, Language language) {
    return act.applying(amending, Set.of())
        .flatMap(
            applying ->
                switch (applying) {
                  case Applying.SetBy set ->
                      set.dates().stream()
                          .flatMap(date -> date.clause().provisions().stream())
                          .filter(each -> each.type() == ActDate.Provision.Type.ARTICLE)
                          .filter(each -> !article(each).isEmpty())
                          .findFirst()
                          .map(
                              each ->
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
      case NOT_IN_FORCE, IN_PART, NOT_KNOWN -> "From " + day;
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

  private static Change change(
      Act act,
      ActIdentifier amending,
      Amendment change,
      LocalDate day,
      boolean past,
      @Nullable ComparePage compare,
      @Nullable Step step) {
    var occurrences =
        step == null ? List.<String>of() : Act.occurrences(change, step.later(), step.earlier());
    var days = Act.days(change);
    var state = act.stateOn(amending, days, day);
    var name = change.location().place().words();
    var statement = StateWording.statement(act, amending, days, day, past);
    return new Change(
        name.isEmpty() ? change.location().code() : name,
        made(change.role()),
        statement,
        StateWording.tone(state),
        anchor(change),
        compare == null || occurrences.isEmpty() ? null : compare.place(occurrences));
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
   * The two texts that show the wording of the changes of an amending act.
   *
   * @param from the first day of the version before the act
   * @param to the first day of the first version that integrates the act
   * @param earlier the eIds of the occurrences of the text of {@code from}, in document order
   * @param later the eIds of the occurrences of the text of {@code to}, in document order
   */
  public record Step(LocalDate from, LocalDate to, List<String> earlier, List<String> later) {
    public Step {
      earlier = List.copyOf(earlier);
      later = List.copyOf(later);
    }
  }

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
