# c3kit-jig Roadmap Design

**Date:** 2026-05-12
**Status:** Draft, pending approval
**Author:** Alex Root-Roatch
**Scope:** Roadmap-level design. CLI architecture, phasing, and per-template sketches. Each template and the CLI itself get their own follow-up sub-spec when their phase begins.

## Goal

Open-source, Vite-style scaffolder for full-stack Clojure / ClojureScript projects, branded as part of the c3kit collection. Interactive CLI bootstraps new projects from a registry of templates without forcing users to wire up tooling by hand.

## Scope & Decomposition

The project is too large for a single spec. It decomposes into the following deliverables:

| ID | Deliverable                              | Phase | Status                        |
|----|------------------------------------------|-------|-------------------------------|
| D0 | `c3kit-create` CLI (bb) + installer      | 1     | new                           |
| T1 | Template: full-stack Reagent             | 1     | adapt existing starter        |
| T5 | Template: FE-only vanilla CLJS           | 1     | new                           |
| T3 | Template: FE-only Reagent (+ opt SSG)    | 2     | new                           |
| T4 | Template: FE-only CLJS SSG (Clj-Astro)   | 2     | new                           |
| T2 | Template: full-stack non-Reagent         | 2 (lowest pri) | new                  |

Phase 1: CLI + T1 + T5 in parallel.
Phase 2: T3 + T4 in parallel (priority), T2 later.

**Sub-spec hierarchy:**

- `2026-05-12-c3kit-jig-roadmap-design.md` (this doc — roadmap + CLI contract)
- `c3kit-create-cli-design.md` (written when CLI work starts)
- `template-full-stack-reagent-design.md` (written when T1 work starts)
- `template-fe-vanilla-design.md` (written when T5 work starts)
- Phase 2 specs deferred.

## Repo Layout

Monorepo, MIT licensed. Single source of truth for CLI + templates; atomic PRs across both.

```
c3kit-jig/
├── cli/
│   ├── bb.edn                    # bb deps
│   ├── src/c3kit_create/
│   │   ├── main.clj              # entry: arg parse, dispatch
│   │   ├── wizard.clj            # interactive prompts (read-line + ansi)
│   │   ├── fetch.clj             # git clone --depth 1 OR local --template-dir
│   │   ├── rename.clj            # acme → user-name (port of bin/setup logic)
│   │   ├── features.clj          # toggle optional features per template
│   │   └── registry.clj          # template list + metadata
│   ├── test/c3kit_create/...
│   └── install.sh                # curl|bash entry; installs bb if missing, then CLI
├── templates/
│   ├── full-stack-reagent/       # T1
│   └── fe-vanilla/               # T5
├── docs/
└── LICENSE                       # MIT
```

## CLI Architecture (D0)

**Runtime:** Babashka. Distributed as a single uberscript (`c3kit-create.bb`) via GitHub release artifact.

**Command surface:**

```
c3kit-create                               # full interactive wizard (prompts name + template + features)
c3kit-create <name>                        # wizard, name pre-filled
c3kit-create <name> --template <id>        # skip template prompt
c3kit-create <name> --template <id> --yes  # non-interactive, all defaults
c3kit-create --list                        # list templates
c3kit-create --version
c3kit-create --upgrade                     # download latest uberscript
c3kit-create --template-dir <path>         # dev: use local templates dir
```

Env var `C3KIT_TEMPLATES=<path>` = same as `--template-dir`.

**Name validation:** regex `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`. Re-prompt on failure inside wizard; error + exit when name supplied via CLI. CLI also rejects names that match a template's source token (e.g. `acme`).

**Execution flow:**

```
parse args → resolve template (flag | prompt) → load template manifest →
prompt features (or apply --yes defaults) → fetch template tree →
rename tokens → apply feature toggles (delete/uncomment files) →
generate secrets → drop .git → git init → print next steps
```

**Template manifest** (`templates/<id>/c3kit-template.edn`):

