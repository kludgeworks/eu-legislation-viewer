// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Locale;

/** The pages' addresses: an act's by its ELI, as VersionController answers them. */
public final class Links {
  private Links() {}

  /**
   * The page of {@code version} in {@code language}: /eli/reg_impl/2015/1998/2026-03-22/eng, the
   * version's first day and the language's code, as an ELI has them.
   */
  public static String read(Eli eli, Version version, Language language) {
    return "/eli/"
        + eli.path()
        + "/"
        + version.date()
        + "/"
        + language.code().toLowerCase(Locale.ROOT);
  }
}
