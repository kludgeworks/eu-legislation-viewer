// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.site;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Gives a handler the {@link Frame} of the page that its request asks for. A handler of a page
 * takes the frame as an argument, and passes it to the template of the page.
 */
@Component
class FrameResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(this);
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return Frame.class.equals(parameter.getParameterType());
  }

  @Override
  public Frame resolveArgument(
      MethodParameter parameter,
      @Nullable ModelAndViewContainer container,
      NativeWebRequest request,
      @Nullable WebDataBinderFactory binders) {
    return Frame.of(Objects.requireNonNull(request.getNativeRequest(HttpServletRequest.class)));
  }
}
