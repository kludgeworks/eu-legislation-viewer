// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a request of the API whose act the catalogue does not have yet with 202 Accepted: the
 * viewer looks the act up. {@code Retry-After} says when to ask again, and {@code Location} is the
 * address to ask.
 */
@RestControllerAdvice(
    assignableTypes = {
      ApiActController.class,
      ApiPreparationController.class,
      ApiTextController.class
    })
class ApiNotCataloguedAdvice {

  /** The seconds after which a client asks again. A lookup takes one to a few seconds. */
  static final String RETRY_AFTER = "2";

  @ExceptionHandler(NotCatalogued.class)
  ResponseEntity<Void> notCatalogued(NotCatalogued notCatalogued, HttpServletRequest request) {
    var query = request.getQueryString();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER)
        .location(URI.create(request.getRequestURI() + (query == null ? "" : "?" + query)))
        .build();
  }
}
