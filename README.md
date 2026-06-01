# Jig

![Jig](https://github.com/cleancoders/c3kit/blob/master/img/jig_200.png?raw=true)

A library component of [c3kit — Clean Coders Clojure Kit](https://github.com/cleancoders/c3kit).

> _"First make the jig — then make a thousand parts the same."_ — Workshop proverb

[![Jig Build](https://github.com/cleancoders/c3kit-jig/actions/workflows/ci.yml/badge.svg)](https://github.com/cleancoders/c3kit-jig/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Jig is the project scaffolder for the c3kit toolkit — a Vite-style CLI that templates new full-stack Clojure / ClojureScript projects with sensible defaults and selectable features.

## Install

```sh
curl -fsSL https://raw.githubusercontent.com/cleancoders/c3kit-jig/main/cli/install.sh | bash
```

The installer detects [Babashka](https://babashka.org) and `git`, installs Babashka if missing, then drops the `c3kit-jig` command onto your `PATH`.

## Usage

```sh
c3kit-jig                   # show help
c3kit-jig create            # interactive wizard
c3kit-jig create my-app     # wizard with project name pre-filled
c3kit-jig list              # list available templates
c3kit-jig upgrade           # download latest CLI release
c3kit-jig version           # print CLI version
```

## Templates

| ID                   | Description                                                  | Status  |
|----------------------|--------------------------------------------------------------|---------|
| `full-stack-reagent` | Clojure backend + Reagent frontend (c3kit, Datomic, opt SSR) | shipped |

See [Roadmap](#roadmap) for templates in progress or planned.

## Repo layout

```
cli/           # bb CLI source + installer
templates/     # template trees, one dir per template
verification/  # post-scaffold verification harness
docs/          # design specs and implementation plans
```

## Verification harness

The verification harness scaffolds each template across a `{db × feature-combo}` matrix and runs the checks listed below. CI runs the same harness on every push.

Run it locally from the `verification/` directory:

```sh
cd verification

bb verify-all                                   # every combo at its declared tier
bb verify --combo memory-defaults --tier full   # one combo, full tier
bb verify --combo memory-minimal --tier light   # one combo, light tier
bb verify --combo memory-minimal --verbose      # also show CLI scaffold stdout
bb scan-coupling                                # static cross-feature coupling scan
bb test                                         # harness unit tests
```

### Checks

| Check          | Purpose                                                                                       |
|----------------|-----------------------------------------------------------------------------------------------|
| `no-cruft`     | No denylisted paths in scaffold (`.idea`, `.cpcache`, `target`, template-dev files, etc.).    |
| `combo`        | `:must-exist` / `:must-not-exist` / `:file-contains` / `:file-not-contains` assertions match. |
| `residue`      | No leftover `;; @c3kit/feature` or `;; @c3kit/db` markers in the rendered scaffold.           |
| `ns-hyphen`    | Every `ns` form uses hyphens (underscores only in filenames).                                 |
| `lint`         | `clj-kondo` clean against scaffold `.clj-kondo/config.edn` (strict; zero findings allowed).   |
| `fmt`          | `cljfmt check src spec dev` clean against scaffold `cljfmt.edn`.                              |
| `clj-clean`    | `clojure -M:test:spec` exits 0 with zero failures and clean log output.                       |
| `cljs-run`     | `clojure -M:test:cljs-spec` exits 0, > 0 examples, 0 failures, no stray log lines.            |
| `server-boot`  | `clojure -M:test:run` boots, `curl /` returns HTTP 200, then SIGTERM.                         |
| coupling scan  | Static pass before any combo: every cross-feature ref must sit inside a matching marker.      |

Tiers:

| Tier  | Checks                                                                                       |
|-------|----------------------------------------------------------------------------------------------|
| light | `no-cruft`, `combo`, `residue`, `ns-hyphen`, `lint`, `fmt`, `clj-clean`, `cljs-run`           |
| full  | light + `server-boot`                                                                        |

`--tier` overrides the per-combo default in `verification/templates/<template-id>/verify.edn`.

### Combos

Combos are defined in `verification/templates/<template-id>/combos/*.expected.edn`. Each file pins `:must-exist`, `:must-not-exist`, `:file-contains`, and `:file-not-contains` assertions on the rendered scaffold for one `db × features` permutation. Add a new combo by dropping a new `.expected.edn` file and registering it under `:combos` in `verify.edn` with a tier.

### Canonical configs

`clj-kondo.edn` and `cljfmt.edn` at the repo root are the single source of truth for lint and format rules across the monorepo. They are symlinked into `verification/` and into each template, so changes to one canonical file affect both the harness self-checks and every generated scaffold.

Templates carry **two** clj-kondo configs:

- `.clj-kondo/config.edn` — loose, for editing the marker-laden raw template. NOT shipped to scaffolds.
- `.scaffold-clj-kondo/config.edn` — strict + project-specific (lazy-route excludes, secretary route bindings). Merges the canonical via `:config-paths ["c3kit-shared"]` (a symlink back to the canonical).

The CLI render step renames `.scaffold-clj-kondo/` to `.clj-kondo/` on the way out, so scaffolds receive the strict config under the conventional path.

### Prerequisites

[Babashka](https://babashka.org), `clojure`/`clj`, `clj-kondo`, `cljfmt`, `curl`, Node.js + a Playwright-installed Chromium (`npx playwright install --with-deps chromium`) for the `cljs-run` check. The `server-boot` check additionally needs whichever database backend the combo targets (`sqlite`, `postgres`, or `datomic-pro`).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). All design work flows through specs in `docs/specs/` and plans in `docs/plans/`. Pull requests must be linked to an open, maintainer-acknowledged issue.

## Roadmap

| ID                       | Description                                                  | Status      |
|--------------------------|--------------------------------------------------------------|-------------|
| `full-stack-reagent`     | Clojure backend + Reagent frontend (c3kit, Datomic, opt SSR) | shipped     |
| `fe-vanilla`             | ClojureScript SPA, no framework                              | in progress |
| `fe-reagent`             | ClojureScript SPA with Reagent + optional SSG build          | not started |
| `fe-ssg`                 | Static-site generator (Clj-Astro)                            | not started |
| `full-stack-non-reagent` | Backend + non-Reagent CLJS frontend                          | not started |

Roadmap details: [`docs/specs/2026-05-12-c3kit-jig-roadmap-design.md`](docs/specs/2026-05-12-c3kit-jig-roadmap-design.md).

## Security

See [`SECURITY.md`](SECURITY.md) for the private vulnerability-reporting channel.

## License

MIT — see [`LICENSE`](LICENSE).
