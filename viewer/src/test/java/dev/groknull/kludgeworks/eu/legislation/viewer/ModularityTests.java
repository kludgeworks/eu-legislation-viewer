// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.lang.ArchRule;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Repository;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.VerificationOptions;

/**
 * The architecture's checks, by Spring Modulith: no cycles between modules, no access to another
 * module's internals, and, since jmolecules-archunit is on the classpath, jMolecules' rules too.
 * Among them the sliced onion's: in every module the domain ring depends on nothing outward and the
 * application ring only on the domain, and infrastructure (the cellar module, every {@code infra})
 * is used only from infrastructure. On top of them, how absence is written: a lookup that may find
 * nothing returns an Optional, a value that may be absent is @Nullable (which ArchUnit can't read,
 * JSpecify's being a type-use annotation), so no field is an Optional; record components are
 * fields.
 */
class ModularityTests {

  static final ArchRule NO_OPTIONAL_FIELDS =
      fields()
          .should()
          .notHaveRawType(Optional.class)
          .because("a value that may be absent, as a record component, is @Nullable");

  static final ArchRule REPOSITORIES_FIND_OPTIONALS =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areMetaAnnotatedWith(Repository.class)
          .and()
          .haveNameStartingWith("find")
          .should()
          .haveRawReturnType(Optional.class)
          .because("a lookup that may find nothing returns an Optional");

  @Test
  void modulesRespectTheirBoundaries() {
    // and, not with: with would replace the default verifications, jMolecules' among them
    ApplicationModules.of(ViewerApplication.class, new ProductionClasses())
        .verify(
            VerificationOptions.defaults()
                .andAdditionalVerifications(NO_OPTIONAL_FIELDS, REPOSITORIES_FIND_OPTIONALS));
  }
}
