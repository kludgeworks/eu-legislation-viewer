// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A reference to an Official Journal publication or a URI. */
public record Ref(
    Kind kind,
    @Nullable String href,
    @Nullable String series,
    @Nullable String number,
    @Nullable LocalDate date,
    @Nullable String page,
    List<Inline> content)
    implements Inline {

  public Ref {
    content = List.copyOf(content);
  }

  /** What is referred to. */
  public enum Kind {
    OJ,
    URI
  }
}
