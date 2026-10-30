// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/** What a preamble holds, in order: its formulas, citations, recitals and other blocks. */
public sealed interface PreambleContent permits Formula, Citations, Recitals, Block {}
