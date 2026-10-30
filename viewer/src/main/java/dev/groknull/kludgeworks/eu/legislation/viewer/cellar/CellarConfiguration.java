// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * The RestClient every call to Cellar goes through: Spring Boot's, so its timeouts and redirects
 * are Boot's {@code spring.http.clients.*} (application.yaml), with our User-Agent.
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
