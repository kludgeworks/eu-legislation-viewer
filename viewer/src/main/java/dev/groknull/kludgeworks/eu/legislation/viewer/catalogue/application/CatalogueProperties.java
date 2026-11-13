// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the catalogue learns of changes in Cellar. The properties are {@code viewer.catalogue.*}, set
 * in {@code application.yaml}.
 *
 * @param changes how the held acts are kept as Cellar has them
 */
@ConfigurationProperties("viewer.catalogue")
@Validated
record CatalogueProperties(@NotNull @Valid Changes changes) {

  /**
   * How the held acts are kept as Cellar has them ({@link ChangeScan}, {@link ActRefresher}).
   *
   * @param every how often one instance asks Cellar what changed. Each time is one SPARQL query.
   * @param overlap how far before the latest change it read each scan reads again. A change becomes
   *     visible up to 18 minutes after its date, so a scan from the latest date alone would miss
   *     it.
   * @param lag how long after its date Cellar shows a change, at most. A held act is read again no
   *     sooner than this after its latest change, so that the read has every change it was told of.
   * @param quiet how long after its latest change a held act is read again
   * @param atMost how long after its first change a held act is read again, at most, if it keeps
   *     changing
   * @param withdrawnAfter how long apart two reads that do not have a version are, at least, before
   *     the version is withdrawn
   */
  record Changes(
      @NotNull Duration every,
      @NotNull Duration overlap,
      @NotNull Duration lag,
      @NotNull Duration quiet,
      @NotNull Duration atMost,
      @NotNull Duration withdrawnAfter) {

    /** How an act waits before it is read again. */
    PendingChange.Debounce debounce() {
      return new PendingChange.Debounce(quiet, atMost, lag);
    }
  }
}
