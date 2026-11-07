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
 * The From and To cards of a comparison, and the acts between them.
 *
 * <p>Each card shows:
 *
 * <ul>
 *   <li>the version, by its CELEX
 *   <li>its period
 *   <li>the acts that made it
 *   <li>where its text lags the law
 *   <li>a form that asks for the version on another date for that end, and keeps the other end
 * </ul>
 *
 * <p>The acts between the cards are the acts that the versions after From, up to To, integrate.
 * Each act is a chip that limits the comparison to the changes of that act.
 *
 * @param from the From card
 * @param to the To card
 * @param acts the acts between the two versions
 * @param note what the reader should know about how the page chose the versions or acts that it
 *     shows. For example, the page was opened without dates, or the reader asked for an act that is
 *     not between the two versions. Null if there is nothing to say.
 */
public record CompareView(Card from, Card to, Acts acts, @Nullable String note) {

  /** Returns the same comparison, with {@code note} under the cards. */
  public CompareView noting(@Nullable String note) {
    return new CompareView(from, to, acts, note);
  }

  /**
   * The largest number of chips that show in full. Above this number, the chips of acts that made
   * no difference here fold.
   */
  private static final int CHIPS = 6;

  /**
   * Returns the cards of the comparison of {@code from} with {@code to} for {@code act}, on {@code
   * today}. The chip of {@code only}, if any, is on. The chips link to the comparison in {@code
   * view}. The chips are not counted yet. Their counts load from {@code counting}, if they can be
   * counted.
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
        Acts.of(act, from, to, language, only, view, Map.of(), counting),
        null);
  }

  /**
   * Returns the acts that the versions after {@code from}, up to {@code to}, integrate, in their
   * order. The list includes their corrigenda.
   */
  public static List<ActIdentifier> between(Act act, Version from, Version to) {
    return act.timeline().versions().stream()
        .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
        .flatMap(v -> v.integrates().stream())
        .distinct()
        .toList();
  }

  /**
   * The acts between the two versions of a comparison, as chips.
   *
   * @param head how many acts there are. For example, "1 act between them".
   * @param chips the chips of the acts, in the order of the versions. If the acts are counted and
   *     there are more than six chips, this list leaves out the chips of acts that made no
   *     difference here.
   * @param none the chips that {@code chips} leaves out, of acts that made no difference here
   * @param counting the address that the counts load from, until the acts are counted. Null when
   *     they are counted, or if they cannot be counted.
   * @param unknown how many differences one of the acts made, where it is not known which act. This
   *     number shows as its own chip. Null if there are none, or until the acts are counted.
   * @param hint the text that explains the counts. It says that a difference that acts made
   *     together counts in the chip of each act. Null until the acts are counted.
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
     * Returns the acts between {@code from} and {@code to}. The chip of {@code only}, if any, is
     * on. The chips link to the comparison in {@code view}. {@code counts} gives how many
     * differences each act made. If {@code counts} is empty, the acts are not counted, and their
     * counts load from {@code counting}.
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
              // Show a corrigendum only if it corrected the text in this language. That is, it has
              // corrections counted here, or its filter is on.
              .filter(
                  each ->
                      !Wording.corrigendum(each)
                          || each.equals(only)
                          || counts.getOrDefault(each, 0) > 0)
              .map(
                  each ->
                      new Chip(
                          Wording.act(each),
                          title(act, each, language),
                          counts.get(each),
                          // If the chip is on, its link shows all acts again.
                          Links.compare(
                              eli, from, to, language, each.equals(only) ? null : each, view),
                          each.equals(only),
                          Wording.corrigendum(each)))
              .toList();
      var none =
          chips.size() > CHIPS
              ? chips.stream()
                  .filter(chip -> !chip.on() && Integer.valueOf(0).equals(chip.count()))
                  .toList()
              : List.<Chip>of();
      var amending = acts.stream().filter(each -> !Wording.corrigendum(each)).count();
      return new Acts(
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
     * @param unknown the number of differences that one of the acts made, where it is not known
     *     which act
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
      var name = Wording.published(act, each, language);
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

  /** A view of a comparison. It sets what part of the two texts the comparison shows, and how. */
  public enum View {
    /** Only the provisions that differ, with the opening of the unit each is in. */
    CHANGES("changes", "Changes"),
    /** Every provision of the later text. The provisions that differ are marked in place. */
    WHOLE("whole", "Whole text"),
    /** The two texts side by side, aligned by provision. */
    SYNOPSIS("synopsis", "Synopsis");

