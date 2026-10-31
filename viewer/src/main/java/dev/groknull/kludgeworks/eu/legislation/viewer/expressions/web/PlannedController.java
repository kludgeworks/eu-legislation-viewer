// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The text resources planned (URLs and API: public API), each 501 until built: one unit of a
 * version's text, and comparisons of two versions, mirroring them: the language-neutral comparison,
 * its text, one unit's, and any two dates (to answer 303 with the comparison of the versions that
 * apply on them).
 */
@RestController
@RequestMapping("/api/acts/{act}")
class PlannedController {
  static final String DATE = "[0-9]{4}-[0-9]{2}-[0-9]{2}";

  @GetMapping("/versions/{date:" + DATE + "}/texts/{lang}/units/{*eId}")
  ErrorResponse unit() {
    return notImplemented("A unit of a version's text");
  }

  @GetMapping(
      path = "/comparisons",
      params = {"from", "to"})
  ErrorResponse comparisonOn() {
    return notImplemented("Comparing the versions that apply on two dates");
  }

  @GetMapping("/comparisons/{from:" + DATE + "}..{to:" + DATE + "}")
  ErrorResponse comparison() {
    return notImplemented("Comparing two versions");
  }

  @GetMapping("/comparisons/{from:" + DATE + "}..{to:" + DATE + "}/texts/{lang}")
  ErrorResponse comparisonText() {
    return notImplemented("Comparing two versions' texts");
  }

  @GetMapping("/comparisons/{from:" + DATE + "}..{to:" + DATE + "}/texts/{lang}/units/{*eId}")
  ErrorResponse comparisonUnit() {
    return notImplemented("Comparing a unit of two versions' texts");
  }

  /**
   * 501 for a resource planned but not built yet, linked to while in development: the status says
   * it all, so a problem of no type of its own (RFC 9457's about:blank); never stored, as a cache
   * may keep a 501 by default (RFC 9110), and it won't be one for long.
   */
  private static ErrorResponse notImplemented(String what) {
    return ErrorResponse.builder(
            new UnsupportedOperationException(what),
            HttpStatus.NOT_IMPLEMENTED,
            what + " isn't built yet")
        .headers(headers -> headers.setCacheControl(CacheControl.noStore()))
        .build();
  }
}
