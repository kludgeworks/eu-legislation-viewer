// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How ingest jobs are run ({@code viewer.ingest.*}, set in application.yaml).
 *
 * @param workers how many jobs an instance runs at once (JobRunner's executor), the others waiting
 *     their turn
 * @param takeover how long a job may run before another worker may start it again, its own taken to
 *     have stopped: longer than the slowest conversion
 * @param maxAttempts how often a job is tried before it fails
 * @param maxConversions how many conversions run at once (FormexConversion)
 */
@ConfigurationProperties("viewer.ingest")
@Validated
record IngestProperties(
    @Positive int workers,
    @NotNull Duration takeover,
    @Positive int maxAttempts,
    @Positive int maxConversions) {}
