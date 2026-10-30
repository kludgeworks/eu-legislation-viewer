// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

/** Anything that stands on its own line: a unit, a paragraph, a list, a table. */
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
