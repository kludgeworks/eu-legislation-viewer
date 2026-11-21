// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
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
 *       enacting terms (the body), the conclusions, the annexes and the notes.
 *   <li>A part holds blocks: running text, occurrences, tables, figures, quotations and deleted
 *       text.
 *   <li>Running text has two forms: plain {@code text}, and {@code html} in a closed list of
 *       elements.
 * </ul>
 *
 * @param title the long title. Absent if the text has none.
 * @param preamble the preamble: the formulas, the citations and the recitals
 * @param body the enacting terms
 * @param conclusions the closing formula and the signatures
 * @param annexes the annexes and the documents attached to the act
 * @param notes the footnotes, from wherever they are in the text
 */
@ValueObject
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PublicText(
    @Nullable Inline title,
    List<Block> preamble,
    List<Block> body,
    List<Block> conclusions,
    List<Block> annexes,
    List<Note> notes) {

  public PublicText {
    preamble = List.copyOf(preamble);
    body = List.copyOf(body);
    conclusions = List.copyOf(conclusions);
    annexes = List.copyOf(annexes);
    notes = List.copyOf(notes);
  }

  /**
   * Running text, in two forms.
   *
   * @param text the plain text, as printed. It leaves out the markers of notes and the text that an
   *     amendment deleted.
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
    @JsonSubTypes.Type(value = Deleted.class, name = "deleted")
  })
  public sealed interface Block permits Text, Occurrence, Table, Figure, Quotation, Deleted {}

  /**
   * A paragraph of running text.
   *
   * @param text the plain text
   * @param html the text as HTML
   * @param amendedBy the amendments that gave the paragraph its wording, if they are not its
   *     occurrence's
   */
  @ValueObject
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Text(
      @JsonInclude(JsonInclude.Include.ALWAYS) String text,
      @JsonInclude(JsonInclude.Include.ALWAYS) String html,
      List<Amendment> amendedBy)
      implements Block {
    public Text {
      amendedBy = List.copyOf(amendedBy);
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
   * @param amendedBy the amendments that gave the occurrence its wording, the outermost first
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
      List<Amendment> amendedBy,
      List<Block> content)
      implements Block {
    public Occurrence {
      amendedBy = List.copyOf(amendedBy);
      content = List.copyOf(content);
    }
  }

  /**
   * A table.
   *
   * @param caption its title
   * @param rows its rows, in order. A group of rows starts with its heading row.
   * @param notes the notes of the table, numbered in it
   * @param amendedBy the amendments that gave the table its wording, if they are not its
   *     occurrence's
   */
  @ValueObject
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Table(
      List<Block> caption, List<Row> rows, List<Note> notes, List<Amendment> amendedBy)
      implements Block {
    public Table {
      caption = List.copyOf(caption);
      rows = List.copyOf(rows);
      notes = List.copyOf(notes);
      amendedBy = List.copyOf(amendedBy);
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
   * @param amendedBy the amendments that gave the image its form, if they are not its occurrence's
   */
  @ValueObject
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Figure(
      String file,
      @Nullable String shows,
      List<Block> caption,
      List<Block> description,
      List<Amendment> amendedBy)
      implements Block {
    public Figure {
      caption = List.copyOf(caption);
      description = List.copyOf(description);
      amendedBy = List.copyOf(amendedBy);
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
   * Blocks that an amendment deleted, which the consolidated version still shows. They are not in
   * force.
   *
   * @param amendedBy the amendments that deleted them
   * @param content the deleted blocks
   */
  @ValueObject
  public record Deleted(List<Amendment> amendedBy, List<Block> content) implements Block {
    public Deleted {
      amendedBy = List.copyOf(amendedBy);
      content = List.copyOf(content);
    }
  }

  /**
   * A footnote.
   *
   * @param eId its identifier in the text. For example, {@code note_3}. The {@code href} of a note
   *     marker in the HTML is {@code #} and this eId.
   * @param marker its marker as printed. For example, {@code 3} or {@code *}.
   * @param content what it says
   */
  @ValueObject
  public record Note(String eId, String marker, List<Block> content) {
    public Note {
      content = List.copyOf(content);
    }
  }

  /**
   * An amendment of a consolidated version: which act changed the text, and how.
   *
   * @param act the CELEX number of the amending act
   * @param action what it did: {@code replaced}, {@code inserted}, {@code deleted} or {@code
   *     renumbered}
   */
  @ValueObject
  public record Amendment(String act, String action) {}

  /**
   * The wording of an occurrence that a comparison marked: the plain text before and after the
   * change, and the runs of words that stayed, went in and went out.
   *
   * @param before the text before the change
   * @param after the text after the change
   * @param changes the runs, in order. The text before is the runs that are not inserted, and the
   *     text after is the runs that are not deleted. Each paragraph, and each number and heading of
   *     an occurrence in the occurrence, ends with a line feed, except the last.
   */
  @ValueObject
  public record Marked(String before, String after, List<Change> changes) {
    public Marked {
      changes = List.copyOf(changes);
    }
  }

  /**
   * A run of words, and what a comparison found of it.
   *
   * @param op {@code equal}, {@code insert} or {@code delete}
   * @param text the words
   */
  @ValueObject
  public record Change(Op op, String text) {

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
   * An occurrence and the notes that it refers to: what the API gives for one occurrence.
   *
   * @param occurrence the occurrence
   * @param notes the notes that the occurrence refers to, in the order of the text
   */
  @ValueObject
  public record Extract(Occurrence occurrence, List<Note> notes) {
    public Extract {
      notes = List.copyOf(notes);
    }
  }
}
