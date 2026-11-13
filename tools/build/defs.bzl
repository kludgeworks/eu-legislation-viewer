# SPDX-License-Identifier: MIT
"""Build macros for the amendment viewer."""

load("@contrib_rules_jvm//java:defs.bzl", "java_junit5_test", "java_library")
load("@rules_java//java/common:java_info.bzl", "JavaInfo")
load("//tools/bzl/rules:jooq_sources.bzl", "jooq_sources")
load("//tools/bzl/rules:jte_sources.bzl", "jte_sources")

def _project_version_impl(ctx):
    """Write the release version from Bazel's stable workspace status."""
    out = ctx.actions.declare_file("version.txt")
    ctx.actions.run_shell(
        inputs = [ctx.info_file],
        outputs = [out],
        arguments = [ctx.info_file.path, out.path],
        command = """\
set -eu
version="$(sed -n '/^BUILD_EMBED_LABEL /{s/^BUILD_EMBED_LABEL //;p;q;}' "$1")"
if [ -z "$version" ]; then
  # The checked-in converter fixtures predate project-wide release stamping.
  # An explicit --embed_label is required for every release build.
  version="1.0.0"
fi
printf '%s\\n' "$version" > "$2"
""",
        mnemonic = "ProjectVersion",
        progress_message = "Stamping project version for %{label}",
    )
    return [DefaultInfo(files = depset([out]))]

project_version = rule(
    implementation = _project_version_impl,
    doc = """A version.txt resource, stamped from --embed_label for release builds.

    The fallback keeps the checked-in converter snapshots stable. Releases pass their validated
    YYYY.MM.MICRO CalVer value through --embed_label.
    """,
)

def _build_info_impl(ctx):
    """Write Spring Boot's build-info.properties, its version from Bazel's stable workspace status."""
    out = ctx.actions.declare_file("META-INF/build-info.properties")
    ctx.actions.run_shell(
        inputs = [ctx.info_file],
        outputs = [out],
        arguments = [ctx.info_file.path, out.path, ctx.attr.artifact],
        command = """\
set -eu
version="$(sed -n '/^BUILD_EMBED_LABEL /{s/^BUILD_EMBED_LABEL //;p;q;}' "$1")"
printf 'build.artifact=%s\\nbuild.name=%s\\n' "$3" "$3" > "$2"
if [ -n "$version" ]; then
  printf 'build.version=%s\\n' "$version" >> "$2"
fi
""",
        mnemonic = "BuildInfo",
        progress_message = "Stamping build info for %{label}",
    )
    return [DefaultInfo(files = depset([out]))]

build_info = rule(
    implementation = _build_info_impl,
    attrs = {
        "artifact": attr.string(mandatory = True, doc = "build.artifact and build.name"),
    },
    doc = """META-INF/build-info.properties, as Spring Boot's build plugins write it: Boot's BuildProperties.

    Release builds pass their YYYY.MM.MICRO CalVer value through --embed_label, which becomes
    build.version. An unlabelled build (development, the tests) has no build.version.
    """,
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

def jooq_tables(name, migrations, package, config, deps = [], **kwargs):
    """A module's tables as jOOQ classes, generated from its Flyway migrations: a java_library.

    The migrations run on Postgres in Docker (production's image, by Testcontainers), and jOOQ's
    generator reads the schema they make (JooqCodegen). The generated code is jOOQ's, so it is left out of our
    checks. Each of its packages is in the infrastructure ring of the sliced onion, so the
    architecture rules do check how it is used.

    Args:
      name: the java_library.
      migrations: the module's migrations: the filegroup of its db/migration/<module>/*.sql, pending
        ones (NEXT_001__description.sql) included, which JooqCodegen runs after the released ones.
      package: the Java package the classes are generated into: the module's infra.jooq.
      config: jOOQ's generator configuration: what to generate (forced types and the like).
      deps: what the generated classes refer to: the types of forced types and their converters.
      **kwargs: passed to the java_library.
    """
    jooq_sources(
        name = name + "_sources",
        config = config,
        migrations = migrations,
        package = package,
    )
    java_library(
        name = name,
        srcs = [":" + name + "_sources"],
        # jOOQ's code, not ours: no Error Prone, doclint or lint warnings
        javacopts = ["-XepDisableAllChecks", "-Xdoclint:none", "-Xlint:none"],
        # the onion ring that JooqCodegen puts each generated package in
        deps = deps + ["@maven//:org_jmolecules_jmolecules_onion_architecture", "@maven//:org_jooq_jooq"],
        **kwargs
    )

def jte_library(name, srcs, root, models_exclude = None, deps = [], **kwargs):
    """Templates, precompiled: a java_library of their classes and jte-models' typed facade.

    jte generates the templates' Java (JteGenerate), as its Maven and Gradle plugins do, into the
    package TemplateEngine.createPrecompiled loads from; so the app runs them without jte's
    compiler (gg.jte.use-precompiled-templates). The facade (Templates, StaticTemplates,
    DynamicTemplates) calls each template by its path: layout/page.jte is layoutPage(…). The
    generated code is jte's, so it is left out of our checks.

    Args:
      name: the java_library.
      srcs: the templates, all below root.
      root: the templates' root directory, relative to this package: template names are paths below
        it.
      models_exclude: a pattern of the templates the facade leaves out, those only ever rendered by
        name: of their classes' names, as jte-models matches them
        (gg.jte.generated.precompiled.legal.JtepGenerated for legal/p.jte).
      deps: what the templates refer to: the types of their @params and imports.
      **kwargs: passed to the java_library.
    """
    jte_sources(
        name = name + "_sources",
        srcs = srcs,
        root = _path(root),
        models_exclude = models_exclude,
    )
    java_library(
        name = name,
        srcs = [":" + name + "_sources"],
        # jte's code, not ours: no Error Prone, doclint or lint warnings
        javacopts = ["-XepDisableAllChecks", "-Xdoclint:none", "-Xlint:none"],
        deps = deps + [
            "@maven//:gg_jte_jte_runtime",
            "@maven//:gg_jte_jte_models",
            "@maven//:gg_jte_jte_models_runtime",
            "@maven//:org_jspecify_jspecify",
        ],
        **kwargs
    )

def _path(directory):
    """A directory of this package's sources, from the execution root, where source files are."""
    package = native.package_name()
    return package if directory == "." else package + "/" + directory
