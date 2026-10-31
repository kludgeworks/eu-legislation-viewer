// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.api.ApiProblems;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The text resources planned (URLs and API: public API), each 501 until built: a version's text in
 * a language and one unit of it, and comparisons of two versions, mirroring them: the
 * language-neutral comparison, its text, one unit's, and any two dates (to answer 303 with the
 * comparison of the versions that apply on them).
 */
@RestController
@RequestMapping("/api/acts/{act}")
class PlannedController {
  static final String DATE = "[0-9]{4}-[0-9]{2}-[0-9]{2}";

  @GetMapping("/versions/{date:" + DATE + "}/texts/{lang}")
  ResponseEntity<ProblemDetail> text() {
    return ApiProblems.notImplemented("A version's text");
  }

  @GetMapping("/versions/{date:" + DATE + "}/texts/{lang}/units/{*eId}")
  ResponseEntity<ProblemDetail> unit() {
    return ApiProblems.notImplemented("A unit of a version's text");
  }

  @GetMapping(
      path = "/comparisons",
      params = {"from", "to"})
  ResponseEntity<ProblemDetail> comparisonOn() {
    return ApiProblems.notImplemented("Comparing the versions that apply on two dates");
  }

  @GetMapping("/comparisons/{from:" + DATE + "}..{to:" + DATE + "}")
  ResponseEntity<ProblemDetail> comparison() {
    return ApiProblems.notImplemented("Comparing two versions");
  }

  @GetMapping("/comparisons/{from:" + DATE + "}..{to:" + DATE + "}/texts/{lang}")
  ResponseEntity<ProblemDetail> comparisonText() {
    return ApiProblems.notImplemented("Comparing two versions' texts");
  }

  @GetMapping("/comparisons/{from:" + DATE + "}..{to:" + DATE + "}/texts/{lang}/units/{*eId}")
  ResponseEntity<ProblemDetail> comparisonUnit() {
    return ApiProblems.notImplemented("Comparing a unit of two versions' texts");
  }
}
