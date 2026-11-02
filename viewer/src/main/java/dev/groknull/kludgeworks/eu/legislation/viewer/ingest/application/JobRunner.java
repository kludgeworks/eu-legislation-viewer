// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionFailed;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ConversionException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJob;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJobs;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.OutOfScopeException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.UnsupportedFormexException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Running a claimed job: the expression's Formex converted, its text stored, the job marked built
 * and ExpressionBuilt published, those three in one transaction; or the job failed, with
 * ExpressionFailed. A refusal (no Formex, not a legal act, Formex not handled) fails it at once;
 * anything else puts it back in the queue, until it has been tried viewer.ingest.max-attempts
 * times.
 *
 * <p>Its work is a span (ingest.job) in the trace of the request that scheduled it, and its log
 * lines carry the job, the CELEX and the language (MDC). Each job's outcome is logged once, here. A
 * job claimed again after this worker's lease ran out can't be finished here: the claim changed its
 * version.
 */
@Component
class JobRunner {
  private static final Logger LOG = LoggerFactory.getLogger(JobRunner.class);

  private final Conversions conversions;
  private final Content content;
  private final IngestJobs jobs;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transactions;
  private final Tracer tracer;
  private final Propagator propagator;
  private final IngestProperties properties;

  JobRunner(
      Conversions conversions,
      Content content,
      IngestJobs jobs,
      ApplicationEventPublisher events,
      TransactionTemplate transactions,
      Tracer tracer,
      Propagator propagator,
      IngestProperties properties) {
    this.conversions = conversions;
    this.content = content;
    this.jobs = jobs;
    this.events = events;
    this.transactions = transactions;
    this.tracer = tracer;
    this.propagator = propagator;
    this.properties = properties;
  }

  void run(IngestJob job) {
    var span = span(job);
    try (var id = MDC.putCloseable("job", String.valueOf(job.id()));
        var celex = MDC.putCloseable("celex", job.celex());
        var language = MDC.putCloseable("language", job.language().code());
        var scope = tracer.withSpan(span)) {
      build(job);
    } catch (RuntimeException e) {
      span.error(e);
      throw e;
    } finally {
      span.end();
    }
  }

  private Span span(IngestJob job) {
    var traceparent = job.traceparent();
    var builder =
        traceparent == null
            ? tracer.spanBuilder()
            : propagator.extract(Map.of("traceparent", traceparent), Map::get);
    return builder
        .name("ingest.job")
        .tag("celex", job.celex())
        .tag("language", job.language().code())
        .tag("attempt", String.valueOf(job.attempts()))
        .start();
  }

  private void build(IngestJob job) {
    var start = System.nanoTime();
    try {
      var text = conversions.convert(job.celex(), job.language());
      if (text.isEmpty()) {
        fail(job, Failure.NO_FORMEX, "Cellar has no Formex of it in the language");
        return;
      }
      var storing = jobs.save(job.storing());
      var converter = conversions.converterVersion();
      var root =
          Objects.requireNonNull(
              transactions.execute(
                  status -> {
                    var stored = content.store(text.get());
                    jobs.save(storing.built(stored));
                    events.publishEvent(
                        new ExpressionBuilt(job.celex(), job.language(), stored, converter));
                    return stored;
                  }));
      LOG.info(
          "Built {} in {} as {} in {} ms, attempt {}",
          job.celex(),
          job.language().code(),
          root,
          millis(start),
          job.attempts());
    } catch (OutOfScopeException e) {
      fail(job, Failure.OUT_OF_SCOPE, detail(e));
    } catch (UnsupportedFormexException e) {
      fail(job, Failure.UNSUPPORTED, detail(e));
    } catch (OptimisticLockingFailureException e) {
      LOG.warn(
          "Gave up {} in {}: claimed again by another worker, after {} ms",
          job.celex(),
          job.language().code(),
          millis(start));
    } catch (ConversionException e) {
      fail(job, Failure.ERROR, detail(e));
    } catch (RuntimeException e) {
      if (job.attempts() >= properties.maxAttempts()) {
        LOG.error(
            "Failed {} in {} after {} attempts",
            job.celex(),
            job.language().code(),
            job.attempts(),
            e);
        fail(job, Failure.ERROR, e.toString());
      } else {
        LOG.warn(
            "Attempt {} at {} in {} failed after {} ms, to be tried again: {}",
            job.attempts(),
            job.celex(),
            job.language().code(),
            millis(start),
            e.toString());
        jobs.save(job.retry());
      }
    }
  }

  private void fail(IngestJob job, Failure failure, String detail) {
    transactions.executeWithoutResult(
        status -> {
          jobs.save(job.failed(failure, detail));
          events.publishEvent(new ExpressionFailed(job.celex(), job.language(), failure, detail));
        });
    LOG.info(
        "Couldn't build {} in {}: {}, {}", job.celex(), job.language().code(), failure, detail);
  }

  private static String detail(RuntimeException e) {
    return Objects.requireNonNullElse(e.getMessage(), e.toString());
  }

  private static long millis(long since) {
    return Duration.ofNanos(System.nanoTime() - since).toMillis();
  }
}
