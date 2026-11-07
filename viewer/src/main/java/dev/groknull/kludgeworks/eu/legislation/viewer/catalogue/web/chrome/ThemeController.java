// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The theme switch: the form on every page posts the reader's choice here, kept in a cookie for the
 * server to render the page's data-theme from. A form's post goes back to the page it was on (303);
 * /js/theme.js, which has already switched the page in place, posts the same form by fetch and gets
 * no content (204). The cookie is set only when the reader chooses, and holds only the choice: a
 * user-interface customisation, which needs no consent (Article 29 Working Party, Opinion 04/2012).
 */
@Controller
class ThemeController {
  /** As long as browsers keep a cookie: 400 days (RFC 6265bis). */
  private static final Duration KEPT = Duration.ofDays(400);

  /**
   * Keeps {@code set} and, for a form's post (a navigation, as Sec-Fetch-Mode says; any request
   * without it), goes back to {@code back}, a page of this site's; to the home page for any other
   * address, so the form can't send a reader elsewhere.
   */
  @PostMapping("/theme")
  ResponseEntity<Void> choose(
      @RequestParam String set,
      @RequestParam String back,
      @RequestHeader(name = "Sec-Fetch-Mode", required = false) @Nullable String mode,
      HttpServletRequest request) {
    var theme =
        Theme.ofCode(set).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
    var cookie =
        ResponseCookie.from(Chrome.THEME, theme.code())
            .path("/")
            .maxAge(KEPT)
            .httpOnly(true)
            .secure(request.isSecure())
            .sameSite("Lax")
            .build();
    if (mode != null && !mode.equals("navigate")) {
      return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }
    return ResponseEntity.status(HttpStatus.SEE_OTHER)
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .location(local(back))
        .build();
  }

  /** {@code back} if it is a path on this site: no scheme, no host; the home page otherwise. */
  static URI local(String back) {
    try {
      var uri = new URI(back);
      if (uri.getScheme() == null
          && uri.getRawAuthority() == null
          && uri.getRawPath() != null
          && uri.getRawPath().startsWith("/")) {
        return uri;
      }
    } catch (URISyntaxException ignored) {
      // not an address: home
    }
    return URI.create("/");
  }
}
