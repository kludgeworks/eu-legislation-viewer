// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.api;

import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** The API's own answers as problems (RFC 9457). */
public final class ApiProblems {
  /** A resource planned but not built yet. */
  public static final URI NOT_IMPLEMENTED =
      URI.create("https://kludgeworks.groknull.dev/problems/not-implemented");

  private ApiProblems() {}

  /**
   * 501 for a resource the API has planned (URLs and API: public API) and links to while in
   * development: not 404, as it is known, nor 503, which is an outage. Never stored, as a cache may
   * keep a 501 by default (RFC 9110), and it won't be one for long.
   */
  public static ResponseEntity<ProblemDetail> notImplemented(String what) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, what + " isn't built yet");
    problem.setType(NOT_IMPLEMENTED);
    problem.setTitle("Not implemented yet");
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .cacheControl(CacheControl.noStore())
        .body(problem);
  }
}
