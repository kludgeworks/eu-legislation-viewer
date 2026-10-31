// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Inline.Emphasis;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Anything that stands on its own line: a unit, a paragraph, a list, a table. */
public sealed interface Block extends PreambleContent
    permits Block.Paragraph,
        Block.ListBlock,
        Block.Table,
        Block.TableGroup,
        Block.QuotedStructure,
        Block.Annotation,
        Block.Figure,
        Block.MathBlock,
        Block.DeletedBlock,
        Block.OldText,
        Block.NewText,
        Block.BlockContainer,
        Unit {

  /**
   * A paragraph of running text.
   *
   * @param provenance The amending acts that put this paragraph in its current form, when they
   *     differ from its unit's.
   * @param num The label of a numbered paragraph outside the hierarchy, e.g. in a table cell.
   */
  record Paragraph(List<Provenance> provenance, List<Inline> num, List<Inline> content)
      implements Block {

    public Paragraph {
      provenance = List.copyOf(provenance);
      num = List.copyOf(num);
      content = List.copyOf(content);
    }
  }

  /**
   * A list of points or indents.
   *
   * @param provenance The amending acts that put this list in its current form, when they differ
   *     from its unit's.
   * @param kind How the items are numbered.
   */
  record ListBlock(List<Provenance> provenance, Kind kind, List<Block> items) implements Block {

    public ListBlock {
      provenance = List.copyOf(provenance);
      items = List.copyOf(items);
    }

    /** How the items of a list are numbered (Formex LIST/@TYPE). */
    public enum Kind {
      /** 1, 2, 3 (ARAB). */
      ARABIC,
      /** a, b, c (alpha). */
      ALPHA,
      /** A, B, C (ALPHA). */
      ALPHA_UPPER,
      /** i, ii, iii (roman). */
      ROMAN,
      /** I, II, III (ROMAN). */
      ROMAN_UPPER,
      /** Em dashes (DASH). */
      DASH,
      /** En dashes (NDASH, Formex 05.59 and later). */
      NDASH,
      /** Bullets (BULLET). */
      BULLET,
      /** No marks (NONE). */
      NONE,
      /** Marks the source writes out in each item (OTHER). */
      OTHER,
      /** A definition list (Formex DLIST). */
      DEFINITION
    }
  }

  /**
   * A table.
   *
   * @param provenance The amending acts that put this table in its current form, when they differ
   *     from its unit's.
   * @param caption The table's title.
   * @param cols The number of columns.
   */
  record Table(
      List<Provenance> provenance, List<Block> caption, @Nullable Integer cols, List<TableRow> rows)
      implements Block {

    public Table {
      provenance = List.copyOf(provenance);
      caption = List.copyOf(caption);
      rows = List.copyOf(rows);
    }
  }

  /**
   * Tables that belong together under one title (Formex GR.TBL).
   *
   * @param provenance The amending acts that put this group in its current form.
   * @param caption The group's title.
   */
  record TableGroup(List<Provenance> provenance, List<Block> caption, List<Block> content)
      implements Block {

    public TableGroup {
      provenance = List.copyOf(provenance);
      caption = List.copyOf(caption);
      content = List.copyOf(content);
    }
  }

  /**
   * The units an amending act inserts or substitutes (Formex QUOT.S). The instruction ("point
   * 1.2.2.3 is replaced by the following:") is the paragraph before it in the same unit.
   *
   * @param eId The enclosing unit's eId plus qstr_N; the quoted units' eIds hang off it.
   * @param ambiguous Inside a unit marked ambiguous, whose eId this one shares through it.
   */
  record QuotedStructure(EId eId, boolean ambiguous, List<Block> content) implements Block {

    public QuotedStructure {
      content = List.copyOf(content);
    }
  }

  /**
   * A box of notes or a caption set apart from the text (Formex ANNOTATION, GR.ANNOTATION).
   *
   * @param provenance The amending acts that put this annotation in its current form.
   * @param heading Its heading, e.g. "NOTES:".
   */
  record Annotation(List<Provenance> provenance, @Nullable Lines heading, List<Block> content)
      implements Block {

    public Annotation {
      provenance = List.copyOf(provenance);
      content = List.copyOf(content);
    }
  }

  /**
   * An image (Formex INCL.ELEMENT TYPE="TIFF").
   *
   * @param src The image file in the act's Cellar manifestation.
   * @param content What the image shows (Formex INCL.ELEMENT CONTENT).
   * @param caption The text printed with the image, usually under it (Formex CAPTION).
   * @param description A transcription of the text inside the image, such as the fields of a form
   *     (Formex IMG.CNT): the image's alternative text, and what makes a form searchable.
   */
  record Figure(
      String src, @Nullable ImageContent content, List<Block> caption, List<Block> description)
      implements Block {

    public Figure {
      caption = List.copyOf(caption);
      description = List.copyOf(description);
    }
  }

  /**
   * A displayed formula (Formex FORMULA.S), as Presentation MathML: delimited groups (EXPR) become
   * mrow with mo fences, fractions mfrac, sums, products and integrals munderover, indices msub,
   * exponents msup, roots msqrt, operators mo.
   */
  record MathBlock(String mathml) implements Block {}

  /**
   * Content an amendment deleted but the consolidation keeps. Never live text: it is in force no
   * longer, and its provenance names the act that deleted it.
   *
   * @param provenance The deleting act.
   */
  record DeletedBlock(List<Provenance> provenance, List<Block> content) implements Block {

    public DeletedBlock {
      provenance = List.copyOf(provenance);
      content = List.copyOf(content);
    }
  }

  /** The wording as published: "for:". */
  record OldText(List<Block> content) implements Block {

    public OldText {
      content = List.copyOf(content);
    }
  }

  /** The wording as corrected: "read:". */
  record NewText(List<Block> content) implements Block {

    public NewText {
      content = List.copyOf(content);
    }
  }

  /**
   * Blocks set together in a typeface or a box, or in another language (Formex HT or LV around
   * whole paragraphs, lists or levels: a boxed notice, a level inside a box), as AKN's
   * blockContainer. Inline emphasis stays emphasis; this is for what wraps blocks.
   *
   * @param emphasis The typeface or the box (Formex HT/@TYPE), as for inline emphasis.
   * @param lang The language of the blocks, when not the document's (Formex LV/@LG).
   */
  record BlockContainer(
      Emphasis.@Nullable Kind emphasis, @Nullable String lang, List<Block> content)
      implements Block {

    public BlockContainer {
      content = List.copyOf(content);
    }
  }
}
