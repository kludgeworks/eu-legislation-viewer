// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Builds xslt-quality's validator, the way its pom does: precompile the rule modules, generate the
 * default configuration, switch off the rules we reject, and transpile the schema with SchXslt 2.
 *
 * <p>usage: XsltQualityBuild &lt;out-dir&gt; &lt;xslt-quality sources&gt; &lt;rejected rules&gt;
 *
 * <p>The output, packed into a jar by the xslt_quality_validator genrule: {@code
 * sch/validator.xsl}, the XSLT library it includes ({@code xsl/}), and {@code
 * conf/xslt-quality.conf.xml}.
 */
public final class XsltQualityBuild {

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

  private XsltQualityBuild() {}

  public static void main(String[] args) throws Exception {
    build(new Processor(false), Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
  }

  static void build(Processor saxon, Path out, Path sources, Path rejected)
      throws SaxonApiException, IOException {
    // the schema and its modules, precompiled, in a working copy: the transpiler needs them as
    // files
    var work = Files.createTempDirectory("xslt-quality");
    copy(sources.resolve("src/main/sch"), work.resolve("sch"));
    var precompile =
        compile(saxon, sources.resolve("src/build/xslt-quality-precompile-schematron.xsl"));
    try (var modules = Files.list(sources.resolve("src/main/sch/modules"))) {
      for (var module : modules.toList()) {
        transform(
            saxon, precompile, module, work.resolve("sch/modules").resolve(module.getFileName()));
      }
    }

    copy(sources.resolve("src/main/xsl"), out.resolve("xsl"));
    Files.createDirectories(out.resolve("sch"));
    Schematron.transpile(
        saxon,
        work.resolve("sch/xslt-quality.sch").toUri().toString(),
        saxon.newSerializer(out.resolve("sch/validator.xsl").toFile()));

    var defaults = work.resolve("defaults.xml");
    transform(
        saxon,
        compile(saxon, sources.resolve("src/build/xslt-quality-generate-default-conf.xsl")),
        sources.resolve("src/main/sch/xslt-quality.sch"),
        defaults);
    Files.createDirectories(out.resolve("conf"));
    var switchOff =
        saxon.newXsltCompiler().compile(new StreamSource(new StringReader(SWITCH_OFF))).load30();
    switchOff.setStylesheetParameters(Map.of(new QName("ids"), ids(saxon, rejected)));
    switchOff.transform(
        new StreamSource(defaults.toFile()),
        saxon.newSerializer(out.resolve("conf/xslt-quality.conf.xml").toFile()));
  }

  /** The ids of the rules the file lists: rejected/rule/@id, each with its reason as text. */
  private static XdmValue ids(Processor saxon, Path rejected) throws SaxonApiException {
    var doc = saxon.newDocumentBuilder().build(rejected.toFile());
    return new XdmValue(
        saxon.newXPathCompiler().evaluate("/rejected/rule/@id ! string()", doc).stream()
            .map(XdmItem::getStringValue)
            .sorted()
            .map(XdmAtomicValue::new)
            .toList());
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
