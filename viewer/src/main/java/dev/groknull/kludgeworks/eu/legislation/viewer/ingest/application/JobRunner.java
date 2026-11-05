// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionDeferred;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionFailed;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.ExpressionStarted;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ConversionException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ExpressionRequested;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJob;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.IngestJobs;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.MisfiledFormexException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.OutOfScopeException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.SourceUnavailableException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.UnsupportedFormexException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Running a requested job, on one of this instance's workers once its request is committed
 * (ExpressionRequested, from the event registry): started (ExpressionStarted), unless it is running
 * or done already; then the expression's Formex converted, its text stored, the job marked built
 * and ExpressionBuilt published, those three in one transaction; or the job failed, with
 * ExpressionFailed. A refusal (no Formex, not a legal act, Formex not handled) fails it at once,
 * and so does anything else on its last attempt (viewer.ingest.max-attempts), as unavailable if it
 * was Cellar not answering (SourceUnavailableException), as an error if not; before that, it is
 * back in the queue (ExpressionDeferred) and its request failed, for the registry to hand out again
 * a little later (EventsConfiguration's resubmission).
 *
 * <p>Its work is a span (ingest.job) in the trace of the request that scheduled it, and its log
 * lines carry the job, the CELEX and the language (MDC). Each job's outcome is logged once, here. A
 * job started again elsewhere meanwhile (taken over: viewer.ingest.takeover) can't be finished
 * here: the start changed its version.
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

  /**
   * The request's job, run: done once it is built, failed or not this worker's to run; failed if it
   * is deferred, so the registry hands the request out again.
   */
  @Async(IngestApplicationConfiguration.WORKERS)
  @TransactionalEventListener
  CompletableFuture<@Nullable Void> on(ExpressionRequested requested) {
    var started = Objects.requireNonNull(transactions.execute(status -> start(requested)));
    if (started.isEmpty()) {
      LOG.debug(
          "{} in {} is running or done: nothing to run",
          requested.celex(),
          requested.language().code());
      return CompletableFuture.completedFuture(null);
    }
    var deferred = run(started.get());
    return deferred == null
        ? CompletableFuture.completedFuture(null)
        : CompletableFuture.failedFuture(deferred);
  }

  private Optional<IngestJob> start(ExpressionRequested requested) {
    var job = jobs.start(requested.celex(), requested.language(), properties.takeover());
    job.ifPresent(
        each ->
            events.publishEvent(
                new ExpressionStarted(each.celex(), each.language(), each.attempts())));
    return job;
  }

  /** Runs the started job; why it was deferred, if it was. */
  private @Nullable RuntimeException run(IngestJob job) {
    var span = span(job);
    try (var id = MDC.putCloseable("job", String.valueOf(job.id()));
        var celex = MDC.putCloseable("celex", job.celex());
        var language = MDC.putCloseable("language", job.language().code());
        var scope = tracer.withSpan(span)) {
      return build(job);
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

  private @Nullable RuntimeException build(IngestJob started) {
    var start = System.nanoTime();
    // the job as last written: each write changes its version
    var job = started;
    try {
      var text = conversions.convert(job.celex(), job.language());
      if (text.isEmpty()) {
        fail(job, Failure.NO_FORMEX, "Cellar has no Formex of it in the language");
        return null;
      }
      job = jobs.save(job.storing());
      var storing = job;
      var converter = conversions.converterVersion();
      var root =
          Objects.requireNonNull(
              transactions.execute(
                  status -> {
                    var stored = content.store(text.get());
                    jobs.save(storing.built(stored, converter));
                    events.publishEvent(
                        new ExpressionBuilt(
                            storing.celex(), storing.language(), stored, converter));
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
    } catch (MisfiledFormexException e) {
      fail(job, Failure.MISFILED, detail(e));
    } catch (UnsupportedFormexException e) {
      fail(job, Failure.UNSUPPORTED, detail(e));
    } catch (OptimisticLockingFailureException e) {
      LOG.warn(
          "Gave up {} in {}: started again by another worker, after {} ms",
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
        fail(
            job,
            e instanceof SourceUnavailableException ? Failure.UNAVAILABLE : Failure.ERROR,
            e.toString());
      } else {
        LOG.warn(
            "Attempt {} at {} in {} failed after {} ms, to be tried again: {}",
            job.attempts(),
            job.celex(),
            job.language().code(),
            millis(start),
            e.toString());
        defer(job, e.toString());
        return e;
      }
    }
    return null;
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

  private void defer(IngestJob job, String detail) {
    transactions.executeWithoutResult(
        status -> {
          jobs.save(job.retry());
          events.publishEvent(
              new ExpressionDeferred(job.celex(), job.language(), job.attempts(), detail));
        });
  }

  private static String detail(RuntimeException e) {
    return Objects.requireNonNullElse(e.getMessage(), e.toString());
  }

  private static long millis(long since) {
    return Duration.ofNanos(System.nanoTime() - since).toMillis();
  }
}
