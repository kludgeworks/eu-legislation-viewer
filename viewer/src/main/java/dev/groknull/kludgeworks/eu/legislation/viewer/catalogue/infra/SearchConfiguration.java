// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** The tuning of the search of acts. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SearchTuning.class)
class SearchConfiguration {}
