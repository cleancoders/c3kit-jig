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
cli/         # bb CLI source + installer
templates/   # template trees, one dir per template
docs/        # design specs and implementation plans
```

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
