// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Ingest;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJob;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJobs;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Scheduling an expression: a job queued, with the trace of the request that asked for it, and this
 * instance's workers woken to claim it (those of other instances find it when they next look).
 */
@Service
class IngestService implements Ingest {
  private static final Logger LOG = LoggerFactory.getLogger(IngestService.class);

  private final IngestJobs jobs;
  private final IngestWorkers workers;
  private final Tracer tracer;
  private final Propagator propagator;

  IngestService(IngestJobs jobs, IngestWorkers workers, Tracer tracer, Propagator propagator) {
    this.jobs = jobs;
    this.workers = workers;
    this.tracer = tracer;
    this.propagator = propagator;
  }

  @Override
  public IngestStatus schedule(String celex, Language language) {
    if (jobs.schedule(celex, language, traceparent())) {
      LOG.info("Queued {} in {}", celex, language.code());
      workers.wake();
    }
    return jobs.find(celex, language).orElseThrow().status();
  }

  @Override
  public Optional<IngestStatus> status(String celex, Language language) {
    return jobs.find(celex, language).map(IngestJob::status);
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
