// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * Runs the business rules that the comparison's domain applies: the features in {@code
 * rules/comparison}, with the steps of this package. The run is saved as Cucumber's messages, for
 * the wiki.
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("rules.comparison")
@ConfigurationParameter(
    key = GLUE_PROPERTY_NAME,
    value = "dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "dev.groknull.kludgeworks.build.rules.RulesMessages")
class ComparisonRules {}
