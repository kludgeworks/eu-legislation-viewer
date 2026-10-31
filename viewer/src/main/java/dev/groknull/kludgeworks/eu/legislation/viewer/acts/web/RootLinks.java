// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.api.ApiRelations;
import dev.groknull.kludgeworks.eu.legislation.viewer.api.ApiRoot;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.stereotype.Component;

/** The acts module's entry point on the API's root: an act by its ELI ({@code av:act}). */
@Component
class RootLinks implements RepresentationModelProcessor<ApiRoot> {

  @Override
  public ApiRoot process(ApiRoot root) {
    return root.add(
        ActLinks.byEli(ApiRelations.of("act"))
            .withTitle("An act by its ELI: reg_impl/2015/1998, treaty/tfeu_2016"));
  }
}
