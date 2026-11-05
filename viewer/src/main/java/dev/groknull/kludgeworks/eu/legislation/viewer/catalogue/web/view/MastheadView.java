// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The head of an act's page (Design: The act page): its title, its ELI and CELEX, each a link to
 * EUR-Lex, and the language switch. The title is the act's own, as the Official Journal printed it,
 * in the page's language, or else in English; the act's CELEX if Cellar has neither. The mode
 * switch links to the other mode: reading a version, to its comparison with the version before;
 * comparing two, to reading the later.
 *
 * @param title the act's title
 * @param titleLanguage the language the title is in, as HTML's lang has it; none for a CELEX
 * @param eli the act's ELI as EUR-Lex writes it: eli/reg_impl/2015/1998/oj
 * @param eliHref the act on EUR-Lex by its ELI
 * @param celex the act's CELEX
 * @param celexHref the act on EUR-Lex by its CELEX, in the page's language
 * @param language the page's language, by its own name
 * @param languages the languages the version is in, each a link to its page in it
 * @param missing the official languages it isn't in, by their own names
 * @param read the version's page, when comparing; none when reading
 * @param compare the comparison, when reading; none when comparing
 */
public record MastheadView(
    String title,
    @Nullable String titleLanguage,
    String eli,
    String eliHref,
    String celex,
    String celexHref,
    String language,
    List<Choice> languages,
    List<String> missing,
    @Nullable String read,
    @Nullable String compare) {

  public MastheadView {
    languages = List.copyOf(languages);
    missing = List.copyOf(missing);
  }

  /**
   * The head of {@code act}'s page of {@code version}, asked for on {@code asked}, in {@code
   * language}: its language switch keeps the date asked.
   */
  public static MastheadView reading(Act act, Version version, LocalDate asked, Language language) {
    var eli = eli(act);
    var before =
        act.timeline()
            .in(language)
            .filter(timeline -> timeline.versions().contains(version))
            .flatMap(timeline -> timeline.predecessor(version))
            .orElse(version);
    return of(
        act,
        version,
        language,
        each -> Links.read(eli, asked, each),
        null,
        Links.compare(eli, before, version, language));
  }

  /**
   * The head of {@code act}'s comparison of {@code from} with {@code to}, in {@code language}: its
   * language switch keeps the versions, and lists the languages {@code to} is in.
   */
  public static MastheadView comparing(Act act, Version from, Version to, Language language) {
    var eli = eli(act);
    return of(
        act,
        to,
        language,
        each -> Links.compare(eli, from, to, each),
        Links.read(eli, to, language),
        null);
  }

  private static MastheadView of(
      Act act,
      Version version,
      Language language,
      Function<Language, String> page,
      @Nullable String read,
      @Nullable String compare) {
    var eli = eli(act);
    var titled = act.title(language).isPresent() ? language : Language.ENG;
    return new MastheadView(
        act.title(titled).orElseGet(act.id()::celex),
        act.title(titled).isPresent() ? titled.iso() : null,
        "eli/" + eli.path() + "/oj",
        Links.eurLex(eli),
        act.id().celex(),
        Links.eurLex(act.id().celex(), language),
        language.endonym(),
        version.languages().stream()
            .sorted()
            .map(each -> new Choice(each.endonym(), each.iso(), page.apply(each), each == language))
            .toList(),
        Arrays.stream(Language.values())
            .filter(each -> !version.languages().contains(each))
            .map(Language::endonym)
            .toList(),
        read,
        compare);
  }

  private static Eli eli(Act act) {
    return Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
  }

  /**
   * A language of the switch.
   *
   * @param name its own name
   * @param iso its ISO 639-1 code, as HTML's lang and hreflang have it
   * @param href the page in it
   * @param current whether it is the page's
   */
  public record Choice(String name, String iso, String href, boolean current) {}
}
