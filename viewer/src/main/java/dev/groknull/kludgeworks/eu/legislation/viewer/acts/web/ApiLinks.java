// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Links to the API's resources, at the host and path the request came by (forwarded headers,
 * server.forward-headers-strategy), each segment encoded on its own: a CELEX's slash as %2F
 * (12016E%2FTXT, ApiServerConfiguration). Every module links through this, not linkTo(…): that
 * takes its base from the current request's path, which it doubles when the path holds a %2F.
 */
public final class ApiLinks {
  private ApiLinks() {}

  /**
   * The resource at these path segments under /api: ("acts", "12016E/TXT") is
   * /api/acts/12016E%2FTXT.
   */
  public static Link to(LinkRelation relation, String... segments) {
    return Link.of(api().pathSegment(segments).build().encode().toUriString(), relation);
  }

  /** A URI template under /api, as RFC 6570 has it: "/acts/{act}", "/acts{?eli}". */
  public static Link template(LinkRelation relation, String template) {
    return Link.of(UriTemplate.of(api().build().toUriString() + template), relation);
  }

  private static UriComponentsBuilder api() {
    return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api");
  }
}
