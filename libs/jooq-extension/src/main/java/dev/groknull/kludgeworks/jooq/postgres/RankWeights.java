// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.jooq.postgres;

/**
 * The weights of the four classes of word in a rank of {@link TextSearch#rankCd}, in Postgres's
 * order D, C, B, A. A word's class is the weight that {@code setweight} gave it.
 *
 * @param d the weight of class D, the default class
 * @param c the weight of class C
 * @param b the weight of class B
 * @param a the weight of class A
 */
public record RankWeights(float d, float c, float b, float a) {

  /**
   * Returns the weights as a literal of a {@code float4[]}, for example {@code {0.1,0.2,0.4,1.0}}.
   */
  String literal() {
    return "{" + d + "," + c + "," + b + "," + a + "}";
  }
}
