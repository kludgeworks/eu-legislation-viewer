// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.infra.jooq.Tables.COMPARISON;
import static org.jooq.impl.DSL.currentInstant;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.application.ComparisonStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The comparisons in Postgres (comparison), with jOOQ: the differences and the closing as JSON,
 * with Jackson.
 */
@Repository
class JooqComparisonStore implements ComparisonStore {
  private static final TypeReference<List<Difference>> DIFFERENCES = new TypeReference<>() {};
  private static final TypeReference<List<Context>> CLOSING = new TypeReference<>() {};

  private final DSLContext db;
  private final JsonMapper json;

  JooqComparisonStore(DSLContext db, JsonMapper json) {
    this.db = db;
    this.json = json;
  }

  @Override
  public Optional<Comparison> find(ContentHash from, ContentHash to) {
    return db.select(COMPARISON.DIFFERENCES, COMPARISON.CLOSING)
        .from(COMPARISON)
        .where(COMPARISON.FROM_ROOT.eq(from), COMPARISON.TO_ROOT.eq(to))
        .fetchOptional()
        .map(
            row ->
                new Comparison(
                    from,
                    to,
                    json.readValue(row.value1().data(), DIFFERENCES),
                    json.readValue(row.value2().data(), CLOSING)));
  }

  @Override
  public void add(Comparison comparison) {
    db.insertInto(COMPARISON)
        .set(COMPARISON.FROM_ROOT, comparison.from())
        .set(COMPARISON.TO_ROOT, comparison.to())
        .set(
            COMPARISON.DIFFERENCES,
            JSONB.valueOf(json.writeValueAsString(comparison.differences())))
        .set(COMPARISON.CLOSING, JSONB.valueOf(json.writeValueAsString(comparison.closing())))
        .set(COMPARISON.COMPARED_AT, currentInstant())
        .onConflictDoNothing()
        .execute();
  }
}
