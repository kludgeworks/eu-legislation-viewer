// SPDX-License-Identifier: MIT
package rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.build.rules.RulesApplied;
import dev.groknull.kludgeworks.build.rules.RulesWiki;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

/**
 * Checks the business rules as the wiki's writer does, on each build: each rule has a name and at
 * least one example, each example is in a rule, and no two rules of a feature have one name. It
 * also checks the tie between the rules and the code, both ways: each rule has a method of the
 * viewer that applies it ({@code @BusinessRule}), unless its examples are all known bugs, and each
 * such mark names a rule that a feature has.
 */
@NullMarked
class RulesCheckTest {
  @Test
  void eachRuleHasANameAndAnExample() throws Exception {
    var features = RulesWiki.features(folder());

    assertThat(features).isNotEmpty();
    assertThat(RulesWiki.check(features)).isEmpty();
  }

  @Test
  void eachRuleHasAMethodThatAppliesItAndEachMarkARule() throws Exception {
    var scan = RulesApplied.scan(RulesCheckTest.class.getClassLoader());

    assertThat(scan.applied()).isNotEmpty();
    assertThat(RulesApplied.check(RulesWiki.features(folder()), scan)).isEmpty();
  }

  private static Path folder() throws java.io.IOException {
    var comparison =
        Runfiles.preload().unmapped().rlocation("_main/viewer/src/test/rules/comparison");
    return Objects.requireNonNull(Path.of(comparison).getParent());
  }
}
