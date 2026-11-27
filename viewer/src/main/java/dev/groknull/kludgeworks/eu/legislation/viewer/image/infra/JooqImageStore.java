// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.infra;

import static dev.groknull.kludgeworks.eu.legislation.viewer.image.infra.jooq.Tables.IMAGE_RENDITION;
import static dev.groknull.kludgeworks.eu.legislation.viewer.image.infra.jooq.Tables.IMAGE_SOURCE;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.sum;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.application.ImageStore;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.model.ImageSource;
import java.net.URI;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.types.DayToSecond;
import org.springframework.stereotype.Repository;

/** Postgres storage for image sources and size-limited WebP images through jOOQ. */
@Repository
class JooqImageStore implements ImageStore {

  private final DSLContext db;

  JooqImageStore(DSLContext db) {
    this.db = db;
  }

  @Override
  public void replaceSources(TextId text, URI manifestation, List<Images.Source> sources) {
    db.deleteFrom(IMAGE_SOURCE).where(IMAGE_SOURCE.TEXT_ID.eq(text)).execute();
    if (sources.isEmpty()) {
      return;
    }
    var insert =
        sources.stream()
            .map(source ->
                db.insertInto(IMAGE_SOURCE)
                    .set(IMAGE_SOURCE.TEXT_ID, text)
                    .set(IMAGE_SOURCE.FILE_NAME, source.file())
                    .set(IMAGE_SOURCE.MANIFESTATION_URI, manifestation.toString())
                    .set(IMAGE_SOURCE.ITEM_URI, source.uri().toString())
                    .set(IMAGE_SOURCE.MEDIA_TYPE, source.mediaType())
                    .set(IMAGE_SOURCE.RDF_SHA1, source.rdfSha1()))
            .toList();
    db.batch(insert).execute();
  }

  @Override
  public Optional<ImageSource> findSource(TextId text, String file) {
    return db.select(
            IMAGE_SOURCE.MANIFESTATION_URI,
            IMAGE_SOURCE.ITEM_URI,
            IMAGE_SOURCE.MEDIA_TYPE,
            IMAGE_SOURCE.RDF_SHA1,
            IMAGE_SOURCE.SOURCE_SHA256,
            IMAGE_SOURCE.GONE_REASON)
        .from(IMAGE_SOURCE)
        .where(IMAGE_SOURCE.TEXT_ID.eq(text), IMAGE_SOURCE.FILE_NAME.eq(file))
        .fetchOptional(row ->
            new ImageSource(
                text,
                file,
                URI.create(Objects.requireNonNull(row.value1())),
                URI.create(Objects.requireNonNull(row.value2())),
                Objects.requireNonNull(row.value3()),
                row.value4(),
                row.value5(),
                row.value6()));
  }

  @Override
  public Optional<byte[]> findSourceDigest(TextId text, String file) {
    return db.select(IMAGE_SOURCE.SOURCE_SHA256)
        .from(IMAGE_SOURCE)
        .where(
            IMAGE_SOURCE.TEXT_ID.eq(text),
            IMAGE_SOURCE.FILE_NAME.eq(file),
            IMAGE_SOURCE.SOURCE_SHA256.isNotNull())
        .fetchOptional(row -> Objects.requireNonNull(row.value1()));
  }

  @Override
  public Map<String, URI> sourceUris(TextId text) {
    return db.select(IMAGE_SOURCE.FILE_NAME, IMAGE_SOURCE.ITEM_URI)
        .from(IMAGE_SOURCE)
        .where(IMAGE_SOURCE.TEXT_ID.eq(text))
        .fetchMap(
            row -> Objects.requireNonNull(row.value1()),
            row -> URI.create(Objects.requireNonNull(row.value2())));
  }

  @Override
  public void recordSourceDigest(TextId text, String file, byte[] sha256) {
    db.update(IMAGE_SOURCE)
        .set(IMAGE_SOURCE.SOURCE_SHA256, sha256)
        .where(
            IMAGE_SOURCE.TEXT_ID.eq(text),
            IMAGE_SOURCE.FILE_NAME.eq(file),
            IMAGE_SOURCE.SOURCE_SHA256.isNull())
        .execute();
  }

  @Override
  public void markGone(TextId text, String file, String reason) {
    db.update(IMAGE_SOURCE)
        .set(IMAGE_SOURCE.GONE_REASON, reason)
        .where(
            IMAGE_SOURCE.TEXT_ID.eq(text),
            IMAGE_SOURCE.FILE_NAME.eq(file),
            IMAGE_SOURCE.GONE_REASON.isNull())
        .execute();
  }

