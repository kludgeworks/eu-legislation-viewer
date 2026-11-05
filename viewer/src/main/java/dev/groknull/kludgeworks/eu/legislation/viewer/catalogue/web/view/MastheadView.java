// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The head of an act's page (Design: The act page): its title, its ELI and CELEX, each a link to
 * EUR-Lex, and the language switch. The title is the act's own, as the Official Journal printed it,
 * in the page's language, or else in English; the act's CELEX if Cellar has neither.
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
    List<String> missing) {

  public MastheadView {
    languages = List.copyOf(languages);
    missing = List.copyOf(missing);
  }

  /**
   * The head of {@code act}'s page of {@code version}, asked for on {@code asked}, in {@code
   * language}: its language switch keeps the date asked.
   */
  public static MastheadView of(Act act, Version version, LocalDate asked, Language language) {
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
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
            .map(
                each ->
                    new Choice(
                        each.endonym(), each.iso(), Links.read(eli, asked, each), each == language))
            .toList(),
        Arrays.stream(Language.values())
            .filter(each -> !version.languages().contains(each))
            .map(Language::endonym)
            .toList());
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
