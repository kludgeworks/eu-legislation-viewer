// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import dev.groknull.kludgeworks.build.Schematron.Finding;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * xslt-quality (github.com/mricaud/xslt-quality): Schematron rules for XSLT. It ships as sources
 * that its pom builds, so this does the same in a working copy: precompile the rule modules,
 * generate the default configuration, switch off the rules we reject, and compile the schema.
 */
final class XsltQuality {
  private static final String XSLQ = "https://github.com/mricaud/xsl-quality";

  /** Switches off rules in xslt-quality's configuration: active="false" on their idref. */
  private static final String SWITCH_OFF =
      """
      <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xsl:param name="ids" as="xs:string*"/>
        <xsl:mode on-no-match="shallow-copy"/>
        <xsl:template match="@active[../@idref = $ids]">
          <xsl:attribute name="active" select="'false'"/>
        </xsl:template>
      </xsl:stylesheet>
      """;

  private final Schematron schema;
  private final Map<QName, XdmValue> parameters;

  private XsltQuality(Schematron schema, Path conf) {
    this.schema = schema;
    // the configuration replaces the default one; current-sch.filename is required regardless
    this.parameters =
        Map.of(
            new QName(XSLQ, "conf-default.uri"), new XdmAtomicValue(conf.toUri().toString()),
            new QName("current-sch.filename"), new XdmAtomicValue("../conf/xslt-quality"));
  }

  /**
   * Builds xslt-quality from {@code sources} (its repository: src/main and src/build), with the
   * rules {@code rejected} lists switched off.
   */
  static XsltQuality build(Processor saxon, Path sources, Path rejected)
      throws SaxonApiException, IOException {
    var work = Files.createTempDirectory("xslt-quality");
    copy(sources.resolve("src/main/xsl"), work.resolve("xsl"));
    copy(sources.resolve("src/main/sch"), work.resolve("sch"));

    var precompile =
        compile(saxon, sources.resolve("src/build/xslt-quality-precompile-schematron.xsl"));
    try (var modules = Files.list(sources.resolve("src/main/sch/modules"))) {
      for (var module : modules.toList()) {
        transform(
            saxon, precompile, module, work.resolve("sch/modules").resolve(module.getFileName()));
      }
    }

    var schema = sources.resolve("src/main/sch/xslt-quality.sch");
    var defaults = work.resolve("conf/defaults.xml");
    var conf = work.resolve("conf/xslt-quality.conf.xml");
    Files.createDirectories(conf.getParent());
    transform(
        saxon,
        compile(saxon, sources.resolve("src/build/xslt-quality-generate-default-conf.xsl")),
        schema,
        defaults);
    var switchOff =
        saxon.newXsltCompiler().compile(new StreamSource(new StringReader(SWITCH_OFF))).load30();
    switchOff.setStylesheetParameters(Map.of(new QName("ids"), ids(saxon, rejected)));
    switchOff.transform(new StreamSource(defaults.toFile()), saxon.newSerializer(conf.toFile()));

    return new XsltQuality(
        Schematron.compile(saxon, work.resolve("sch/xslt-quality.sch").toUri().toString()), conf);
  }

  /** The findings for one stylesheet. */
  List<Finding> check(Path stylesheet) throws SaxonApiException {
    return schema.validate(new StreamSource(stylesheet.toFile()), parameters);
  }

  /** The ids of the rules the file lists: rejected/rule/@id, each with its reason as text. */
  private static XdmValue ids(Processor saxon, Path rejected) throws SaxonApiException {
    var doc = saxon.newDocumentBuilder().build(rejected.toFile());
    Set<String> ids =
        saxon.newXPathCompiler().evaluate("/rejected/rule/@id ! string()", doc).stream()
            .map(XdmItem::getStringValue)
            .collect(Collectors.toSet());
    return new XdmValue(ids.stream().map(XdmAtomicValue::new).toList());
  }

  private static XsltExecutable compile(Processor saxon, Path stylesheet) throws SaxonApiException {
    return saxon.newXsltCompiler().compile(new StreamSource(stylesheet.toFile()));
  }

  private static void transform(Processor saxon, XsltExecutable stylesheet, Path in, Path out)
      throws SaxonApiException {
    stylesheet.load30().transform(new StreamSource(in.toFile()), saxon.newSerializer(out.toFile()));
  }

  private static void copy(Path from, Path to) throws IOException {
    try (var files = Files.walk(from)) {
      for (var file : files.toList()) {
        var target = to.resolve(from.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else {
          Files.copy(file, target);
        }
      }
    }
  }
}
