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
 * @param languages the 24 official languages in the EU's order (Design D-16, D-35), each a link to
 *     the page in it, greyed with why where a version shown isn't in it
 * @param read the version's page, when comparing; none when reading
 * @param compare the comparison, when reading; none when comparing
 * @param preparing whether the act is being prepared: the mode switch is shown, not yet a link
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
    @Nullable String read,
    @Nullable String compare,
    boolean preparing) {

  public MastheadView {
    languages = List.copyOf(languages);
  }

  /**
   * The head of {@code act}'s page of {@code version}, asked for on {@code asked}, in {@code
   * language}: its language switch keeps the date asked.
   */
  public static MastheadView reading(Act act, Version version, LocalDate asked, Language language) {
    var eli = eli(act);
    var timeline = act.timeline().in(language).filter(each -> each.versions().contains(version));
    // the version before; the first, with the one after (Design D-22)
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
        null,
        compare);
  }

  /**
   * The head of {@code act}'s comparison of {@code from} with {@code to}, in {@code language}: its
   * language switch keeps the versions, and lists the languages {@code to} is in.
   */
  public static MastheadView comparing(Act act, Version from, Version to, Language language) {
    var eli = eli(act);
    return of(
        act,
        language,
        each -> Links.compare(eli, from, to, each),
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
        Links.read(eli, to, language),
        null);
  }

  private static MastheadView of(
      Act act,
      Language language,
      Function<Language, String> page,
      Function<Language, @Nullable String> missing,
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
        read,
        compare,
        false);
  }

  /** This head while the act is being prepared: the mode switch not yet a link. */
  public MastheadView whilePreparing() {
    return new MastheadView(
        title,
        titleLanguage,
        eli,
        eliHref,
        celex,
        celexHref,
        language,
        languages,
        read,
        compare,
        true);
  }

  private static Eli eli(Act act) {
    return Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
  }

  /**
   * A language of the switch.
   *
   * @param name its own name
   * @param iso its ISO 639-1 code, as HTML's lang and hreflang have it
   * @param href the page in it; none where a version shown isn't in it
   * @param current whether it is the page's
   * @param missing why it is greyed: Not for this version; From isn’t in Irish; none where it isn't
   */
  public record Choice(
      String name, String iso, @Nullable String href, boolean current, @Nullable String missing) {}
}
