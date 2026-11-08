// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Configures the {@code RestClient} that every call to Cellar uses. It is built from Spring Boot's
 * builder, so the {@code spring.http.clients.*} properties in {@code application.yaml} set its
 * timeouts and redirects. It sends our User-Agent with every request.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CellarProperties.class)
class CellarConfiguration {
  static final String CLIENT = "cellarRestClient";

  @Bean(CLIENT)
  @Qualifier(CLIENT)
  RestClient cellarRestClient(RestClient.Builder builder, CellarProperties properties) {
    return builder.defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent()).build();
  }
}
