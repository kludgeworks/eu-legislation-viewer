// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import gg.jte.Content;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A legal text as HTML: each element by its template, chosen by convention (Rendering and UI), as
 * XSLT's templates match: legal/{parent}/{name}.jte if there is one (a num in a p, inline, against
 * one of a unit's, a block), else legal/{name}.jte; a MathML element's name by its prefix's folder
 * (m:mi is legal/m/mi.jte). A template takes its element (node) and this (html), and holds markup
 * only, recursing with {@code ${html.children(node)}}; the text it writes, jte escapes. Adding an
 * element to legal-text is adding its template, which the coverage test asks for.
 */
@Component
public class LegalRenderer {
  private final TemplateEngine templates;
  private final Map<String, String> chosen = new ConcurrentHashMap<>();

  LegalRenderer(TemplateEngine templates) {
    this.templates = templates;
  }

  /** {@code element}, with everything in it: a text's element, legalText, is the whole text. */
  public Content element(Element element) {
    return out -> render(element, null, out);
  }

  /** {@code element}'s children, each by its template, in order. */
  public Content children(Element element) {
    return out -> element.children().forEach(child -> render(child, element.name(), out));
  }

  /**
   * {@code element}'s children named {@code names}, in order: where a template puts some apart from
   * the rest (a row group's heading row, its num, heading and subheading, before its rows).
   */
  public Content children(Element element, String... names) {
    var named = Set.of(names);
    return out ->
        element.children().stream()
            .filter(child -> named.contains(child.name()))
            .forEach(child -> render(child, element.name(), out));
  }

  /** Whether {@code element} has any child named {@code names}. */
  public boolean has(Element element, String... names) {
    var named = Set.of(names);
    return element.children().stream().anyMatch(child -> named.contains(child.name()));
  }

  /**
   * How many columns {@code group}'s rows span, the widest of them (each cell its colspan), nested
   * groups' included: what its heading row's cell spans, to be as wide as they are. At least 1.
   */
  public int columns(Element group) {
    var widest = 1;
    for (var child : group.children()) {
      var width =
          switch (child.name()) {
            case "tr" ->
                child.children().stream()
                    .filter(cell -> cell.name().equals("td"))
                    .mapToInt(
                        cell -> Integer.parseInt(cell.attributes().getOrDefault("colspan", "1")))
                    .sum();
            case "rowGroup" -> columns(child);
            default -> 0;
          };
      widest = Math.max(widest, width);
    }
    return widest;
  }

  /**
   * Whether {@code unit} is numbered, its number its label (1.0.1, (a)), as its eId's last step
   * says: pnt_ and its number, against a label's (level_PART-1) or a position's (level_2).
   */
  public boolean numbered(Element unit) {
    var eId = unit.attribute("eId");
    return eId != null && eId.substring(eId.lastIndexOf('/') + 1).startsWith("pnt_");
  }

  private void render(Element element, @Nullable String parent, TemplateOutput out) {
    templates.render(template(parent, element.name()), Map.of("node", element, "html", this), out);
  }

  private String template(@Nullable String parent, String name) {
    return chosen.computeIfAbsent(
        parent + " " + name,
        key -> {
          if (parent != null) {
            var inParent = template(path(parent) + "/" + path(name));
            if (templates.hasTemplate(inParent)) {
              return inParent;
            }
          }
          return template(path(name));
        });
  }

  /** The template of an element named {@code name} anywhere: legal/p.jte, legal/m/mi.jte. */
  static String template(String path) {
    return "legal/" + path + ".jte";
  }

  /** An element's name as a path: its own (p), or its prefix's folder (m:mi is m/mi). */
  static String path(String name) {
    return name.replace(':', '/');
  }
}
