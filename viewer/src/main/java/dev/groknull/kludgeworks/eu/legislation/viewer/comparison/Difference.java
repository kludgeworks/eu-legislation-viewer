// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * A subdivision that differs between two texts, as the Differences view shows it. It is a
 * provision, or a citation or a recital.
 *
 * <p>Each text has the subdivision as an occurrence, known by its eId. The occurrence is inserted,
 * deleted, or its own wording changed. Its own wording is its num, heading and blocks. The wording
 * of the occurrences in it is not part of it, because those occurrences differ on their own. An
 * inserted or deleted occurrence is one difference together with its occurrences, not one
 * difference for each occurrence.
 *
 * <p>The difference is shown as legal-text with its edits. Its inserted words are in {@code
 * diff:ins}, and its deleted words are in {@code diff:del}. These edits are only around text
 * ({@code t} and {@code number}). Thus each element keeps the parent that it has in the text, and
 * renders as it reads.
 *
 * <ul>
 *   <li>A changed occurrence shows only its own content, from the later text. The words of the
 *       earlier text show as deleted where they stood.
 *   <li>An inserted occurrence shows whole, with every word inserted.
 *   <li>A deleted occurrence shows whole, as the earlier text had it, with every word deleted.
 * </ul>
 *
 * @param eId the eId of the occurrence
 * @param kind how it differs
 * @param occurrence the occurrence as a fold names it, as the later text has it. For a deleted
 *     occurrence, as the earlier text has it. The Differences view uses this when it folds the
 *     occurrence with others that an act filter leaves out.
 * @param shown the occurrence as the view shows it, with its edits. As the store keeps it ({@link
 *     #kept}), cut down: for an annex inserted whole ({@code whole} is not null), only its opening,
 *     its num and its headings; for any other occurrence, with the rows of its tables that are the
 *     same in both texts folded. {@link Comparisons#difference} gives it whole.
 * @param inserted how many words it has inserted
 * @param deleted how many words it has deleted
 * @param parent the eId of the occurrence that it is in. Null for an outermost occurrence.
 * @param list the list that the occurrence is an item of, as the text has it but without its items.
 *     Thus the occurrence is shown as Read lays it out, with its number hanging beside its text.
 *     Null if the occurrence is in no list.
 * @param section the eId of the occurrence with a rank that it is in, as the outline of the text
 *     nests them. For example, {@code anx/pnt_8.1.2} for {@code anx/pnt_8.1.2.3/pnt_c}. It is the
 *     occurrence itself if the occurrence has a rank. Null if it is in no such occurrence.
 * @param lead the opening of the occurrence that it is in: its num, heading and first block,
 *     without edits. Null for an outermost occurrence.
 * @param context what the later text has before this difference and after the previous one, in
 *     order. For the first difference, it starts at the start of the text. It holds the opening of
 *     each occurrence with differences that it enters, and the folded occurrences that are the same
 *     in both texts. The contexts and the closing of the comparison hold each occurrence of the
 *     later text once, except the occurrences that differ.
 * @param tags the tags that tell which acts put the occurrence in its form in the later text, and
 *     how.
 *     <ul>
 *       <li>Usually these are the tags on the own content of the occurrence in the later text.
 *       <li>If it has none, these are the tags on the nearest occurrence that it is in that an act
 *           put in its form whole.
 *       <li>For a deleted occurrence, these are the tags of the act that deleted it. This is the
 *           tag of the deletion under which the later text keeps it struck. If there is none, it is
 *           the tag of the occurrence that it was in that an act replaced whole, without it.
 *     </ul>
 *     Empty if none of these applies.
 * @param earlierTags the same as {@code tags}, in the earlier text. Empty for an inserted
 *     occurrence.
 * @param whole the size of an annex inserted whole, if the store keeps the annex apart from the
 *     comparison. Then {@code shown} is only its opening, and {@link Comparisons#difference} gives
 *     it whole. Null if {@code shown} is the whole occurrence.
 */
@ValueObject
@RecordBuilder
public record Difference(
    String eId,
    Kind kind,
    Sibling occurrence,
    Element shown,
    int inserted,
    int deleted,
    @Nullable String parent,
    @Nullable Element list,
    @Nullable String section,
    @Nullable Element lead,
    List<Context> context,
    List<Tag> tags,
    List<Tag> earlierTags,
    @Nullable Whole whole) {

  /** The children of an annex that are its opening, as {@link #kept} keeps them. */
  private static final Set<String> OPENING = Set.of("tag", "num", "heading", "subheading");

  public Difference {
    context = List.copyOf(context);
    tags = List.copyOf(tags);
    earlierTags = List.copyOf(earlierTags);
  }

  /**
   * Returns whether it is an annex inserted whole. The views fold such an annex to its size, and
   * load it when the reader opens the fold. The store keeps it apart from the comparison, because
   * one annex can be tens of MB.
   */
  public boolean annexInserted() {
    return kind == Kind.INSERTED && "annex".equals(occurrence.kind());
  }

  /**
   * Returns this difference as the store keeps it in the comparison. Its shown occurrence is cut
   * down, and the store keeps the whole one apart:
   *
   * <ul>
   *   <li>An annex inserted whole has only its opening, and its size.
   *   <li>Any other occurrence has the runs of rows in its tables that are the same in both texts
   *       folded ({@link RowFolds}).
   * </ul>
   *
   * Returns it as it is if neither cuts it down, or if it is kept so already.
   */
  public Difference kept() {
    if (whole != null) {
      return this;
    }
    if (annexInserted()) {
      var opening =
          new Element(
              shown.name(),
              shown.attributes(),
              shown.children().stream().filter(child -> OPENING.contains(child.name())).toList(),
              shown.text());
      return DifferenceBuilder.builder(this).shown(opening).whole(Whole.of(shown)).build();
    }
    var folded = RowFolds.of(shown);
    return folded.equals(shown) ? this : DifferenceBuilder.builder(this).shown(folded).build();
  }

  /**
   * Returns the size of its occurrence: from {@code whole} if the store keeps the occurrence apart,
   * or counted in {@code shown}.
   */
  public Whole size() {
    return whole == null ? Whole.of(shown) : whole;
  }

  /**
   * The size of an occurrence, as a fold says it. For example, "56 provisions · 1,234 words".
   *
   * @param occurrences how many occurrences are in it, at any depth
   * @param words how many words its text has
   */
  @ValueObject
  public record Whole(long occurrences, long words) {

    /** Returns the size of {@code occurrence}. */
    public static Whole of(Element occurrence) {
      return new Whole(occurrences(occurrence), words(occurrence));
    }

    private static long occurrences(Element element) {
      return element.children().stream()
          .mapToLong(child -> (child.name().equals("occurrence") ? 1 : 0) + occurrences(child))
          .sum();
    }

    private static long words(Element element) {
      var text = element.name().equals("t") ? element.attribute("v") : null;
      var own = text == null || text.isBlank() ? 0 : text.strip().split("\\s+").length;
      return own + element.children().stream().mapToLong(Whole::words).sum();
    }
  }

  /** How a provision differs. */
  public enum Kind {
    /** Only in the later text. */
    INSERTED,
    /** Only in the earlier text. */
    DELETED,
    /** In both texts, with its wording changed. */
    CHANGED
  }

  /**
   * What the later text has between two differences, as the Differences view sets it out. It is one
   * of these:
   *
   * <ul>
   *   <li>the opening of an occurrence that holds differences, where the differences enter it
   *   <li>a folded run of occurrences in one occurrence that are the same in both texts, each with
   *       its own occurrences
   * </ul>
   *
   * @param occurrences the folded occurrences, in order. For an opening, only the opened
   *     occurrence.
   * @param parent the eId of the occurrence that they are in. Null for outermost occurrences.
   * @param opening the opening of the opened occurrence: its num, heading and first block. Null for
   *     a fold.
   */
  @ValueObject
  public record Context(
      List<Sibling> occurrences, @Nullable String parent, @Nullable Element opening) {
    public Context {
      occurrences = List.copyOf(occurrences);
    }
  }

  /**
   * An occurrence as a fold names it. For example, "Points (a) and (b)".
   *
   * @param eId its eId
   * @param kind its kind, as legal-text has it. For example, article, point or level.
   * @param num its num, as written. For example, "(a)" or "8.1.2.3". Empty for an occurrence
   *     without one.
   */
  @ValueObject
  public record Sibling(String eId, String kind, String num) {}

  /**
   * A tag on the content of an occurrence, as the consolidation has it. For example, ▼M20. The
   * converter keeps the tag as {@code tag}, {@code changed} or {@code deleted}. The tag names the
   * act that put the content in its form there, and its modification.
   *
   * @param act the CELEX of the amending act. For example, 32026R0449.
   * @param modification what the act did, as legal-text has it: replaced, inserted, deleted or
   *     renumbered
   * @param in the eId of the enclosing occurrence that the act put in its form whole, if the
   *     occurrence has no tag of its own. For example, 2019/103 replaced section 11.1 with its
   *     points. Null for a tag of the occurrence itself.
   */
  @ValueObject
  public record Tag(String act, Modification modification, @Nullable String in) {}
}
