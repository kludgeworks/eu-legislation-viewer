// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.chrome;

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
 * The chrome of a page. UI design uses this word for what frames the content of every page:
 *
 * <ul>
 *   <li>the site bar
 *   <li>the notice
 *   <li>the theme switch
 *   <li>the link back to the results of a search
 * </ul>
 *
 * <p>A controller makes the chrome from the request with {@link #of}. It passes the chrome to the
 * template of its page, for the layout.
 *
 * @param theme the colour theme of the reader, from the {@link #THEME} cookie. It is {@link
 *     Theme#SYSTEM} if there is no cookie.
 * @param here the path and query of the page. The theme switch comes back to it.
 * @param assets the locations of the stylesheet and scripts of the page
 * @param results the last search of the reader, from the {@link #SEARCH} cookie. It is none if
 *     there is no cookie.
 */
public record Chrome(Theme theme, String here, Assets assets, @Nullable Results results) {
  /** The cookie that keeps the theme of the reader. */
  public static final String THEME = "theme";

  /**
   * The cookie that keeps the last search of the reader for a while. It holds the query of the home
   * page. For example, {@code q=aviation+security&amp;type=regulation}. Thus the address of an act
   * does not need to carry the search.
   */
  public static final String SEARCH = "search";

  /** Returns the chrome of the page that {@code request} asks for. */
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
   * The results of a search, as the site bar links back to them. For example, "‹ Results for
   * “aviation security”".
   *
   * @param words what the reader searched for, as they typed it
   * @param href the address of the results. It is built again from the words and the kind in the
   *     cookie. It is not taken from the cookie.
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
   * The stylesheet and scripts of the page. The URL of each file contains a hash of its content.
   * For example, {@code /css/viewer-<hash>.css}. The property {@code
   * spring.web.resources.chain.strategy.content} enables this. Thus a page always asks for the
   * current files, never for an old copy that a browser kept.
   */
  public record Assets(
      String css,
      String htmx,
      String sse,
      String theme,
      String clock,
      String outline,
      String markers,
      String receipt) {
    static Assets of(HttpServletRequest request) {
      // Spring MVC sets this attribute on every request that its handlers serve.
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
          url(urls, request, "/js/markers.js"),
          url(urls, request, "/js/receipt.js"));
    }

    /**
     * Returns {@code path} with the hash of its content. Returns {@code path} unchanged if Spring
     * MVC has no URL for it, or if there is no resource handling. A test, for example, has none.
     */
    private static String url(
        @Nullable ResourceUrlProvider urls, HttpServletRequest request, String path) {
      return urls == null
          ? path
          : Objects.requireNonNullElse(urls.getForRequestUrl(request, path), path);
    }
  }
}
