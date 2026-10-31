// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * The ELI of a route under either of EliPaths' shapes, read from its path variables (a treaty's
 * edition, or an act's type, year and number) into the request attribute EliPaths.ELI, which a
 * controller takes as {@code @RequestAttribute(EliPaths.ELI) Eli}: an ELI spans several path
 * variables, so it is no @PathVariable.
 */
class EliInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    @SuppressWarnings("unchecked")
    var variables =
        (@Nullable Map<String, String>)
            request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (variables != null) {
      eli(variables).ifPresent(eli -> request.setAttribute(EliPaths.ELI, eli));
    }
    return true;
  }

  private static Optional<Eli> eli(Map<String, String> variables) {
    var edition = variables.get("edition");
    if (edition != null) {
      return Optional.of(Eli.treaty(edition));
    }
    var type = variables.get("type");
    var year = variables.get("year");
    var number = variables.get("number");
    if (type == null || year == null || number == null) {
      return Optional.empty();
    }
    return Optional.of(new Eli(type, Integer.parseInt(year), number));
  }
}
