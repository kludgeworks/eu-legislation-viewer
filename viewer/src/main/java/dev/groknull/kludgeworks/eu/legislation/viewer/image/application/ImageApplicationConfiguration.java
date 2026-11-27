// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers configuration properties for the image module. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImageProperties.class)
class ImageApplicationConfiguration {}
