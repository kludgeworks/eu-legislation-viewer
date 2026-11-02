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
 * @param workers how many jobs an instance runs at once
 * @param poll how often an idle worker looks for a job another instance queued
 * @param lease how long a job is held without its worker's heartbeat, before another may claim it
 * @param heartbeat how often a worker says it still holds its jobs: well within the lease
 * @param maxAttempts how often a job is tried before it fails
 * @param maxConversions how many conversions run at once (FormexConversion)
 */
@ConfigurationProperties("viewer.ingest")
@Validated
record IngestProperties(
    @Positive int workers,
    @NotNull Duration poll,
    @NotNull Duration lease,
    @NotNull Duration heartbeat,
    @Positive int maxAttempts,
    @Positive int maxConversions) {}
