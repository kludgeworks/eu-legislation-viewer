// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

/**
 * One segment of a text, the window a reader's page loads: a run of one container's children, cut
 * where their content decides, so a change rewrites only the segments it falls in. Segments index
 * the tree, they aren't part of it: how a text is cut doesn't change what it is (its hashes).
 *
 * @param ordinal its place among the text's segments, from 0
 * @param hash the run's identity, from its children's: the same in every version that has the run
 * @param parent the container whose children it holds
 * @param from the first child's position among the container's children
 * @param to the position after its last child
 * @param offset how much of the text comes before it, in bytes of stored content
 * @param weight how much it holds, in the same bytes: what its placeholder is sized from
 */
public record Segment(
    int ordinal,
    ContentHash hash,
    ContentHash parent,
    int from,
    int to,
    long offset,
    long weight) {}
