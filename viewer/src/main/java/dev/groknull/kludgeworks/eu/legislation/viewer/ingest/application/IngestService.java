// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ConversionFailed;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ConversionRequested;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ConversionStarted;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Ingest;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.TextConverted;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ConversionQueued;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJob;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJobs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Queues the conversions that are asked for, and tells where they stand.
 *
 * <p>A text has one job, which is its status. The work itself is an event. When {@link
 * ConversionRequested} arrives, the service adds a job, or resets one to run again, and publishes
 * {@link ConversionQueued} in the same transaction, with the trace of the request that asked. When
 * the transaction commits, a worker in {@link JobRunner} gets it. For a job that it leaves as it
 * is, the service publishes where the job stands, so that whoever asked comes to know.
 */
@Service
class IngestService implements Ingest {
  private static final Logger LOG = LoggerFactory.getLogger(IngestService.class);

  private final IngestJobs jobs;
  private final ApplicationEventPublisher events;
  private final Tracer tracer;
  private final Propagator propagator;

  IngestService(
      IngestJobs jobs, ApplicationEventPublisher events, Tracer tracer, Propagator propagator) {
    this.jobs = jobs;
    this.events = events;
    this.tracer = tracer;
    this.propagator = propagator;
  }

  @ApplicationModuleListener
  void on(ConversionRequested requested) {
    var celex = requested.celex();
    var language = requested.language();
    var traceparent = traceparent();
    var queued =
        switch (requested.reason()) {
          case NEEDED -> false;
          case RETRIED -> jobs.resetRetryable(celex, language, traceparent);
          case REPUBLISHED -> jobs.resetFinished(celex, language, traceparent);
        };
    if (queued || jobs.add(celex, language, traceparent)) {
      events.publishEvent(new ConversionQueued(celex, language, traceparent));
      LOG.info("Queued {} in {}: {}", celex, language.code(), requested.reason());
    } else {
      jobs.find(celex, language).ifPresent(this::tell);
    }
  }

  /** Publishes where the job stands, if it started: the event that told it first, once more. */
  private void tell(IngestJob job) {
    var celex = job.celex();
    var language = job.language();
    // A queued job has nothing to tell: it waits its turn, as whoever asked takes it to.
    @Nullable Object told =
        switch (job.state()) {
          case QUEUED -> null;
          case RUNNING -> new ConversionStarted(celex, language, job.attempts());
          case BUILT -> new TextConverted(celex, language);
          case FAILED ->
              new ConversionFailed(
                  celex,
                  language,
                  Objects.requireNonNull(job.failure()),
                  Objects.requireNonNullElse(job.detail(), ""),
                  null);
        };
    if (told != null) {
      LOG.debug("{} in {} is {} already: telling so again", celex, language.code(), job.state());
      events.publishEvent(told);
    }
  }

  @Override
  public Optional<IngestStatus> status(String celex, Language language) {
    return jobs.find(celex, language).map(IngestJob::status);
  }

  @Override
  public int ahead(Collection<String> celexes, Language language) {
    return jobs.ahead(celexes, language);
  }

  /**
   * Returns the current trace, in the form of the W3C Trace Context header. Returns null outside a
   * trace.
   */
  private @Nullable String traceparent() {
    var context = tracer.currentTraceContext().context();
    if (context == null) {
      return null;
    }
    var headers = new HashMap<String, String>();
    propagator.inject(context, headers, (carrier, key, value) -> headers.put(key, value));
    return headers.get("traceparent");
  }
}
