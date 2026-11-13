// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.TEXT;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.val;

import com.github.luben.zstd.Zstd;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.application.TextStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the texts in Postgres, in the table {@code text}, with jOOQ. The table is in {@code
 * db/migration/content}.
 *
 * <ul>
 *   <li>A text is one row, by its identity, which Postgres gives. The version and language are
 *       unique.
 *   <li>It compresses the JSON of each text with zstd. Postgres can compress only with pglz or lz4,
 *       which take about 60% more space.
 * </ul>
 */
@Repository
class JooqTextStore implements TextStore {

  /** The compression level of zstd: its default, fast to write and to read. */
  private static final int LEVEL = 3;

  private final DSLContext db;
  private final JsonMapper json;

  JooqTextStore(DSLContext db, JsonMapper json) {
    this.db = db;
    this.json = json;
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code select … for update} locks only a row that exists. Two writers of a text not yet
   * stored would both find no row and both insert, and one would fail on the unique version and
   * language. So it first takes a transaction-scoped advisory lock on the version and language,
   * which holds whether the row exists or not.
   */
  @Override
  public Optional<Kept> lock(String celex, Language language) {
    db.select(
            function(
                "pg_advisory_xact_lock",
                Object.class,
                function(
                    "hashtextextended",
                    Long.class,
                    val("content.text " + celex + " " + language.code()),
                    inline(0L))))
        .fetch();
    return db.select(TEXT.ID, TEXT.DIGEST)
        .from(TEXT)
        .where(TEXT.CELEX.eq(celex), TEXT.LANGUAGE_CODE.eq(language))
        .forUpdate()
        .fetchOptional(row -> new Kept(row.value1(), row.value2()));
  }

  @Override
  public Added add(
      String celex,
      Language language,
      String converter,
      byte[] digest,
      Outline outline,
      byte[] json) {
    var body = Zstd.compress(json, LEVEL);
    var id =
        db.insertInto(TEXT)
            .set(TEXT.CELEX, celex)
            .set(TEXT.LANGUAGE_CODE, language)
            .set(TEXT.DIGEST, digest)
            .set(TEXT.CONVERTER, converter)
            .set(TEXT.STORED_AT, currentInstant())
            .set(TEXT.OUTLINE, JSONB.valueOf(this.json.writeValueAsString(outline)))
            .set(TEXT.BODY, body)
            .returning(TEXT.ID)
            .fetchSingle(TEXT.ID);
    return new Added(Objects.requireNonNull(id), body.length);
  }

  @Override
  public void delete(TextId text) {
    db.deleteFrom(TEXT).where(TEXT.ID.eq(text)).execute();
  }

  @Override
  public Map<Language, TextId> delete(String celex) {
    return db.deleteFrom(TEXT)
        .where(TEXT.CELEX.eq(celex))
        .returningResult(TEXT.LANGUAGE_CODE, TEXT.ID)
        .fetchMap(TEXT.LANGUAGE_CODE, TEXT.ID);
  }

  @Override
  public Optional<byte[]> json(TextId text) {
    return db.select(TEXT.BODY)
        .from(TEXT)
        .where(TEXT.ID.eq(text))
        .fetchOptional(row -> decompress(row.value1()));
  }

  @Override
  public Optional<Outline> outline(TextId text) {
    return db.select(TEXT.OUTLINE)
        .from(TEXT)
        .where(TEXT.ID.eq(text))
        .fetchOptional(row -> json.readValue(row.value1().data(), Outline.class));
  }

  @Override
  public boolean contains(TextId text) {
    return db.fetchExists(TEXT, TEXT.ID.eq(text));
  }

  @Override
  public Map<String, TextId> texts(Collection<String> celexes, Language language) {
    return db.select(TEXT.CELEX, TEXT.ID)
        .from(TEXT)
        .where(TEXT.CELEX.in(celexes), TEXT.LANGUAGE_CODE.eq(language))
        .fetchMap(TEXT.CELEX, TEXT.ID);
  }

  private static byte[] decompress(byte[] body) {
    return Zstd.decompress(body, Math.toIntExact(Zstd.getFrameContentSize(body)));
  }
}
