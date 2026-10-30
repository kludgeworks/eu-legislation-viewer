// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/** What a preamble holds, in order: formulas, citations, recitals and blocks. */
public sealed interface PreambleContent permits Formula, Citations, Recitals, Block {}