    private final String code;
    private final String label;

    View(String code, String label) {
      this.code = code;
      this.label = label;
    }

    /**
     * Returns the code of the view in the address of the comparison: {@code changes}, {@code whole}
     * or {@code synopsis}.
     */
    public String code() {
      return code;
    }

    /** Returns the name of the view in the controls. For example, "Whole text". */
    public String label() {
      return label;
    }

    /** Returns the view with this code. Returns empty for an unknown code. */
    public static Optional<View> of(String code) {
      return Arrays.stream(values()).filter(view -> view.code.equals(code)).findFirst();
    }
  }

  /**
   * Returns the links to the views of a comparison, as its controls show them. {@code current} is
   * the view that shows now.
   */
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
   * The link to a view.
   *
   * @param label the name of the view. For example, "Whole text".
   * @param href the address of the comparison in this view
   * @param on whether this view shows now
   */
  public record ViewLink(String label, String href, boolean on) {}

  /**
   * The banner of a comparison that is limited to the changes of one act. For example, "Only
   * 2026/449's changes · show all acts".
   *
   * @param act the act, by its number
   * @param all the address of the comparison of all acts
   */
  public record Only(String act, String all) {}

  /**
   * An end of a comparison. It has the heading of its card and its field in the address of the
   * comparison.
   */
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
   * The card of a version.
   *
   * @param kind which end the card is: From or To
   * @param celex the CELEX of the version
   * @param eurLex the address of the version on EUR-Lex, in the language of the page
   * @param period when the version applied or applies
   * @param made what made the version
   * @param published how the acts that made the version were published, one line for each act. The
   *     hover of {@code made} lists them the same way. Null if there are none.
   * @param lags where the text of the version lags the law during its period
   * @param mayLag where the text of the version may lag the law, because the date of application of
   *     an act is not known
   * @param name the field of the date form for this end: {@code from} or {@code to}
   * @param on the date that the form starts at. This is the first day of the version.
   * @param keep the field of the other end, which the form sends unchanged
   * @param kept the date of the other end
   * @param action the address the form goes to: the comparisons of the act in the language of the
   *     page
   * @param first the earliest date that the form accepts. This is the date of the first version.
   */
  @RecordBuilder
  public record Card(
      String kind,
      String celex,
      String eurLex,
      String period,
      String made,
      @Nullable String published,
      List<String> lags,
      List<String> mayLag,
      String name,
      String on,
      String keep,
      String kept,
      String action,
      String first) {

    public Card {
      lags = List.copyOf(lags);
      mayLag = List.copyOf(mayLag);
    }

    /**
     * Returns the card of {@code version} as the {@code end} of the comparison. {@code other} is
     * the version at the other end.
     */
    static Card of(
        Act act,
        Version version,
        End end,
        Version other,
        Language language,
        LocalDate today,
        String action,
        String first) {
      var published = Wording.makers(act, version, List.of());
      return CompareViewCardBuilder.builder()
          .kind(end.kind)
          .celex(version.celex())
          .eurLex(Links.eurLex(version.celex(), language))
          .period(ReadingView.period(act, version, today))
          .made(Wording.made(act, version, List.of()))
          .published(published.isEmpty() ? null : Wording.published(act, published, language))
          .lags(act.notYetConsolidated(version).stream().map(Wording::lagOf).toList())
          .mayLag(act.mayLag(version).stream().map(Wording::mayLag).toList())
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
