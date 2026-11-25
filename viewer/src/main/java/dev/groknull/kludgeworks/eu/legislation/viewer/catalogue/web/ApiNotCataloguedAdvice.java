// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NoSuchAct;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NotCatalogued;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
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
      ApiAmendmentsController.class,
      ApiLawController.class,
      ApiPreparationController.class,
      ApiTextController.class,
      ApiVersionController.class
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

  /** Answers a request whose act no act has the identifier of with 404. */
  @ExceptionHandler(NoSuchAct.class)
  ErrorResponse noSuchAct() {
    return Requests.notFound();
  }
}
