// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests how an act cites the acts that amend it. */
class ActTest {
  private static final ActIdentifier SECURITY = new ActIdentifier("32008R0300");
  private static final ActIdentifier SUPPLEMENT = new ActIdentifier("32009R0272");
  private static final ActIdentifier DIRECTIVE = new ActIdentifier("32010L0018");
  private static final ActIdentifier EURATOM = new ActIdentifier("31993R0001");
  private static final ActIdentifier UNTITLED = new ActIdentifier("32010R0185");
  private static final ActIdentifier RECENT = new ActIdentifier("32015R1998");

  private static final Act ACT =
      new Act(
          SECURITY,
          null,
          new ResourceType("REG"),
          Map.of(),
          null,
          true,
          null,
          new Timeline(
              List.of(
                  Version.original(
                      LocalDate.of(2008, 4, 29),
                      SECURITY,
                      EnumSet.allOf(Language.class),
                      Map.of()))),
          List.of(),
          Map.of(),
          List.of(),
          Map.of(
              SUPPLEMENT,
              published(
                  "Commission Regulation (EC) No\u00A0272/2009 of 2 April 2009 supplementing the"
                      + " common basic standards on civil aviation security"),
              DIRECTIVE,
              published(
                  "Council Directive 2010/18/EU of 8 March 2010 implementing the revised Framework"
                      + " Agreement on parental leave"),
              EURATOM,
              published("Council Regulation (Euratom) No 1/1993 of 4 January 1993 on a test"),
              RECENT,
              published("Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015")));

  private static Publication published(String title) {
    return new Publication(null, Map.of(Language.ENG, title), null);
  }

  @Test
  void citesAnOlderActWithItsKindFromItsTitle() {
    assertThat(ACT.cited(SUPPLEMENT)).isEqualTo("Reg. (EC) 272/2009");
    assertThat(ACT.cited(DIRECTIVE)).isEqualTo("Dir. 2010/18/EU");
    assertThat(ACT.cited(EURATOM)).isEqualTo("Reg. (Euratom) 1/1993");
  }

  @Test
  void citesAnActFrom2015AndAnActWithoutATitleByItsNumberAlone() {
    assertThat(ACT.cited(RECENT)).isEqualTo("2015/1998");
    assertThat(ACT.cited(UNTITLED)).isEqualTo("185/2010");
  }
}
