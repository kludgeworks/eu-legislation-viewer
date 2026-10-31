// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * Cellar failing, for any request that needed it: 502 Bad Gateway, a problem detail of its own
 * type, apart from the 503 of a conversion still running. Cellar gave no answer (a
 * RestClientException: refused, timed out, an error status) or one the viewer can't use (a
 * CellarException). What went wrong is logged, not told: it names Cellar's internals.
 */
@RestControllerAdvice
class CellarProblems {
  static final URI UPSTREAM_UNAVAILABLE =
      URI.create("https://kludgeworks.groknull.dev/problems/upstream-unavailable");

  private static final Logger LOG = LoggerFactory.getLogger(CellarProblems.class);

  @ExceptionHandler({CellarException.class, RestClientException.class})
  ProblemDetail upstreamUnavailable(RuntimeException e) {
    LOG.warn("Cellar failed: {}", e.getMessage(), e);
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY,
            "Cellar, the EU's repository the viewer reads acts from, failed or gave an answer the"
                + " viewer can't use. Try again later.");
    problem.setType(UPSTREAM_UNAVAILABLE);
    problem.setTitle("Cellar unavailable");
    return problem;
  }
}
