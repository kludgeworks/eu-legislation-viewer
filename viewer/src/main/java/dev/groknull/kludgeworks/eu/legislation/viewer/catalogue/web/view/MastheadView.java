// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The head of the page of an act. It shows the title of the act, its ELI and its CELEX, and the
 * language switch. The ELI and the CELEX each link to EUR-Lex.
 *
 * <p>The title is the act's own title, as the Official Journal printed it. It is in the language of
 * the page if Cellar has it, else in English. If Cellar has neither, the title is the CELEX of the
 * act.
 *
 * <p>The mode switch links to the other mode. When the reader reads a version, it links to the
 * comparison of that version with the version before. When the reader compares two versions, it
 * links to the page of the later version.
 *
 * @param title the title of the act. In English, the title stops at its date. For example,
 *     "Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015".
 * @param subject the rest of the English title, which shows under the title. For example, "laying
 *     down detailed measures …". Null in other languages. Until a language has its own rule, its
 *     whole title is the heading.
 * @param titleLanguage the language of the title, as the HTML {@code lang} attribute has it. Null
 *     when the title is a CELEX.
 * @param eli the ELI of the act, as EUR-Lex writes it. For example, {@code
 *     eli/reg_impl/2015/1998/oj}.
 * @param eliHref the address of the act on EUR-Lex, by its ELI
 * @param celex the CELEX of the act
 * @param celexHref the address of the act on EUR-Lex, by its CELEX, in the language of the page
 * @param language the language of the page, by its own name
 * @param languages the 24 official languages, in the EU's order. Each one links to the page in that
 *     language. A language is greyed, with the reason, where a version on the page is not in it.
 * @param history the address of the history of the act. Null when the page is the history.
 * @param historyLabel the label of the link to the history, with the number of amending acts. For
 *     example, "Amending acts (4)". For an act with no amending act and with corrigenda, it has the
 *     number of corrigenda also: "Amending acts (0) · Corrigenda (3)".
 * @param historyEmpty whether the history has nothing to show: the act has no amending act and no
 *     corrigendum in the language. Then the label is not a link.
 * @param read the address of the page of the version. Null when the reader reads.
 * @param compare the address of the comparison, when the reader reads. Null when the reader
 *     compares.
 * @param preparing whether the act is being prepared. If so, the mode switch shows, but it is not a
 *     link yet.
 * @param notices the address of the stream of notices that the act changed while the page is open
 *     ({@code NoticeController}). Null while the act is being prepared.
 */
