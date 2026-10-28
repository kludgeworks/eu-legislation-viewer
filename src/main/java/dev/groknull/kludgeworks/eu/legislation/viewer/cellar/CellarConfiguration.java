// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The HTTP client and the request discipline every call to Cellar goes through. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CellarProperties.class)
class CellarConfiguration {

  @Bean
  CellarHttp cellarHttp(CellarProperties properties) {
    return new CellarHttp(restClient(properties), properties);
  }

  /** The JDK's HTTP client, following Cellar's 303s from CELEX URIs to cellar URIs. */
  static RestClient restClient(CellarProperties properties) {
    var http =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    var requests = new JdkClientHttpRequestFactory(http);
    requests.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .requestFactory(requests)
        .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
        .build();
  }
}
