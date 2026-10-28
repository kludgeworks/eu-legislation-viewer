# SPDX-License-Identifier: MIT
"""Build macros for the amendment viewer."""

load("@contrib_rules_jvm//java:defs.bzl", "java_junit5_test")
load("@rules_java//java:defs.bzl", "java_library")

def xjc_library(name, schema, bindings = None, xjc_args = [], srcs = [], deps = [], visibility = None):
    """Generate JAXB classes from an XSD (and its bindings) into a reproducible srcjar, and compile them.

    xjc turns the schema's xs:documentation into the classes' and properties' Javadoc.
    `srcs` adds hand-written helpers the bindings name, such as an XmlAdapter.
    """
    inputs = [schema] + ([bindings] if bindings else [])
    args = list(xjc_args) + ["-extension"]
    if bindings:
        args += ["-b", "$(location %s)" % bindings]
    native.genrule(
        name = name + "_srcjar",
        srcs = inputs,
        outs = [name + ".srcjar"],
        cmd = "$(location //tools/build:xjc) $@ %s $(location %s)" % (" ".join(args), schema),
        tools = ["//tools/build:xjc"],
    )
    java_library(
        name = name,
        srcs = [":" + name + ".srcjar"] + srcs,
        deps = deps + ["@maven//:jakarta_xml_bind_jakarta_xml_bind_api"],
        runtime_deps = ["@maven//:org_glassfish_jaxb_jaxb_runtime"],
        visibility = visibility,
    )

def xspec_test(name, specs, data = [], **kwargs):
    """One cached JUnit 5 test per .xspec file (run by XSpecTests), plus a test_suite named `name`."""
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
