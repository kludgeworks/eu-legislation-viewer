// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * Includes the viewer's own classes, not the classes of its tests, in Spring Modulith's model of
 * the modules. {@code ModularityTests}, {@code CodingRulesTests} and {@code ModuleDocumentation}
 * use it.
 *
 * <p>ArchUnit's {@code DoNotIncludeTests} does the same for the output folders of Maven and Gradle.
 * This class does it for Bazel, which puts the classes of a test in a jar built from {@code
 * src/test/java}. If the test classes were included, the fixtures of whole-application tests in the
 * main package would make that package depend on the modules that depend on it.
 */
public final class ProductionClasses implements ImportOption {

  @Override
  public boolean includes(Location location) {
    return !location.contains("/src/test/");
  }
}
