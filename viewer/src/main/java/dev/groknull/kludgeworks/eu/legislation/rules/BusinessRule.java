// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.rules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that applies a business rule. The rule is a {@code Rule} of a Gherkin feature in
 * {@code viewer/src/test/rules}: the feature is its {@link #group}, and the rule starts with its
 * {@link #name}, for example {@code Rule: acts-changed-together · Acts that changed an occurrence
 * together}.
 *
 * <p>Put it on the method that decides the rule. Put it on a type only if the whole type applies
 * the rule. A rule can have more than one method, and a method can apply more than one rule: give
 * the mark once for each rule.
 *
 * <p>The rules check fails if a rule of a feature has no such method, if this mark names a rule
 * that no feature has, or if the revisions are not the same. The wiki shows the methods beside each
 * rule.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
@Repeatable(BusinessRules.class)
public @interface BusinessRule {
  /** The feature that has the rule. */
  RuleGroup group();

  /** The name of the rule in its feature, in kebab case. For example, {@code tags-else-cellar}. */
  String name();

  /**
   * The revision of the rule that the method applies. The feature gives a rule's revision with the
   * tag {@code @revision-n} on the rule, and a rule without the tag is revision 1. When a rule
   * changes its meaning, its revision goes up, and each method that applies it is checked against
   * the new rule before its mark gets the new revision.
   */
  int revision() default 1;
}
