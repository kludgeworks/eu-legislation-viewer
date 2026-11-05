// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/** What a failure calling Cellar says of Cellar. */
public final class CellarFailures {
  private CellarFailures() {}

  /**
   * Whether {@code failure} is Cellar not answering, which may pass: not reached, too slow, the
   * connection lost while its answer came, or an answer that it can't answer now (a 5xx status, or
   * 429). Not an answer the viewer can't use, nor a refusal of what was asked (another 4xx).
   */
  public static boolean unavailable(Throwable failure) {
    for (Throwable each = failure; each != null; each = each.getCause()) {
      switch (each) {
        case ResourceAccessException _ -> {
          return true;
        }
        case HttpStatusCodeException status -> {
          return status.getStatusCode().is5xxServerError()
              || status.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
        }
        // its answer's body broke off as it was read (RestClient's retrieve)
        case RestClientException client when client.getCause() instanceof IOException -> {
          return true;
        }
        default -> {}
      }
    }
    return false;
  }
}
