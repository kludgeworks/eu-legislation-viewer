// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;
import org.springframework.stereotype.Component;

/**
 * A document's legal text converted from its Formex: each XML file of its package in the language
 * parsed as it is read from the source, then converted; nothing is written anywhere. Every call
 * converts; ValkeyExpressions keeps the result.
 */
@Component
class FormexConversion {
  private final FormexSource source;
  private final FormexConverter converter;

  FormexConversion(FormexSource source, FormexConverter converter) {
    this.source = source;
    this.converter = converter;
  }

  /**
   * The legal-text XML of {@code id}'s document in its language.
   *
   * @return none if Cellar has no Formex of the document in the language
   */
  Optional<XdmNode> convert(ExpressionIdentifier id) {
    return source
        .fetch(id.document(), id.language(), converter::parse)
        .map(documents -> converter.convert(documents, Optional.of(id.document())));
  }

  /** The converter's version: what the legal text is made with, so part of its key in the store. */
  String converterVersion() {
    return converter.version();
  }
}
