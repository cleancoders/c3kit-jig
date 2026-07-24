# Security CI Integration — Design

**Date:** 2026-07-24
**Status:** Approved (pending spec review)

## Goal

Adopt the reusable security-scan workflow from `cleancoders/github-actions`
(`security.yml@v1`) across the c3kit-jig monorepo. Cover three surfaces:

1. **The jig repo's own code** — `cli/install.sh` (bash) and the babashka CLI.
2. **Scaffolded projects** — every project generated from the
   `full-stack-reagent` template ships its own security CI.
3. **Rendered template output** — the verification harness scans rendered combos
   before release, since rendered output is the only valid-Clojure form of the
   Handlebars-annotated template source.

## Background

- `security.yml` is a **reusable workflow** (`workflow_call`) running six
  scanners. Hard-fail: `clj-kondo`, `clj-holmes`, `shellcheck`, `gitleaks`.
  Advisory-by-default (per-consumer toggle): `clj-watson`, `semgrep`.
- Its input defaults assume a single-project layout (`src/clj src/cljs src/cljc`,
  `./bin`, `deps.edn` at root). The jig repo is a **monorepo**
  (`cli/` = babashka CLI on `bb.edn`; `templates/` = Handlebars template source;
  `verification/` = the bb verification harness) and needs input overrides.
- Existing `ci.yml` already runs a standalone `shellcheck cli/install.sh` job and
  a `cli` test/lint job. `template-full-stack-reagent.yml` runs the harness
  (`bb verify`) across combos.
- The template layout matches the reusable workflow's defaults:
  `src/clj src/cljs src/cljc`, a `bin/` dir with shell scripts, and a root
  `deps.edn` — so the baked caller needs no input overrides.

## Part 1 — Jig repo's own security (fold into `ci.yml`)

Add a job to `.github/workflows/ci.yml` that calls the reusable workflow:

```yaml
  security:
    uses: cleancoders/github-actions/.github/workflows/security.yml@v1
    with:
      src-paths: "cli/src cli/spec"
      shellcheck-dir: "."
    secrets: inherit
```

Behavior per scanner:

- **clj-kondo** — lints the CLI source only (`cli/src cli/spec`). The
  `verification/` harness is build tooling that never ships, so it is excluded.
  Template source is Handlebars-annotated and is also excluded (not valid
  Clojure until rendered; covered by Part 3).
- **shellcheck** (`shellcheck-dir: "."`) — one pass covers `cli/install.sh` and
  `templates/full-stack-reagent/bin/*`.
- **clj-holmes** — SAST over the whole repo (`path: '.'`), as shipped.
- **gitleaks** — full-history secret scan, as shipped. Add a repo-local
  `.gitleaksignore` baseline only if the initial run surfaces a pre-existing
  backlog.
- **clj-watson** — self-skips: the jig root has `bb.edn`, no `deps.edn`.
  Accepted risk — the CLI's own deps are three dev libraries (cljfmt, tools.cli,
  speclj). Not worth converting the CLI to `deps.edn` for CVE scanning.

**Remove** the standalone `shellcheck cli/install.sh` job from `ci.yml` — the
reusable workflow's shellcheck step now covers it (and more).

## Part 2 — Bake caller into template output

Ship a static caller workflow in the template so every scaffolded project gets
security CI on day one:

`templates/full-stack-reagent/.github/workflows/security.yml`

```yaml
name: Security
on:
  pull_request: {}
  workflow_call: {}
jobs:
  security:
    uses: cleancoders/github-actions/.github/workflows/security.yml@v1
    with:
      clj-watson-blocking: true
      semgrep-blocking: true
    secrets: inherit
```

- All-default scanner targets (template layout matches defaults). Only override
  is the **blocking policy**: `clj-watson` and `semgrep` are set **blocking**, so
  a scaffolded project fails CI on any dependency CVE or semgrep finding. This
  enforces that generated projects start clean and stay clean.
- The file is static YAML with no Handlebars placeholders. The scaffolder must
  copy `.github/` through untouched.

**Plan must verify:** the scaffolder (`c3kit-template.bb` hook / `c3kit-template.edn`)
includes `.github/**` in scaffold output and does not strip or template it. Add
a regression assertion if `.github/` is not currently emitted.

## Part 3 — Harness security verification

Extend the verification harness (`bb verify`) so rendered combos are scanned
before release. Scanner breadth is matched to what varies across combos:

| Scanner | Scans | Varies by | Coverage |
|---------|-------|-----------|----------|
| `:security-workflow` presence check | rendered `.github/` | nothing (static) | **all combos** (bb check) |
| clj-kondo (`:lint`) | rendered source | feature flags | **all combos** (existing bb check) |
| clj-holmes | rendered source | feature flags | **1 features-maximal combo** (CI) |
| semgrep | rendered source | feature flags | **1 features-maximal combo** (CI) |
| clj-watson | rendered `deps.edn` | DB backend | **1 per DB with a driver** (sqlite, postgres, datomic) (CI) |

Rationale: DB backends are mutually exclusive, so no single render contains all
driver deps — a single-combo clj-watson scan would leave DB-driver CVEs
unscanned, hence one per driver-backed DB. Source SAST (clj-holmes, semgrep) is
largely DB/feature-independent, so one feature-maximal combo suffices.

clj-holmes runs on one representative combo in CI, **not** as a per-combo bb
check: making it a bb check would force the clj-holmes binary onto every
developer's machine for `bb verify`. Broad per-combo static coverage is retained
by the existing clj-kondo `:lint` check and the new `:security-workflow`
presence check, both of which run on every combo. Rendered-output clj-holmes
coverage still happens — the CI representative combo plus each scaffolded
project's own baked CI.

Checks:

- **All combos** (bb harness checks, folded into per-combo `verify-light`):
  - New `:security-workflow` check: assert rendered `.github/workflows/security.yml`
    exists, calls the reusable workflow `@v1`, and enables both blocking toggles.
  - Existing `:lint` (clj-kondo) on rendered source.
- **Heavy scans** (new CI jobs in `template-full-stack-reagent.yml`, each
  rendering a combo via a new `bb render` task):
  - clj-holmes + semgrep on one features-maximal combo (`memory-defaults`).
  - clj-watson on one combo per driver-backed DB (sqlite, postgres, datomic;
    memory has no driver).

**Blocking:** harness heavy scans are **blocking** (fail the verify / release).
A CVE or finding in the rendered template means every scaffolded project would
fail its own blocking CI — so it must be caught and fixed before release, not
shipped.

## Out of scope

- Converting the CLI from `bb.edn` to `deps.edn` for clj-watson coverage.
- Baking security CI into any template other than `full-stack-reagent`.
- Retagging / versioning of the upstream `cleancoders/github-actions` workflow
  (consumers pin the moving `@v1` tag).

## Testing

- Part 1: push a branch, confirm the `security` job runs and the redundant
  shellcheck job is gone; confirm clj-watson self-skips cleanly (notice, not
  failure).
- Part 2: scaffold a project, assert `.github/workflows/security.yml` is present,
  valid, and references `@v1` with blocking toggles set.
- Part 3: TDD the harness checks against rendered combos — presence/validity
  assertion, static SAST invocation, and the DB-matched clj-watson matrix.
  Verify a deliberately-planted finding fails the harness.
