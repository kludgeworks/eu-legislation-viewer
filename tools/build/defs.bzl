# SPDX-License-Identifier: MIT
"""Build macros for the amendment viewer."""

load("@contrib_rules_jvm//java:defs.bzl", "java_junit5_test")
load("@rules_java//java:defs.bzl", "java_library")
load("@rules_java//java/common:java_info.bzl", "JavaInfo")

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
        if jar in own:
            continue
        if jar.owner.workspace_name == "":
            # ours, by its Java package: Bazel's own names (libsparql.jar) repeat across packages
            args.add("--local")
            args.add(_local_jar_name(jar))
        else:
            args.add("--library")
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

def _local_jar_name(jar):
    """A target of ours by its Java package, or its component at a source root.

    dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra-sparql.jar for a package below
    src/main/java or src/main/resources, viewer-resources.jar for viewer/src/main/resources itself.
    """
    package = jar.owner.package
    for root in ["src/main/java", "src/main/resources"]:
        if root + "/" in package:
            package = package.split(root + "/")[1]
        elif package.endswith(root):
            package = package[:-len(root)].rstrip("/")
    return package.replace("/", ".") + "-" + jar.owner.name + ".jar"

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

def jooq_tables(name, migrations, package, config, deps = [], image = "postgres:18.6", **kwargs):
    """A module's tables as jOOQ classes, generated from its Flyway migrations: a java_library.

    The migrations run on Postgres in a container, and jOOQ's generator reads the schema they make
    (JooqCodegen): so it needs Docker, when the migrations or the configuration change. The
    generated code is jOOQ's, so it is left out of our checks.

    Args:
      name: the java_library.
      migrations: the module's migrations (db/migration/<module>/*.sql), all in one directory.
      package: the Java package the classes are generated into: the module's infra.jooq.
      config: jOOQ's generator configuration: what to generate (forced types and the like).
      deps: what the generated classes refer to: the types of forced types and their converters.
      image: Postgres, as the tests' (TestcontainersConfiguration).
      **kwargs: passed to the java_library.
    """
    native.genrule(
        name = name + "_sources",
        srcs = migrations + [config],
        outs = [name + "-sources.srcjar"],
        cmd = " && ".join([
            "out=$$(mktemp -d)",
            "migrations=$$(dirname $$(echo $(execpaths %s) | cut -d' ' -f1))" % migrations[0],
            "$(execpath //tools/build/src/main/java/dev/groknull/kludgeworks/build:jooq_codegen) $(execpath %s) $$migrations %s $$out/src %s > $$out/log 2>&1 || { cat $$out/log; exit 1; }" % (config, package, image),
            "jar=$$PWD/$@",
            "zipper=$$PWD/$(execpath @bazel_tools//tools/zip:zipper)",
            "cd $$out/src",
            "$$zipper c $$jar $$(find . -name '*.java' | sed 's|^./||' | LC_ALL=C sort)",
            "rm -rf $$out",
        ]),
        # Docker, outside the sandbox
        tags = ["no-sandbox", "requires-docker", "requires-network"],
        tools = [
            "//tools/build/src/main/java/dev/groknull/kludgeworks/build:jooq_codegen",
            "@bazel_tools//tools/zip:zipper",
        ],
    )
    java_library(
        name = name,
        srcs = [":" + name + "_sources"],
        # jOOQ's code, not ours: no Error Prone, doclint or lint warnings
        javacopts = ["-XepDisableAllChecks", "-Xdoclint:none", "-Xlint:none"],
        deps = deps + ["@maven//:org_jooq_jooq"],
        **kwargs
    )
