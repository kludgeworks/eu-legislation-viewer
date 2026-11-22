// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Cell;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Deleted;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Edited;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Extract;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Figure;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Footnote;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Inline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Quotation;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Row;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Run;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Run.Op;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Table;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Turns a stored legal text, or a part of it, into the public model ({@link PublicText}).
 *
 * <p>The stored text follows the converter's schema. This class is the one place that knows both
 * forms, so a change of the schema stops here. It keeps the meaning and leaves out the form:
 *
 * <ul>
 *   <li>Occurrences, running text, tables, figures, quotations and deleted text keep their
 *       structure.
 *   <li>Lists, groups of tables, boxes, formulas of the preamble, citations, recitals and the files
 *       of an annex give their blocks to the block around them. Their occurrences keep their kinds,
 *       for example {@code point}, {@code citation} or {@code recital}.
 *   <li>A group of rows in a table gives its heading as a heading row.
 *   <li>A displayed formula is running text, with MathML in its HTML.
 *   <li>The footnotes of the files of an annex join the footnotes of the text.
 * </ul>
 *
 * <p>Running text has two forms. The plain text is the text as printed, without the markers of
 * footnotes and without the words that an amendment deleted. The HTML uses only these elements:
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
public final class PublicTexts {
  private static final String MATHML = "http://www.w3.org/1998/Math/MathML";

  /** The typefaces that have an element of their own in HTML. */
  private static final Map<String, String> TYPEFACES =
      Map.of("b", "b", "i", "i", "u", "u", "sup", "sup", "sub", "sub", "strike", "s");

  /** What an occurrence holds before its content. */
  private static final Set<String> OCCURRENCE_PARTS = Set.of("tag", "num", "heading", "subheading");

  /** The footnotes of the files of an annex, which join the footnotes of the text. */
  private final List<Footnote> footnotes = new ArrayList<>();

  private PublicTexts() {}

  /**
   * @param legalText the element of a text, {@code legalText}
   */
  public static PublicText of(Element legalText) {
    var texts = new PublicTexts();
    Inline title = null;
    List<Block> preamble = List.of();
    List<Block> body = List.of();
    List<Block> conclusions = List.of();
    List<Block> annexes = List.of();
    var footnotes = new ArrayList<Footnote>();
    for (var part : legalText.children()) {
      switch (part.name()) {
        case "preface" -> title = child(part, "longTitle").map(PublicTexts::lines).orElse(null);
        case "preamble" -> preamble = texts.preamble(part);
        case "body" -> body = texts.blocks(part.children());
        case "conclusions" -> conclusions = texts.conclusions(part);
        case "annexes" -> annexes = texts.blocks(part.children());
        case "footnotes" -> footnotes.addAll(texts.footnotes(part));
        default -> {
          // The source and the lifecycle are the version's, which the API gives elsewhere
        }
      }
    }
    footnotes.addAll(texts.footnotes);
    return new PublicText(title, preamble, body, conclusions, annexes, footnotes);
  }

  /**
   * Returns an occurrence of a text, with the footnotes that it refers to. Returns none if the text
   * has no occurrence {@code eId} outside the kept deletions of a consolidation.
   *
   * @param legalText the element of a text, {@code legalText}
   */
  public static Optional<Extract> extract(Element legalText, String eId) {
    return legalText.occurrences(eId, eId).stream()
        .findFirst()
        .map(
            occurrence -> {
              var referred = new LinkedHashSet<String>();
              references(occurrence, referred);
              var all = new LinkedHashMap<String, Footnote>();
              new PublicTexts().allFootnotes(legalText, all);
              return new Extract(
                  occurrence(occurrence),
                  referred.stream().map(all::get).filter(footnote -> footnote != null).toList());
            });
  }

  /**
   * Returns an occurrence on its own, for example an occurrence of a comparison, in which {@code
   * diff:ins} and {@code diff:del} show what changed.
   */
  public static Occurrence occurrence(Element occurrence) {
    return new PublicTexts().toOccurrence(occurrence);
  }

  /**
   * Returns the wording of an occurrence of a comparison, in which {@code diff:ins} and {@code
   * diff:del} show what changed: the plain text before and after the change, and the runs of words
   * that stayed, went in and went out. The lines of the occurrence's paragraphs, and the numbers
   * and headings of the occurrences in it, end with a line feed. The occurrence's own number and
   * heading are not in it.
   */
  public static Edited edited(Element occurrence) {
    var words = new Words();
    lines(content(occurrence), words);
    return new Edited(words.before().strip(), words.after().strip(), words.runs());
  }

  /** Returns what an occurrence holds after its number, headings and tag. */
  private static List<Element> content(Element occurrence) {
    return occurrence.children().stream()
        .filter(child -> !OCCURRENCE_PARTS.contains(child.name()))
        .toList();
  }

  // ------------------------------------------------------------------------------------- parts

  private List<Block> preamble(Element preamble) {
    var blocks = new ArrayList<Block>();
    for (var child : preamble.children()) {
      blocks.addAll(blocks(List.of(child)));
    }
    return blocks;
  }

