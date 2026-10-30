// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CAttributePropertyInfo;
import com.sun.tools.xjc.model.CElementPropertyInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.model.CReferencePropertyInfo;
import com.sun.tools.xjc.model.CValuePropertyInfo;
import com.sun.tools.xjc.outline.Outline;
import org.xml.sax.ErrorHandler;

/**
 * An xjc plugin, {@code -Xjspecify}: the schema's optionality as JSpecify annotations on the
 * generated getters. An optional element or attribute's getter is {@code @Nullable}; a required
 * one's, and a list's (JAXB creates it on first access, so it is never null), {@code @NonNull}.
 * Getters of primitives (an attribute with a default) are left alone. MapStruct and NullAway read
 * them, so code that maps or reads the generated classes is checked against the schema.
 */
public final class JSpecifyPlugin extends Plugin {

  private static final String NULLABLE = "org.jspecify.annotations.Nullable";
  private static final String NON_NULL = "org.jspecify.annotations.NonNull";

  @Override
  public String getOptionName() {
    return "Xjspecify";
  }

  @Override
  public String getUsage() {
    return "  -Xjspecify        :  annotate getters @Nullable or @NonNull, as the schema has them";
  }

  @Override
  public boolean run(Outline outline, Options options, ErrorHandler errorHandler) {
    var nullable = outline.getCodeModel().ref(NULLABLE);
    var nonNull = outline.getCodeModel().ref(NON_NULL);
    for (var type : outline.getClasses()) {
      for (var field : type.getDeclaredFields()) {
        var property = field.getPropertyInfo();
        var getter = getter(type.implClass, property.getName(true));
        if (getter != null && !getter.type().isPrimitive()) {
          getter.annotate(property.isCollection() || required(property) ? nonNull : nullable);
        }
      }
    }
    return true;
  }

  private static boolean required(CPropertyInfo property) {
    return switch (property) {
      case CElementPropertyInfo element -> element.isRequired();
      case CAttributePropertyInfo attribute -> attribute.isRequired();
      case CReferencePropertyInfo reference -> reference.isRequired();
      case CValuePropertyInfo value -> true;
      default -> false;
    };
  }

  private static JMethod getter(JDefinedClass type, String property) {
    var getter = type.getMethod("get" + property, new JType[0]);
    return getter != null ? getter : type.getMethod("is" + property, new JType[0]);
  }
}
