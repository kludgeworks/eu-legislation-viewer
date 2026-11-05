// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import gg.jte.Content;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A legal text as HTML: each element by its template, chosen by convention (Rendering and UI), as
 * XSLT's templates match: legal/{parent}/{name}.jte if there is one (the deleted notes in notes,
 * against deleted blocks), else legal/{name}.jte; a MathML element's name by its prefix's folder
 * (m:mi is legal/m/mi.jte). A template takes its element (node) and this (html), and holds markup
 * only, recursing with {@code ${html.children(node)}}; the text it writes, jte escapes. Adding an
 * element to legal-text is adding its template, which the coverage test asks for.
 *
 * <p>A page can have some units rendered otherwise ({@link #instead}): a comparison's Whole text
 * has each that differs marked, with its sidenote, the rest as they read.
 */
@Component
public class LegalRenderer {
  private static final Pattern WHOLE = Pattern.compile("[1-9][0-9]{3,}");

  private final TemplateEngine templates;
  private final Map<String, String> chosen;
  private final Map<String, Otherwise> instead;

  @Autowired
  LegalRenderer(TemplateEngine templates) {
    this(templates, new ConcurrentHashMap<>(), Map.of());
  }

  private LegalRenderer(
      TemplateEngine templates, Map<String, String> chosen, Map<String, Otherwise> instead) {
    this.templates = templates;
    this.chosen = chosen;
    this.instead = Map.copyOf(instead);
  }

  /**
   * This, but for the units whose eIds {@code instead} has, each rendered as it says: wherever one
   * is, in whatever this renders.
   */
  public LegalRenderer instead(Map<String, Otherwise> instead) {
    return new LegalRenderer(templates, chosen, instead);
  }

  /**
   * How a unit is rendered otherwise: given the unit and the renderer rendering it, which can
   * render it as it is ({@link #asIs}), and what is in it, within what it renders instead.
   */
  @FunctionalInterface
  public interface Otherwise {
    Content render(Element unit, LegalRenderer html);
  }

  /** {@code element} by its template, even where it is to be rendered otherwise: its own. */
  public Content asIs(Element element) {
    return out -> template(element, null, out);
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

  /**
   * {@code element}'s children not named {@code names}, in order: the rest, once a template has put
   * those apart (a deletion's content, its provenance in its mark).
   */
  public Content others(Element element, String... names) {
    var named = Set.of(names);
    return out ->
        element.children().stream()
            .filter(child -> !named.contains(child.name()))
            .forEach(child -> render(child, element.name(), out));
  }

  /**
   * A number (as written: 6000) as EUR-Lex prints it: a whole number of four digits or more with
   * its thousands set apart by a no-break space (6 000); any other as written (250, 1.5).
   */
  public String thousands(String number) {
    if (!WHOLE.matcher(number).matches()) {
      return number;
    }
    var out = new StringBuilder(number);
    for (var at = number.length() - 3; at > 0; at -= 3) {
      out.insert(at, '\u00A0');
    }
    return out.toString();
  }

  /** Whether {@code element} has any child named {@code names}. */
  public boolean has(Element element, String... names) {
    var named = Set.of(names);
    return element.children().stream().anyMatch(child -> named.contains(child.name()));
  }

  private void render(Element element, @Nullable String parent, TemplateOutput out) {
    var eId = element.name().equals("unit") ? element.attribute("eId") : null;
    var otherwise = eId == null ? null : instead.get(eId);
    if (otherwise != null) {
      otherwise.render(element, this).writeTo(out);
    } else {
      template(element, parent, out);
    }
  }

  private void template(Element element, @Nullable String parent, TemplateOutput out) {
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
