// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.*;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the .xspec files named in -Dxspec.files as JUnit dynamic tests: one container per scenario,
 * one test per expect.
 */
public class XSpecTests {
  static final String X = "http://www.jenitennison.com/xslt/xspec";
  static final Processor saxon = new Processor(false);

  @TestFactory
  Stream<DynamicNode> xspec() throws Exception {
    var compiler =
        saxon
            .newXsltCompiler()
            .compile(
                new StreamSource(
                    XSpecTests.class
                        .getResource("/io/xspec/xspec/impl/src/compiler/compile-xslt-tests.xsl")
                        .toString()));
    return Arrays.stream(System.getProperty("xspec.files").trim().split("\\s+"))
        .map(File::new)
        .map(
            f -> DynamicContainer.dynamicContainer(f.getName(), f.toURI(), scenarios(compiler, f)));
  }

  Stream<DynamicNode> scenarios(XsltExecutable compiler, File spec) {
    try {
      var compiled = new XdmDestination();
      compiler.load30().transform(new StreamSource(spec), compiled);
      var report = new XdmDestination();
      saxon
          .newXsltCompiler()
          .compile(compiled.getXdmNode().asSource())
          .load30()
          .callTemplate(new QName(X, "main"), report);
      var xpath = saxon.newXPathCompiler();
      xpath.declareNamespace("x", X);
      return nodes(xpath, "//x:scenario[x:test]", report.getXdmNode())
          .map(
              s ->
                  DynamicContainer.dynamicContainer(
                      str(xpath, s, "string-join(ancestor-or-self::x:scenario/x:label, ' › ')"),
                      nodes(xpath, "x:test", s).map(t -> expect(xpath, t))));
    } catch (SaxonApiException e) {
      return Stream.of(
          DynamicTest.dynamicTest(
              "compile/run " + spec.getName(),
              () -> {
                throw e;
              }));
    }
  }

  DynamicTest expect(XPathCompiler xpath, XdmNode test) {
    var ok = str(xpath, test, "string(@successful)").equals("true");
    var detail =
        str(
            xpath,
            test,
            "'expected: ' || serialize(x:expect ! (string(@select), node())) || ' | actual: ' ||"
                + " serialize(../x:result ! (string(@select), node()))");
    return DynamicTest.dynamicTest(
        str(xpath, test, "string(x:label)"),
        () -> {
          if (!ok) fail(detail);
        });
  }

  static Stream<XdmNode> nodes(XPathCompiler xpath, String expr, XdmNode n) {
    try {
      return xpath.evaluate(expr, n).stream().map(XdmNode.class::cast);
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }

  static String str(XPathCompiler xpath, XdmNode n, String expr) {
    try {
      return xpath.evaluateSingle(expr, n).getStringValue();
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }
}
