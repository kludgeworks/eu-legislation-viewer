// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions;

/** Anything inside running text. */
public sealed interface Inline
    permits Text,
        Emphasis,
        DateRef,
        Quote,
        NoteRef,
        Ref,
        Eol,
        MathInline,
        Span,
        InlineImage,
        Changed,
        DeletedInline,
        DocType,
        DocNumber,
        DocDate,
        DocPurpose {}
