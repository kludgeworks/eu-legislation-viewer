// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import gg.jte.html.HtmlAttribute;
import gg.jte.html.HtmlPolicyException;
import gg.jte.html.HtmlTag;
import gg.jte.html.OwaspHtmlPolicy;
import gg.jte.html.policy.PreventInlineEventHandlers;
import java.util.Locale;

/**
 * The rules that {@link JteGenerate} applies to the HTML of each template when it compiles it. A
 * template that breaks one does not compile.
 *
 * <ul>
 *   <li>jte's {@link OwaspHtmlPolicy}: lowercase tags and attributes, quoted attribute values, and
 *       no output in a tag name or an attribute name.
 *   <li>No inline event handler ({@code onclick} and the like): {@link PreventInlineEventHandlers}.
 *   <li>No htmx inline handler ({@code hx-on:click}, {@code data-hx-on:click}) either. It is script
 *       in an attribute too, so it goes in a module in {@code static/js}.
 * </ul>
 */
final class TemplatePolicy extends OwaspHtmlPolicy {
  TemplatePolicy() {
    addPolicy(new PreventInlineEventHandlers());
  }

  @Override
  public void validateHtmlAttribute(HtmlTag tag, HtmlAttribute attribute) {
    super.validateHtmlAttribute(tag, attribute);
    var name = attribute.getName().toLowerCase(Locale.ROOT);
    if (name.startsWith("hx-on") || name.startsWith("data-hx-on")) {
      throw new HtmlPolicyException(
          "The htmx inline handler "
              + attribute.getName()
              + " is not allowed. Put the script in a module in static/js.");
    }
  }
}
