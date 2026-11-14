// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

/**
 * A term of the API, as its documentation shows it: a link relation or a problem type.
 *
 * @param name the name, as it is in the address of its page. For example, {@code act}.
 * @param description what the term means, a fragment of HTML
 */
public record ApiTermView(String name, String description) {}
