// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.CHUNK;
import static dev.groknull.kludgeworks.eu.legislation.viewer.content.infra.jooq.Tables.MANIFEST;
import static org.jooq.impl.DSL.any;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.list;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.val;

import com.github.luben.zstd.Zstd;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.ContentHash;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.application.TextStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Chunks;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the texts in Postgres, with jOOQ: their chunks in {@code chunk}, and each text's list of
 * chunks and outline in {@code manifest}. The tables are in {@code db/migration/content}.
 *
 * <ul>
 *   <li>The store removes duplicates. A chunk is keyed by its hash, and only the chunks that are
 *       not there yet are sent. Writers that store the same chunks at the same time do not
 *       conflict.
 *   <li>A text is added in the transaction of the caller, its chunks before its manifest. Thus if a
 *       root is stored, all of its chunks are stored.
 *   <li>It compresses each chunk with zstd. Postgres can compress only with pglz or lz4, which take
 *       about 60% more space.
 *   <li>A text is read in two statements: its manifest, then its chunks by their hashes.
 * </ul>
 */
@Repository
class JooqTextStore implements TextStore {
  private static final DataType<byte[][]> HASHES = SQLDataType.BLOB.array();

  /** The length of a hash in a manifest, in bytes. */
  private static final int HASH = 32;

  /** The compression level of zstd: its default, fast to write and to read. */
  private static final int LEVEL = 3;

  private final DSLContext db;
  private final JsonMapper json;

  JooqTextStore(DSLContext db, JsonMapper json) {
    this.db = db;
    this.json = json;
  }

  @Override
  public boolean contains(ContentHash root) {
    return db.fetchExists(MANIFEST, MANIFEST.ROOT.eq(root));
  }

  @Override
  public int add(ContentHash root, Chunks chunks, Outline outline) {
    var added = insertChunks(chunks);
    db.insertInto(MANIFEST)
        .set(MANIFEST.ROOT, root)
        .set(MANIFEST.CHUNKS, manifest(chunks.manifest()))
        .set(MANIFEST.OUTLINE, JSONB.valueOf(json.writeValueAsString(outline)))
        .onConflictDoNothing()
        .execute();
    return added;
  }

  @Override
  public Optional<Chunks> chunks(ContentHash root) {
    var manifest =
        db.select(MANIFEST.CHUNKS).from(MANIFEST).where(MANIFEST.ROOT.eq(root)).fetchOptional();
    if (manifest.isEmpty()) {
      return Optional.empty();
    }
    var hashes = hashes(manifest.get().value1());
    var stored = new HashMap<ContentHash, byte[]>();
    db.select(CHUNK.DIGEST, CHUNK.BODY)
        .from(CHUNK)
        .where(matches(hashes))
        .forEach(row -> stored.put(row.value1(), decompress(row.value2())));
    var bodies = new LinkedHashMap<ContentHash, byte[]>();
    for (var hash : hashes) {
      var body = stored.get(hash);
      if (body == null) {
        throw new IllegalStateException("A chunk of " + root + " is missing: " + hash);
      }
      bodies.put(hash, body);
    }
    return Optional.of(new Chunks(hashes.getFirst(), bodies));
  }

  @Override
  public Optional<Outline> outline(ContentHash root) {
    return db.select(MANIFEST.OUTLINE)
        .from(MANIFEST)
        .where(MANIFEST.ROOT.eq(root))
        .fetchOptional()
        .map(row -> json.readValue(row.value1().data(), Outline.class));
  }

  /** Inserts the chunks that are not stored yet, and returns how many there were. */
  private int insertChunks(Chunks chunks) {
    var known = new HashSet<ContentHash>();
    db.select(CHUNK.DIGEST)
        .from(CHUNK)
        .where(matches(chunks.manifest()))
        .forEach(row -> known.add(row.value1()));
    var hashes = new ArrayList<byte[]>();
    var bodies = new ArrayList<byte[]>();
    chunks
        .bodies()
        .forEach(
            (hash, body) -> {
              if (!known.contains(hash)) {
                hashes.add(hash.bytes());
                bodies.add(Zstd.compress(body, LEVEL));
              }
            });
    if (hashes.isEmpty()) {
      return 0;
    }
    var rows =
        unnest(
            val(hashes.toArray(byte[][]::new), HASHES), val(bodies.toArray(byte[][]::new), HASHES));
    db.insertInto(CHUNK, CHUNK.DIGEST, CHUNK.BODY)
        .select(select(column(rows, 1, CHUNK.DIGEST), column(rows, 2, CHUNK.BODY)).from(rows))
        .onConflictDoNothing()
        .execute();
    return hashes.size();
  }

  /** Matches the chunks with these hashes, as one array: a long text has thousands. */
  private static Condition matches(List<ContentHash> hashes) {
    var array = hashes.stream().map(ContentHash::bytes).toArray(byte[][]::new);
    return CHUNK.DIGEST.coerce(SQLDataType.BLOB).eq(any(val(array, HASHES)));
  }

  private static byte[] manifest(List<ContentHash> hashes) {
    var out = new ByteArrayOutputStream(hashes.size() * HASH);
    hashes.forEach(hash -> out.writeBytes(hash.bytes()));
    return out.toByteArray();
  }

  private static List<ContentHash> hashes(byte[] manifest) {
    var hashes = new ArrayList<ContentHash>(manifest.length / HASH);
    for (var at = 0; at < manifest.length; at += HASH) {
      hashes.add(ContentHash.of(Arrays.copyOfRange(manifest, at, at + HASH)));
    }
    return hashes;
  }

  private static byte[] decompress(byte[] body) {
    return Zstd.decompress(body, Math.toIntExact(Zstd.getFrameContentSize(body)));
  }

  /**
   * Turns arrays of equal length, one for each column, into rows. It uses the {@code unnest} of
   * Postgres on several arrays, because {@code DSL.unnest} of jOOQ takes only one. The columns are
   * {@code c1}, {@code c2} and so on.
   */
  private static Table<?> unnest(Field<?>... columns) {
    var names = new String[columns.length];
    for (var i = 0; i < columns.length; i++) {
      names[i] = "c" + (i + 1);
    }
    return table("unnest({0})", list(columns)).as("row", names);
  }

  /** Returns a column of the unnested rows, with the type of the table column that it goes into. */
  private static <T> Field<T> column(Table<?> rows, int index, Field<T> into) {
    return field(name(rows.getName(), "c" + index), into.getDataType());
  }
}
