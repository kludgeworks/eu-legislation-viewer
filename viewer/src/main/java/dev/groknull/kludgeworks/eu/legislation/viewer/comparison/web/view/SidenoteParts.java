// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The parts that many sidenotes of one view repeat, each sent once. An act amended by many acts in
 * one range has thousands of differences, and their sidenotes name the same acts: 2005/396 from
 * December 2024 to August 2026 has 2,685 sidenotes but 12 hover texts and 9 lists of dates, 17 MB
 * of its 35 MB.
 *
 * <p>The view sends the parts in a template before the differences (sidenoteparts.jte), and each
 * sidenote names its parts by id. js/sidenotes.js sets the hover text of who made the difference
 * from them, and fills a fold of dates when the reader opens it. A list of dates is a part only if
 * it is long enough to fold ({@link #FOLDED}). A shorter one shows as it is.
 */
public final class SidenoteParts {

  /** The fewest dates that fold. Fewer show as they are. */
  public static final int FOLDED = 6;

  private final Map<String, String> published = new LinkedHashMap<>();
  private final Map<List<SidenoteView.Applies>, String> dates = new LinkedHashMap<>();

  private SidenoteParts() {}

  /** Returns the parts of {@code sidenotes}, each once, numbered in the order of the sidenotes. */
  public static SidenoteParts of(Collection<SidenoteView> sidenotes) {
    var parts = new SidenoteParts();
    for (var sidenote : sidenotes) {
      var hover = sidenote.published();
      if (hover != null) {
        parts.published.computeIfAbsent(hover, each -> "published-" + (parts.published.size() + 1));
      }
      if (sidenote.dates().size() >= FOLDED) {
        parts.dates.computeIfAbsent(sidenote.dates(), each -> "dates-" + (parts.dates.size() + 1));
      }
    }
    return parts;
  }

  /** Returns the hover texts of who made the differences, each by its id. */
  public Map<String, String> published() {
    var byId = new LinkedHashMap<String, String>();
    published.forEach((hover, id) -> byId.put(id, hover));
    return Collections.unmodifiableMap(byId);
  }

  /** Returns the lists of dates that fold, each by its id. */
  public Map<String, List<SidenoteView.Applies>> dates() {
    var byId = new LinkedHashMap<String, List<SidenoteView.Applies>>();
    dates.forEach((list, id) -> byId.put(id, list));
    return Collections.unmodifiableMap(byId);
  }

  /** Returns the id of the hover text of {@code sidenote}, or null if it has none. */
  public @Nullable String published(SidenoteView sidenote) {
    var hover = sidenote.published();
    return hover == null ? null : published.get(hover);
  }

  /** Returns the id of the dates of {@code sidenote}, or null if they are too few to fold. */
  public @Nullable String dates(SidenoteView sidenote) {
    return dates.get(sidenote.dates());
  }
}
