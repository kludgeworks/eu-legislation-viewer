// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
@Validated
record CellarProperties(
    @NotNull URI sparqlEndpoint,
    @NotNull URI resources,
    @NotBlank String userAgent,
    @Positive int maxRows) {}
