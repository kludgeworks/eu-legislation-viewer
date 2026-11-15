// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

/**
 * A resource of the API, as its documentation shows it.
 *
 * @param id the anchor of the resource on the page. For example, {@code act}. The relation that
 *     leads to the resource has the same name, so the {@code av} CURIE leads to it.
 * @param title the title of the resource. For example, {@code Act}.
 * @param html what the resource is, its members and links, as a fragment of HTML with its examples
 */
public record ApiResourceView(String id, String title, String html) {}