  private List<Block> conclusions(Element conclusions) {
    var blocks = new ArrayList<Block>();
    for (var child : conclusions.children()) {
      if (child.name().equals("signature")) {
        for (var line : child.children()) {
          if (line.name().equals("placeAndDate")) {
            blocks.add(text(line.children(), List.of()));
          } else {
            line.children().stream()
                .map(PublicTexts::lines)
                .forEach(lines -> blocks.add(new Text(lines.text(), lines.html(), List.of())));
          }
        }
      } else {
        blocks.addAll(blocks(List.of(child)));
      }
    }
    return blocks;
  }

  private List<Footnote> footnotes(Element footnotes) {
    var found = new ArrayList<Footnote>();
    for (var child : footnotes.children()) {
      if (child.name().equals("footnote")) {
        found.add(footnote(child));
      } else {
        // The footnotes of the text that an amendment deleted
        child.children().stream()
            .filter(footnote -> footnote.name().equals("footnote"))
            .forEach(footnote -> found.add(footnote(footnote)));
      }
    }
    return found;
  }

  private Footnote footnote(Element footnote) {
    return new Footnote(
        attribute(footnote, "eId"), attribute(footnote, "marker"), blocks(footnote.children()));
  }

  // ------------------------------------------------------------------------------------ blocks

  private List<Block> blocks(List<Element> elements) {
    var blocks = new ArrayList<Block>();
    for (var element : elements) {
      switch (element.name()) {
        case "p" -> blocks.add(text(element.children(), amendments(element)));
        case "occurrence" -> blocks.add(toOccurrence(element));
        case "table" -> blocks.add(table(element));
        case "figure" -> blocks.add(figure(element));
        case "quotedStructure" ->
            blocks.add(new Quotation(attribute(element, "eId"), blocks(element.children())));
        case "deleted" ->
            blocks.add(new Deleted(amendments(element), blocks(withoutTags(element))));
        case "math" -> blocks.add(math(element));
        case "annotation" -> {
          child(element, "heading")
              .map(PublicTexts::lines)
              .ifPresent(
                  heading -> blocks.add(new Text(heading.text(), heading.html(), List.of())));
          blocks.addAll(blocks(withoutParts(element)));
        }
        case "tableGroup", "figureGroup" -> {
          child(element, "caption").ifPresent(caption -> blocks.addAll(blocks(caption.children())));
          blocks.addAll(blocks(withoutParts(element)));
        }
        case "footnotes" -> footnotes.addAll(footnotes(element));
        case "tag", "num", "heading", "subheading", "caption" -> {
          // The block around them gives them
        }
        default ->
            // A list, a box, a formula of the preamble, citations, recitals, an introduction, the
            // file of an annex, a correction's wording: their blocks join the blocks around them
            blocks.addAll(blocks(element.children()));
      }
    }
    return blocks;
  }

  private Occurrence toOccurrence(Element occurrence) {
    return new Occurrence(
        attribute(occurrence, "eId"),
        attribute(occurrence, "kind"),
        child(occurrence, "num").map(num -> text(num.children())).orElse(null),
        child(occurrence, "heading").map(PublicTexts::lines).orElse(null),
        child(occurrence, "subheading").map(PublicTexts::lines).orElse(null),
        amendments(occurrence),
        blocks(content(occurrence)));
  }

  private Table table(Element table) {
    var rows = new ArrayList<Row>();
    rows(table.children(), rows);
    return new Table(
        blocksOf(table, "caption"),
        rows,
        child(table, "footnotes").map(this::footnotes).orElseGet(List::of),
        amendments(table));
  }

  /** Adds the rows, and the rows of each group after its heading row. */
  private void rows(List<Element> elements, List<Row> rows) {
    for (var element : elements) {
      switch (element.name()) {
        case "tr" ->
            rows.add(
                new Row(
                    "true".equals(element.attribute("header")),
                    element.children().stream()
                        .filter(cell -> cell.name().equals("td"))
                        .map(this::cell)
                        .toList()));
        case "rowGroup" -> {
          var columns = Integer.parseInt(attribute(element, "columns"));
          var num = child(element, "num").map(n -> text(n.children())).orElse(null);
          var heading = child(element, "heading").map(PublicTexts::lines).orElse(null);
          if (num != null || heading != null) {
            rows.add(headingRow(columns, title(num, heading)));
          }
          child(element, "subheading")
              .map(PublicTexts::lines)
              .ifPresent(subheading -> rows.add(headingRow(columns, subheading)));
          rows(element.children(), rows);
        }
        default -> {
          // The caption, the footnotes and the tag are the table's
        }
      }
    }
  }

  private static Row headingRow(int columns, Inline title) {
    return new Row(
        true,
        List.of(
            new Cell(
                true,
                columns > 1 ? columns : null,
                null,
                List.of(new Text(title.text(), title.html(), List.of())))));
  }

