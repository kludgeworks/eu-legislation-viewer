// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.chrome;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The colour theme of the reader: light, dark, or the theme of the device. It is {@link #SYSTEM}
 * until the reader chooses.
 */
public enum Theme {
  LIGHT,
  SYSTEM,
  DARK;

  /**
   * Returns the name of the theme in the cookie, in the form and in the {@code data-theme} of the
   * page. The names are {@code light}, {@code system} and {@code dark}.
   */
  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Returns the theme for its code. Returns none for any other value. */
  public static Optional<Theme> ofCode(@Nullable String code) {
    return Arrays.stream(values()).filter(theme -> theme.code().equals(code)).findFirst();
  }
}
