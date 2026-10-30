// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

/** A piece of running text. */
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
