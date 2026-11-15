# SPDX-License-Identifier: MIT
"""A test that finds copied Java code across modules with PMD's CPD (ISS-82)."""

JavaSourcesInfo = provider(
    doc = "The Java sources of this repository that a target and its dependencies compile.",
    fields = {"sources": "depset of .java source files"},
)

def _java_sources_impl(_target, ctx):
    own = [
        f
        for f in getattr(ctx.rule.files, "srcs", [])
        if f.is_source and f.extension == "java" and not f.owner.workspace_name
    ]
    deps = []
    for name in ("deps", "runtime_deps", "exports", "srcs"):
        for dep in getattr(ctx.rule.attr, name, []):
            if type(dep) == "Target" and JavaSourcesInfo in dep:
                deps.append(dep[JavaSourcesInfo].sources)
    return [JavaSourcesInfo(sources = depset(own, transitive = deps))]

_java_sources = aspect(
    implementation = _java_sources_impl,
    attr_aspects = ["deps", "runtime_deps", "exports", "srcs"],
    doc = "Collects the .java sources of this repository, not of external repositories.",
)

def _cpd_test_impl(ctx):
    sources = depset(transitive = [target[JavaSourcesInfo].sources for target in ctx.attr.targets])
    files = sources.to_list()
    listed = ctx.actions.declare_file(ctx.label.name + ".files")
    ctx.actions.write(listed, "\n".join([f.short_path for f in files]) + "\n")
    launcher = ctx.actions.declare_file(ctx.label.name + ".sh")
    ctx.actions.write(
        launcher,
        """#!/usr/bin/env bash
set -euo pipefail
exec "{pmd}" cpd \\
  --minimum-tokens {tokens} \\
  --file-list "{listed}" \\
  --language java \\
  --ignore-annotations \\
  --fail-on-violation \\
  --no-fail-on-error
""".format(
            pmd = ctx.executable._pmd.short_path,
            tokens = ctx.attr.minimum_tokens,
            listed = listed.short_path,
        ),
        is_executable = True,
    )
    runfiles = ctx.runfiles(files = files + [listed]).merge(ctx.attr._pmd[DefaultInfo].default_runfiles)
    return [DefaultInfo(executable = launcher, runfiles = runfiles)]

cpd_test = rule(
    implementation = _cpd_test_impl,
    test = True,
    doc = """Fails if a run of at least minimum_tokens tokens of Java is in two places.

    The sources are those of this repository that the targets compile, with their dependencies:
    a Java target and the targets it depends on, or a filegroup of .java files. They are the
    test's inputs, so Bazel runs it again only when one of them changes.
    """,
    attrs = {
        "targets": attr.label_list(
            aspects = [_java_sources],
            mandatory = True,
            doc = "Java targets or filegroups whose sources, and their dependencies' sources, CPD reads",
        ),
        "minimum_tokens": attr.int(default = 100, doc = "The shortest copy that counts"),
        "_pmd": attr.label(
            default = "//tools/build:pmd_cli",
            executable = True,
            cfg = "target",
        ),
    },
)