public record MastheadView(
    String title,
    @Nullable String subject,
    @Nullable String titleLanguage,
    String eli,
    String eliHref,
    String celex,
    String celexHref,
    String language,
    List<Choice> languages,
    @Nullable String history,
    String historyLabel,
    boolean historyEmpty,
    @Nullable String read,
    @Nullable String compare,
    boolean preparing,
    @Nullable String notices) {

  public MastheadView {
    languages = List.copyOf(languages);
  }

  /**
   * Returns the head of the page of {@code version} of {@code act}, in {@code language}. The reader
   * asked for the version on the date {@code asked}. The language switch keeps that date.
   */
  public static MastheadView reading(Act act, Version version, LocalDate asked, Language language) {
    var eli = eli(act);
    var timeline = act.timeline().in(language).filter(each -> each.versions().contains(version));
    // Compare with the version before. The first version has none, so compare it with the version
    // after. If there is no version after, compare the version with itself.
    var compare =
        timeline
            .flatMap(each -> each.predecessor(version))
            .map(before -> Links.compare(eli, before, version, language))
            .or(
                () ->
                    timeline
                        .flatMap(each -> each.successor(version))
                        .map(after -> Links.compare(eli, version, after, language)))
            .orElseGet(() -> Links.compare(eli, version, version, language));
    return of(
        act,
        language,
        each -> Links.read(eli, asked, each),
        each -> version.languages().contains(each) ? null : "Not for this version",
        Links.history(eli, asked, language),
        null,
        compare);
  }

  /**
   * Returns the head of the comparison of the law on {@code fromDay}, which {@code from} gives,
   * with the law on {@code toDay}, which {@code to} gives, for {@code act}, in {@code language}.
   * The language switch keeps the two days. It greys each language that {@code from} or {@code to}
   * is not in. Read reads the law on {@code toDay}.
   */
  public static MastheadView comparing(
      Act act, Version from, LocalDate fromDay, Version to, LocalDate toDay, Language language) {
    var eli = eli(act);
    return of(
        act,
        language,
        each -> Links.compare(eli, fromDay, toDay, each),
        each -> {
          var inFrom = from.languages().contains(each);
          var inTo = to.languages().contains(each);
          if (inFrom && inTo) {
            return null;
          }
          var name = each.english();
          return inFrom
              ? "To isn’t in " + name
              : inTo ? "From isn’t in " + name : "Neither version is in " + name;
        },
        Links.history(eli, toDay, language),
        Links.read(eli, toDay, language),
        null);
  }

  /**
   * Returns the head of the history of {@code act} on {@code day}, in {@code language}, and of the
   * page of one of its amending acts. The language switch keeps the day. Read reads the law on
   * {@code day}. Compare compares the version of that day with the version before it.
   */
  public static MastheadView history(Act act, LocalDate day, Language language) {
    var eli = eli(act);
    var version = act.versionOn(day, language).orElseGet(act.timeline()::first);
    var reading = reading(act, version, day, language);
    return of(
        act,
        language,
        each -> Links.history(eli, day, each),
        each -> null,
        null,
        Links.read(eli, day, language),
        reading.compare());
  }

  private static MastheadView of(
      Act act,
      Language language,
      Function<Language, String> page,
      Function<Language, @Nullable String> missing,
      @Nullable String history,
      @Nullable String read,
      @Nullable String compare) {
    var eli = eli(act);
    var titled = act.title(language).isPresent() ? language : Language.ENG;
    var full = act.title(titled).orElseGet(act.id()::celex);
    // Split English titles only, for now. The titles in other languages have their own patterns.
    var dated = Wording.DATED.matcher(full);
    var split = titled == Language.ENG && dated.matches();
    return new MastheadView(
        split ? dated.group(1) : full,
        split ? dated.group(2) : null,
        act.title(titled).isPresent() ? titled.iso() : null,
        "eli/" + eli.path() + "/oj",
        eli.eurLex().toString(),
        act.id().celex(),
        act.id().eurLex(language).toString(),
        language.endonym(),
        Language.inProtocolOrder().stream()
            .map(
                each -> {
                  var why = missing.apply(each);
                  return new Choice(
                      each.endonym(),
                      each.iso(),
                      why == null ? page.apply(each) : null,
                      each == language,
                      why);
                })
            .toList(),
        history,
        historyLabel(act, language),
        act.amendingActs(language).isEmpty() && act.corrigendaOf(act.id(), language).isEmpty(),
        read,
        compare,
        false,
        Links.notices(eli, language));
  }

  /**
   * Returns the label of the link to the history of {@code act}. The history lists the amending
   * acts, and below them the corrigenda. The label names the corrigenda only where they are all
   * that the history shows.
   */
  private static String historyLabel(Act act, Language language) {
    var amending = act.amendingActs(language).size();
    var corrigenda = act.corrigendaOf(act.id(), language).size();
    return "Amending acts ("
        + amending
        + ")"
        + (amending == 0 && corrigenda > 0 ? " · Corrigenda (" + corrigenda + ")" : "");
  }

  /**
   * Returns this head for the time while the act is being prepared. The mode switch is not a link
   * yet.
   */
  public MastheadView whilePreparing() {
    return new MastheadView(
        title,
        subject,
        titleLanguage,
        eli,
        eliHref,
        celex,
        celexHref,
        language,
        languages,
        history,
        historyLabel,
        historyEmpty,
        read,
        compare,
        true,
        null);
  }

  private static Eli eli(Act act) {
    return Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
  }

  /**
   * A language of the language switch.
   *
   * @param name the name of the language, in that language
   * @param iso the ISO 639-1 code of the language, as the HTML {@code lang} and {@code hreflang}
   *     attributes have it
   * @param href the address of the page in this language. Null where a version on the page is not
   *     in this language.
   * @param current whether this is the language of the page
   * @param missing why the language is greyed. For example, "Not for this version" or "From isn’t
   *     in Irish". Null where it is not greyed.
   */
  public record Choice(
      String name, String iso, @Nullable String href, boolean current, @Nullable String missing) {}
}
