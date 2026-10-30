// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/** A formula in running text (Formex FORMULA), as MathML. */
public record MathInline(String mathml) implements Inline {}
