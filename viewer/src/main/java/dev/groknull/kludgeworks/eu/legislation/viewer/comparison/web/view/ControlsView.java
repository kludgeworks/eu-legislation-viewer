// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attributed;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Attribution;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Summary;
import java.util.ArrayList;
import java.util.Arrays;
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
   * sidenotes} by number. What it names is the comparison's {@link Summary}: the edits that the
   * page shows, and the one act that made every difference, if there is one.
   */
  public static Legend legend(DifferencesView differences, Map<Integer, SidenoteView> sidenotes) {
    var shown = new ArrayList<Attributed>();
    for (var passage : differences.passages()) {
      if (passage instanceof DifferencesView.Shown each) {
        var sidenote = sidenotes.get(each.number());
        shown.add(
            new Attributed(
                each.difference(), sidenote == null ? Attribution.NONE : sidenote.attribution()));
      }
    }
    var summary = Summary.of(shown);
    var by = summary.by();
    return new Legend(
        summary.inserted(), summary.deleted(), summary.corrected(), by == null ? null : by.cited());
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
