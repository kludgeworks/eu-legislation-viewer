// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.grafana.LgtmStackContainer;

/**
 * Shows the app's traces and metrics when it runs locally. Grafana's OpenTelemetry stack runs in a
 * container. The stack is LGTM: Loki, Grafana, Tempo and Mimir. Boot's service connection gives the
 * app its OTLP endpoints. The container logs the address of Grafana when it starts, in the line
 * "Access to the Grafana dashboard". The login is admin, with the password admin.
 */
@TestConfiguration(proxyBeanMethods = false)
class ObservabilityContainers {

  @Bean
  @ServiceConnection
  LgtmStackContainer lgtmContainer() {
    return new LgtmStackContainer("grafana/otel-lgtm:0.36.0").withReuse(true);
  }
}
