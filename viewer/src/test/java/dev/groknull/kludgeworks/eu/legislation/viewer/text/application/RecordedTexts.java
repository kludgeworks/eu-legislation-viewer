// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTextIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Source;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A text in English for every document, carrying only its identity; it records what it was asked
 * for, and fails for the documents in {@code failing}.
 */
final class RecordedTexts implements LegalTexts {
  final List<LegalTextIdentifier> asked = new ArrayList<>();
  final Set<String> failing = new HashSet<>();

  @Override
  public Optional<LegalText> find(LegalTextIdentifier id) {
    asked.add(id);
    if (failing.contains(id.document())) {
      throw new IllegalStateException("can't convert " + id);
    }
    if (id.language() != Language.ENG) {
      return Optional.empty();
    }
    return Optional.of(
        new LegalText(
            OneAct.AVSEC,
            LegalText.Kind.ORIGINAL,
            null,
            "en",
            "06.02.1",
            "test",
            new Source(id.document(), null, null),
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of()));
  }
}
