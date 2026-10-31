// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.api;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A CELEX with a slash (12016E/TXT, 11994N/AFI/DCL/48) as one path segment, its slash as %2F (URLs
 * and API: CELEX with a slash). Tomcat rejects a %2F by default (400); passed through, it stays in
 * its segment, which Spring MVC routes on and decodes into the path variable. Decoding it instead
 * would let Tomcat's path and Spring's disagree (12016E%2F..%2F..%2Fetc). Boot has no property for
 * it.
 */
@Configuration(proxyBeanMethods = false)
class ApiServerConfiguration {

  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashes() {
    return factory ->
        factory.addConnectorCustomizers(
            connector ->
                connector.setEncodedSolidusHandling(
                    EncodedSolidusHandling.PASS_THROUGH.getValue()));
  }
}
