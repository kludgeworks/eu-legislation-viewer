// SPDX-License-Identifier: MIT
package rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.build.rules.RulesWiki;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

/**
 * Checks the business rules as the wiki's writer does, on each build: each rule has an identifier
 * and at least one example, each example is in a rule, and no two rules have one identifier.
 */
@NullMarked
class RulesCheckTest {
  @Test
  void eachRuleHasAnIdentifierAndAnExample() throws Exception {
    var comparison =
        Runfiles.preload().unmapped().rlocation("_main/viewer/src/test/rules/comparison");
    var folder = Objects.requireNonNull(Path.of(comparison).getParent());

    var features = RulesWiki.features(folder);

    assertThat(features).isNotEmpty();
    assertThat(RulesWiki.check(features)).isEmpty();
  }
}
