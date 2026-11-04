// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The reader's colour theme: light, dark, or the device's (SYSTEM, unless they chose). */
public enum Theme {
  LIGHT,
  SYSTEM,
  DARK;

  /** Its name in the cookie, the form and the page's data-theme: light, system, dark. */
  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** The theme by its code; none for any other value. */
  public static Optional<Theme> ofCode(@Nullable String code) {
    return Arrays.stream(values()).filter(theme -> theme.code().equals(code)).findFirst();
  }
}
