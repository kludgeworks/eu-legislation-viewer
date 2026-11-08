// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.cellar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The location of Cellar and how the viewer identifies itself to it. These are the {@code
 * viewer.cellar.*} properties in {@code application.yaml}. The Spring Boot properties {@code
 * spring.http.clients.*} set the timeouts and redirects.
 *
 * @param sparqlEndpoint the SPARQL endpoint
 * @param resources the start of Cellar's resource URIs. For example, {@code
 *     …/resource/celex/32015R1998} starts with it.
 * @param userAgent the User-Agent sent with every request. It tells who we are and how to contact
 *     us.
 * @param maxRows the row limit of the endpoint. The endpoint may have cut off a SPARQL result with
 *     this many rows.
 */
@ConfigurationProperties("viewer.cellar")
@Validated
record CellarProperties(
    @NotNull URI sparqlEndpoint,
    @NotNull URI resources,
    @NotBlank String userAgent,
    @Positive int maxRows) {}
