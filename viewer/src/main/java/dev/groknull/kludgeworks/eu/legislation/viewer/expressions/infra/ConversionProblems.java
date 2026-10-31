// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A text the converter couldn't give, for any request that needed it, each a problem detail of its
 * own type, apart from Cellar failing (CellarProblems): Formex the converter doesn't handle yet is
 * 501, never stored, as a new converter may handle it; a document that isn't a legal act is 404;
 * anything else is the viewer's own failure, 500, logged and not told.
 */
@RestControllerAdvice
class ConversionProblems {
  static final URI UNSUPPORTED_FORMEX =
      URI.create("https://kludgeworks.groknull.dev/problems/unsupported-formex");
  static final URI NOT_A_LEGAL_ACT =
      URI.create("https://kludgeworks.groknull.dev/problems/not-a-legal-act");
  static final URI CONVERSION_FAILED =
      URI.create("https://kludgeworks.groknull.dev/problems/conversion-failed");

  private static final Logger LOG = LoggerFactory.getLogger(ConversionProblems.class);

  @ExceptionHandler(UnsupportedFormexException.class)
  ResponseEntity<ProblemDetail> unsupportedFormex(UnsupportedFormexException e) {
    LOG.info("Formex not converted yet: {}", e.getMessage());
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_IMPLEMENTED,
            "The text uses Formex the viewer doesn't convert yet: " + e.getMessage());
    problem.setType(UNSUPPORTED_FORMEX);
    problem.setTitle("Formex not supported yet");
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .cacheControl(CacheControl.noStore())
        .body(problem);
  }

  @ExceptionHandler(OutOfScopeException.class)
  ProblemDetail notALegalAct(OutOfScopeException e) {
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "The document isn't a legal act, which is all the viewer gives the text of: "
                + e.getMessage());
    problem.setType(NOT_A_LEGAL_ACT);
    problem.setTitle("Not a legal act");
    return problem;
  }

  @ExceptionHandler({ConversionException.class, InvalidLegalTextException.class})
  ProblemDetail conversionFailed(RuntimeException e) {
    LOG.error("Conversion failed: {}", e.getMessage(), e);
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The viewer failed to convert the text from Cellar's Formex.");
    problem.setType(CONVERSION_FAILED);
    problem.setTitle("Conversion failed");
    return problem;
  }
}
