// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

/** A row of a table, or a group of rows under a heading. */
public sealed interface TableRow permits Row, RowGroup {}
