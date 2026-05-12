# c3kit-starter

Vite-style scaffolder for full-stack Clojure / ClojureScript projects, part of the [c3kit](https://github.com/cleancoders/c3kit) collection.

> **Status:** Phase 0 bootstrap. The CLI and templates are not yet implemented. See [`docs/specs/2026-05-12-c3kit-starter-roadmap-design.md`](docs/specs/2026-05-12-c3kit-starter-roadmap-design.md) for the roadmap.

## Install (planned)

```sh
curl -fsSL https://raw.githubusercontent.com/cleancoders/c3kit-starter/main/cli/install.sh | bash
```

The installer detects [Babashka](https://babashka.org) and `git`, installs Babashka if missing, then drops the `c3kit-create` command onto your `PATH`.

## Usage (planned)

```sh
c3kit-create                # interactive wizard
c3kit-create my-app         # wizard with project name pre-filled
c3kit-create --list         # list available templates
```

## Templates (planned)

| ID                       | Description                                                  | Status     |
|--------------------------|--------------------------------------------------------------|------------|
| `full-stack-reagent`     | Clojure backend + Reagent frontend (c3kit, Datomic, opt SSR) | phase 1    |
| `fe-vanilla`             | ClojureScript SPA, no framework                              | phase 1    |
| `fe-reagent`             | ClojureScript SPA with Reagent + optional SSG build          | phase 2    |
| `fe-ssg`                 | Static-site generator (Clojure-Astro)                        | phase 2    |
| `full-stack-non-reagent` | Backend + non-Reagent CLJS frontend                          | phase 2    |

## Repo layout

```
cli/         # bb CLI source + installer (phase 1)
templates/   # template trees, one dir per template (phase 1+)
docs/        # design specs and implementation plans
```

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). All design work flows through specs in `docs/specs/` and plans in `docs/plans/`.

## License

MIT — see [`LICENSE`](LICENSE).
