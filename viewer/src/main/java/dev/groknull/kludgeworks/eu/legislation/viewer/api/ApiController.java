// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.api;

import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The root resource: where a client starts, finding the resources by their relations, so it never
 * builds a URL. Its links are the modules' (ApiRoot). HAL, as application/hal+json or plain
 * application/json.
 */
@RestController
@RequestMapping("/api")
class ApiController {

  @GetMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
  ApiRoot root() {
    return new ApiRoot().add(ApiLinks.to(IanaLinkRelations.SELF));
  }
}
