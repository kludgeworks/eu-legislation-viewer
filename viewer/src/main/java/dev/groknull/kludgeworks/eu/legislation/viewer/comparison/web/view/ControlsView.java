// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a comparison's controls link to: each view of the comparison, and, in a comparison limited
 * to the changes of one act, the comparison of all acts.
 */
public final class ControlsView {
  private ControlsView() {}

  /**
   * Returns the links to the views of the comparison on {@code page}, as its controls show them.
   * The view that the page shows, {@code shown}, is on.
   */
  public static List<ViewLink> views(ComparePage page, ComparisonView shown) {
    return Arrays.stream(ComparisonView.values())
        .map(
            view ->
                new ViewLink(
                    view.label(),
                    view.code(),
                    page.href(page.act(), view.parameter()),
                    view == shown))
        .toList();
  }

  /**
   * Returns the legend of the comparison {@code differences}, whose differences have {@code
   * sidenotes} by number. The legend names only what the edits of the page show: inserted and
   * deleted words, and the words of a correction. If one act made every difference, the summary
   * names it.
   */
  public static Legend legend(DifferencesView differences, Map<Integer, SidenoteView> sidenotes) {
    var inserted = false;
    var deleted = false;
    var corrected = false;
    var acts = new LinkedHashSet<ActIdentifier>();
    var known = true;
    for (var passage : differences.passages()) {
      if (passage instanceof DifferencesView.Shown shown) {
        var sidenote = sidenotes.get(shown.number());
        var difference = shown.difference();
        if (sidenote != null && sidenote.corrected()) {
          corrected = true;
        } else {
          inserted |= difference.kind() == Kind.INSERTED || difference.inserted() > 0;
          deleted |= difference.kind() == Kind.DELETED || difference.deleted() > 0;
        }
        if (sidenote == null || sidenote.acts().isEmpty()) {
          known = false;
        } else {
          acts.addAll(sidenote.acts());
        }
      }
    }
    return new Legend(
        inserted, deleted, corrected, known && acts.size() == 1 ? acts.getFirst().cited() : null);
  }

  /**
   * What the summary and the legend of a comparison say.
   *
   * @param inserted whether the page shows inserted words
   * @param deleted whether the page shows deleted words
   * @param corrected whether the page shows a corrigendum's words
   * @param by the act that made every difference, if there is one. For example, "2026/449".
   */
  public record Legend(boolean inserted, boolean deleted, boolean corrected, @Nullable String by) {}

  /**
   * The link to a view.
   *
   * @param label the name of the view. For example, "Whole text".
   * @param code the code of the view, for example {@code whole}. The page shows the shape of this
   *     view while it loads.
   * @param href the address of the comparison in this view
   * @param on whether this view shows now
   */
  public record ViewLink(String label, String code, String href, boolean on) {}
}
