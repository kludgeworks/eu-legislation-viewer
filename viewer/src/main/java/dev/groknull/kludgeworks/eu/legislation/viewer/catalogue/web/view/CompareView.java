// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The From and To cards of a comparison (Design: Mode: Compare), and the acts between them: each
 * card the version by its CELEX, its period, the acts that made it, where its text lags the law,
 * and a form asking for the version on another date for that end, the other kept; the acts, those
 * the versions after From up to To integrate, each a chip limiting the comparison to its changes.
 *
 * @param from the From card
 * @param to the To card
 * @param acts the acts between them
 */
public record CompareView(Card from, Card to, Acts acts) {

  /** Above this many chips, those of acts that made no difference here fold. */
  private static final int CHIPS = 6;

  /**
   * The cards of {@code act}'s comparison of {@code from} with {@code to}, on {@code today}, its
   * acts' chips {@code only}'s on, if any, linking in {@code view}, uncounted, counted from {@code
   * counting} if they can be.
   */
  public static CompareView of(
      Act act,
      Version from,
      Version to,
      Language language,
      LocalDate today,
      @Nullable ActIdentifier only,
      View view,
      @Nullable String counting) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var action = Links.compare(eli, language);
    var first = act.timeline().versions().getFirst().date().toString();
    return new CompareView(
        Card.of(act, from, End.FROM, to, language, today, action, first),
        Card.of(act, to, End.TO, from, language, today, action, first),
        Acts.of(act, from, to, language, only, view, Map.of(), counting));
  }

  /**
   * The acts the versions after {@code from}, up to {@code to}, integrate, in their order, their
   * corrigenda left out.
   */
  public static List<ActIdentifier> between(Act act, Version from, Version to) {
    return act.timeline().versions().stream()
        .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
        .flatMap(v -> v.integrates().stream())
        .filter(id -> !Wording.corrigendum(id))
        .distinct()
        .toList();
  }

  /**
   * The acts between the two versions (Design: Acts between the two versions), as chips.
   *
   * @param head how many: 1 act between them
   * @param chips their chips, in the versions' order; counted, more than six, but for those that
   *     made no difference here
   * @param none the chips left out, of acts that made no difference here: + 3 with no differences
   *     here
   * @param counting where their counts load from, until they're counted; none once they are, or if
   *     they can't be
   * @param unknown how many differences one of them made, not known which, as its own chip; none if
   *     none, or until counted
   * @param hint what the counts say: that a difference acts made together counts in each act's
   *     chip; none until counted
   */
  public record Acts(
      String head,
      List<Chip> chips,
      List<Chip> none,
      @Nullable String counting,
      @Nullable Integer unknown,
      @Nullable String hint) {
    public Acts {
      chips = List.copyOf(chips);
      none = List.copyOf(none);
    }

    /**
     * The acts between {@code from} and {@code to}, {@code only}'s chip on, linking in {@code
     * view}: each with how many differences it made ({@code counts}), where counted; uncounted, to
     * be from {@code counting}, where none is.
     */
    public static Acts of(
        Act act,
        Version from,
        Version to,
        Language language,
        @Nullable ActIdentifier only,
        View view,
        Map<ActIdentifier, Integer> counts,
        @Nullable String counting) {
      var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
      var acts = between(act, from, to);
      var chips =
          acts.stream()
              .map(
                  each ->
                      new Chip(
                          Wording.act(each),
                          title(act, each),
                          counts.get(each),
                          // on, it shows all acts again
                          Links.compare(
                              eli, from, to, language, each.equals(only) ? null : each, view),
                          each.equals(only)))
              .toList();
      var none =
          chips.size() > CHIPS
              ? chips.stream()
                  .filter(chip -> !chip.on() && Integer.valueOf(0).equals(chip.count()))
                  .toList()
              : List.<Chip>of();
      return new Acts(
          acts.size() + (acts.size() == 1 ? " act between them" : " acts between them"),
          chips.stream().filter(chip -> !none.contains(chip)).toList(),
          none,
          counts.isEmpty() ? counting : null,
          null,
          null);
    }

    /**
     * The same, counted: {@code together} differences acts made together, {@code unknown} one of
     * them made, not known which.
     */
    public Acts counted(int together, int unknown) {
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
      return new Acts(head, chips, none, counting, unknown == 0 ? null : unknown, hint);
    }

    /** What the chips left out say: + 3 with no differences here. */
    public String more() {
      return "+ " + none.size() + " with no differences here";
    }

    /** An act's chip's hover: 2026/449 · entered into force on 22 March 2026. */
    private static String title(Act act, ActIdentifier each) {
      var name = Wording.act(each);
      return act.datesOf(each).stream()
          .filter(date -> date.kind() == ActDate.Kind.ENTRY_INTO_FORCE && date.date() != null)
          .map(
              date ->
                  name
                      + " · entered into force on "
                      + Wording.date(Objects.requireNonNull(date.date())))
          .findFirst()
          .orElse(name);
    }
  }

  /**
   * An act's chip.
   *
   * @param label the act, by its number: 2026/449
   * @param title its hover: the act and when it entered into force
   * @param count how many differences it made here; none until counted
   * @param href the comparison limited to its changes; on, the comparison of all acts
   * @param on whether the comparison is limited to its changes
   */
  public record Chip(
      String label, String title, @Nullable Integer count, String href, boolean on) {}

  /** A comparison's view (Design: Mode: Compare): what of the two texts it shows, and how. */
  public enum View {
    /** Only the provisions that differ, with the opening of the unit each is in. */
    CHANGES("changes", "Changes"),
    /** Every provision of the later text, those that differ marked in place. */
    WHOLE("whole", "Whole text"),
    /** The two texts side by side, aligned by provision. */
    SYNOPSIS("synopsis", "Synopsis");

    private final String code;
    private final String label;

    View(String code, String label) {
      this.code = code;
      this.label = label;
    }

    /** As the comparison's address has it: changes, whole, synopsis. */
    public String code() {
      return code;
    }

    /** As its controls name it: Whole text. */
    public String label() {
      return label;
    }

    /** The view by its code; none for another. */
    public static Optional<View> of(String code) {
      return Arrays.stream(values()).filter(view -> view.code.equals(code)).findFirst();
    }
  }

  /** A comparison's views, as its controls link them, {@code current} the one shown. */
  public static List<ViewLink> views(
      Eli eli,
      Version from,
      Version to,
      Language language,
      @Nullable ActIdentifier only,
      View current) {
    return Arrays.stream(View.values())
        .map(
            view ->
                new ViewLink(
                    view.label,
                    Links.compare(eli, from, to, language, only, view),
                    view == current))
        .toList();
  }

  /**
   * A view's link.
   *
   * @param label its name: Whole text
   * @param href the comparison in it
   * @param on whether it is the one shown
   */
  public record ViewLink(String label, String href, boolean on) {}

  /**
   * A comparison limited to one act's changes, as its banner says it (Design: Acts between the two
   * versions): Only 2026/449's changes · show all acts.
   *
   * @param act the act, by its number
   * @param all the comparison of all acts
   */
  public record Only(String act, String all) {}

  /** An end of a comparison: what its card is headed, and its field in the comparison's address. */
  private enum End {
    FROM("From", "from"),
    TO("To", "to");

    private final String kind;
    private final String field;

    End(String kind, String field) {
      this.kind = kind;
      this.field = field;
    }

    End other() {
      return this == FROM ? TO : FROM;
    }
  }

  /**
   * A version's card.
   *
   * @param kind which end: From, To
   * @param celex the version's CELEX
   * @param eurLex the version on EUR-Lex, in the page's language
   * @param period when it applied or applies
   * @param made what made it
   * @param corrigenda the corrigenda it integrates, by name, for made's hover; none if none
   * @param lags where its text lags the law over its period
   * @param name the date form's field for this end: from, to
   * @param on the date the form starts at: the version's first day
   * @param keep the other end's field, which the form sends unchanged
   * @param kept the other end's date
   * @param action where the form goes: the act's comparisons in the page's language
   * @param first the earliest date the form takes: the first version's
   */
  @RecordBuilder
  public record Card(
      String kind,
      String celex,
      String eurLex,
      String period,
      String made,
      @Nullable String corrigenda,
      List<String> lags,
      String name,
      String on,
      String keep,
      String kept,
      String action,
      String first) {

    public Card {
      lags = List.copyOf(lags);
    }

    /** {@code version}'s card as the comparison's {@code end}, {@code other} the other end. */
    static Card of(
        Act act,
        Version version,
        End end,
        Version other,
        Language language,
        LocalDate today,
        String action,
        String first) {
      List<ActIdentifier> corrigenda =
          version.integrates().stream().filter(Wording::corrigendum).toList();
      return CompareViewCardBuilder.builder()
          .kind(end.kind)
          .celex(version.celex())
          .eurLex(Links.eurLex(version.celex(), language))
          .period(ReadingView.period(act, version, today))
          .made(Wording.made(version))
          .corrigenda(corrigenda.isEmpty() ? null : ReadingView.names(corrigenda))
          .lags(act.notYetConsolidated(version).stream().map(Wording::lagOf).toList())
          .name(end.field)
          .on(version.date().toString())
          .keep(end.other().field)
          .kept(other.date().toString())
          .action(action)
          .first(first)
          .build();
    }
  }
}