  @Override
  public void removeSources(TextId text) {
    db.deleteFrom(IMAGE_SOURCE).where(IMAGE_SOURCE.TEXT_ID.eq(text)).execute();
  }

  @Override
  public Optional<Images.Rendition> findRendition(
      byte[] sha256, Images.Variant variant, String recipe) {
    var sha256Hex = HexFormat.of().formatHex(sha256);
    return db.select(IMAGE_RENDITION.WEBP, IMAGE_RENDITION.WIDTH, IMAGE_RENDITION.HEIGHT)
        .from(IMAGE_RENDITION)
        .where(
            IMAGE_RENDITION.SOURCE_SHA256.eq(sha256),
            IMAGE_RENDITION.VARIANT.eq(variant),
            IMAGE_RENDITION.RECIPE.eq(recipe))
        .fetchOptional(row ->
            new Images.Rendition(
                Objects.requireNonNull(row.value1()),
                sha256Hex,
                recipe,
                Objects.requireNonNull(row.value2()),
                Objects.requireNonNull(row.value3())));
  }

  @Override
  public void touchRenditionIfStale(
      byte[] sha256, Images.Variant variant, String recipe, Duration minAge) {
    db.update(IMAGE_RENDITION)
        .set(IMAGE_RENDITION.USED_AT, currentInstant())
        .where(
            IMAGE_RENDITION.SOURCE_SHA256.eq(sha256),
            IMAGE_RENDITION.VARIANT.eq(variant),
            IMAGE_RENDITION.RECIPE.eq(recipe),
            IMAGE_RENDITION.USED_AT.lt(currentInstant().minus(DayToSecond.valueOf(minAge))))
        .execute();
  }

  @Override
  public void storeRendition(
      byte[] sha256,
      Images.Variant variant,
      String recipe,
      int width,
      int height,
      byte[] webp,
      long maxBytes,
      int evictionBatch) {
    db.insertInto(IMAGE_RENDITION)
        .set(IMAGE_RENDITION.SOURCE_SHA256, sha256)
        .set(IMAGE_RENDITION.VARIANT, variant)
        .set(IMAGE_RENDITION.RECIPE, recipe)
        .set(IMAGE_RENDITION.WIDTH, width)
        .set(IMAGE_RENDITION.HEIGHT, height)
        .set(IMAGE_RENDITION.SIZE_BYTES, webp.length)
        .set(IMAGE_RENDITION.USED_AT, currentInstant())
        .set(IMAGE_RENDITION.WEBP, webp)
        .onConflict(IMAGE_RENDITION.SOURCE_SHA256, IMAGE_RENDITION.VARIANT, IMAGE_RENDITION.RECIPE)
        .doUpdate()
        .set(IMAGE_RENDITION.USED_AT, currentInstant())
        .execute();
    evictIfOverBudget(sha256, variant, recipe, maxBytes, evictionBatch);
  }

  private void evictIfOverBudget(
      byte[] keptSha256,
      Images.Variant keptVariant,
      String keptRecipe,
      long maxBytes,
      int evictionBatch) {
    var total =
        db.select(sum(IMAGE_RENDITION.SIZE_BYTES)).from(IMAGE_RENDITION).fetchOne(0, Long.class);
    while (total != null && total > maxBytes) {
      var victims =
          db.select(IMAGE_RENDITION.SOURCE_SHA256, IMAGE_RENDITION.VARIANT, IMAGE_RENDITION.RECIPE)
              .from(IMAGE_RENDITION)
              .where(
                  row(
                          IMAGE_RENDITION.SOURCE_SHA256,
                          IMAGE_RENDITION.VARIANT,
                          IMAGE_RENDITION.RECIPE)
                      .ne(row(keptSha256, keptVariant, keptRecipe)))
              .orderBy(IMAGE_RENDITION.USED_AT.asc())
              .limit(evictionBatch);
      var deleted =
          db.deleteFrom(IMAGE_RENDITION)
              .where(
                  row(
                          IMAGE_RENDITION.SOURCE_SHA256,
                          IMAGE_RENDITION.VARIANT,
                          IMAGE_RENDITION.RECIPE)
                      .in(victims))
              .returningResult(IMAGE_RENDITION.SIZE_BYTES)
              .fetch();
      if (deleted.isEmpty()) {
        break;
      }
      long freed = deleted.stream().mapToLong(row -> Objects.requireNonNull(row.value1())).sum();
      total -= freed;
    }
  }
}
