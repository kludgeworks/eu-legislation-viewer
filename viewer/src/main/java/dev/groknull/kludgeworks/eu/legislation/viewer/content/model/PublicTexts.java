// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalText.attribute;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalText.child;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalText.content;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Block;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Cell;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Edited;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Figure;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Footnote;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Inline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.KeptDeletion;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.OccurrenceWithFootnotes;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Quotation;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Row;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Table;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.PublicText.Tag;
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
 *   <li>Occurrences, running text, tables, figures, quotations and kept deletions keep their
 *       structure.
 *   <li>Lists, groups of tables, boxes, formulas of the preamble, citations, recitals and the files
 *       of an annex give their blocks to the block around them. Their occurrences keep their kinds,
 *       for example {@code point}, {@code citation} or {@code recital}.
 *   <li>A group of rows in a table gives its heading as a heading row.
 *   <li>A displayed formula is running text, with MathML in its HTML.
 *   <li>The footnotes of the files of an annex join the footnotes of the text.
 * </ul>
 *
 * <p>Running text, in its two forms, is {@link RunningText}'s.
 */
public final class PublicTexts {
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
  public static Optional<OccurrenceWithFootnotes> occurrenceWithFootnotes(
      Element legalText, String eId) {
    return legalText.occurrences(eId, eId).stream()
        .findFirst()
        .map(occurrence -> {
          var referred = new LinkedHashSet<String>();
          references(occurrence, referred);
          var all = new LinkedHashMap<String, Footnote>();
          new PublicTexts().allFootnotes(legalText, all);
          return new OccurrenceWithFootnotes(
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
    return RunningText.edited(occurrence);
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
            blocks.add(RunningText.text(line.children(), List.of()));
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
        case "p" -> blocks.add(RunningText.text(element.children(), tags(element)));
        case "occurrence" -> blocks.add(toOccurrence(element));
        case "table" -> blocks.add(table(element));
        case "figure" -> blocks.add(figure(element));
        case "quotedStructure" ->
            blocks.add(new Quotation(attribute(element, "eId"), blocks(element.children())));
        case "deleted" -> blocks.add(new KeptDeletion(tags(element), blocks(withoutTags(element))));
        case "math" -> blocks.add(RunningText.formula(element, tags(element)));
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
        child(occurrence, "num").map(num -> RunningText.text(num.children())).orElse(null),
        child(occurrence, "heading").map(PublicTexts::lines).orElse(null),
        child(occurrence, "subheading").map(PublicTexts::lines).orElse(null),
        tags(occurrence),
        blocks(content(occurrence)));
  }

  private Table table(Element table) {
    var rows = new ArrayList<Row>();
    rows(table.children(), rows);
    return new Table(
        blocksOf(table, "caption"),
        rows,
        child(table, "footnotes").map(this::footnotes).orElseGet(List::of),
        tags(table));
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
          var num = child(element, "num").map(n -> RunningText.text(n.children())).orElse(null);
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
      return new Inline(num == null ? "" : num, RunningText.escape(num == null ? "" : num));
    }
    if (num == null) {
      return heading;
    }
    return new Inline(num + " " + heading.text(), RunningText.escape(num) + " " + heading.html());
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
        tags(figure));
  }

  /**
   * Returns the text of a title or a heading. Its lines are inline or, where the source has them
   * so, paragraphs, directly or in {@code content}: then the lines are joined by line breaks.
   */
  private static Inline lines(Element lines) {
    var children = child(lines, "content").map(Element::children).orElseGet(lines::children);
    if (children.stream().noneMatch(child -> child.name().equals("p"))) {
      return RunningText.inline(children);
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

  // ---------------------------------------------------------------------------------- helpers

  private static List<Tag> tags(Element element) {
    return element.children().stream()
        .filter(child -> child.name().equals("tag"))
        .map(tag -> new Tag(attribute(tag, "amendingAct"), attribute(tag, "modification")))
        .toList();
  }

  private static List<Element> withoutTags(Element element) {
    return element.children().stream().filter(child -> !child.name().equals("tag")).toList();
  }

  /** Returns the blocks of an element, without its tag, heading and caption. */
  private static List<Element> withoutParts(Element element) {
    return element.children().stream()
        .filter(child ->
            !child.name().equals("tag")
                && !child.name().equals("heading")
                && !child.name().equals("caption"))
        .toList();
  }

  /** Returns the blocks of the child {@code name} of {@code element}, or none if it has none. */
  private List<Block> blocksOf(Element element, String name) {
    return child(element, name).map(part -> blocks(part.children())).orElseGet(List::of);
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
}