```clojure
{:id          :full-stack-reagent
 :name        "Full-stack Reagent"
 :description "Clojure backend + Reagent frontend with c3kit, Datomic, optional SSR/CSP/content"
 :tokens      {:acme {:hyphen "acme" :underscore "acme" :pascal "Acme"}}
 :secrets     [:jwt-dev :jwt-staging :jwt-production]
 :features    [{:id :content      :prompt "Markdown content pipeline?" :default true}
               {:id :ssr          :prompt "SSR/prerender (Reagent + Node)?" :default true}
               {:id :csp          :prompt "Content Security Policy plugin?" :default false}
               {:id :markdownc    :prompt "Client-side markdown (CLJC)?" :default true}
               {:id :auth         :prompt "JWT auth?" :default true}]
 :db          {:options [:datomic-pro :sqlite :postgres :memory] :default :datomic-pro}}
```

CLI is template-agnostic. Templates declare their own prompts/features/tokens via manifest. New template = new dir + manifest + (optional) post-scaffold bb hook; **no CLI changes required.**

**Feature toggle mechanism:** Files/blocks tagged with `;; @c3kit/feature :ssr` markers. `features.clj` strips disabled blocks during scaffold. Per-template logic for non-trivial removals (e.g. delete `resources/prerender/` dir when `:ssr` off) lives in optional `templates/<id>/c3kit-template.bb` hook invoked by CLI after rename + toggle.

**Rename mechanism:** CLI-side only. Port of current `bin/setup` logic into `rename.clj` using token map from manifest. Template tree does **not** ship its own setup script — single source of truth in CLI. Power users can still `git clone` the template tree and run `c3kit-create --template-dir templates/full-stack-reagent <name>` against it.

## Installer & Distribution

**User-facing install command:**

```
curl -fsSL https://raw.githubusercontent.com/cleancoders/c3kit-jig/main/cli/install.sh | bash
```

(Homebrew tap `cleancoders/tap/c3kit-create` is a nice-to-have for phase 1.5.)

**`install.sh` responsibilities:**

1. Detect OS — abort with clear message if not Darwin / Linux / WSL.
2. Detect prerequisites:
   - `bb` (babashka): required. If missing, install via official one-liner.
   - `git`: required. Abort with link to git install docs if missing.
   - `java`: required for downstream Clojure work but not for CLI itself. Warn but do not abort if missing.
3. Download latest `c3kit-create` uberscript from GitHub release artifact to `~/.c3kit/bin/c3kit-create`, `chmod +x`.
4. Ensure `~/.c3kit/bin` on PATH: detect `$SHELL`, append idempotent export to `~/.bashrc` / `~/.zshrc` / `~/.config/fish/config.fish`. Print message telling user to `source` rc or restart shell.
5. Print verification command: `c3kit-create --version`.

**Uninstall:** documented as `rm -rf ~/.c3kit` + remove PATH line. No script in phase 1.

**Updates:** `c3kit-create --upgrade` re-runs step 3.

**OS scope phase 1:** macOS + Linux + WSL. Native Windows / PowerShell deferred to phase 2+ based on demand.

**Template fetch:** at scaffold time CLI runs `git clone --depth 1 --branch <ref>` of the monorepo into a temp dir, copies `templates/<id>/` out, deletes temp. Default `<ref>` = release tag pinned to current CLI version (mapping baked into `registry.clj` at CLI build time). Overridable via `--template-ref`. Local dev mode via `--template-dir` / `C3KIT_TEMPLATES` skips the clone entirely.

**Versioning:**

- CLI: `cli-v0.1.0`, `cli-v0.2.0`, …
- Templates: per-template tags like `template-full-stack-reagent-v0.1.0`.
- CLI release manifest maps CLI version → default template refs.

## Phase 1 Template Sketches

Per-template sub-specs will flesh these out. Sketch only here.

### T1 — Full-stack Reagent

**Source:** copy current internal starter tree → `templates/full-stack-reagent/`.

**Required changes vs current state:**

