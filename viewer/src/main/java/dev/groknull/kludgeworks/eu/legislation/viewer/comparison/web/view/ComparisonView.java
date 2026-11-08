// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view;

import java.util.Arrays;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A view of a comparison. It sets what part of the two texts the comparison shows, and how. The
 * address of a comparison names it, in the {@code view} field of the {@code comparepage} library's
 * {@code ComparePage}.
 */
public enum ComparisonView {
  /** Only the provisions that differ, with the opening of the unit each is in. */
  CHANGES("changes", "Changes"),
  /** Every provision of the later text. The provisions that differ are marked in place. */
  WHOLE("whole", "Whole text"),
  /** The two texts side by side, aligned by provision. */
  SYNOPSIS("synopsis", "Synopsis");

  private final String code;
  private final String label;

  ComparisonView(String code, String label) {
    this.code = code;
    this.label = label;
  }

  /**
   * Returns the code of the view in the address of the comparison: {@code changes}, {@code whole}
   * or {@code synopsis}.
   */
  public String code() {
    return code;
  }

  /**
   * Returns the code of the view in the {@code view} field of an address, or null for the first
   * view, {@link #CHANGES}: its address has no {@code view} field.
   */
  public @Nullable String parameter() {
    return this == CHANGES ? null : code;
  }

  /** Returns the name of the view in the controls. For example, "Whole text". */
  public String label() {
    return label;
  }

  /** Returns the view with this code. Returns empty for an unknown code. */
  public static Optional<ComparisonView> of(String code) {
    return Arrays.stream(values()).filter(view -> view.code.equals(code)).findFirst();
  }
}
