// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The ingest properties, and Spring's scheduling, for the workers' heartbeat (IngestWorkers). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestProperties.class)
@EnableScheduling
class IngestApplicationConfiguration {}
