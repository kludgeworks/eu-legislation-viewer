// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.legaltext;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers the library's renderer, {@link LegalRenderer}. The library is outside the packages that
 * the viewer scans, so the viewer imports this configuration.
 */
@Configuration(proxyBeanMethods = false)
@Import(LegalRenderer.class)
public class LegalTextConfiguration {}
