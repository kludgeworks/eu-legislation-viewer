// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import dev.groknull.kludgeworks.eu.legislation.legaltext.LegalTextConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers the beans of the library beside the modules, {@code legaltext}: legal text as HTML. Its
 * package is outside this one, so neither component scanning nor Spring Modulith finds it.
 */
@Configuration(proxyBeanMethods = false)
@Import(LegalTextConfiguration.class)
class LibrariesConfiguration {}
