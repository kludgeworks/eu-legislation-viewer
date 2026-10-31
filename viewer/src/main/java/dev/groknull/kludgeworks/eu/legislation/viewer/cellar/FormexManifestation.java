// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * The Formex files of one language version, from Cellar's RDF.
 *
 * @param uri the manifestation
 * @param files the files, by name: XML (the package descriptor, the act, its annexes) and images
 */
public record FormexManifestation(URI uri, List<File> files) {

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
   */
  public record File(String name, URI uri, String mediaType) {}
}
