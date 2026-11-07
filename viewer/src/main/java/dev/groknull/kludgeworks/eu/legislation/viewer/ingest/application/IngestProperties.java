// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The settings for how ingest jobs run. They are the {@code viewer.ingest.*} properties, set in
 * {@code application.yaml}.
 *
 * @param workers how many jobs an instance runs at once, on the executor of {@link JobRunner}. The
 *     other jobs wait their turn.
 * @param takeover how long a job may run before another worker may start it again. After this time,
 *     the first worker is taken to have stopped. It must be longer than the slowest conversion.
 * @param maxAttempts how many times a job is tried before it fails
 * @param maxConversions how many conversions run at once, in {@code FormexConversion}
 */
@ConfigurationProperties("viewer.ingest")
@Validated
record IngestProperties(
    @Positive int workers,
    @NotNull Duration takeover,
    @Positive int maxAttempts,
    @Positive int maxConversions) {}
