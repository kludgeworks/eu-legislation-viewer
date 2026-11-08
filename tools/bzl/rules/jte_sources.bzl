# SPDX-License-Identifier: MIT
"""Rule for generating JTE source JARs."""

def _jte_sources_impl(ctx):
    out = ctx.actions.declare_file(ctx.label.name + ".srcjar")
    args = ctx.actions.args()
    args.add(ctx.attr.root)
    args.add(out)
    if ctx.attr.models_exclude:
        args.add(ctx.attr.models_exclude)
    ctx.actions.run(
        executable = ctx.executable._tool,
        arguments = [args],
        inputs = ctx.files.srcs,
        outputs = [out],
        tools = [ctx.executable._tool],
        mnemonic = "JteGenerate",
        progress_message = "Generating JTE sources for %{label}",
    )
    return [DefaultInfo(files = depset([out]))]

jte_sources = rule(
    implementation = _jte_sources_impl,
    doc = """A source JAR of precompiled JTE templates and their typed facade.""",
    attrs = {
        "srcs": attr.label_list(allow_files = True, mandatory = True),
        "root": attr.string(mandatory = True),
        "models_exclude": attr.string(),
        "_tool": attr.label(
            default = "//tools/build/src/main/java/dev/groknull/kludgeworks/build:jte_generate",
            executable = True,
            cfg = "exec",
        ),
    },
)
