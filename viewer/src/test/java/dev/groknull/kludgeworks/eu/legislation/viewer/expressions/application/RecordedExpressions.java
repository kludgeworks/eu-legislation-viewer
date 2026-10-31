// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expressions;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Source;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An expression in English for every document, carrying only its identity; it records what it was
 * asked for, and fails for the documents in {@code failing}.
 */
final class RecordedExpressions implements Expressions {
  final List<ExpressionIdentifier> asked = new ArrayList<>();
  final Set<String> failing = new HashSet<>();

  @Override
  public Optional<Expression> find(ExpressionIdentifier id) {
    asked.add(id);
    if (failing.contains(id.document())) {
      throw new IllegalStateException("can't convert " + id);
    }
    if (id.language() != Language.ENG) {
      return Optional.empty();
    }
    return Optional.of(
        new Expression(
            OneAct.AVSEC,
            Expression.Kind.ORIGINAL,
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
