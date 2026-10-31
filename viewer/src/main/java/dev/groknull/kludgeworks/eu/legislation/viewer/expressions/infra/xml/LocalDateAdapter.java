// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra.xml;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.time.LocalDate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Binds xs:date to {@link LocalDate} in the classes generated from legal-text.xsd. Null-marked on
 * its own: the package's other classes are generated.
 */
@NullMarked
public class LocalDateAdapter extends XmlAdapter<String, LocalDate> {
  @Override
  public @Nullable LocalDate unmarshal(@Nullable String s) {
    return s == null ? null : LocalDate.parse(s);
  }

  @Override
  public @Nullable String marshal(@Nullable LocalDate d) {
    return d == null ? null : d.toString();
  }
}
