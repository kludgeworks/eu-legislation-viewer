// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for Formex image fetching, WebP rendering, and the Postgres LRU cache ({@code
 * viewer.image.*}).
 *
 * @param maxFetches maximum number of concurrent Cellar TIFF downloads
 * @param maxConversions maximum number of concurrent TIFF decode/scale/encode operations
 * @param maxSourceBytes maximum downloaded TIFF size in bytes
 * @param waitTimeout total time a cache miss or joining request waits for permits or an in-flight
 *     conversion before failing as unavailable, bounding HTTP request blocking
 * @param cache LRU cache settings
 */
@ConfigurationProperties("viewer.image")
@Validated
public record ImageProperties(
    @Positive int maxFetches,
    @Positive int maxConversions,
    @Positive long maxSourceBytes,
    @NotNull @DurationMin(nanos = 1) Duration waitTimeout,
    @NotNull @Valid Cache cache) {

  /**
   * Size-bounded Postgres LRU settings ({@code viewer.image.cache.*}).
   *
   * @param maxBytes maximum total rendition bytes kept in Postgres
   * @param evictionBatch number of oldest renditions deleted per eviction batch
   * @param touchInterval minimum interval before updating a rendition's {@code used_at} timestamp
   */
  public record Cache(
      @Positive long maxBytes, @Positive int evictionBatch, @NotNull Duration touchInterval) {}
}
