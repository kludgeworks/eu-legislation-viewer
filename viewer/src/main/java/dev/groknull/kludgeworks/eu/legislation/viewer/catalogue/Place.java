// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import dev.groknull.kludgeworks.eu.legislation.rules.BusinessRule;
import dev.groknull.kludgeworks.eu.legislation.rules.RuleGroup;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The subdivision of an act that an amendment modifies, named in words as EUR-Lex names it. For
 * example:
 *
 * <ul>
 *   <li>annex point 1.3.1.1 point (g)
 *   <li>article 114 paragraph 1 unnumbered paragraph 1
 *   <li>annex chapter 5 APP 5-A, where an older annotation of Cellar does not code the appendix
 * </ul>
 *
 * <p>Its {@link Location} finds it among the occurrences of a text.
 *
 * @param words the name of the place
 */
@ValueObject
public record Place(String words) {

  /**
   * A code as Cellar writes it in a location, with the URI of its concept in the table of
   * subdivisions. For example, {@code {AR|http://…/fd_370/AR}}.
   */
  private static final Pattern CODE = Pattern.compile("\\{([^|}]*)\\|([^}]*)}");

  /**
   * Returns the place that Cellar's {@code location} names, as EUR-Lex names it: each code in its
   * words in Cellar's table of subdivisions, from the outermost in, and each other word as Cellar
   * writes it. A code without words in {@code labels} keeps its code.
   *
   * @param location the location as Cellar writes it, with the URIs of its codes. For example,
   *     {@code {AN|…/fd_370/AN} {PO|…/fd_370/PO} 1.3.1.1 {PTA|…/fd_370/PTA} (g)}.
   * @param labels the English words of each code of the location, by the URI of its concept, as
   *     Cellar gives them. For example, "point" for {@code …/fd_370/PTA}.
   */
  @BusinessRule(group = RuleGroup.PLACES, name = "a-page-names-a-place-as-eur-lex-does")
  public static Place named(String location, Map<String, String> labels) {
    var words =
        CODE.matcher(location)
            .replaceAll(code ->
                Matcher.quoteReplacement(labels.getOrDefault(code.group(2), code.group(1))));
    return new Place(words.strip().replaceAll("\\s+", " "));
  }

  /** {@inheritDoc} It is the name of the place. */
  @Override
  public String toString() {
    return words;
  }
}
