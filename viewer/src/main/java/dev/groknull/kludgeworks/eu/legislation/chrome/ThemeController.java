// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.chrome;

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
 * The theme switch. The form on every page posts the choice of the reader here. A cookie keeps the
 * choice, and the server renders the {@code data-theme} of the page from it.
 *
 * <ul>
 *   <li>A post of the form redirects back to the page that it came from, with 303.
 *   <li>{@code /js/theme.js} switches the page in place first. Then it posts the same form with
 *       fetch and gets 204, no content.
 * </ul>
 *
 * <p>The cookie is set only when the reader chooses, and it holds only the choice. It is a
 * user-interface customisation, which needs no consent. See Article 29 Working Party, Opinion
 * 04/2012.
 */
@Controller
class ThemeController {
  /** The longest time that browsers keep a cookie: 400 days, as RFC 6265bis sets. */
  private static final Duration KEPT = Duration.ofDays(400);

  /**
   * Keeps the theme {@code set} in a cookie. Returns 400 for an unknown theme.
   *
   * <p>If the request is a post of the form, it redirects to {@code back}. A post of the form is a
   * navigation, as the {@code Sec-Fetch-Mode} header says, or a request without that header. Any
   * other request gets 204. If {@code back} is not a page of this site, it redirects to the home
   * page. Thus the form cannot send a reader to another site.
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

  /**
   * Returns {@code back} if it is a path on this site, with no scheme and no host. Otherwise
   * returns the home page.
   */
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
      // It is not an address, so return the home page.
    }
    return URI.create("/");
  }
}
