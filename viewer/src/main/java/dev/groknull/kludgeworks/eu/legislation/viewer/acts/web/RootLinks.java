// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.stereotype.Component;

/**
 * The acts module's entry points on the API's root: an act by its CELEX ({@code av:act}), and by
 * its ELI ({@code av:act-by-eli}, a lookup answered 303 with the act's URL).
 */
@Component
class RootLinks implements RepresentationModelProcessor<ApiRoot> {

  @Override
  public ApiRoot process(ApiRoot root) {
    return root.add(
            ApiLinks.template(ApiRelations.of("act"), "/acts/{act}")
                .withTitle("An act by its CELEX: 32015R1998, 12016E/TXT"))
        .add(
            ApiLinks.template(ApiRelations.of("act-by-eli"), "/acts{?eli}")
                .withTitle("An act by its ELI: reg_impl/2015/1998, treaty/tfeu_2016"));
  }
}
