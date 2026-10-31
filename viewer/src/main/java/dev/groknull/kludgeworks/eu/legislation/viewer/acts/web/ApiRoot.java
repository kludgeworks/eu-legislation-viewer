// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.web;

import org.springframework.hateoas.RepresentationModel;

/**
 * The root resource at /api: its own link, and those the modules add to it, each its entry points
 * as a RepresentationModelProcessor of ApiRoot, so the root knows no module.
 */
public class ApiRoot extends RepresentationModel<ApiRoot> {}
