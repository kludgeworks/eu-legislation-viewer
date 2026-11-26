// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActAddress;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Gives a handler the {@link ActAddress} of its request: the act and the language that the address
 * names. It reads the path variables {@code type}, {@code year}, {@code number} and {@code
 * language} of the mapping of the handler. A handler whose address names an act and no language
 * takes the {@link Eli} of the act.
 *
 * <p>The answer is 404 if no language has the code, and 400 if the year is not a number. A handler
 * whose mapping does not have the variables cannot take the argument.
 */
@Component
class ActAddressResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(this);
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    var type = parameter.getParameterType();
    return ActAddress.class.equals(type) || Eli.class.equals(type);
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      @Nullable ModelAndViewContainer container,
      NativeWebRequest request,
      @Nullable WebDataBinderFactory binders) {
    var variables =
        request.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    if (!(variables instanceof Map<?, ?> path)
        || !(path.get("type") instanceof String type)
        || !(path.get("year") instanceof String year)
        || !(path.get("number") instanceof String number)) {
      throw new IllegalStateException(
          "The mapping of " + parameter.getExecutable() + " does not name an act.");
    }
    var eli = new Eli(type, year(year), number);
    if (Eli.class.equals(parameter.getParameterType())) {
      return eli;
    }
    if (!(path.get("language") instanceof String language)) {
      throw new IllegalStateException(
          "The mapping of " + parameter.getExecutable() + " does not name a language.");
    }
    return new ActAddress(eli, Requests.language(language));
  }

  private static int year(String year) {
    try {
      return Integer.parseInt(year);
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The year is not a number.", e);
    }
  }
}
