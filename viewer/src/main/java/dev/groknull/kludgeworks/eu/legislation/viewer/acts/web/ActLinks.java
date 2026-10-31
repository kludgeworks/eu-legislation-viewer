// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import java.time.LocalDate;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;

/**
 * Links to the resources under an act's ELI, at the host and path the request came by (forwarded
 * headers, server.forward-headers-strategy). Built from the ELI, not by linkTo(methodOn(…)): that
 * fills path variables only, and of a route's two patterns would take the first.
 */
public final class ActLinks {
  /** The name of an alternate on EUR-Lex: the official text, or its document page. */
  public static final String EUR_LEX = "eur-lex";

  /** The name of an alternate in Cellar: the work, or the expression. */
  public static final String CELLAR = "cellar";

  private ActLinks() {}

  /**
   * The act resource of any ELI, as a URI template with the ELI's path as it is (RFC 6570's
   * reserved expansion): …/eli/{+eli}, for eli reg_impl/2015/1998 or treaty/tfeu_2016.
   */
  public static Link byEli(LinkRelation relation) {
    return Link.of(UriTemplate.of(linkTo(ActController.class).toUri() + "/eli/{+eli}"), relation);
  }

  /** The act's resource: /eli/reg_impl/2015/1998, /eli/treaty/tfeu_2016. */
  public static WebMvcLinkBuilder act(Eli eli) {
    return linkTo(ActController.class).slash("eli").slash(eli.path());
  }

  /** The act as it stood on {@code date}: /eli/reg_impl/2015/1998/2026-03-22. */
  public static WebMvcLinkBuilder version(Eli eli, LocalDate date) {
    return act(eli).slash(date);
  }

  /** That, in {@code language}: /eli/reg_impl/2015/1998/2026-03-22/eng. */
  public static WebMvcLinkBuilder expression(Eli eli, LocalDate date, Language language) {
    return version(eli, date).slash(language.eli());
  }
}
