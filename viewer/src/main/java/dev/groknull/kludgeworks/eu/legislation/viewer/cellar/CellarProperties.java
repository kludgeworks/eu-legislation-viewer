// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where Cellar is and who we are to it ({@code viewer.cellar.*}, set in application.yaml); timeouts
 * and redirects are Spring Boot's ({@code spring.http.clients.*}).
 *
 * @param sparqlEndpoint the SPARQL endpoint
 * @param resources where Cellar's resource URIs start ({@code …/resource/celex/32015R1998})
 * @param userAgent sent with every request: who we are and where to reach us
 * @param maxRows a SPARQL result with this many rows may have been cut off by the endpoint
 */
@ConfigurationProperties("viewer.cellar")
record CellarProperties(URI sparqlEndpoint, URI resources, String userAgent, int maxRows) {}
