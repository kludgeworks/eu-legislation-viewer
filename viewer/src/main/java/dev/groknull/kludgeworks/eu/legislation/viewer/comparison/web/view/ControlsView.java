// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage;
import java.util.Arrays;
import java.util.List;

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
                new ViewLink(view.label(), page.href(page.act(), view.parameter()), view == shown))
        .toList();
  }

  /**
   * The link to a view.
   *
   * @param label the name of the view. For example, "Whole text".
   * @param href the address of the comparison in this view
   * @param on whether this view shows now
   */
  public record ViewLink(String label, String href, boolean on) {}

  /**
   * The banner of a comparison that is limited to the changes of one act. For example, "Only
   * 2026/449's changes · show all acts".
   *
   * @param act the act, by its number
   * @param all the address of the comparison of all acts
   */
  public record Only(String act, String all) {}
}
