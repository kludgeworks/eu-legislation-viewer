// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
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
import java.util.Optional;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.Test;

/**
 * Legal texts converted from Formex: the version acts chooses, its Formex from the source, the real
 * converter and reader; here the source is the converter's fixture of 2015/1998's consolidation of
 * 22 March 2026.
 */
@AutoBazelRepository
class FormexLegalTextsTest {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final String CONSOLIDATION = "02015R1998-20260322";
  static final Processor SAXON = new Processor(false);

  final Fixtures source = new Fixtures();
  final FormexLegalTexts texts =
      new FormexLegalTexts(
          new OneAct(), source, new FormexConverter(SAXON), new LegalTextReader(SAXON));

  @Test
  void convertsTheVersionThatAppliesOnTheDate() {
    var text = texts.find(AVSEC, LocalDate.of(2026, 4, 1), Language.ENG).orElseThrow();

    assertThat(text.act()).isEqualTo(AVSEC);
    assertThat(text.kind()).isEqualTo(LegalText.Kind.CONSOLIDATED);
    assertThat(text.version()).isEqualTo(LocalDate.of(2026, 3, 22));
    assertThat(text.source().celex()).isEqualTo(CONSOLIDATION);
    assertThat(text.body()).isNotEmpty();
    assertThat(source.asked).containsExactly(CONSOLIDATION + " ENG");
  }

  @Test
  void deletesTheFormexOnceRead() {
    texts.find(AVSEC, LocalDate.of(2026, 4, 1), Language.ENG);

    assertThat(source.folders).isNotEmpty().allSatisfy(folder -> assertThat(folder).doesNotExist());
  }

  @Test
  void hasNoTextBeforeTheActsTextAppliesOrForAnotherAct() {
    assertThat(texts.find(AVSEC, LocalDate.of(2015, 1, 1), Language.ENG)).isEmpty();
    assertThat(texts.find(new ActIdentifier("32010R0185"), LocalDate.of(2026, 4, 1), Language.ENG))
        .isEmpty();
    assertThat(source.asked).isEmpty();
  }

  @Test
  void hasNoTextInALanguageWithoutFormex() {
    assertThat(texts.find(AVSEC, LocalDate.of(2026, 4, 1), Language.GLE)).isEmpty();
    assertThat(source.folders).allSatisfy(folder -> assertThat(folder).doesNotExist());
  }

  /** 2015/1998 with one consolidated version, of 22 March 2026; no other act. */
  static final class OneAct implements Acts {
    @Override
    public Optional<Act> find(ActIdentifier id) {
      if (!id.equals(AVSEC)) {
        return Optional.empty();
      }
      var timeline =
          new Timeline(
              List.of(
                  Version.original(LocalDate.of(2015, 11, 15), AVSEC),
                  new Version(
                      Version.Kind.CONSOLIDATED,
                      LocalDate.of(2026, 3, 22),
                      CONSOLIDATION,
                      false,
                      List.of(new ActIdentifier("32026R0449")))));
      return Optional.of(
          new Act(
              AVSEC,
              new Eli("reg_impl", 2015, "1998"),
              "REG_IMPL",
              LocalDate.of(2015, 11, 5),
              true,
              null,
              timeline));
    }

    @Override
    public Optional<Act> find(Eli eli) {
      return Optional.empty();
    }
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
