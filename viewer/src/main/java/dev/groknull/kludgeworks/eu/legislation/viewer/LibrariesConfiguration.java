// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import dev.groknull.kludgeworks.eu.legislation.cellar.CellarConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers the beans of the libraries beside the modules. Their packages are outside this one, so
 * neither component scanning nor Spring Modulith finds them:
 *
 * <ul>
 *   <li>{@code cellar}: the client of Cellar, for the {@code infra} of the catalogue and of ingest.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Import(CellarConfiguration.class)
class LibrariesConfiguration {}
