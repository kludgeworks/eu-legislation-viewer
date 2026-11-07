// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.resource.ResourceUrlProvider;
import org.springframework.web.servlet.resource.ResourceUrlProviderExposingInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.WebUtils;

/**
 * What every page has around it (its chrome, as UI design calls what frames the content: the site
 * bar, the notice, the theme switch, the way back to a search's results), from the request: a
 * controller makes it (of) and passes it to its page's template, for the layout.
 *
 * @param theme the reader's colour theme, from the theme cookie; SYSTEM without one
 * @param here the page's path and query, where the theme switch comes back to
 * @param assets where the page's stylesheet and scripts are
 * @param results the reader's last search, from the search cookie; none without one
 */
public record Chrome(Theme theme, String here, Assets assets, @Nullable Results results) {
  /** The cookie the reader's theme is kept in. */
  public static final String THEME = "theme";

  /**
   * The cookie the reader's last search is kept in, for a while (Design: Every page): the home
   * page's query, q=aviation+security&amp;type=regulation, so an act's address stays its own.
   */
  public static final String SEARCH = "search";

  /** The chrome of the page {@code request} asks for. */
  public static Chrome of(HttpServletRequest request) {
    var cookie = WebUtils.getCookie(request, THEME);
    var query = request.getQueryString();
    return new Chrome(
        Theme.ofCode(cookie == null ? null : cookie.getValue()).orElse(Theme.SYSTEM),
        request.getRequestURI() + (query == null ? "" : "?" + query),
        Assets.of(request),
        Results.of(WebUtils.getCookie(request, SEARCH)));
  }

  /**
   * A search's results, as the site bar links back to them: "‹ Results for “aviation security”".
   *
   * @param words what the reader searched for, as they typed it
   * @param href the results' address, rebuilt from the cookie's words and kind, not taken from it
   */
  public record Results(String words, String href) {
    static @Nullable Results of(@Nullable Cookie cookie) {
      if (cookie == null) {
        return null;
      }
      var query =
          UriComponentsBuilder.fromUriString("/?" + cookie.getValue()).build().getQueryParams();
      var words = decoded(query.getFirst("q"));
      if (words == null || words.isBlank()) {
        return null;
      }
      var type = decoded(query.getFirst("type"));
      var href = UriComponentsBuilder.fromPath("/").queryParam("q", words);
      if (type != null) {
        href.queryParam("type", type);
      }
      return new Results(words, href.encode().build().toUriString());
    }

    private static @Nullable String decoded(@Nullable String value) {
      return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
  }

  /**
   * The page's stylesheet and scripts, each at its URL with a hash of its content in it
   * (/css/viewer-&lt;hash&gt;.css: spring.web.resources.chain.strategy.content), so a page asks for
   * them as they are now, never for a copy a browser kept from before them.
   */
  public record Assets(
      String css,
      String htmx,
      String sse,
      String theme,
      String clock,
      String outline,
      String markers) {
    static Assets of(HttpServletRequest request) {
      // Spring MVC's, for every request its handlers serve
      var urls =
          (ResourceUrlProvider)
              request.getAttribute(
                  ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR);
      return new Assets(
          url(urls, request, "/css/viewer.css"),
          url(urls, request, "/webjars/htmx.org/dist/htmx.min.js"),
          url(urls, request, "/webjars/htmx.org/dist/ext/hx-sse.min.js"),
          url(urls, request, "/js/theme.js"),
          url(urls, request, "/js/clock.js"),
          url(urls, request, "/js/outline.js"),
          url(urls, request, "/js/markers.js"));
    }

    /** {@code path} with its content's hash; as it is without Spring MVC's resources (a test's). */
    private static String url(
        @Nullable ResourceUrlProvider urls, HttpServletRequest request, String path) {
      return urls == null
          ? path
          : Objects.requireNonNullElse(urls.getForRequestUrl(request, path), path);
    }
  }
}
