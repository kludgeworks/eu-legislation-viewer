// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.grafana.LgtmStackContainer;

/**
 * What running locally sees of the app's traces and metrics: Grafana's OpenTelemetry stack (LGTM:
 * Loki, Grafana, Tempo, Mimir) in a container, the app's OTLP endpoints through Boot's service
 * connection. The container logs Grafana's address as it starts ("Access to the Grafana
 * dashboard"); its login is admin, admin.
 */
@TestConfiguration(proxyBeanMethods = false)
class ObservabilityContainers {

  @Bean
  @ServiceConnection
  LgtmStackContainer lgtmContainer() {
    return new LgtmStackContainer("grafana/otel-lgtm:0.36.0").withReuse(true);
  }
}
