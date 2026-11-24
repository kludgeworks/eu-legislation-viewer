// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalText.attribute;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalText.child;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalText.content;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Edited;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Inline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Run;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Run.Op;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Makes the running text of the public model from the inline elements of a stored text: its plain
 * text, its HTML, and the runs of words of an occurrence of a comparison.
 *
 * <p>The plain text is the text as printed, without the markers of footnotes and without the words
 * that an amendment deleted. The HTML uses only these elements:
 *
 * <ul>
 *   <li>{@code b}, {@code i}, {@code u}, {@code s}, {@code sup} and {@code sub} for typefaces, and
 *       {@code span} with the class {@code uc}, {@code sc}, {@code expanded} or {@code box} for the
 *       others;
 *   <li>{@code time}, with {@code datetime}, for a date;
 *   <li>{@code cite} for a reference to the Official Journal, and {@code a} for a link;
 *   <li>{@code sup} with {@code a}, whose {@code href} is {@code #} and a footnote's eId, for the
 *       marker of a footnote;
 *   <li>{@code br} for a line break, {@code span} with {@code lang} for another language, and
 *       {@code img} with {@code data-file} for an image;
 *   <li>{@code span} with {@code data-act} and {@code data-modification} for words that an amending
 *       act changed, and {@code s} with the same attributes for words that it deleted;
 *   <li>{@code ins} and {@code del} for the edits of a comparison;
 *   <li>{@code math}, in MathML, for a formula.
 * </ul>
 */
final class RunningText {
  private static final String MATHML = "http://www.w3.org/1998/Math/MathML";

  /** The typefaces that have an element of their own in HTML. */
  private static final Map<String, String> TYPEFACES =
      Map.of("b", "b", "i", "i", "u", "u", "sup", "sup", "sub", "sub", "strike", "s");

  private RunningText() {}

  /**
   * Returns the wording of an occurrence of a comparison, in which {@code diff:ins} and {@code
   * diff:del} show what changed. See {@link PublicTexts#edited}.
   */
  static Edited edited(Element occurrence) {
    var words = new Words();
    lines(content(occurrence), words);
    return new Edited(words.before().strip(), words.after().strip(), words.runs());
  }

  /** Returns inline elements as running text, in its two forms. */
  static Inline inline(List<Element> inline) {
    var html = new StringBuilder();
    html(inline, html);
    return new Inline(text(inline), html.toString());
  }

  /** Returns a displayed formula as running text, with MathML in its HTML. */
  static Text formula(Element math, List<Tag> tags) {
    var text = new StringBuilder();
    var html = new StringBuilder();
    for (var child : math.children()) {
      if (child.name().equals("m:math")) {
        mathText(child, text);
        mathml(child, html, true);
      }
    }
    return new Text(text.toString(), html.toString(), tags);
  }

  /** Returns a paragraph of running text, with {@code tags}. */
  static Text text(List<Element> inline, List<Tag> tags) {
    var text = text(inline);
    var html = new StringBuilder();
    html(inline, html);
    return new Text(text, html.toString(), tags);
  }

  /** Returns the plain text: what is printed and in force. */
  static String text(List<Element> inline) {
    var words = new Words();
    words(inline, Op.EQUAL, words);
    return words.after();
  }

  /**
   * Adds the words of running text to {@code words} as {@code op} did them, or as a comparison
   * shows them. Leaves out what is not printed as text, and what is not in force.
   */
  private static void words(List<Element> inline, Op op, Words words) {
    for (var element : inline) {
      switch (element.name()) {
        case "t", "number" -> words.add(op, attribute(element, "v"));
        case "eol" -> words.add(op, "\n");
        case "quote" -> {
          words.add(op, attribute(element, "startQuote"));
          words(element.children(), op, words);
          words.add(op, attribute(element, "endQuote"));
        }
        case "math" ->
            element.children().stream()
                .filter(child -> child.name().equals("m:math"))
                .forEach(child -> {
                  var text = new StringBuilder();
                  mathText(child, text);
                  words.add(op, text.toString());
                });
        case "diff:ins" -> words(element.children(), Op.INSERT, words);
        case "diff:del" -> words(element.children(), Op.DELETE, words);
        case "footnoteRef", "deleted", "img", "tag" -> {
          // Not printed as text, or not in force
        }
        default -> words(element.children(), op, words);
      }
    }
  }

  /**
   * Adds the lines of the blocks to {@code words}: a paragraph, the number and heading of an
   * occurrence, a formula. Each line ends with a line feed.
   */
  private static void lines(List<Element> blocks, Words words) {
    for (var element : blocks) {
      switch (element.name()) {
        case "p" -> {
          words(element.children(), Op.EQUAL, words);
          words.add(Op.EQUAL, "\n");
        }
        case "occurrence" -> {
          child(element, "num")
              .ifPresent(num -> {
                words(num.children(), Op.EQUAL, words);
                words.add(Op.EQUAL, " ");
              });
          for (var title : List.of("heading", "subheading")) {
            child(element, title)
                .ifPresent(heading -> {
                  words(heading.children(), Op.EQUAL, words);
                  words.add(Op.EQUAL, "\n");
                });
          }
          lines(content(element), words);
        }
        case "math" -> {
          words(List.of(element), Op.EQUAL, words);
          words.add(Op.EQUAL, "\n");
        }
        case "deleted", "tag", "footnotes", "caption" -> {
          // Not in force, or not the occurrence's wording
        }
        default -> lines(element.children(), words);
      }
    }
  }

  /**
   * Runs of words, each as a comparison shows it. A run follows a run that is in another edit
   * otherwise.
   */
  private static final class Words {
    private final List<OpenRun> open = new ArrayList<>();

    void add(Op op, String text) {
      if (text.isEmpty()) {
        return;
      }
      var last = open.isEmpty() ? null : open.getLast();
      if (last != null && last.op() == op) {
        last.text().append(text);
      } else {
        open.add(new OpenRun(op, new StringBuilder(text)));
      }
    }

    /** Returns the words before the change: all but the inserted ones. */
    String before() {
      return join(Op.INSERT);
    }

    /** Returns the words after the change: all but the deleted ones. */
    String after() {
      return join(Op.DELETE);
    }

    private String join(Op without) {
      var text = new StringBuilder();
      open.stream().filter(run -> run.op() != without).forEach(run -> text.append(run.text()));
      return text.toString();
    }

    /** Returns the runs, without the line feed that ends the last line. */
    List<Run> runs() {
      var found = new ArrayList<Run>();
      open.forEach(run -> found.add(new Run(run.op(), run.text().toString())));
      if (!found.isEmpty() && found.getLast().text().endsWith("\n")) {
        var last = found.removeLast();
        var text = last.text().substring(0, last.text().length() - 1);
        if (!text.isEmpty()) {
          found.add(new Run(last.op(), text));
        }
      }
      return found;
    }

    /** A run of words, which grows while the next words are in the same edit as it is. */
    private record OpenRun(Op op, StringBuilder text) {}
  }

  private static void html(List<Element> inline, StringBuilder html) {
    for (var element : inline) {
      switch (element.name()) {
        case "t", "number" -> html.append(escape(attribute(element, "v")));
        case "eol" -> html.append("<br>");
        case "emphasis" -> {
          var kind = attribute(element, "kind");
          var typeface = TYPEFACES.get(kind);
          wrap(
              element,
              html,
              typeface == null ? "span" : typeface,
              typeface == null ? " class=\"" + escape(kind) + "\"" : "");
        }
        case "date", "docDate" -> {
          var date = element.attribute("date");
          wrap(element, html, "time", date == null ? "" : " datetime=\"" + escape(date) + "\"");
        }
        case "quote" -> {
          html.append(escape(attribute(element, "startQuote")));
          html(element.children(), html);
          html.append(escape(attribute(element, "endQuote")));
        }
        case "footnoteRef" ->
            html.append("<sup><a href=\"")
                .append(escape(attribute(element, "href")))
                .append("\">")
                .append(escape(attribute(element, "marker")))
                .append("</a></sup>");
        case "ref" -> {
          var href = element.attribute("href");
          if ("uri".equals(element.attribute("kind")) && href != null) {
            wrap(element, html, "a", " href=\"" + escape(href) + "\"");
          } else {
            wrap(element, html, "cite", "");
          }
        }
        case "span" ->
            wrap(element, html, "span", " lang=\"" + escape(attribute(element, "xml:lang")) + "\"");
        case "img" ->
            html.append("<img alt=\"\" data-file=\"")
                .append(escape(attribute(element, "src")))
                .append("\">");
        case "math" ->
            element.children().stream()
                .filter(child -> child.name().equals("m:math"))
                .forEach(child -> mathml(child, html, false));
        case "changed" -> wrap(element, html, "span", tagAttributes(element));
        case "deleted" -> wrap(element, html, "s", tagAttributes(element));
        case "diff:ins" -> wrap(element, html, "ins", "");
        case "diff:del" -> wrap(element, html, "del", "");
        case "tag" -> {
          // The paragraph gives it
        }
        default -> html(element.children(), html);
      }
    }
  }

  private static void wrap(Element element, StringBuilder html, String name, String attributes) {
    html.append('<').append(name).append(attributes).append('>');
    html(element.children(), html);
    html.append("</").append(name).append('>');
  }

  /**
   * Returns the attributes that name the act and the modification of the tag of {@code element}.
   */
  private static String tagAttributes(Element element) {
    return " data-act=\""
        + escape(attribute(element, "amendingAct"))
        + "\" data-modification=\""
        + escape(attribute(element, "modification"))
        + "\"";
  }

  // ----------------------------------------------------------------------------------- MathML

  private static void mathText(Element math, StringBuilder text) {
    text.append(math.text());
    math.children().forEach(child -> mathText(child, text));
  }

  private static void mathml(Element element, StringBuilder html, boolean block) {
    var name = element.name().substring(element.name().indexOf(':') + 1);
    html.append('<').append(name);
    if (name.equals("math")) {
      html.append(" xmlns=\"").append(MATHML).append('"');
      if (block && !element.attributes().containsKey("display")) {
        html.append(" display=\"block\"");
      }
    }
    element.attributes().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(attribute ->
            html.append(' ')
                .append(attribute.getKey())
                .append("=\"")
                .append(escape(attribute.getValue()))
                .append('"'));
    html.append('>').append(escape(element.text()));
    element.children().forEach(child -> mathml(child, html, block));
    html.append("</").append(name).append('>');
  }

  /** Returns {@code text} with the characters that HTML gives a meaning escaped. */
  static String escape(String text) {
    var escaped = new StringBuilder(text.length());
    for (var i = 0; i < text.length(); i++) {
      var c = text.charAt(i);
      switch (c) {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '"' -> escaped.append("&quot;");
        default -> escaped.append(c);
      }
    }
    return escaped.toString();
  }
}
