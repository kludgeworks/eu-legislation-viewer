// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTextIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTexts;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The point-in-time view (Scope): an act's text as it applies on a date. Acts chooses the version
 * (Design: Choosing the version to show), legal texts give its text.
 */
@Service
public class PointInTime {
  private final Acts acts;
  private final LegalTexts texts;

  PointInTime(Acts acts, LegalTexts texts) {
    this.acts = acts;
    this.texts = texts;
  }

  /**
   * The text of the version of {@code act} that applies on {@code date}, in {@code language}.
   *
   * @return none for an act Cellar doesn't know, a date before its text first applies, or a version
   *     Cellar has no Formex of in {@code language}
   */
  public Optional<LegalText> text(ActIdentifier act, LocalDate date, Language language) {
    return acts.find(act)
        .flatMap(a -> a.versionOn(date))
        .flatMap(version -> texts.find(LegalTextIdentifier.of(version, language)));
  }
}
