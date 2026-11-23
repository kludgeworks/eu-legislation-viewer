// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.rules;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Holds the marks of a method that applies more than one rule. Java writes it; do not use it. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
public @interface BusinessRules {
  /** The marks. */
  BusinessRule[] value();
}
