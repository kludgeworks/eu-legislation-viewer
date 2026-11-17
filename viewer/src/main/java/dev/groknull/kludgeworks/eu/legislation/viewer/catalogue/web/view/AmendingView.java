// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextMarks;
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
 * The rules are the act's: {@link Act#changesBy}, {@link Act#stateOn} and {@link Act#stageOf}. This
 * view puts them into words.
 *
 * @param title the title of the page
 * @param heading the heading, for example "What 2022/1190 changed in 2018/1862"
 * @param subject the title of the amending act after its date, or null if Cellar has no title
 * @param subjectLanguage the ISO code of the language of the title
 * @param journalHref the address of the amending act on EUR-Lex
 * @param journal its Official Journal reference
 * @param day the day, in words
 * @param on the day, as the date box has it
 * @param action the address that the date form sends to
 * @param history the address of the history of the act, on the day
 * @param base how the amended act is cited
 * @param state how the amending act stands on the day, as sentences
 * @param dates its dates, with the clause that sets them
 * @param stages the stages of its changes, each a link that limits the page. The first is all of
 *     its changes. Empty if Cellar lists no changed place.
 * @param runs its changes, in the order of the text, limited to the stage that is on. Changes that
 *     follow each other and have the same state are one run, so the page says the state one time.
 * @param ordered whether the changes are in the order of the text. They are in the order of the
 *     data while no text of the act is built.
 * @param open whether the wording of each change shows when the page opens. It does if the page has
 *     few changes. If not, the reader opens the wording of a change.
 * @param wording the address of the wording of its changes in a comparison. Null if no version
 *     includes the act.
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
    String on,
    String action,
    String history,
    String base,
    String state,
    String dates,
    List<Stage> stages,
    List<Run> runs,
    boolean ordered,
    boolean open,
    @Nullable String wording,
    @Nullable String either,
    List<HistoryView.Corrigendum> corrigenda) {

  /** The page shows the wording of each change when it opens, up to this many changes. */
  private static final int OPEN = 25;

  /** The value of the stage parameter for the changes whose stage the data does not give. */
  public static final String NO_STAGE = "none";

  public AmendingView {
    stages = List.copyOf(stages);
    runs = List.copyOf(runs);
    corrigenda = List.copyOf(corrigenda);
  }

  /**
   * Returns the page of {@code amending} for {@code act} on {@code day}, in {@code language}.
   *
   * @param stage the stage that limits the page: a day, or {@link #NO_STAGE}. Null for all changes.
   * @param text the marks of the latest built text of the act, which give the order of the text.
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
      @Nullable String stage,
      @Nullable TextMarks text,
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
    var integrated =
        act.timeline().versions().stream().anyMatch(each -> each.integrates().contains(amending));
    var compare =
        step == null
            ? null
            : new ComparePage(
                Links.compare(eli, language), step.from(), step.to(), amending.celex(), null);
    var shown =
        inOrder.stream()
            .filter(change -> stage == null || stage.equals(key(act, amending, change)))
            .toList();
    return new AmendingView(
        cited + " in " + act.cited(act.id()) + " · on " + Dates.inShort(day),
        "What " + cited + " changed in " + act.cited(act.id()),
        subject.text(),
        subject.language(),
        act.eurLex(amending, language).toString(),
        HistoryView.journal(act, amending),
        Dates.inWords(day),
        day.toString(),
        Links.amending(eli, amending, null, language),
        Links.history(eli, kept, language),
        act.cited(act.id()),
        cited
            + " "
            + States.phrase(standing.whole(), past)
            + "."
            + (all.isEmpty()
                ? " The data lists no changed provisions."
                : " It changed "
                    + (all.size() == 1 ? "1 provision." : all.size() + " provisions.")),
        States.dates(amending, act.applying(amending, Set.of()).orElse(null)),
        stages(act, amending, all, day, past, page, stage),
        runs(
            shown.stream()
                .map(change -> change(act, amending, change, day, past, compare, step))
                .toList()),
        text != null,
        shown.size() <= OPEN,
        integrated ? Links.changesBy(eli, amending, language) : null,
        compare == null || act.integratedWith(amending, language).isEmpty()
            ? null
            : compare.either(),
        act.corrigendaOf(amending, language).stream()
            .map(each -> HistoryView.Corrigendum.of(act, each, language))
            .toList());
  }

  /** Returns the value of the stage parameter for {@code change}: its day, or {@link #NO_STAGE}. */
  private static String key(Act act, ActIdentifier amending, Amendment change) {
    return act.stageOf(amending, Act.days(change)).map(LocalDate::toString).orElse(NO_STAGE);
  }

  private static List<Stage> stages(
      Act act,
      ActIdentifier amending,
      List<Amendment> all,
      LocalDate day,
      boolean past,
      String page,
      @Nullable String on) {
    if (all.isEmpty()) {
      return List.of();
    }
    Map<Optional<LocalDate>, Integer> counts =
        new TreeMap<>(
            Comparator.comparing(
                (Optional<LocalDate> each) -> each.orElse(LocalDate.MAX),
                Comparator.naturalOrder()));
    all.forEach(change -> counts.merge(act.stageOf(amending, Act.days(change)), 1, Integer::sum));
    var separator = page.contains("?") ? "&" : "?";
    var stages = new ArrayList<Stage>();
    stages.add(new Stage("All", all.size(), page, on == null, null));
    counts.forEach(
        (from, count) -> {
          var key = from.map(LocalDate::toString).orElse(NO_STAGE);
          var state = from.map(each -> act.stateOn(amending, Set.of(each), day));
          stages.add(
              new Stage(
                  from.map(each -> label(state.orElseThrow(), each, past))
                      .orElse("Stage not given by the data"),
                  count,
                  page + separator + "stage=" + key,
                  key.equals(on),
                  state.isPresent() ? States.tone(state.get()) : States.OPEN));
        });
    return stages;
  }

  /** Returns the label of the stage that starts on {@code from}. */
  private static String label(State state, LocalDate from, boolean past) {
    var day = Dates.inWords(from);
    return switch (state) {
      case APPLIES -> (past ? "Applied from " : "Applies from ") + day;
      case NOT_APPLYING -> (past ? "Did not apply yet, from " : "Does not apply yet, from ") + day;
      case NOT_IN_FORCE, NOT_KNOWN -> "From " + day;
    };
  }

  /**
   * Returns {@code changes} as runs: changes that follow each other and have the same note are one
   * run. The order of the changes stays.
   */
  private static List<Run> runs(List<Change> changes) {
    var runs = new ArrayList<Run>();
    for (var change : changes) {
      var last = runs.isEmpty() ? null : runs.getLast();
      if (last != null
          && Objects.equals(last.note(), change.note())
          && Objects.equals(last.tone(), change.tone())) {
        var joined = new ArrayList<>(last.changes());
        joined.add(change);
        runs.set(runs.size() - 1, new Run(last.note(), last.tone(), joined));
      } else {
        runs.add(new Run(change.note(), change.tone(), List.of(change)));
      }
    }
    return runs;
  }

  private static Change change(
      Act act,
      ActIdentifier amending,
      Amendment change,
      LocalDate day,
      boolean past,
      @Nullable ComparePage compare,
      @Nullable Step step) {
    var units = step == null ? List.<String>of() : Act.units(change, step.later(), step.earlier());
    var days = Act.days(change);
    var state = act.stateOn(amending, days, day);
    var from = act.stageOf(amending, days);
    var name = change.location().name();
    var note =
        switch (state) {
          case APPLIES ->
              from.map(
                      each ->
                          (past ? "Applied from " : "Applies from ")
                              + Dates.inWords(each)
                              + States.clauseOn(act, amending, each)
                              + ".")
                  .orElse(null);
          case NOT_APPLYING ->
              (past ? "Did not apply yet." : "Does not apply yet.")
                  + from.map(
                          each ->
                              " Applies from "
                                  + Dates.inWords(each)
                                  + States.clauseOn(act, amending, each)
                                  + ".")
                      .orElse("");
          case NOT_IN_FORCE -> past ? "Was not in force yet." : "Not in force yet.";
          case NOT_KNOWN -> States.unknown(amending, act.applying(amending, Set.of()).orElse(null));
        };
    return new Change(
        name.isEmpty() ? change.location().code() : name,
        switch (change.role()) {
          case REPLACEMENT -> "Replaced";
          case ADDITION -> "Inserted";
          case DELETION -> "Deleted";
          case REPEAL -> "Repealed";
          case AMENDMENT -> "Amended";
          case EXTENSION, SUSPENSION, DEFERRAL, OTHER -> "Changed";
        },
        note,
        States.tone(state),
        compare == null || units.isEmpty() ? null : compare.place(units));
  }

  /**
   * A stage of the changes, as a link that limits the page.
   *
   * @param label what the stage is, for example "Does not apply yet, from 15 October 2026"
   * @param count how many changes are in it
   * @param href the address of the page, limited to the stage
   * @param on whether the page is limited to this stage now
   * @param tone as {@link Change#tone}
   */
  public record Stage(String label, int count, String href, boolean on, @Nullable String tone) {}

  /**
   * One change of the amending act.
   *
   * @param place the provision, for example "Article 3, point 8"
   * @param made what the act did to it, for example "Replaced"
   * @param note its state on the day, with the date and the clause. Null where the data gives no
   *     day for a change that applies.
   * @param tone "warn" where the change does not apply on the day, "open" where Cellar does not
   *     tell, "off" where the act is not in force yet. Null where the change applies.
   * @param wording the address of the wording of the change: the differences at its place between
   *     the text before the act and the first text with it. Null if the texts are not built, or if
   *     neither text has the place.
   */
  public record Change(
      String place,
      String made,
      @Nullable String note,
      @Nullable String tone,
      @Nullable String wording) {}

  /**
   * The two texts that show the wording of the changes of an amending act.
   *
   * @param from the first day of the version before the act
   * @param to the first day of the first version that integrates the act
   * @param earlier the eIds of the units of the text of {@code from}, in document order
   * @param later the eIds of the units of the text of {@code to}, in document order
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
   * @param note their state on the day, with the date and the clause, said one time for the run
   * @param tone as {@link Change#tone}
   * @param changes the changes, in the order of the text
   */
  public record Run(@Nullable String note, @Nullable String tone, List<Change> changes) {
    public Run {
      changes = List.copyOf(changes);
    }

    /** Returns whether a change of the run has its wording to show. */
    public boolean worded() {
      return changes.stream().anyMatch(change -> change.wording() != null);
    }

    /** Returns how many provisions the run has, in words. For example, "17 provisions". */
    public String size() {
      return changes.size() == 1 ? "1 provision" : changes.size() + " provisions";
    }
  }
}
