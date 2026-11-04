// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.WebUtils;

/**
 * What every page has around it (its chrome, as UI design calls what frames the content: the site
 * bar, the notice, the theme switch), from the request: a controller makes it (of) and passes it to
 * its page's template, for the layout.
 *
 * @param theme the reader's colour theme, from the theme cookie; SYSTEM without one
 * @param here the page's path and query, where the theme switch comes back to
 */
public record Chrome(Theme theme, String here) {
  /** The cookie the reader's theme is kept in. */
  public static final String THEME = "theme";

  /** The chrome of the page {@code request} asks for. */
  public static Chrome of(HttpServletRequest request) {
    var cookie = WebUtils.getCookie(request, THEME);
    var query = request.getQueryString();
    return new Chrome(
        Theme.ofCode(cookie == null ? null : cookie.getValue()).orElse(Theme.SYSTEM),
        request.getRequestURI() + (query == null ? "" : "?" + query));
  }
}
