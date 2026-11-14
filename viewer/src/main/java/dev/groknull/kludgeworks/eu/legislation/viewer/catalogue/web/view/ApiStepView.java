// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

/**
 * A step of the API's walk, from an act to a comparison, as its documentation shows it.
 *
 * @param id the anchor of the step on the page. For example, {@code start-act}.
 * @param title the title of the step. For example, {@code Find an act}.
 * @param html the step, a fragment of HTML with its examples.
 */
public record ApiStepView(String id, String title, String html) {}
