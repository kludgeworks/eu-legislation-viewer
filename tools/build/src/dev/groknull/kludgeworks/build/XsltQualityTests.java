// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks the stylesheets named in -Dxslt-quality.files with xslt-quality, one dynamic test each.
 * The validator is built the way xslt-quality's pom builds it (precompile the modules, generate the
 * default configuration) and transpiled with SchXslt 2; the rules in -Dxslt-quality.conf are
 * switched off. Any other finding fails its stylesheet's test.
 */
public class XsltQualityTests {
  static final String SVRL = "http://purl.oclc.org/dsdl/svrl";
  static final String XSLQ = "https://github.com/mricaud/xsl-quality";
  static final Processor saxon = new Processor(false);

  @TestFactory
  Stream<DynamicTest> xsltQuality() throws Exception {
    // the root of xslt-quality's sources, from its main schema: src/main/sch/xslt-quality.sch
    var schema = Path.of(System.getProperty("xslt-quality.schema")).toAbsolutePath().normalize();
    var validator = validator(schema.getParent().getParent().getParent().getParent());
    return Arrays.stream(System.getProperty("xslt-quality.files").trim().split("\\s+"))
        .map(File::new)
        .map(f -> DynamicTest.dynamicTest(f.getName(), f.toURI(), () -> check(validator, f)));
  }

  record Validator(XsltExecutable executable, Path conf) {}

  /**
   * xslt-quality's build, in a temporary copy of its sources: modules precompiled, conf generated.
   */
  static Validator validator(Path root) throws SaxonApiException, IOException {
    var work = Files.createTempDirectory("xslt-quality");
    copy(root.resolve("src/main/xsl"), work.resolve("xsl"));
    copy(root.resolve("src/main/sch"), work.resolve("sch"));
    var precompile = compile(root.resolve("src/build/xslt-quality-precompile-schematron.xsl"));
    try (var modules = Files.list(root.resolve("src/main/sch/modules"))) {
      for (var module : modules.toList()) {
        transform(precompile, module, work.resolve("sch/modules").resolve(module.getFileName()));
      }
    }
    var defaultConf = new XdmDestination();
    compile(root.resolve("src/build/xslt-quality-generate-default-conf.xsl"))
        .load30()
        .transform(
            new StreamSource(root.resolve("src/main/sch/xslt-quality.sch").toFile()), defaultConf);
    var conf = work.resolve("conf/xslt-quality.conf.xml");
    Files.createDirectories(conf.getParent());
    Files.writeString(conf, switchOff(defaultConf.getXdmNode(), rejected()).toString());
    // the validator includes ../xsl/xslt-quality.xsl, so it is written next to the schema
    var validator = work.resolve("sch/validator.xsl");
    var transpiler =
        saxon
            .newXsltCompiler()
            .compile(
                new StreamSource(
                    XsltQualityTests.class.getResource("/content/transpile.xsl").toString()));
    transform(transpiler, work.resolve("sch/xslt-quality.sch"), validator);
    return new Validator(compile(validator), conf);
  }

  /** The rule ids switched off in -Dxslt-quality.conf, which give each a reason. */
  static Set<String> rejected() throws SaxonApiException {
    var doc =
        saxon
            .newDocumentBuilder()
            .build(new StreamSource(new File(System.getProperty("xslt-quality.conf"))));
    return saxon.newXPathCompiler().evaluate("/rejected/rule/@id ! string()", doc).stream()
        .map(XdmItem::getStringValue)
        .collect(Collectors.toSet());
  }

  /** The default configuration with active="false" on the rejected rules. */
  static XdmNode switchOff(XdmNode conf, Set<String> ids) throws SaxonApiException {
    var stylesheet =
        """
        <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
          <xsl:param name="ids" as="xs:string*" xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
          <xsl:mode on-no-match="shallow-copy"/>
          <xsl:template match="@active[../@idref = $ids]">
            <xsl:attribute name="active" select="'false'"/>
          </xsl:template>
        </xsl:stylesheet>
        """;
    var transformer =
        saxon
            .newXsltCompiler()
            .compile(new StreamSource(new java.io.StringReader(stylesheet)))
            .load30();
    transformer.setStylesheetParameters(
        Map.of(new QName("ids"), new XdmValue(ids.stream().map(XdmAtomicValue::new).toList())));
    var out = new XdmDestination();
    transformer.applyTemplates(conf, out);
    return out.getXdmNode();
  }

  static void check(Validator validator, File stylesheet) throws SaxonApiException {
    var transformer = validator.executable().load30();
    transformer.setStylesheetParameters(
        Map.of(
            new QName(XSLQ, "conf-default.uri"),
            new XdmAtomicValue(validator.conf().toUri().toString()),
            new QName("current-sch.filename"),
            new XdmAtomicValue("../conf/xslt-quality")));
    var report = new XdmDestination();
    transformer.transform(new StreamSource(stylesheet), report);
    var xpath = saxon.newXPathCompiler();
    xpath.declareNamespace("svrl", SVRL);
    List<String> findings =
        xpath
            .evaluate(
                "//(svrl:failed-assert | svrl:successful-report) ! (string(@role) || ' ' || @id ||"
                    + " ' at ' || @location || ': ' || normalize-space(svrl:text))",
                report.getXdmNode())
            .stream()
            .map(XdmItem::getStringValue)
            .toList();
    if (!findings.isEmpty()) {
      fail(findings.size() + " xslt-quality findings:\n  " + String.join("\n  ", findings));
    }
  }

  static XsltExecutable compile(Path stylesheet) throws SaxonApiException {
    return saxon.newXsltCompiler().compile(new StreamSource(stylesheet.toFile()));
  }

  static void transform(XsltExecutable stylesheet, Path in, Path out) throws SaxonApiException {
    stylesheet.load30().transform(new StreamSource(in.toFile()), saxon.newSerializer(out.toFile()));
  }

  static void copy(Path from, Path to) throws IOException {
    try (var files = Files.walk(from)) {
      for (var f : files.toList()) {
        var target = to.resolve(from.relativize(f).toString());
        if (Files.isDirectory(f)) {
          Files.createDirectories(target);
        } else {
          Files.copy(f, target);
        }
      }
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }
}
