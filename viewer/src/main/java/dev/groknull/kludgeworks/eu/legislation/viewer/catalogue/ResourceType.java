// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What kind of act an act is, by the code of the EU's resource-type authority table: REG_IMPL (an
 * implementing regulation), DIR, DEC. Finer than the CELEX's type letter (R), and the ELI's type in
 * capitals for most acts.
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
   * The resource type of its URI in the authority table, …/resource-type/REG_IMPL. Not of(…), which
   * jMolecules takes for the inverse of the code (in JSON, in a path).
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
