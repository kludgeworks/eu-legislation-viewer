// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/** A block of a unit's content, the body, a note or a cell. */
public sealed interface Block extends PreambleContent
    permits Paragraph,
        ListBlock,
        Table,
        TableGroup,
        Unit,
        QuotedStructure,
        Annotation,
        Figure,
        MathBlock,
        DeletedBlock,
        OldText,
        NewText,
        BlockContainer {}
