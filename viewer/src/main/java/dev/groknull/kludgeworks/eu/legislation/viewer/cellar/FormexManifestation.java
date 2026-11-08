// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * The Formex files of one language version, from Cellar's RDF.
 *
 * @param uri the manifestation
 * @param files the files, by name. The XML files are the package descriptor, the act and its
 *     annexes. The other files are images.
 */
public record FormexManifestation(URI uri, List<File> files) {

  public FormexManifestation {
    files = List.copyOf(files);
  }

  /** Returns the file that a package descriptor or an {@code INCL.ELEMENT} names. */
  public Optional<File> file(String name) {
    return files.stream().filter(f -> f.name().equals(name)).findFirst();
  }

  /**
   * One file.
   *
   * @param name its name in Formex, which the package uses to refer to it. For example, {@code
   *     L_2009330EN.01008001.xml}.
   * @param uri where to GET it. This is the item, or the entry of the zip item.
   * @param mediaType the MIME type of the item. For a zip entry, it comes from the file extension.
   */
  public record File(String name, URI uri, String mediaType) {}
}
