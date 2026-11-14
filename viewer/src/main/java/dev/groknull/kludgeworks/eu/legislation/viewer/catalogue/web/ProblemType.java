// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The catalogue's own problem types of the API, as RFC 9457 has them. An error is a problem, {@code
 * application/problem+json}. A problem that the status alone explains has no type. A problem that a
 * program must tell from another with the same status has one of these. The address of the type
 * gives its documentation: {@code /api/docs#before-first-version}. Its text is the resource {@code
 * api/problems/before-first-version.html}, as {@link ApiDocsController} reads it.
 */
enum ProblemType {
  BEFORE_FIRST_VERSION("before-first-version", "Before the first version", HttpStatus.NOT_FOUND),
  TEXT_FAILED("text-failed", "The text could not be built", HttpStatus.NOT_FOUND);

  private final String name;
  private final String title;
  private final HttpStatus status;

  ProblemType(String name, String title, HttpStatus status) {
    this.name = name;
    this.title = title;
    this.status = status;
  }

  /** Returns a problem of this type, with its status and title, that says {@code detail}. */
  ProblemDetail problem(String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(
        URI.create(linkTo(methodOn(ApiRootController.class).root()).toUri() + "/docs#" + name));
    problem.setTitle(title);
    return problem;
  }
}
