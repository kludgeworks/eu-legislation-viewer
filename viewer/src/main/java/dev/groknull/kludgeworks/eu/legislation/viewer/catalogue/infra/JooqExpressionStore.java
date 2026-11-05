// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.jooq.Tables.EXPRESSION;
import static org.jooq.impl.DSL.currentInstant;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application.ExpressionStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** The built texts in Postgres (expression), with jOOQ. */
@Repository
class JooqExpressionStore implements ExpressionStore {
  private final DSLContext db;

  JooqExpressionStore(DSLContext db) {
    this.db = db;
  }

  @Override
  public Optional<ContentHash> root(String celex, Language language) {
    return db.select(EXPRESSION.ROOT)
        .from(EXPRESSION)
        .where(EXPRESSION.CELEX.eq(celex), EXPRESSION.LANGUAGE.eq(language))
        .fetchOptional(EXPRESSION.ROOT);
  }

  @Override
  public Map<String, ContentHash> roots(Collection<String> celexes, Language language) {
    return db.select(EXPRESSION.CELEX, EXPRESSION.ROOT)
        .from(EXPRESSION)
        .where(EXPRESSION.CELEX.in(celexes), EXPRESSION.LANGUAGE.eq(language))
        .fetchMap(EXPRESSION.CELEX, EXPRESSION.ROOT);
  }

  @Override
  public void keep(String celex, Language language, ContentHash root, String converter) {
    db.insertInto(EXPRESSION)
        .set(EXPRESSION.CELEX, celex)
        .set(EXPRESSION.LANGUAGE, language)
        .set(EXPRESSION.ROOT, root)
        .set(EXPRESSION.CONVERTER, converter)
        .set(EXPRESSION.BUILT_AT, currentInstant())
        .onConflict(EXPRESSION.CELEX, EXPRESSION.LANGUAGE)
        .doUpdate()
        .set(EXPRESSION.ROOT, root)
        .set(EXPRESSION.CONVERTER, converter)
        .set(EXPRESSION.BUILT_AT, currentInstant())
        .execute();
  }
}
