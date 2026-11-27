// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * A legal text in the public model: the form in which the API gives a text to other programs.
 *
 * <p>The public model is small, and it is not the form in which the text is stored. The stored form
 * follows the converter's schema, which changes as the converter learns more of Formex. The public
 * model changes only when the meaning of a text changes. So a client depends on the public model,
 * never on the schema. {@code api/schemas/v1/text.schema.json} is its JSON Schema.
 *
 * <ul>
 *   <li>The text is in parts, as the Official Journal prints it: the title, the preamble, the
 *       enacting terms (the body), the conclusions, the annexes and the footnotes.
 *   <li>A part holds blocks: running text, occurrences, tables, figures, quotations and kept
 *       deletions.
 *   <li>Running text has two forms: plain {@code text}, and {@code html} in a closed list of
 *       elements.
 * </ul>
 *
 * @param title the long title. Absent if the text has none.
 * @param preamble the preamble: the formulas, the citations and the recitals
 * @param body the enacting terms
 * @param conclusions the closing formula and the signatures
 * @param annexes the annexes and the documents attached to the act
 * @param footnotes the footnotes, from wherever they are in the text
 */
@ValueObject
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PublicText(
    @Nullable Inline title,
    List<Block> preamble,
    List<Block> body,
    List<Block> conclusions,
    List<Block> annexes,
    List<Footnote> footnotes) {

  public PublicText {
    preamble = List.copyOf(preamble);
    body = List.copyOf(body);
    conclusions = List.copyOf(conclusions);
    annexes = List.copyOf(annexes);
    footnotes = List.copyOf(footnotes);
  }

  /**
   * Running text, in two forms.
   *
   * @param text the plain text, as printed. It leaves out the markers of footnotes and the text
   *     that an amendment deleted.
   * @param html the same text as HTML, in the closed list of elements of the public model
   */
  @ValueObject
  public record Inline(String text, String html) {}

  /** A block: a thing that stands on its own lines. Its {@code type} tells which. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Text.class, name = "text"),
    @JsonSubTypes.Type(value = Occurrence.class, name = "occurrence"),
    @JsonSubTypes.Type(value = Table.class, name = "table"),
    @JsonSubTypes.Type(value = Figure.class, name = "figure"),
    @JsonSubTypes.Type(value = Quotation.class, name = "quotation"),
    @JsonSubTypes.Type(value = KeptDeletion.class, name = "keptDeletion")
  })
  public sealed interface Block permits Text, Occurrence, Table, Figure, Quotation, KeptDeletion {}

  /**
   * A paragraph of running text.
   *
   * @param text the plain text
   * @param html the text as HTML
   * @param tags the tags of the acts that gave the paragraph its wording, if they are not its
   *     occurrence's
   */
  @ValueObject
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Text(
      @JsonInclude(JsonInclude.Include.ALWAYS) String text,
      @JsonInclude(JsonInclude.Include.ALWAYS) String html,
      List<Tag> tags)
      implements Block {
    public Text {
      tags = List.copyOf(tags);
    }
  }

  /**
   * An occurrence: an article, a paragraph, a point, an annex and so on.
   *
   * @param eId its identifier in the text. For example, {@code art_3/par_2/pnt_a}.
   * @param kind what it is. For example, {@code article} or {@code point}. The list can grow.
   * @param num its label as printed. For example, {@code Article 3} or {@code (a)}. Absent if it
   *     has none.
   * @param heading its heading. Absent if it has none.
   * @param subheading a second heading, under the first. Absent if it has none.
   * @param tags the tags of the acts that gave the occurrence its wording, the outermost first
   * @param content what it holds, in order
   */
  @ValueObject
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Occurrence(
      String eId,
      String kind,
      @Nullable String num,
      @Nullable Inline heading,
      @Nullable Inline subheading,
      List<Tag> tags,
      List<Block> content)
      implements Block {
    public Occurrence {
      tags = List.copyOf(tags);
      content = List.copyOf(content);
    }
  }

  /**
   * A table.
   *
   * @param caption its title
   * @param rows its rows, in order. A group of rows starts with its heading row.
   * @param footnotes the footnotes of the table, numbered in it
   * @param tags the tags of the acts that gave the table its wording, if they are not its
   *     occurrence's
   */
  @ValueObject
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Table(List<Block> caption, List<Row> rows, List<Footnote> footnotes, List<Tag> tags)
      implements Block {
    public Table {
      caption = List.copyOf(caption);
      rows = List.copyOf(rows);
      footnotes = List.copyOf(footnotes);
      tags = List.copyOf(tags);
    }
  }

  /**
   * A row of a table.
   *
   * @param header whether it is a heading row
   * @param cells its cells, in order
   */
  @ValueObject
  public record Row(
      @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean header, List<Cell> cells) {
    public Row {
      cells = List.copyOf(cells);
    }
  }

  /**
   * A cell of a table.
   *
   * @param header whether it is a heading cell
   * @param colspan the number of columns it spans, if more than one
   * @param rowspan the number of rows it spans, if more than one
   * @param content what it holds. Empty for an empty cell.
   */
  @ValueObject
  public record Cell(
      @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean header,
      @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer colspan,
      @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer rowspan,
      List<Block> content) {
    public Cell {
      content = List.copyOf(content);
    }
  }

  /**
   * An image.
   *
   * @param file the image file, as the Publications Office names it in the act's files
   * @param shows what the image shows: {@code character}, {@code form}, {@code formula}, {@code
   *     signature}, {@code techDesign} or {@code other}. Absent if the source does not say.
   * @param caption the text printed with the image
   * @param description the text in the image, written out
   * @param tags the tags of the acts that gave the image its form, if they are not its occurrence's
   */
  @ValueObject
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Figure(
      String file,
      @Nullable String shows,
      List<Block> caption,
      List<Block> description,
      List<Tag> tags)
      implements Block {
    public Figure {
      caption = List.copyOf(caption);
      description = List.copyOf(description);
      tags = List.copyOf(tags);
    }
  }

  /**
   * Text that an amending act quotes: the occurrences that it inserts or replaces in another act.
   * It is not law of this act.
   *
   * @param eId its identifier in the text
   * @param content the quoted blocks
   */
  @ValueObject
  public record Quotation(String eId, List<Block> content) implements Block {
    public Quotation {
      content = List.copyOf(content);
    }
  }

  /**
   * A kept deletion: blocks that an act deleted, which the consolidated version still shows under
   * the tag of that act. They are not in force.
   *
   * @param tags the tags of the acts that deleted them
   * @param content the deleted blocks
   */
  @ValueObject
  public record KeptDeletion(List<Tag> tags, List<Block> content) implements Block {
    public KeptDeletion {
      tags = List.copyOf(tags);
      content = List.copyOf(content);
    }
  }

  /**
   * A footnote.
   *
   * @param eId its identifier in the text. For example, {@code note_3}. The {@code href} of a
   *     footnote marker in the HTML is {@code #} and this eId.
   * @param marker its marker as printed. For example, {@code 3} or {@code *}.
   * @param content what it says
   */
  @ValueObject
  public record Footnote(String eId, String marker, List<Block> content) {
    public Footnote {
      content = List.copyOf(content);
    }
  }

  /**
   * A tag of a consolidated version: the act that gave the text its wording, and what it did.
   *
   * @param act the CELEX number of the amending act
   * @param modification what it did: {@code replaced}, {@code inserted}, {@code deleted} or {@code
   *     renumbered}
   */
  @ValueObject
  public record Tag(String act, String modification) {}

  /**
   * The wording of an occurrence with the edits of a comparison: the plain text before and after
   * the change, and the runs of words that stayed, went in and went out.
   *
   * @param before the text before the change
   * @param after the text after the change
   * @param runs the runs, in order. The text before is the runs that are not inserted, and the text
   *     after is the runs that are not deleted. Each paragraph, and each number and heading of an
   *     occurrence in the occurrence, ends with a line feed, except the last.
   */
  @ValueObject
  public record Edited(String before, String after, List<Run> runs) {
    public Edited {
      runs = List.copyOf(runs);
    }
  }

  /**
   * A run of words, and what a comparison found of it.
   *
   * @param op {@code equal}, {@code insert} or {@code delete}
   * @param text the words
   */
  @ValueObject
  public record Run(Op op, String text) {

    /** What a comparison found of a run of words. */
    public enum Op {
      /** The run is in both versions. */
      @JsonProperty("equal")
      EQUAL,
      /** The run is only in the later version. */
      @JsonProperty("insert")
      INSERT,
      /** The run is only in the earlier version. */
      @JsonProperty("delete")
      DELETE
    }
  }

  /**
   * An occurrence and the footnotes that it refers to: what the API gives for one occurrence. The
   * footnotes are at the end of the text, so they are not in the occurrence.
   *
   * @param occurrence the occurrence
   * @param footnotes the footnotes that the occurrence refers to, in the order of the text
   */
  @ValueObject
  public record OccurrenceWithFootnotes(
      Occurrence occurrence, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Footnote> footnotes) {
    private static final Pattern IMAGE_FILE = Pattern.compile("data-file=\"([^\"]+)\"");

    public OccurrenceWithFootnotes {
      footnotes = List.copyOf(footnotes);
    }

    /**
     * Returns the image file names referenced anywhere in this occurrence or its footnotes, in
     * encounter order.
     */
    public Set<String> imageFiles() {
      var found = new LinkedHashSet<String>();
      collectFromInline(occurrence.heading(), found);
      collectFromInline(occurrence.subheading(), found);
      collectFromBlocks(occurrence.content(), found);
      for (var footnote : footnotes) {
        collectFromBlocks(footnote.content(), found);
      }
      return Collections.unmodifiableSet(found);
    }

    private static void collectFromInline(@Nullable Inline inline, Set<String> found) {
      if (inline == null) {
        return;
      }
      var matcher = IMAGE_FILE.matcher(inline.html());
      while (matcher.find()) {
        found.add(matcher.group(1));
      }
    }

    private static void collectFromBlocks(List<Block> blocks, Set<String> found) {
      for (var each : blocks) {
        switch (each) {
          case Text running -> {
            var matcher = IMAGE_FILE.matcher(running.html());
            while (matcher.find()) {
              found.add(matcher.group(1));
            }
          }
          case Occurrence nested -> {
            collectFromInline(nested.heading(), found);
            collectFromInline(nested.subheading(), found);
            collectFromBlocks(nested.content(), found);
          }
          case Table table -> {
            collectFromBlocks(table.caption(), found);
            table.footnotes().forEach(fn -> collectFromBlocks(fn.content(), found));
            table
                .rows()
                .forEach(row -> row.cells().forEach(c -> collectFromBlocks(c.content(), found)));
          }
          case Figure figure -> {
            found.add(figure.file());
            collectFromBlocks(figure.caption(), found);
            collectFromBlocks(figure.description(), found);
          }
          case Quotation quotation -> collectFromBlocks(quotation.content(), found);
          case KeptDeletion deletion -> collectFromBlocks(deletion.content(), found);
        }
      }
    }
  }
}
