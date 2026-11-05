// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the catalogue keeps its acts ({@code viewer.catalogue.*}, set in application.yaml).
 *
 * @param refreshAfter how long an act is kept as Cellar had it before a page opening it has it read
 *     again (ActRefresher), for the versions that appeared since
 */
@ConfigurationProperties("viewer.catalogue")
@Validated
record CatalogueProperties(@NotNull Duration refreshAfter) {}
