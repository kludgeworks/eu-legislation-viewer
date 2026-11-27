// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.model;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.net.URI;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/**
 * Saved metadata linking an image file of a text to its Cellar item, known digest, and any
 * permanent failure reason.
 *
 * @param text the stored text identity
 * @param file the Formex {@code FILEREF} filename
 * @param manifestation the Cellar {@code fmx4} manifestation address
 * @param itemUri the Cellar item or zip entry address
 * @param mediaType the source MIME media type
 * @param rdfSha1 the SHA-1 digest from Cellar metadata if present, or null
 * @param sourceSha256 the 32-byte SHA-256 digest of the source TIFF bytes if saved, or null
 * @param goneReason permanent failure reason recorded to stop repeated reads from Cellar, or null
 */
@ValueObject
public record ImageSource(
    TextId text,
    String file,
    URI manifestation,
    URI itemUri,
    String mediaType,
    @Nullable String rdfSha1,
    byte @Nullable [] sourceSha256,
    @Nullable String goneReason) {}
