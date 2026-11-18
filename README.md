<!-- SPDX-License-Identifier: MIT -->

# EU Legislation Viewer

Compare any two versions of an EU act, provision by provision, and see exactly what changed and when.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/hero-dark.webp">
  <img alt="Comparing two versions of Regulation (EU) 2015/1998: a timeline of its versions, the two compared, and the acts that changed it between them" src="docs/images/hero-light.webp">
</picture>

## Why

EUR-Lex publishes each consolidated version of an act, but not what changed between two of them. Finding out means
reading both side by side, then working back through the amending acts to learn which one made a change, and from when
it applies.

The viewer does that work: pick two dates, and it shows each difference in place, the act that made it, and when it
took effect, in any of the EU's official languages the act was published in.

## How it works

```mermaid
flowchart LR
  cellar[("Cellar<br/>EU Publications Office")] -- "Formex XML" --> convert["formex-converter<br/>(XSLT)"]
  convert -- "legal text" --> store[("Postgres<br/>texts as one shared tree")]
  store --> compare["comparison<br/>provision by provision"]
  compare --> pages["pages<br/>(htmx)"]
```

An act's text is fetched from Cellar the first time someone asks for it, converted from Formex, and stored as a tree that
every version shares, so an unchanged provision is stored once and two versions compare quickly.

### Act search

Set `VIEWER_CATALOGUE_SEARCH_ENABLED=true` on a viewer instance to build the shared Postgres search index.
The first build starts after ten seconds. The viewer checks hourly for the first successful build after 02:00 UTC.
It issues 39 sequential Cellar queries per build. All instances search the previous complete index during a refresh
and use Cellar until the first index is available. Catalogue maintainers review exact names and acronyms in SQL migrations.

## Develop

Java 25, Spring Boot and Spring Modulith, Postgres, htmx; built with Bazel. Tools come from [mise](https://mise.jdx.dev).

```sh
mise install          # the tools, at the versions in mise.toml
mise run app:dev      # the viewer, with Postgres in Docker
mise run test         # every test
```

`mise tasks` lists the rest; each has `--help`.

## Contributing

Bug reports and ideas are welcome as [issues](../../issues). Please open one before a large pull request.

## Licence and notice

The code is [MIT](LICENSE). The legal texts are © European Union, reused under the
[Commission's reuse policy](https://eur-lex.europa.eu/content/legal-notice/legal-notice.html). This isn't the official text,
nor legal advice: only the Official Journal of the European Union is authentic.
