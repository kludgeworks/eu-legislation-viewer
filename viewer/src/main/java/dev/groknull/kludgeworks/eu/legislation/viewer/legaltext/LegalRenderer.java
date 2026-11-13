// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

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
 * Renders a legal text as HTML. It renders each element with its template. The template is chosen
 * by convention, in the way that XSLT templates match:
 *
 * <ul>
 *   <li>{@code legal/{parent}/{name}.jte}, if there is one. For example, the deleted notes in notes
 *       have a template different from that of deleted blocks.
 *   <li>Otherwise {@code legal/{name}.jte}.
 *   <li>A MathML element uses the folder of its prefix. For example, {@code m:mi} uses {@code
 *       legal/m/mi.jte}.
 * </ul>
 *
 * <p>A template takes its element as {@code node} and this renderer as {@code html}. It holds only
 * markup, and recurses with {@code ${html.children(node)}}. jte escapes the text that it writes. To
 * add an element to legal-text, add its template. The coverage test asks for it.
 *
 * <p>A page can render some units in a different way, with {@link #instead}. For example, the Whole
 * text of a comparison marks each unit that differs and gives it a sidenote. The other units show
 * as they read.
 */
@Component
public class LegalRenderer {
  private static final Pattern WHOLE = Pattern.compile("[1-9][0-9]{3,}");

  /** The parent of an element at the top of what is rendered, as {@link #chosen} keys it. */
  private static final String NO_PARENT = "";

  private final TemplateEngine templates;

  /** The template chosen for each element name, then for each parent name. */
  private final Map<String, Map<String, String>> chosen;

  private final Map<String, Otherwise> instead;

  @Autowired
  LegalRenderer(TemplateEngine templates) {
    this(templates, new ConcurrentHashMap<>(), Map.of());
  }

  private LegalRenderer(
      TemplateEngine templates,
      Map<String, Map<String, String>> chosen,
      Map<String, Otherwise> instead) {
    this.templates = templates;
    this.chosen = chosen;
    this.instead = Map.copyOf(instead);
  }

  /**
   * Returns a copy of this renderer that renders the units whose eIds are in {@code instead} as
   * {@code instead} says. This applies wherever such a unit is, in all that the copy renders.
   */
  public LegalRenderer instead(Map<String, Otherwise> instead) {
    return new LegalRenderer(templates, chosen, instead);
  }

  /**
   * A different way to render a unit. It gets the unit and the renderer that renders it. Within
   * what it renders instead, the renderer can render the unit as it is ({@link #asIs}) and what is
   * in the unit.
   */
  @FunctionalInterface
  public interface Otherwise {
    Content render(Element unit, LegalRenderer html);
  }

  /**
   * Renders {@code element} with its own template, even if it is to be rendered in a different way.
   */
  public Content asIs(Element element) {
    return out -> template(element, null, out);
  }

  /**
   * Renders {@code element} with everything in it. The {@code legalText} element of a text renders
   * the whole text.
   */
  public Content element(Element element) {
    return out -> render(element, null, out);
  }

  /** Renders the children of {@code element} in order, each with its template. */
  public Content children(Element element) {
    return out -> element.children().forEach(child -> render(child, element.name(), out));
  }

  /**
   * Renders the children of {@code element} named {@code names}, in order. A template uses this to
   * put some children apart from the rest. For example, the heading row of a row group shows its
   * num, heading and subheading before its rows.
   */
  public Content children(Element element, String... names) {
    var named = Set.of(names);
    return out ->
        element.children().stream()
            .filter(child -> named.contains(child.name()))
            .forEach(child -> render(child, element.name(), out));
  }

  /**
   * Renders the children of {@code element} not named {@code names}, in order. These are the rest,
   * after a template put the named ones apart. For example, the content of a deletion, after its
   * provenance went in its mark.
   */
  public Content others(Element element, String... names) {
    var named = Set.of(names);
    return out ->
        element.children().stream()
            .filter(child -> !named.contains(child.name()))
            .forEach(child -> render(child, element.name(), out));
  }

  /**
   * Returns a number, written as in the text, in the form that EUR-Lex prints it.
   *
   * <ul>
   *   <li>A whole number of four digits or more gets a no-break space between its thousands. For
   *       example, "6000" becomes "6 000".
   *   <li>Any other number stays as written. For example, "250" or "1.5".
   * </ul>
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

  /** Returns whether {@code element} has a child with one of the names {@code names}. */
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
    return chosen
        .computeIfAbsent(name, key -> new ConcurrentHashMap<>())
        .computeIfAbsent(
            parent == null ? NO_PARENT : parent,
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

  /**
   * Returns the template for an element at {@code path}, wherever the element is. For example,
   * {@code legal/p.jte} or {@code legal/m/mi.jte}.
   */
  static String template(String path) {
    return "legal/" + path + ".jte";
  }

  /**
   * Returns the name of an element as a path. A name without a prefix stays the same, for example
   * {@code p}. A prefix becomes a folder, for example {@code m:mi} becomes {@code m/mi}.
   */
  static String path(String name) {
    return name.replace(':', '/');
  }
}
