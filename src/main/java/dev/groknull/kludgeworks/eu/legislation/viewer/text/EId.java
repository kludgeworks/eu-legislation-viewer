// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/** A unit's identifier, AKN4EU style: art_3/par_2/pnt_a, anx/pnt_1.2.2.6. */
public record EId(String value) {

  @Override
  public String toString() {
    return value;
  }
}
