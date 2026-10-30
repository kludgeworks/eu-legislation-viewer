# SPDX-License-Identifier: MIT
"""Build macros for the amendment viewer."""

load("@contrib_rules_jvm//java:defs.bzl", "java_junit5_test")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("@rules_java//java/common:java_info.bzl", "JavaInfo")

def xjc_library(name, schema, bindings = None, imports = [], xjc_args = [], srcs = [], deps = [], visibility = None):
    """Generate JAXB classes from an XSD (and its bindings) into a reproducible srcjar, and compile them.

    xjc turns the schema's xs:documentation into the classes' and properties' Javadoc. It writes
    into a temporary directory; Bazel's zipper packs that into the srcjar with fixed timestamps,
    sorted, so the srcjar is the same bytes on every build.

    Args:
      name: the java_library; the srcjar is `name`.srcjar.
      schema: the XSD.
      bindings: the JAXB bindings file (.xjb), if any.
      imports: the schemas the schema imports by file name, copied beside it.
      xjc_args: further xjc arguments, such as plugin switches.
      srcs: hand-written helpers the bindings name, such as an XmlAdapter.
      deps: further dependencies of the generated code.
      visibility: the java_library's visibility.
    """
    inputs = [schema] + list(imports) + ([bindings] if bindings else [])
    args = ["-npa", "-no-header", "-quiet", "-extension"] + list(xjc_args)
    if bindings:
        args += ["-b", "$$in/$$(basename $(location %s))" % bindings]
    native.genrule(
        name = name + "_srcjar",
        srcs = inputs,
        outs = [name + ".srcjar"],
        # the bindings name the schema by its file name (schemaLocation), so the two are copied side
        # by side, wherever their packages are
        cmd = " && ".join([
            "zipper=$$PWD/$(execpath @bazel_tools//tools/zip:zipper)",
            "srcjar=$$PWD/$@",
            "in=$$(mktemp -d)",
            "out=$$(mktemp -d)",
            "cp %s $$in/" % " ".join(["$(location %s)" % i for i in inputs]),
            "$(execpath //tools/build/src/main/java/dev/groknull/kludgeworks/build:xjc) -d $$out %s $$in/$$(basename $(location %s))" % (" ".join(args), schema),
            "cd $$out",
            "$$zipper c $$srcjar $$(find . -name '*.java' | LC_ALL=C sort | sed 's|^[.]/||')",
            "rm -rf $$in $$out",
        ]),
        tools = [
            "//tools/build/src/main/java/dev/groknull/kludgeworks/build:xjc",
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
                "//tools/build/src/main/java/dev/groknull/kludgeworks/build:xspec_runner",
                "@maven//:org_junit_jupiter_junit_jupiter_engine",
                "@maven//:org_junit_platform_junit_platform_launcher",
                "@maven//:org_junit_platform_junit_platform_reporting",
            ],
            **kwargs
        )
        tests.append(":" + test_name)
    native.test_suite(name = name, tests = tests)

def schema_javadoc(name, schema, srcs, types = {}, components = {}, **kwargs):
    """Records' Javadoc from an XML Schema's documentation, checked and written.

    `name`_test checks the sources say it; `name`_update (bazel run) writes it into them.

    A record says what its schema type's documentation says, each component (@param) what the
    element or attribute it maps from says, each enum constant what its enumeration value says.
    Records and schema types share their names but for those the tables rename.

    Args:
      name: the prefix of the two targets.
      schema: the XML Schema.
      srcs: the records' sources.
      types: record types whose schema type has another name, {"Inline": "InlineNode"}; nested
        enums by their outer type, {"Unit.Kind": "UnitKind"}.
      components: components mapped from an element or attribute of another name,
        {"Row.cells": "td"}.
      **kwargs: passed to the java_junit5_test.
    """
    update = "bazel run //%s:%s_update" % (native.package_name(), name)
    flags = [
        "-Dschema-javadoc.schema=$(rootpath %s)" % schema,
        "-Dschema-javadoc.srcs='%s'" % " ".join(["$(rootpath %s)" % s for s in srcs]),
        "-Dschema-javadoc.types=" + ",".join(["%s=%s" % (k, v) for k, v in types.items()]),
        "-Dschema-javadoc.components=" + ",".join(["%s=%s" % (k, v) for k, v in components.items()]),
        "-Dschema-javadoc.update='%s'" % update,
    ]
    java_junit5_test(
        name = name + "_test",
        test_class = "dev.groknull.kludgeworks.build.SchemaJavadocTests",
        jvm_flags = flags,
        data = [schema] + srcs,
        runtime_deps = [
            "//tools/build/src/main/java/dev/groknull/kludgeworks/build:schema_javadoc_runner",
            "@maven//:org_junit_jupiter_junit_jupiter_engine",
            "@maven//:org_junit_platform_junit_platform_launcher",
            "@maven//:org_junit_platform_junit_platform_reporting",
        ],
        **kwargs
    )
    java_binary(
        name = name + "_update",
        main_class = "dev.groknull.kludgeworks.build.UpdateSchemaJavadoc",
        jvm_flags = flags,
        data = [schema] + srcs,
        runtime_deps = ["//tools/build/src/main/java/dev/groknull/kludgeworks/build:schema_javadoc"],
    )

