// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

/**
 * One Formex manifestation as Cellar serves it (Cellar's FRBR levels: work, expression,
 * manifestation, items): its items, the files of one folder. A fixture is one,
 * fixtures/formex/&lt;Formex version&gt;/&lt;CELEX&gt;/ or fixtures/refused/&lt;root element&gt;/;
 * so is each manifestation of the corpus mirror, files/&lt;Cellar ID&gt;/.
 *
 * @param name how tests name it: its folder relative to fixtures/
 *     (formex/06.02.1/02015R1998-20260322) or its Cellar ID
 * @param folder the folder
 */
record Manifestation(String name, Path folder) {

  private static final Pattern OJ_CELEX = Pattern.compile("[1-9E]\\d{4}[A-Z]{1,2}[0-9A-Z()]+");

  /** The file to convert, as the converter chooses it ({@link FormexConverter#input}). */
  Path input() {
    return FormexConverter.input(folder);
  }

  /** The input with its system id set, so the files it refers to resolve. */
  Source source() {
    return new StreamSource(input().toFile());
  }

  /**
   * The CELEX number to pass for an OJ act, which the folder is named after; consolidations (CELEX
   * 0…) carry their own, and refused documents have none.
   */
  Optional<String> celex() {
    var folderName = folder.getFileName().toString();
    return OJ_CELEX.matcher(folderName).matches() ? Optional.of(folderName) : Optional.empty();
  }

  @Override
  public String toString() {
    return name;
  }
}
