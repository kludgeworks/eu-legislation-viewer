// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.formex.converter.FormexConverter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.Test;

/**
 * Legal texts converted from Formex: a version's Formex from the source, the real converter and
 * reader; here the source is the converter's fixture of 2015/1998's consolidation of 22 March 2026.
 */
@AutoBazelRepository
class FormexLegalTextsTest {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final String CONSOLIDATION = "02015R1998-20260322";
  static final Processor SAXON = new Processor(false);
  static final Version VERSION =
      new Version(
          Version.Kind.CONSOLIDATED,
          LocalDate.of(2026, 3, 22),
          CONSOLIDATION,
          false,
          List.of(new ActIdentifier("32026R0449")));

  final Fixtures source = new Fixtures();
  final FormexLegalTexts texts =
      new FormexLegalTexts(source, new FormexConverter(SAXON), new LegalTextReader(SAXON));

  @Test
  void convertsTheVersionsFormex() {
    var text = texts.find(VERSION, Language.ENG).orElseThrow();

    assertThat(text.act()).isEqualTo(AVSEC);
    assertThat(text.kind()).isEqualTo(LegalText.Kind.CONSOLIDATED);
    assertThat(text.version()).isEqualTo(LocalDate.of(2026, 3, 22));
    assertThat(text.source().celex()).isEqualTo(CONSOLIDATION);
    assertThat(text.body()).isNotEmpty();
    assertThat(source.asked).containsExactly(CONSOLIDATION + " ENG");
  }

  @Test
  void deletesTheFormexOnceRead() {
    texts.find(VERSION, Language.ENG);

    assertThat(source.folders).isNotEmpty().allSatisfy(folder -> assertThat(folder).doesNotExist());
  }

  @Test
  void hasNoTextInALanguageWithoutFormex() {
    assertThat(texts.find(VERSION, Language.GLE)).isEmpty();
    assertThat(source.folders).allSatisfy(folder -> assertThat(folder).doesNotExist());
  }

  /** The converter's fixture of the consolidation, in English only. */
  static final class Fixtures implements FormexSource {
    final List<String> asked = new ArrayList<>();
    final List<Path> folders = new ArrayList<>();

    @Override
    public boolean fetch(String celex, Language language, Path folder) {
      folders.add(folder);
      if (language != Language.ENG) {
        return false;
      }
      asked.add(celex + " " + language.code());
      try (var files = Files.list(fixture(celex))) {
        for (var file : files.toList()) {
          Files.copy(file, folder.resolve(file.getFileName()));
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return true;
    }

    private static Path fixture(String celex) throws IOException {
      return Path.of(
          Runfiles.preload()
              .withSourceRepository(AutoBazelRepository_FormexLegalTextsTest.NAME)
              .rlocation(
                  "kludgeworks_viewer/formex-converter/src/test/resources/fixtures/formex/06.02.1/"
                      + celex));
    }
  }
}
