// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.time.LocalDate;

/** Binds xs:date to {@link LocalDate} in the classes generated from legal-text.xsd. */
public class LocalDateAdapter extends XmlAdapter<String, LocalDate> {
  @Override
  public LocalDate unmarshal(String s) {
    return s == null ? null : LocalDate.parse(s);
  }

  @Override
  public String marshal(LocalDate d) {
    return d == null ? null : d.toString();
  }
}
