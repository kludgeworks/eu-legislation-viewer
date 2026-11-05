// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** The catalogue's properties. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogueProperties.class)
class CatalogueApplicationConfiguration {}
