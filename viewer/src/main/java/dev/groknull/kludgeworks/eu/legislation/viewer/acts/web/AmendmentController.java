// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.api.ApiProblems;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An act's amendments, planned (URLs and API: public API): the effects of other acts on it, pending
 * and integrated (legislation.gov.uk's changes); 501 until built.
 */
@RestController
class AmendmentController {

  @GetMapping("/api/acts/{act}/amendments")
  ResponseEntity<ProblemDetail> amendments() {
    return ApiProblems.notImplemented("An act's amendments");
  }
}
