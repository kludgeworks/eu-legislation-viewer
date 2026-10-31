// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How converted texts are kept in Valkey ({@code viewer.expressions.store.*}, set in
 * application.yaml); where Valkey is, is Spring Boot's ({@code spring.data.redis.*}).
 *
 * @param lockTimeout how long one conversion may hold the lock that keeps others from converting
 *     the same text; longer than the largest conversion (2005/396's 77 MB)
 * @param pollInterval how often a request waiting on another's conversion looks for its result
 * @param keepFor how long a text is kept after it was last read: texts no one reads any more, among
 *     them an old converter's and those of Formex Cellar has republished, go on their own
 * @param absentFor how long a document without Formex in a language is remembered as such
 * @param failedFor how long a text the converter refused or failed on is remembered as such: it
 *     stays so until the converter changes (part of the key) or Cellar publishes new Formex
 */
@ConfigurationProperties("viewer.expressions.store")
@Validated
record StoreProperties(
    @NotNull Duration lockTimeout,
    @NotNull Duration pollInterval,
    @NotNull Duration keepFor,
    @NotNull Duration absentFor,
    @NotNull Duration failedFor) {}
