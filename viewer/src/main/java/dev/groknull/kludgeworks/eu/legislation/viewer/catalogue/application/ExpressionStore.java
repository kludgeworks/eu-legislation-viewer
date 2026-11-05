// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** The versions' texts that are built, by the CELEX of the document holding each. */
public interface ExpressionStore {

  /** The text's root, if it is built. */
  Optional<ContentHash> root(String celex, Language language);

  /** The roots of those of the texts that are built, by CELEX. */
  Map<String, ContentHash> roots(Collection<String> celexes, Language language);

  /** Points the text at the root it was built as, in place of any it had. */
  void keep(String celex, Language language, ContentHash root, String converter);
}
