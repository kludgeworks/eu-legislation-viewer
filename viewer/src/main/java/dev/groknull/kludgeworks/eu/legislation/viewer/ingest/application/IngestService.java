// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Ingest;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ExpressionRequested;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJob;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJobs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduling an expression: a job queued, with the trace of the request that asked for it, and
 * requested (ExpressionRequested) in its transaction, the request handed to a worker once it
 * commits (JobRunner). A job retried is queued again so, from its first attempt.
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

  @Override
  @Transactional
  public IngestStatus schedule(String celex, Language language) {
    var traceparent = traceparent();
    if (jobs.schedule(celex, language, traceparent)) {
      events.publishEvent(new ExpressionRequested(celex, language, traceparent));
      LOG.info("Queued {} in {}", celex, language.code());
    }
    return jobs.find(celex, language).orElseThrow().status();
  }

  @Override
  @Transactional
  public boolean retry(String celex, Language language) {
    var traceparent = traceparent();
    if (!jobs.retry(celex, language, traceparent)) {
      return false;
    }
    events.publishEvent(new ExpressionRequested(celex, language, traceparent));
    LOG.info("Queued {} in {} again", celex, language.code());
    return true;
  }

  @Override
  public Optional<IngestStatus> status(String celex, Language language) {
    return jobs.find(celex, language).map(IngestJob::status);
  }

  @Override
  public int ahead(Collection<String> celexes, Language language) {
    return jobs.ahead(celexes, language);
  }

  /** The current trace, as W3C Trace Context's header carries it; none outside a trace. */
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
