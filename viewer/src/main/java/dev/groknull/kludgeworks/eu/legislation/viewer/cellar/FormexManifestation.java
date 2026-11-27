// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The Formex files of one text (a version in one language): the manifestation of its expression,
 * from Cellar's RDF.
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
   * @param sha1 the SHA-1 fixity digest of the item from Cellar's technical metadata, in lower-case
   *     hex, or null if Cellar gave none (for example, an entry of a zip item or older acts).
   */
  public record File(String name, URI uri, String mediaType, @Nullable String sha1) {

    /** Creates a file with no SHA-1 fixity. */
    public File(String name, URI uri, String mediaType) {
      this(name, uri, mediaType, null);
    }
  }
}
