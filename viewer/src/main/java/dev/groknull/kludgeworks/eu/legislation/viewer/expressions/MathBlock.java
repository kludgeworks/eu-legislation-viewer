// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

/**
 * A displayed formula (Formex FORMULA.S), as Presentation MathML: delimited groups (EXPR) become
 * mrow with mo fences, fractions mfrac, sums, products and integrals munderover, indices msub,
 * exponents msup, roots msqrt, operators mo.
 */
public record MathBlock(String mathml) implements Block {}