def xslt_quality_test(name, srcs, **kwargs):
    """Check stylesheets with xslt-quality: one JUnit 5 test with a dynamic test per stylesheet.

    Every finding fails, whatever its level, except those of the rules
    //tools/build:xslt-quality.conf.xml switches off. The validator is built once, by
    //tools/build:xslt_quality_validator.

    Args:
      name: the test.
      srcs: the stylesheets, or filegroups of them.
      **kwargs: passed to java_junit5_test.
    """
    java_junit5_test(
        name = name,
        test_class = "dev.groknull.kludgeworks.build.XsltQualityTests",
        jvm_flags = ["-Dxslt-quality.files='%s'" % " ".join(["$(rootpaths %s)" % s for s in srcs])],
        data = srcs,
        runtime_deps = [
            "//tools/build/src/main/java/dev/groknull/kludgeworks/build:xslt_quality_runner",
            "@maven//:org_junit_jupiter_junit_jupiter_engine",
            "@maven//:org_junit_platform_junit_platform_launcher",
            "@maven//:org_junit_platform_junit_platform_reporting",
        ],
        **kwargs
    )

def _spring_boot_jar_impl(ctx):
    java = ctx.attr.app[JavaInfo]
    own = java.runtime_output_jars
    out = ctx.actions.declare_file(ctx.label.name + ".jar")
    args = ctx.actions.args()
    args.use_param_file("@%s", use_always = True)
    args.add(out)
    args.add(ctx.attr.main_class)
    args.add_all(own)
    for jar in java.transitive_runtime_jars.to_list():
        if jar not in own:
            # ours (this repository's) or a dependency's
            args.add("--local" if jar.owner.workspace_name == "" else "--library")
            args.add(jar)
    ctx.actions.run(
        executable = ctx.executable._tool,
        arguments = [args],
        inputs = java.transitive_runtime_jars,
        outputs = [out],
        mnemonic = "SpringBootJar",
        progress_message = "Packaging %{label} as a Spring Boot jar",
    )
    return [DefaultInfo(files = depset([out]))]

spring_boot_jar = rule(
    implementation = _spring_boot_jar_impl,
    doc = """A Spring Boot executable jar, <name>.jar: java -jar runs it.

    Not a java_binary's _deploy.jar: merging the jars keeps one META-INF/spring/*.imports of many,
    so the app would start without most of its auto-configuration. This is the layout Spring Boot's
    Maven and Gradle plugins write, by their Repackager: app's own jar with the launcher, every
    other runtime jar nested in BOOT-INF/lib.
    """,
    attrs = {
        "app": attr.label(
            doc = "The java_library with the main class; its runtime classpath is the app's.",
            mandatory = True,
            providers = [JavaInfo],
        ),
        "main_class": attr.string(doc = "The @SpringBootApplication class.", mandatory = True),
        "_tool": attr.label(
            default = "//tools/build/src/main/java/dev/groknull/kludgeworks/build:spring_boot_jar",
            executable = True,
            cfg = "exec",
        ),
    },
)

# The tdm: terms our queries use. tdm, Cellar's ontology of technical metadata (file names,
# checksums), isn't published, so it can't be pinned as CDM is; these are declared in Cellar's
# graph <http://publications.europa.eu/ontology/tdm> (checked 2026-10-30).
TDM_TERMS = [
    "http://publications.europa.eu/ontology/tdm#fixity_message_digest",
    "http://publications.europa.eu/ontology/tdm#stream_fixity",
    "http://publications.europa.eu/ontology/tdm#stream_name",
]

def sparql_terms_test(
        name,
        srcs,
        ontologies = ["@cdm//:cdm.rdf", "@cdm//:cdm_annotationproperties.rdf"],
        allowed = TDM_TERMS,
        **kwargs):
    """Check SPARQL queries against the ontologies that declare their terms: one test per query.

    A term in a namespace one of the ontologies covers (cdm:, cmr:, annotation:) fails when the
    ontology doesn't declare it or deprecates it; other namespaces (owl:, xsd:) aren't checked.

    Args:
      name: the test's name.
      srcs: the .rq files.
      ontologies: RDF/XML files declaring terms (rdf:about), CDM 4.17.0's by default.
      allowed: full IRIs of terms that exist but that no ontology here declares, tdm:'s by default.
      **kwargs: passed to the java_junit5_test.
    """
    java_junit5_test(
        name = name,
        test_class = "dev.groknull.kludgeworks.build.SparqlTermsTests",
        jvm_flags = [
            "-Dsparql-terms.ontologies='%s'" % " ".join(["$(rootpath %s)" % o for o in ontologies]),
            "-Dsparql-terms.queries='%s'" % " ".join(["$(rootpath %s)" % s for s in srcs]),
            "-Dsparql-terms.allowed='%s'" % " ".join(allowed),
        ],
        data = ontologies + srcs,
        runtime_deps = [
            "//tools/build/src/main/java/dev/groknull/kludgeworks/build:sparql_terms_runner",
            "@maven//:org_junit_jupiter_junit_jupiter_engine",
            "@maven//:org_junit_platform_junit_platform_launcher",
            "@maven//:org_junit_platform_junit_platform_reporting",
        ],
        **kwargs
    )
