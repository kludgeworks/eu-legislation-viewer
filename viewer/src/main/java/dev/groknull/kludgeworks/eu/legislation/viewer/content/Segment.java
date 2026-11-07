// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content;

/**
 * One segment of a text. A segment is the window that the page of a reader loads.
 *
 * <p>A segment is a run of the children of one container. The content of the children decides where
 * the runs are cut. Thus a change rewrites only the segments that it falls in. Segments index the
 * tree but are not part of it. How a text is cut does not change what it is, so its hashes stay the
 * same.
 *
 * @param ordinal its position among the segments of the text, from 0
 * @param hash the identity of the run, made from the identities of its children. It is the same in
 *     every version that has the run.
 * @param parent the container whose children it holds
 * @param from the position of the first child among the children of the container
 * @param to the position after its last child
 * @param offset how much of the text comes before it, in bytes of stored content
 * @param weight how much it holds, in the same bytes. Its placeholder gets its size from this.
 */
public record Segment(
    int ordinal,
    ContentHash hash,
    ContentHash parent,
    int from,
    int to,
    long offset,
    long weight) {}
