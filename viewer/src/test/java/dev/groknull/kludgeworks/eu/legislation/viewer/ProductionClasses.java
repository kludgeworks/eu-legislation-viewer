// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * The viewer's own classes, not its tests', for Spring Modulith's model of the modules
 * (ModularityTests): what ArchUnit's DoNotIncludeTests does for Maven's and Gradle's output
 * folders, for Bazel's, where a test's classes are in a jar built from src/test/java. With them,
 * the fixtures of the whole-application tests in the main package (OneTreaty) would make it depend
 * on the modules that depend on it.
 */
public final class ProductionClasses implements ImportOption {

  @Override
  public boolean includes(Location location) {
    return !location.contains("/src/test/");
  }
}
