// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.site;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** What resources in the API share for links and schemas. */
public final class ApiLinks {

  /**
   * The IANA relation of a link to the schema of an answer, as the registry spells it. Spring
   * HATEOAS's {@code IanaLinkRelations.DESCRIBED_BY} is {@code describedBy}, and a HAL client looks
   * up its links by the exact name.
   */
  public static final LinkRelation DESCRIBED_BY = LinkRelation.of("describedby");

  private ApiLinks() {}

  /** Returns a link to the JSON Schema at {@code path}, with the {@code describedby} relation. */
  public static Link schema(String path) {
    return Link.of(
        ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString(),
        DESCRIBED_BY);
  }

  /**
   * Returns a link whose address is a URI template of RFC 6570: the address of {@code from}, then
   * {@code template}. For example, {@code /acts/{celex}} or {@code /acts{?eli}}.
   */
  public static Link template(Link from, String template, LinkRelation relation) {
    return Link.of(UriTemplate.of(from.getHref() + template), relation);
  }
}
