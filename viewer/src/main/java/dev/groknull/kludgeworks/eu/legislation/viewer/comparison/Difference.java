// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * A provision that differs between two texts, as the Changes view shows it.
 *
 * <p>The provision is a unit, known by its eId. The unit is inserted, deleted, or its own wording
 * changed. Its own wording is its num, heading and blocks. The wording of the units in it is not
 * part of it, because those units differ on their own. An inserted or deleted unit is one
 * difference together with its units, not one difference for each unit.
 *
 * <p>The difference is shown as marked legal-text. Its inserted words are in {@code diff:ins}, and
 * its deleted words are in {@code diff:del}. These marks are only around text ({@code t} and {@code
 * number}). Thus each element keeps the parent that it has in the text, and renders as it reads.
 *
 * <ul>
 *   <li>A changed unit shows only its own content, from the later text. The words of the earlier
 *       text show as deleted where they stood.
 *   <li>An inserted unit shows whole, with every word inserted.
 *   <li>A deleted unit shows whole, as the earlier text had it, with every word deleted.
 * </ul>
 *
 * @param eId the eId of the unit
 * @param kind how it differs
 * @param unit the unit as a fold names it, as the later text has it. For a deleted unit, as the
 *     earlier text has it. The Changes view uses this when it folds the unit with others that an
 *     act filter leaves out.
 * @param shown the unit as the view shows it, marked. As the store keeps it ({@link #kept}), cut
 *     down: for an annex inserted whole ({@code whole} is not null), only its opening, its num and
 *     its headings; for any other unit, with the rows of its tables that are the same in both texts
 *     folded. {@link Comparisons#difference} gives it whole.
 * @param inserted how many words it has inserted
 * @param deleted how many words it has deleted
 * @param parent the eId of the unit that it is in. Null for an outermost unit.
 * @param list the list that the unit is an item of, as the text has it but without its items. Thus
 *     the unit is shown as Read lays it out, with its number hanging beside its text. Null if the
 *     unit is in no list.
 * @param section the eId of the unit with a rank that it is in, as the outline of the text nests
 *     them. For example, {@code anx/pnt_8.1.2} for {@code anx/pnt_8.1.2.3/pnt_c}. It is the unit
 *     itself if the unit has a rank. Null if it is in no such unit.
 * @param lead the opening of the unit that it is in: its num, heading and first block, unmarked.
 *     Null for an outermost unit.
 * @param context what the later text has before this difference and after the previous one, in
 *     order. For the first difference, it starts at the start of the text. It holds the opening of
 *     each unit with differences that it enters, and the folded units that are the same in both
 *     texts. The contexts and the closing of the comparison hold each unit of the later text once,
 *     except the units that differ.
 * @param markers the amendment markers that tell which acts put the unit in its form in the later
 *     text, and how.
 *     <ul>
 *       <li>Usually these are the markers on the own content of the unit in the later text.
 *       <li>If it has none, these are the markers on the nearest unit that it is in that an act put
 *           in its form whole.
 *       <li>For a deleted unit, these are the markers of the act that deleted it. This is the
 *           marker of the deletion under which the later text keeps it struck. If there is none, it
 *           is the marker of the unit that it was in that an act replaced whole, without it.
 *     </ul>
 *     Empty if none of these applies.
 * @param earlierMarkers the same as {@code markers}, in the earlier text. Empty for an inserted
 *     unit.
 * @param whole the size of an annex inserted whole, if the store keeps the annex apart from the
 *     comparison. Then {@code shown} is only its opening, and {@link Comparisons#difference} gives
 *     it whole. Null if {@code shown} is the whole unit.
 */
@ValueObject
@RecordBuilder
public record Difference(
    String eId,
    Kind kind,
    Sibling unit,
    Element shown,
    int inserted,
    int deleted,
    @Nullable String parent,
    @Nullable Element list,
    @Nullable String section,
    @Nullable Element lead,
    List<Context> context,
    List<Marker> markers,
    List<Marker> earlierMarkers,
    @Nullable Whole whole) {

  /** The children of an annex that are its opening, as {@link #kept} keeps them. */
  private static final Set<String> OPENING = Set.of("provenance", "num", "heading", "subheading");

  public Difference {
    context = List.copyOf(context);
    markers = List.copyOf(markers);
    earlierMarkers = List.copyOf(earlierMarkers);
  }

  /**
   * Returns whether it is an annex inserted whole. The views fold such an annex to its size, and
   * load it when the reader opens the fold. The store keeps it apart from the comparison, because
   * one annex can be tens of MB.
   */
  public boolean annexInserted() {
    return kind == Kind.INSERTED && "annex".equals(unit.kind());
  }

  /**
   * Returns this difference as the store keeps it in the comparison. Its shown unit is cut down,
   * and the store keeps the whole one apart:
   *
   * <ul>
   *   <li>An annex inserted whole has only its opening, and its size.
   *   <li>Any other unit has the runs of rows in its tables that are the same in both texts folded
   *       ({@link RowFolds}).
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
   * Returns the size of its unit: from {@code whole} if the store keeps the unit apart, or counted
   * in {@code shown}.
   */
  public Whole size() {
    return whole == null ? Whole.of(shown) : whole;
  }

  /**
   * The size of a unit, as a fold says it. For example, "56 provisions · 1,234 words".
   *
   * @param units how many units are in it, at any depth
   * @param words how many words its text has
   */
  @ValueObject
  public record Whole(long units, long words) {

    /** Returns the size of {@code unit}. */
    public static Whole of(Element unit) {
      return new Whole(units(unit), words(unit));
    }

    private static long units(Element element) {
      return element.children().stream()
          .mapToLong(child -> (child.name().equals("unit") ? 1 : 0) + units(child))
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
   * What the later text has between two differences, as the Changes view sets it out. It is one of
   * these:
   *
   * <ul>
   *   <li>the opening of a unit that holds differences, where the differences enter it
   *   <li>a folded run of units in one unit that are the same in both texts, each with its own
   *       units
   * </ul>
   *
   * @param units the folded units, in order. For an opening, only the opened unit.
   * @param parent the eId of the unit that they are in. Null for outermost units.
   * @param opening the opening of the opened unit: its num, heading and first block. Null for a
   *     fold.
   */
  @ValueObject
  public record Context(List<Sibling> units, @Nullable String parent, @Nullable Element opening) {
    public Context {
      units = List.copyOf(units);
    }
  }

  /**
   * A unit as a fold names it. For example, "Points (a) and (b)".
   *
   * @param eId its eId
   * @param kind its kind, as legal-text has it. For example, article, point or level.
   * @param num its num, as written. For example, "(a)" or "8.1.2.3". Empty for a unit without one.
   */
  @ValueObject
  public record Sibling(String eId, String kind, String num) {}

  /**
   * An amendment marker on the content of a unit, as the consolidation has it. For example, ▼M20.
   * The converter keeps the marker as {@code provenance}, {@code changed} or {@code deleted}. The
   * marker names the act that put the content in its form there, and what the act did.
   *
   * @param act the CELEX of the amending act. For example, 32026R0449.
   * @param action what the act did, as legal-text has it: replaced, inserted, deleted or renumbered
   * @param in the eId of the enclosing unit that the act put in its form whole, if the unit has no
   *     marker of its own. For example, 2019/103 replaced section 11.1 with its points. Null for a
   *     marker of the unit itself.
   */
  @ValueObject
  public record Marker(String act, String action, @Nullable String in) {}
}
