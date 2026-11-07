// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What kind of act an act is, by the code of the EU's resource-type authority table. For example,
 * {@code REG_IMPL} for an implementing regulation, {@code DIR} or {@code DEC}. It is finer than the
 * type letter of the CELEX, such as R. For most acts, it is the type of the ELI in capitals.
 */
@ValueObject
public record ResourceType(String code) {
  private static final String AUTHORITY =
      "http://publications.europa.eu/resource/authority/resource-type/";
  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

  public ResourceType {
    if (!CODE.matcher(code).matches()) {
      throw new IllegalArgumentException("Not a resource type's code: " + code);
    }
  }

  /**
   * Returns the resource type of its URI in the authority table, for example {@code
   * …/resource-type/REG_IMPL}. The method is not named {@code of}, because jMolecules takes a
   * method {@code of(…)} to make the type from its code, in JSON and in a path.
   */
  public static ResourceType ofUri(String uri) {
    if (!uri.startsWith(AUTHORITY)) {
      throw new IllegalArgumentException("Not a resource type: " + uri);
    }
    return new ResourceType(uri.substring(AUTHORITY.length()));
  }

  @Override
  public String toString() {
    return code;
  }
}
