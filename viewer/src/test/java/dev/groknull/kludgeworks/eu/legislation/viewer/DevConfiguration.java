// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * What only running locally has (TestViewerApplication): {@code /hal}, HAL Explorer on the API's
 * root. A redirect, as the explorer's page loads its scripts relative to its own path.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DevConfiguration implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/hal", "/webjars/hal-explorer/index.html#uri=/api");
  }
}
