// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An act's amendments, planned (URLs and API: public API): the effects of other acts on it, pending
 * and integrated (legislation.gov.uk's changes); 501 until built.
 */
@RestController
class AmendmentController {

  @GetMapping("/api/acts/{act}/amendments")
  ErrorResponse amendments() {
    return notImplemented("An act's amendments");
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
