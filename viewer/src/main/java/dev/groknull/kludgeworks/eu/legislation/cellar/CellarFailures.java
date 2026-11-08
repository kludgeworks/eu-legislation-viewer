// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.cellar;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/** Tells what a failed call to Cellar means about the state of Cellar. */
public final class CellarFailures {
  private CellarFailures() {}

  /**
   * Returns whether {@code failure} means that Cellar is not answering. This state may pass. It is
   * one of these cases:
   *
   * <ul>
   *   <li>The viewer cannot reach Cellar.
   *   <li>Cellar is too slow.
   *   <li>The connection breaks while the answer arrives.
   *   <li>Cellar answers that it cannot answer now, with a 5xx status or 429.
   * </ul>
   *
   * <p>It returns false for an answer the viewer cannot use. It also returns false when Cellar
   * refuses the request with another 4xx status.
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
        // The body of the answer broke off while RestClient's retrieve read it.
        case RestClientException client when client.getCause() instanceof IOException -> {
          return true;
        }
        default -> {}
      }
    }
    return false;
  }
}
