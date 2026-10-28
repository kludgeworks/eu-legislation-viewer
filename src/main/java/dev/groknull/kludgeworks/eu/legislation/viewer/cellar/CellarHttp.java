// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Every request to Cellar: at most {@code maxConcurrentRequests} in flight, starts at least {@code
 * minInterval} apart, and retried with exponential backoff on what may pass (a network error, a
 * 5xx, a 429). Other 4xx are not retried.
 */
final class CellarHttp {
  private final RestClient client;
  private final CellarProperties properties;
  private final Semaphore permits;
  private final RetryTemplate retry;
  private long nextStart = System.nanoTime();

  CellarHttp(RestClient client, CellarProperties properties) {
    this.client = client;
    this.properties = properties;
    this.permits = new Semaphore(properties.maxConcurrentRequests(), true);
    this.retry =
        new RetryTemplate(
            RetryPolicy.builder()
                .maxRetries(properties.retries())
                .delay(properties.retryDelay())
                .multiplier(2)
                .predicate(CellarHttp::mayPass)
                .build());
  }

  RestClient client() {
    return client;
  }

  CellarProperties properties() {
    return properties;
  }

  /** Runs one request under the throttle, retrying it while the failure may pass. */
  <T> T call(Supplier<T> request) {
    try {
      return retry.execute(() -> throttled(request));
    } catch (RetryException e) {
      var cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new CellarException("Cellar request failed", e);
    }
  }

  private <T> T throttled(Supplier<T> request) {
    permits.acquireUninterruptibly();
    try {
      pace();
      return request.get();
    } finally {
      permits.release();
    }
  }

  /** Waits until the next start is due; starts are spaced even when several run at once. */
  private synchronized void pace() {
    var now = System.nanoTime();
    if (nextStart > now) {
      try {
        TimeUnit.NANOSECONDS.sleep(nextStart - now);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CellarException("Interrupted waiting to call Cellar", e);
      }
    }
    nextStart = Math.max(now, nextStart) + properties.minInterval().toNanos();
  }

  private static boolean mayPass(Throwable failure) {
    return failure instanceof ResourceAccessException
        || failure instanceof HttpServerErrorException
        || failure instanceof HttpClientErrorException.TooManyRequests;
  }
}
