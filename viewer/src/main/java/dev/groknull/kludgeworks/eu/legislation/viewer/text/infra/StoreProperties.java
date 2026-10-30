// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How converted texts are kept in Valkey ({@code viewer.text.store.*}, set in application.yaml);
 * where Valkey is, is Spring Boot's ({@code spring.data.redis.*}).
 *
 * @param lockTimeout how long one conversion may hold the lock that keeps others from converting
 *     the same text; longer than the largest conversion (2005/396's 77 MB)
 * @param pollInterval how often a request waiting on another's conversion looks for its result
 * @param absentFor how long a document without Formex in a language is remembered as such
 */
@ConfigurationProperties("viewer.text.store")
@Validated
record StoreProperties(
    @NotNull Duration lockTimeout, @NotNull Duration pollInterval, @NotNull Duration absentFor) {}
