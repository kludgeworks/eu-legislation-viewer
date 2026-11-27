// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.legaltext;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.ImageUrls;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import gg.jte.Content;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import java.util.Map;
import java.util.Objects;
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
 *   <li>{@code legal/{parent}/{name}.jte}, if there is one. For example, the deleted footnotes in
 *       {@code footnotes} have a template different from that of deleted blocks.
 *   <li>Otherwise {@code legal/{name}.jte}.
 *   <li>A MathML element uses the folder of its prefix. For example, {@code m:mi} uses {@code
 *       legal/m/mi.jte}.
 * </ul>
 *
 * <p>A template takes its element as {@code node} and this renderer as {@code html}. It holds only
 * markup, and recurses with {@code ${html.children(node)}}. jte escapes the text that it writes. To
 * add an element to legal-text, add its template. The coverage test asks for it.
 *
 * <p>A page can render some occurrences in a different way, with {@link #instead}. For example, the
 * Whole text of a comparison shows the edits of each occurrence that differs and gives it a
 * sidenote. The other occurrences show as they read.
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
  private final @Nullable Images images;
  private final @Nullable TextId text;
  private final Set<String> activeFiles;
  private final @Nullable TextId deleted;
  private final Set<String> deletedFiles;
  private final @Nullable String imageVersion;

  LegalRenderer(TemplateEngine templates) {
    this(
        templates, new ConcurrentHashMap<>(), Map.of(), null, null, Set.of(), null, Set.of(), null);
  }

  @Autowired
  LegalRenderer(TemplateEngine templates, Images images) {
    this(
        templates,
        new ConcurrentHashMap<>(),
        Map.of(),
        images,
        null,
        Set.of(),
        null,
        Set.of(),
        null);
  }

  private LegalRenderer(
      TemplateEngine templates,
      Map<String, Map<String, String>> chosen,
      Map<String, Otherwise> instead,
      @Nullable Images images,
      @Nullable TextId text,
      Set<String> activeFiles,
      @Nullable TextId deleted,
      Set<String> deletedFiles,
      @Nullable String imageVersion) {
    this.templates = templates;
    this.chosen = chosen;
    this.instead = Map.copyOf(instead);
    this.images = images;
    this.text = text;
    this.activeFiles = Set.copyOf(activeFiles);
    this.deleted = deleted;
    this.deletedFiles = Set.copyOf(deletedFiles);
    this.imageVersion = imageVersion;
  }

  /**
   * Returns a copy of this renderer that renders the occurrences whose eIds are in {@code instead}
   * as {@code instead} says. This applies wherever such an occurrence is, in all that the copy
   * renders.
   */
  public LegalRenderer instead(Map<String, Otherwise> instead) {
    return new LegalRenderer(
        templates, chosen, instead, images, text, activeFiles, deleted, deletedFiles, imageVersion);
  }

  /** Returns a copy of this renderer bound to {@code text} for registered images. */
  public LegalRenderer forText(TextId text) {
    if (images == null) {
      return this;
    }
    var files = images.sourceUris(text).keySet();
    return forText(text, files, images.version());
  }

  /** Returns a copy of this renderer bound to {@code text} with explicit {@code availableFiles}. */
  LegalRenderer forText(TextId text, Set<String> availableFiles, String imageVersion) {
    return new LegalRenderer(
        templates,
        chosen,
        instead,
        images,
        text,
        availableFiles,
        text,
        availableFiles,
        imageVersion);
  }

  /**
   * Returns a copy of this renderer bound to a comparison from {@code before} to {@code after}.
   * Deleted images use {@code before}, and other images use {@code after}.
   */
  public LegalRenderer forComparison(TextId before, TextId after) {
    if (images == null) {
      return this;
    }
    var beforeFiles = images.sourceUris(before).keySet();
    var afterFiles = images.sourceUris(after).keySet();
    return forComparison(before, beforeFiles, after, afterFiles, images.version());
  }

  /** Returns a copy bound to explicit file sets for {@code before} and {@code after}. */
  LegalRenderer forComparison(
      TextId before,
      Set<String> beforeFiles,
      TextId after,
      Set<String> afterFiles,
      String imageVersion) {
    return new LegalRenderer(
        templates, chosen, instead, images, after, afterFiles, before, beforeFiles, imageVersion);
  }

  /** Returns a copy of this renderer whose active text is the deleted-side text. */
  public LegalRenderer deleted() {
    if (deleted == null || (deleted.equals(text) && deletedFiles.equals(activeFiles))) {
      return this;
    }
    return new LegalRenderer(
        templates,
        chosen,
        instead,
        images,
        deleted,
        deletedFiles,
        deleted,
        deletedFiles,
        imageVersion);
  }

  /**
   * Returns the viewer address of the preview image for {@code src}, or null if no registered image
   * exists for that name in the active text.
   */
  public @Nullable String imagePreviewUrl(@Nullable String src) {
    if (text == null || src == null || src.isBlank() || !activeFiles.contains(src)) {
      return null;
    }
    return imageVersion == null
        ? ImageUrls.preview(text, src)
        : ImageUrls.preview(text, src, imageVersion);
  }

  /** Returns non-empty alternative text for a figure or inline graphic element. */
  public String imageAlt(Element node) {
    var content = Objects.requireNonNullElse(node.attribute("content"), "");
    return switch (content) {
      case "character" -> "Character";
      case "form" -> "Form";
      case "formula" -> "Formula";
      case "signature" -> "Signature";
      case "techDesign" -> "Technical diagram";
      default -> node.name().equals("figure") ? "Figure" : "Image";
    };
  }

  /**
   * A different way to render an occurrence. It gets the occurrence and the renderer that renders
   * it. Within what it renders instead, the renderer can render the occurrence as it is ({@link
   * #asIs}) and what is in the occurrence.
   */
  @FunctionalInterface
  public interface Otherwise {
    Content render(Element occurrence, LegalRenderer html);
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
   * tags went into its ▼ sign.
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
    var eId = element.name().equals("occurrence") ? element.attribute("eId") : null;
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