- Strip proprietary refs (internal config, `my.datomic.com` repo creds, internal seed data).
- Delete `bin/setup` (CLI owns rename).
- Rewrite README for OSS audience.
- Replace hard-coded Datomic Peer setup with c3kit/bucket multi-backend config; Datomic Pro stays default but SQLite / Postgres / memory become wizard options.
- Tag optional-feature blocks with `;; @c3kit/feature :ssr` (and `:content`, `:csp`, `:markdownc`, `:auth`).
- Add `c3kit-template.edn` manifest.
- Add `LICENSE` (MIT), `CONTRIBUTING.md`.
- CI (GitHub Actions): scaffold the template, run `clj -M:test:spec` and `clj -M:test:cljs once` against the result to prove the template still boots after changes.

**Open questions for T1 sub-spec:**

- Datomic Pro license / setup story for OSS users (free tier docs, single-jar install).
- Which c3kit/bucket backends ship as DB options on day one.
- Whether to ship a `docker-compose.yml` for the DB.

### T5 — FE-only vanilla CLJS

**Concept:** Vite-vanilla equivalent. Pure ClojureScript + DOM, no framework.

**Stack:**

- Build: `c3kit-scaffold` for cljs and css compile (matches T1 / wider c3kit ecosystem).
- Dev server: bb task (`bb serve`) — serves `public/` over HTTP, watches build outputs, triggers full-page reload via SSE. No shadow-cljs, no figwheel. Vanilla CLJS has no component state to preserve, so full reload is acceptable.
- Release: `bb release` produces a deployable static `public/` dir (Netlify / Vercel / S3).
- Tests: `clj -M:test:cljs` (same as T1). Speclj vs cljs.test decided in T5 sub-spec.

**Open questions for T5 sub-spec:**

- CSS pipeline: same `c3kit.scaffold.css` as T1, or simpler (raw CSS / Tailwind)?
- Tooling parity with T1 wherever possible (consistent `bin/` scripts) vs minimalism.
- Phase 2 candidate: upstream `bb serve`/`bb release` logic into `c3kit.scaffold.dev-server` so future templates inherit it instead of duplicating.

## Risks & Mitigations

| Risk                                                                | Mitigation                                                                                                                  |
|---------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| Datomic Pro free tier auth/install confusion for OSS users          | Default is Datomic Pro per user choice; SQLite is the zero-friction wizard option. Doc Datomic setup separately.            |
| Feature-toggle markers polluting template source                    | Markers are comments only, stripped at scaffold. CI runs scaffolded output (post-strip).                                     |
| Template added but CLI registry not updated                         | Single `registry.clj`. CI fails if `templates/<id>/c3kit-template.edn` exists without a registry entry, and vice versa.      |
| `curl | bash` installer is a security smell                         | Document SHA256 verification path. Provide manual install instructions in README. GPG-sign releases (phase 2).               |
| bb install fails on niche distros                                    | Installer exits with clear message + link to bb's own install docs. Do not get clever.                                       |
| Token rename collisions (user picks `acme`)                          | CLI validates user name against each template's source token map and rejects collisions.                                     |

## Success Criteria (Phase 1)

- `curl … | bash` on a clean macOS / Linux / WSL machine installs `c3kit-create` end-to-end.
- `c3kit-create` (no args) walks the user through name + template + features + DB and produces a working project.
- `cd <project> && clj -M:test:spec` passes for T1 with the chosen DB (Datomic Pro or SQLite).
- `cd <project> && clj -M:test:cljs once && bb serve` produces a working static site for T5; `bb release` produces a deployable `public/` dir.
- All renames complete — no `acme` strings remain (CI grep test).
- Each scaffolded project's README tells the user exactly how to run, test, and deploy.

## Out of Scope (Phase 1)

- Native Windows / PowerShell installer.
- Homebrew tap (nice-to-have, phase 1.5).
- GPG-signed releases.
- Plugin / community-template registry beyond the monorepo.
- Per-template scoped uninstall script.
- Phase 2 templates (T2, T3, T4) and their concerns.
