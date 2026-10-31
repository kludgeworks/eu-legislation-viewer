// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;

/**
 * Links to the resources under an act's ELI, at the host and path the request came by (forwarded
 * headers, server.forward-headers-strategy). Built from the ELI, not by linkTo(methodOn(…)): that
 * fills path variables only, and of a route's two patterns would take the first.
 */
public final class ActLinks {

  private ActLinks() {}

  /** The act's resource: /eli/reg_impl/2015/1998, /eli/treaty/tfeu_2016. */
  public static WebMvcLinkBuilder act(Eli eli) {
    return linkTo(ActController.class).slash("eli").slash(eli.path());
  }
}
