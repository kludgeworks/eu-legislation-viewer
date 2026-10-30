// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/** A displayed formula, as presentation MathML. */
public record MathBlock(String mathml) implements Block {}
