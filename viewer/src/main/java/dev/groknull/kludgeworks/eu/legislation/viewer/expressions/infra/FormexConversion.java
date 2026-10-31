// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.ExpressionIdentifier;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Component;

/**
 * A document's legal text converted from its Formex: each XML file of its package in the language
 * parsed as it is read from the source, then converted; nothing is written anywhere. Every call
 * converts; ValkeyExpressions keeps the result.
 *
 * <p>A conversion holds its package's files and its result in memory, which for the largest
 * (2016/2070's 87 MB file) takes about 1 GB of heap: only so many run at once
 * (viewer.expressions.max-conversions), the others waiting their turn, so the conversions done
 * ahead (PreConversion) of a version of such an act can't run the viewer out of memory together.
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
  @ConcurrencyLimit(limitString = "${viewer.expressions.max-conversions}")
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
