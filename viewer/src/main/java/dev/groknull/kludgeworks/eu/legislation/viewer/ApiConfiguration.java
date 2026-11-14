// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.DefaultCurieProvider;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Configures the API, the interface for other programs at {@code /api}, in HAL. See "HATEOAS REST
 * interface (greenfield design)" in Notion.
 *
 * <ul>
 *   <li>The {@code av} CURIE of the viewer's own link relations.
 *   <li>The label of a HAL document.
 *   <li>The sites that may call the API.
 *   <li>How a client keeps an answer: it asks again each time, with the entity tag that it has.
 *   <li>A CELEX number with a {@code /} in one path segment.
 * </ul>
 *
 * <p>Each module serves its own resources, in its {@code web} package, with its own links and
 * relations. Only what all of them share is here.
 *
 * <p>It is public so that a test of a module's controllers can import it, to answer as the
 * application does.
 */
@Configuration(proxyBeanMethods = false)
public class ApiConfiguration implements WebMvcConfigurer {

  /** The path of the root of the API. */
  private static final String ROOT = "/api";

  /**
   * Returns the {@code av} CURIE, whose address gives the documentation of a relation. Only a
   * relation that a module names with the CURIE is under it. By default, Spring HATEOAS puts every
   * relation that is not on its list of the IANA registry under the CURIE, and its list is not
   * complete.
   */
  @Bean
  CurieProvider curies() {
    return new DefaultCurieProvider("av", UriTemplate.of(ROOT + "/docs#{rel}")) {
      @Override
      public HalLinkRelation getNamespacedRelFor(LinkRelation relation) {
        return HalLinkRelation.of(relation);
      }
    };
  }

  /**
   * Lets a page of any site read the API, and post to it. The API is public, and it uses no
   * cookies, so it sends no credentials.
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping(ROOT + "/**")
        .allowedOrigins("*")
        .allowedMethods(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.POST.name());
  }

  /**
   * Has a client ask again for every answer of the API: what an address answers can change, for
   * example when the Publications Office publishes a version.
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    var revalidated = new WebContentInterceptor();
    revalidated.addCacheMapping(CacheControl.noCache(), ROOT + "/**");
    registry.addInterceptor(revalidated);
  }

  /**
   * Gives every answer 200 of the API that has no entity tag of its own a weak one, from its
   * content, and answers 304 to a client that has it already. The tag is weak because Tomcat does
   * not compress a response with a strong one. Another status gets no tag: a 202 is the state of
   * work in progress, not a representation of what was asked for.
   */
  @Bean
  FilterRegistrationBean<ShallowEtagHeaderFilter> entityTags() {
    ShallowEtagHeaderFilter tags =
        new ShallowEtagHeaderFilter() {
          @Override
          protected boolean isEligibleForEtag(
              HttpServletRequest request,
              HttpServletResponse response,
              int responseStatusCode,
              InputStream inputStream) {
            return responseStatusCode == HttpServletResponse.SC_OK
                && super.isEligibleForEtag(request, response, responseStatusCode, inputStream);
          }
        };
    tags.setWriteWeakETag(true);
    var registration = new FilterRegistrationBean<>(tags);
    registration.addUrlPatterns(ROOT + "/*");
    return registration;
  }

  /**
   * Has Tomcat pass an encoded {@code /} through. Some CELEX numbers have a {@code /}, for example
   * {@code 12016E/TXT}. The API has the number in one path segment, with {@code %2F}, as Cellar
   * does. Tomcat refuses {@code %2F} by default, with 400. Passed through, it stays in its segment,
   * and Spring MVC decodes it into the path variable. Boot has no property for this.
   */
  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashes() {
    return factory ->
        factory.addConnectorCustomizers(
            connector ->
                connector.setEncodedSolidusHandling(
                    EncodedSolidusHandling.PASS_THROUGH.getValue()));
  }

  /**
   * Labels every HAL document {@code application/hal+json}. A request that accepts only {@code
   * application/json} gets the same document, and without this its label would be {@code
   * application/json}. A server may give a type that the request did not ask for. See RFC 9110,
   * section 12.5.1.
   */
  @ControllerAdvice
  static class HalLabel implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
        MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
      return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(
        @Nullable Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response) {
      if (body instanceof RepresentationModel<?>) {
        response.getHeaders().setContentType(MediaTypes.HAL_JSON);
      }
      return body;
    }
  }
}
