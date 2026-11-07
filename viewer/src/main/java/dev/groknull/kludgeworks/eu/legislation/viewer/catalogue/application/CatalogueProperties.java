// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the catalogue stores its acts. The properties are {@code viewer.catalogue.*}, set in {@code
 * application.yaml}.
 *
 * @param refreshAfter how long an act stays stored as Cellar had it. After this time, a page that
 *     opens the act has {@link ActRefresher} read it again, for the versions that appeared since.
 */
@ConfigurationProperties("viewer.catalogue")
@Validated
record CatalogueProperties(@NotNull Duration refreshAfter) {}