  private static Inline title(@Nullable String num, @Nullable Inline heading) {
    if (heading == null) {
      return new Inline(num == null ? "" : num, escape(num == null ? "" : num));
    }
    if (num == null) {
      return heading;
    }
    return new Inline(num + " " + heading.text(), escape(num) + " " + heading.html());
  }

  private Cell cell(Element cell) {
    return new Cell(
        "true".equals(cell.attribute("header")),
        span(cell.attribute("colspan")),
        span(cell.attribute("rowspan")),
        blocks(cell.children()));
  }

  private static @Nullable Integer span(@Nullable String span) {
    return span == null || span.equals("1") ? null : Integer.valueOf(span);
  }

  private Figure figure(Element figure) {
    return new Figure(
        attribute(figure, "src"),
        figure.attribute("content"),
        blocksOf(figure, "caption"),
        blocksOf(figure, "description"),
        amendments(figure));
  }

  private static Text math(Element math) {
    var text = new StringBuilder();
    var html = new StringBuilder();
    for (var child : math.children()) {
      if (child.name().equals("m:math")) {
        mathText(child, text);
        mathml(child, html, true);
      }
    }
    return new Text(text.toString(), html.toString(), amendments(math));
  }

  // ------------------------------------------------------------------------------ running text

  private static Text text(List<Element> inline, List<Amendment> amendedBy) {
    var text = text(inline);
    var html = new StringBuilder();
    html(inline, html);
    return new Text(text, html.toString(), amendedBy);
  }

  /**
   * Returns the text of a title or a heading. Its lines are inline or, where the source has them
   * so, paragraphs, directly or in {@code content}: then the lines are joined by line breaks.
   */
  private static Inline lines(Element lines) {
    var children = child(lines, "content").map(Element::children).orElseGet(lines::children);
    if (children.stream().noneMatch(child -> child.name().equals("p"))) {
      var html = new StringBuilder();
      html(children, html);
      return new Inline(text(children), html.toString());
    }
    var texts =
        new PublicTexts()
            .blocks(children).stream()
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .toList();
    return new Inline(
        String.join("\n", texts.stream().map(Text::text).toList()),
        String.join("<br>", texts.stream().map(Text::html).toList()));
  }

  /** Returns the plain text: what is printed and in force. */
  private static String text(List<Element> inline) {
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
                .forEach(
                    child -> {
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
              .ifPresent(
                  num -> {
                    words(num.children(), Op.EQUAL, words);
                    words.add(Op.EQUAL, " ");
                  });
          for (var title : List.of("heading", "subheading")) {
            child(element, title)
                .ifPresent(
                    heading -> {
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
        case "changed" -> wrap(element, html, "span", amendment(element));
        case "deleted" -> wrap(element, html, "s", amendment(element));
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

  private static String amendment(Element element) {
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
        .forEach(
            attribute ->
                html.append(' ')
                    .append(attribute.getKey())
                    .append("=\"")
                    .append(escape(attribute.getValue()))
                    .append('"'));
    html.append('>').append(escape(element.text()));
    element.children().forEach(child -> mathml(child, html, block));
    html.append("</").append(name).append('>');
  }

  // ---------------------------------------------------------------------------------- helpers

  private static List<Amendment> amendments(Element element) {
    return element.children().stream()
        .filter(child -> child.name().equals("tag"))
        .map(tag -> new Amendment(attribute(tag, "amendingAct"), attribute(tag, "modification")))
        .toList();
  }

  private static List<Element> withoutTags(Element element) {
    return element.children().stream().filter(child -> !child.name().equals("tag")).toList();
  }

  /** Returns the blocks of an element, without its tag, heading and caption. */
  private static List<Element> withoutParts(Element element) {
    return element.children().stream()
        .filter(
            child ->
                !child.name().equals("tag")
                    && !child.name().equals("heading")
                    && !child.name().equals("caption"))
        .toList();
  }

  /** Returns the blocks of the child {@code name} of {@code element}, or none if it has none. */
  private List<Block> blocksOf(Element element, String name) {
    return child(element, name).map(part -> blocks(part.children())).orElseGet(List::of);
  }

  private static Optional<Element> child(Element element, String name) {
    return element.children().stream().filter(child -> child.name().equals(name)).findFirst();
  }

  private static String attribute(Element element, String name) {
    var value = element.attribute(name);
    if (value == null) {
      throw new IllegalArgumentException(element.name() + " has no " + name);
    }
    return value;
  }

  /** Adds the eIds of the footnotes that {@code element} refers to, in order. */
  private static void references(Element element, Set<String> eIds) {
    if (element.name().equals("footnoteRef")) {
      eIds.add(attribute(element, "href").substring(1));
    }
    element.children().forEach(child -> references(child, eIds));
  }

  /** Adds every footnote of the text, wherever it is, by its eId. */
  private void allFootnotes(Element element, Map<String, Footnote> footnotes) {
    if (element.name().equals("footnote")) {
      footnotes.put(attribute(element, "eId"), footnote(element));
      return;
    }
    element.children().forEach(child -> allFootnotes(child, footnotes));
  }

  private static String escape(String text) {
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
