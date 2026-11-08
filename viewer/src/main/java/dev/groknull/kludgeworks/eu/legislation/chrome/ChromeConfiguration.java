// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.chrome;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers the library's controller, {@link ThemeController}. The library is outside the packages
 * that the viewer scans, so the viewer imports this configuration.
 */
@Configuration(proxyBeanMethods = false)
@Import(ThemeController.class)
public class ChromeConfiguration {}
