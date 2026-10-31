// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The Formex files of one language version, from Cellar's RDF.
 *
 * @param uri the manifestation
 * @param lastModified its cmr:lastModificationDate: part of every cache key built on it
 * @param zipped whether Cellar holds the files as one zip item, whose entries are then the files
 * @param files the files, by name: XML (the package descriptor, the act, its annexes) and images
 */
public record FormexManifestation(URI uri, Instant lastModified, boolean zipped, List<File> files) {

  public FormexManifestation {
    files = List.copyOf(files);
  }

  /** The file a package descriptor or an INCL.ELEMENT names. */
  public Optional<File> file(String name) {
    return files.stream().filter(f -> f.name().equals(name)).findFirst();
  }

  /**
   * One file.
   *
   * @param name its name in Formex (L_2009330EN.01008001.xml), which the package refers to
   * @param uri where to GET it: the item, or the zip item's entry
   * @param mediaType the item's MIME type; for a zip entry, from its extension
   * @param sha1 the SHA-1 Cellar records (tdm:stream_fixity), where it does: not for zip entries
   */
  public record File(String name, URI uri, String mediaType, @Nullable String sha1) {}
}
