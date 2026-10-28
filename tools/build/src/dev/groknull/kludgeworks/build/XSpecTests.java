// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltExecutable;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the XSpec suites in {@code xspec.files} (set by xspec_test, separated by spaces): a
 * container per suite and per scenario, a test per expectation. A suite runs as XSpec runs it:
 * compiled into a stylesheet by XSpec's compiler, then called at x:main, which writes the report
 * read here.
 */
public class XSpecTests {
  private static final String XSPEC = "http://www.jenitennison.com/xslt/xspec";
  private static final String COMPILER = "/io/xspec/xspec/impl/src/compiler/compile-xslt-tests.xsl";

  private final Processor saxon = new Processor(false);
  private final XPathCompiler xpath = saxon.newXPathCompiler();

  @TestFactory
  Stream<DynamicContainer> suites() throws SaxonApiException {
    xpath.declareNamespace("x", XSPEC);
    var compiler =
        saxon
            .newXsltCompiler()
            .compile(
                new StreamSource(
                    Objects.requireNonNull(XSpecTests.class.getResource(COMPILER), COMPILER)
                        .toExternalForm()));
    return Arrays.stream(System.getProperty("xspec.files").trim().split("\\s+"))
        .map(Path::of)
        .map(
            suite ->
                DynamicContainer.dynamicContainer(
                    suite.getFileName().toString(), suite.toUri(), scenarios(compiler, suite)));
  }

  /**
   * The suite's scenarios with expectations; a suite that doesn't compile or run is one failure.
   */
  private Stream<DynamicNode> scenarios(XsltExecutable compiler, Path suite) {
    XdmNode report;
    try {
      report = run(compiler, suite);
    } catch (SaxonApiException e) {
      return Stream.of(
          DynamicTest.dynamicTest(
              "compile and run",
              () -> {
                throw e;
              }));
    }
    return nodes("//x:scenario[x:test]", report)
        .map(
            scenario ->
                DynamicContainer.dynamicContainer(
                    string("string-join(ancestor-or-self::x:scenario/x:label, ' › ')", scenario),
                    nodes("x:test", scenario).map(this::expectation)));
  }

  /** Compiles the suite and calls its x:main: the report. */
  private XdmNode run(XsltExecutable compiler, Path suite) throws SaxonApiException {
    var compiled = new XdmDestination();
    compiler.load30().transform(new StreamSource(suite.toFile()), compiled);
    var report = new XdmDestination();
    saxon
        .newXsltCompiler()
        .compile(compiled.getXdmNode().asSource())
        .load30()
        .callTemplate(new QName(XSPEC, "main"), report);
    return report.getXdmNode();
  }

  /**
   * One expectation: passes when XSpec found it successful, else fails with expected and actual.
   */
  private DynamicTest expectation(XdmNode test) {
    var successful = string("string(@successful)", test).equals("true");
    var detail =
        string(
            "'expected: ' || serialize(x:expect ! (string(@select), node()))"
                + " || ' | actual: ' || serialize(../x:result ! (string(@select), node()))",
            test);
    return DynamicTest.dynamicTest(
        string("string(x:label)", test),
        () -> {
          if (!successful) {
            fail(detail);
          }
        });
  }

  private Stream<XdmNode> nodes(String expression, XdmNode context) {
    try {
      return xpath.evaluate(expression, context).stream().map(XdmNode.class::cast);
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }

  private String string(String expression, XdmNode context) {
    try {
      return xpath.evaluateSingle(expression, context).getStringValue();
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }
}
