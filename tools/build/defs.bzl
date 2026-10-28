# SPDX-License-Identifier: MIT
"""Build macros for the amendment viewer."""

load("@contrib_rules_jvm//java:defs.bzl", "java_junit5_test")
load("@rules_java//java:defs.bzl", "java_library")

def xjc_library(name, schema, bindings = None, xjc_args = [], srcs = [], deps = [], visibility = None):
    """Generate JAXB classes from an XSD (and its bindings) into a reproducible srcjar, and compile them.

    xjc turns the schema's xs:documentation into the classes' and properties' Javadoc. It writes
    into a temporary directory; Bazel's zipper packs that into the srcjar with fixed timestamps,
    sorted, so the srcjar is the same bytes on every build.

    Args:
      name: the java_library; the srcjar is `name`.srcjar.
      schema: the XSD.
      bindings: the JAXB bindings file (.xjb), if any.
      xjc_args: further xjc arguments, such as plugin switches.
      srcs: hand-written helpers the bindings name, such as an XmlAdapter.
      deps: further dependencies of the generated code.
      visibility: the java_library's visibility.
    """
    inputs = [schema] + ([bindings] if bindings else [])
    args = ["-npa", "-no-header", "-quiet", "-extension"] + list(xjc_args)
    if bindings:
        args += ["-b", "$(location %s)" % bindings]
    native.genrule(
        name = name + "_srcjar",
        srcs = inputs,
        outs = [name + ".srcjar"],
        cmd = " && ".join([
            "zipper=$$PWD/$(execpath @bazel_tools//tools/zip:zipper)",
            "srcjar=$$PWD/$@",
            "out=$$(mktemp -d)",
            "$(execpath //tools/build:xjc) -d $$out %s $(location %s)" % (" ".join(args), schema),
            "cd $$out",
            "$$zipper c $$srcjar $$(find . -name '*.java' | LC_ALL=C sort | sed 's|^[.]/||')",
            "rm -rf $$out",
        ]),
        tools = [
            "//tools/build:xjc",
            "@bazel_tools//tools/zip:zipper",
        ],
    )
    java_library(
        name = name,
        srcs = [":" + name + ".srcjar"] + srcs,
        deps = deps + ["@maven//:jakarta_xml_bind_jakarta_xml_bind_api"],
        runtime_deps = ["@maven//:org_glassfish_jaxb_jaxb_runtime"],
        visibility = visibility,
    )

def xspec_test(name, specs, data = [], **kwargs):
    """One cached JUnit 5 test per .xspec file (run by XSpecTests), plus a test_suite named `name`.

    Args:
      name: the test_suite; each test is `name`_ and the spec's file name.
      specs: the .xspec files.
      data: what the specs read: the stylesheets, fixtures.
      **kwargs: passed to each java_junit5_test.
    """
    tests = []
    for spec in specs:
        test_name = name + "_" + spec.rsplit("/", 1)[-1].replace(".xspec", "").replace("-", "_")
        java_junit5_test(
            name = test_name,
            test_class = "dev.groknull.kludgeworks.build.XSpecTests",
            jvm_flags = ["-Dxspec.files=$(rootpath %s)" % spec],
            data = [spec] + data,
            runtime_deps = [
                "//tools/build:xspec_runner",
                "@maven//:org_junit_jupiter_junit_jupiter_engine",
                "@maven//:org_junit_platform_junit_platform_launcher",
                "@maven//:org_junit_platform_junit_platform_reporting",
            ],
            **kwargs
        )
        tests.append(":" + test_name)
    native.test_suite(name = name, tests = tests)

def xslt_quality_test(name, srcs, conf = "//tools/build:xslt-quality.conf.xml", **kwargs):
    """Check stylesheets with xslt-quality: one JUnit 5 test with a dynamic test per stylesheet.

    Every finding fails, whatever its level, except those of the rules `conf` switches off.

    Args:
      name: the test.
      srcs: the stylesheets, or filegroups of them.
      conf: the rules switched off, each with its reason.
      **kwargs: passed to java_junit5_test.
    """
    java_junit5_test(
        name = name,
        test_class = "dev.groknull.kludgeworks.build.XsltQualityTests",
        jvm_flags = [
            "-Dxslt-quality.files='%s'" % " ".join(["$(rootpaths %s)" % s for s in srcs]),
            "-Dxslt-quality.schema=$(rootpath @xslt_quality//:src/main/sch/xslt-quality.sch)",
            "-Dxslt-quality.rejected=$(rootpath %s)" % conf,
        ],
        data = srcs + [conf, "@xslt_quality//:sources", "@xslt_quality//:src/main/sch/xslt-quality.sch"],
        runtime_deps = [
            "//tools/build:xslt_quality_runner",
            "@maven//:org_junit_jupiter_junit_jupiter_engine",
            "@maven//:org_junit_platform_junit_platform_launcher",
            "@maven//:org_junit_platform_junit_platform_reporting",
        ],
        **kwargs
    )
