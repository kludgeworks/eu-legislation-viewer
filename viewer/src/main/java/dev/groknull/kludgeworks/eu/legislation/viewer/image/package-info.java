// SPDX-License-Identifier: MIT
/**
 * Stored WebP images for the figures and inline graphics of Formex texts.
 *
 * <ul>
 *   <li>When ingest stores a text, this module keeps each image reference per {@link
 *       dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId} without downloading bytes.
 *   <li>When a page or API request asks for an image, this module reads the TIFF from Cellar,
 *       checks size limits and digests, converts the image to lossless WebP, and keeps the result
 *       in Postgres up to a size limit.
 * </ul>
 *
 * <p>The module is a sliced onion:
 *
 * <ul>
 *   <li>The root package is the API and domain ring ({@link Images}, {@link ImageUrls}).
 *   <li>{@code model} holds image size limits and the conversion version.
 *   <li>{@code application} coordinates single-flight reads, worker permits, digest checks, and
 *       cache cleanup.
 *   <li>{@code infra} holds Cellar downloads, TIFF to WebP conversion, and Postgres queries.
 *   <li>{@code web} serves the images over HTTP.
 * </ul>
 */
@ApplicationModule(displayName = "Image")
@DomainRing
@NullMarked
package dev.groknull.kludgeworks.eu.legislation.viewer.image;

import org.jmolecules.architecture.onion.simplified.DomainRing;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
