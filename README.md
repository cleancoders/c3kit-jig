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

The verification harness scaffolds each template across a `{db × feature-combo}` matrix and runs structural checks (file presence, content substrings), lint (`clj-kondo`), format (`cljfmt`), Clojure specs, and — at the `full` tier — ClojureScript specs and a server-boot smoke test on the rendered project. CI runs the same harness on every push.

Run it locally from the `verification/` directory:

```sh
cd verification

bb verify-all                                   # every combo at its declared tier
bb verify --combo memory-defaults --tier full   # one combo, full tier
bb verify --combo memory-minimal --tier light   # one combo, light tier
bb verify --combo memory-minimal --verbose      # also show CLI scaffold stdout
bb test                                         # harness unit tests
```

Combos are defined in `verification/templates/<template-id>/combos/*.expected.edn`. Each file pins `:must-exist`, `:must-not-exist`, `:file-contains`, and `:file-not-contains` assertions on the rendered scaffold for one `db × features` permutation.

Tiers:

| Tier  | Checks                                                                                       |
|-------|----------------------------------------------------------------------------------------------|
| light | `no-cruft`, `combo`, `residue`, `ns-hyphen`, `lint`, `fmt`, `clj-clean`, `cljs-run`            |
| full  | light + `server-boot`                                                                         |

`--tier` overrides the per-combo default in `verification/templates/<template-id>/verify.edn`.

Prerequisites: [Babashka](https://babashka.org), `clojure`/`clj`, `clj-kondo`, `cljfmt`, `curl`, Node.js + a Playwright-installed Chromium (`npx playwright install --with-deps chromium`) for the `cljs-run` check. The `server-boot` check additionally needs whichever database backend the combo targets (`sqlite`, `postgres`, or `datomic-pro`).

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
