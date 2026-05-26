# `c3kit-create` CLI Sub-Spec

**Date:** 2026-05-12
**Status:** Draft, pending approval
**Parent spec:** [`2026-05-12-c3kit-jig-roadmap-design.md`](2026-05-12-c3kit-jig-roadmap-design.md)
**Scope:** Implementation contract for the `c3kit-create` CLI alone. The CLI is one of three Phase 1 deliverables (alongside templates `full-stack-reagent` and `fe-vanilla`). Templates have their own sub-specs.

## Goal

A Babashka-based command-line scaffolder that turns a chosen template into a personalized project tree, with feature toggles, secret generation, atomic move semantics, and optional git-init + dep-install. Distributed as a single uberscript via GitHub Releases and installed by a `curl … | bash` script.

---

## 1. Scope & Non-Goals

**In scope (v1):**

- Single-command interactive scaffolder: prompt user → fetch template → rename + toggle features + generate secrets → atomic move → optional `git init + commit` + optional `--install`.
- Argument-driven non-interactive mode (`--template … --yes`) for CI / scripting.
- Local-dev fetch mode (`--template-dir` / `C3KIT_TEMPLATES`) so the CLI can be developed before any real template ships.
- Distributed as bb uberscript via GitHub Releases. Installer = bash, target macOS / Linux / WSL.
- Self-updates via `--upgrade` (re-downloads latest release uberscript).

**Out of scope (v1):**

- Native Windows / PowerShell installer.
- Homebrew tap.
- `bbin` integration.
- `--force` collision override.
- `--resume` for partial scaffolds.
- Community template registry / plugin system.
- Telemetry.
- Arrow-key TUI prompts (typed input only — see §4).
- Multi-language i18n.
- Self-hosted template mirror.

---

## 2. Module Layout & Dependencies

```
cli/
├── bb.edn                          # bb deps + tasks (test, uberscript, lint)
├── src/c3kit_create/
│   ├── main.clj                    # entry: parse args → dispatch subcommand
│   ├── args.clj                    # arg parser (clojure.tools.cli)
│   ├── wizard.clj                  # interactive prompts (typed input)
│   ├── registry.clj                # baked-in template list + version pins
│   ├── manifest.clj                # parse c3kit-template.edn
│   ├── fetch.clj                   # git clone --depth 1 | local --template-dir
│   ├── rename.clj                  # token replacement (acme → user-name)
│   ├── features.clj                # @c3kit/feature marker stripping + file deletion
│   ├── secrets.clj                 # JWT-style secret generation
│   ├── hook.clj                    # invoke template's optional c3kit-template.bb
│   ├── postscaffold.clj            # git init, commit, --install runner
│   ├── ui.clj                      # ANSI color + step printing + friendly errors
│   ├── version.clj                 # CLI semver + upgrade fetcher
│   └── fs.clj                      # path helpers, atomic move, temp-dir mgmt
├── spec/c3kit_create/              # Speclj suites (one per src file)
├── test-fixtures/
│   └── tiny-fixture/               # fake template for e2e tests
├── dist/                           # build output, gitignored
└── install.sh
```

Each file has one clear responsibility — `wizard.clj` only knows how to prompt, never touches disk; `fetch.clj` only knows how to put bytes into a temp dir, never renames; etc. Composed in `main.clj`.

**Dependencies (bb.edn):**

```clojure
{:paths ["src"]
 :deps  {org.clojure/tools.cli {:mvn/version "1.1.230"}
         speclj/speclj         {:mvn/version "3.12.0"}}
 :tasks
 {test       {:doc "Run all tests"
              :task (shell "bb" "-m" "speclj.cli.run.standard")}
  uberscript {:doc "Build cli/dist/c3kit-create.bb"
              :task (shell "bb" "uberscript" "dist/c3kit-create.bb"
                          "-m" "c3kit-create.main")}
  lint       {:doc "Run clj-kondo"
              :task (shell "clj-kondo" "--lint" "src" "spec")}}}
```

Only non-stdlib deps are `tools.cli` (bb-compatible) and `speclj` (testing). Everything else is from bb stdlib (`babashka.fs`, `babashka.process`, `babashka.http-client`, `clojure.edn`).

**Test framework:** Speclj-on-bb (per design decision). Carries known risk that speclj's macros / runner may not load under sci. The implementation plan starts with a 1-task spike to confirm; if blocked, fall back to `clojure.test` (no design change required, just file renames).

---

## 3. Command Surface & Exit Codes

```
c3kit-create — scaffold a new Clojure project from a c3kit template

USAGE
  c3kit-create [<name>] [options]
  c3kit-create --list
  c3kit-create --version
  c3kit-create --upgrade

ARGUMENTS
  <name>                    Project name (kebab-case). Prompted if omitted.

OPTIONS
  -t, --template <id>       Template id (skip template prompt)
      --template-ref <ref>   Git ref/tag/branch for template fetch
                            (default: pin baked into this CLI version)
      --template-dir <path>  Use local templates dir instead of fetching
                            (also: env C3KIT_TEMPLATES)
  -y, --yes                 Accept all feature defaults, non-interactive
      --install             Run `clj -P` (and `npm install` if applicable)
                            after scaffold
      --no-git              Skip `git init` and initial commit
      --debug               Print full stack traces on error
  -h, --help                Show this help
      --version             Print CLI version
      --list                List available templates
      --upgrade             Download latest CLI release and replace this binary
```

**Examples:**

```sh
c3kit-create
c3kit-create my-app
c3kit-create my-app --template full-stack-reagent --yes
c3kit-create my-app -t fe-vanilla --install
C3KIT_TEMPLATES=./templates c3kit-create my-app -t full-stack-reagent
```

**Flag override priority** (highest wins): CLI flag > env var > wizard answer > template manifest default.

**Stdin discipline:** if stdin is not a tty and `--yes` was not passed, the wizard refuses with exit 2 + msg `"non-interactive shell requires --yes"`. Prevents hangs in CI.

**Exit codes:**

| Code | Meaning                                                          |
|------|------------------------------------------------------------------|
| 0    | Success                                                          |
| 1    | Generic / unexpected error (stack trace under `--debug`)         |
| 2    | Bad CLI usage — arg parse failure, unknown flag                  |
| 3    | Target dir already exists (collision)                            |
| 4    | Invalid project name (regex fail) or invalid feature value       |
| 5    | Unknown template id                                              |
| 6    | Manifest read / parse error                                      |
| 7    | Fetch failure (git clone error, network, no `git` on PATH)       |
| 8    | Rename / feature-toggle / secret generation failure              |
| 9    | Post-scaffold step failure (git init, commit, `clj -P`, etc.)    |
| 10   | User aborted (Ctrl+C, "no" at final confirmation)                |
| 11   | `--upgrade` download / install failure                           |

---

## 4. Wizard Flow

Typed-input prompts only — no arrow-key TUI. ANSI color always on (with TTY fallback to plain when stdout isn't a tty). Sample full session:

```
$ c3kit-create
┌─ c3kit-create v0.1.0 ────────────────────────────────────────────┐

Project name: my-app
  ✓ ok

Available templates:
  1) full-stack-reagent   — Clojure backend + Reagent FE (c3kit, Datomic, opt SSR)
  2) fe-vanilla           — ClojureScript SPA, no framework
Template [1]: 1
  ✓ full-stack-reagent

Features (press Enter for default):
  Markdown content pipeline?            [Y/n]:
  SSR/prerender (Reagent + Node)?       [Y/n]:
  Content Security Policy plugin?       [y/N]:
  Client-side markdown (CLJC)?          [Y/n]:
  JWT auth?                             [Y/n]:

Database:
  1) datomic-pro (default)
  2) sqlite
  3) postgres
  4) memory
Database [1]: 2
  ✓ sqlite

Summary:
  name      my-app
  template  full-stack-reagent v0.1.0
  features  content, ssr, markdownc, auth
  database  sqlite
  target    /Users/.../my-app
  git init  yes (default)
  install   no (default)

Proceed? [Y/n]: y

▸ fetching template …
▸ rendering tokens …
▸ generating secrets …
▸ applying feature toggles …
▸ moving into place …
▸ git init + initial commit …

✓ Done. Created my-app in 4.2s.

Next steps:
  cd my-app
  clj -M:test:spec               # run Clojure specs
  clj -M:test:cljs               # run ClojureScript specs (auto-watch)
  clj -M:test:css                # compile CSS (auto-watch)
  clj -M:test:cljss              # compile CLJS + CSS (auto-watch, combined)
  clj -M:test:dev                # run server + specs + cljs in one process
  clj -M:test:run                # run server only
```

**Prompt protocol:**

- `read-line`, trim; lowercased where applicable.
- Empty input → default (bracketed value).
- Y/n: `y` / `yes` true; `n` / `no` false; anything else re-prompts with hint.
- Numbered select: integer in range; otherwise re-prompt.
- Free-form (name): validated via regex `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, re-prompt on miss with the regex shown.
- Ctrl+C: catch SIGINT, print "Aborted.", remove temp stage dir, exit 10.

**`--yes`** skips all prompts and uses manifest defaults. If `<name>` not provided alongside `--yes` → exit 2 ("name required with --yes").

The "Next steps" block is **not hardcoded** in the CLI — it is rendered from the template's manifest (`:next-steps`). See §5.

---

## 5. Template Manifest

**Location:** `templates/<id>/c3kit-template.edn`. Required for every template. CLI fails fast (exit 6) if absent or malformed.

**Schema:**

```clojure
{;; --- Identity ---
 :id           :full-stack-reagent              ; keyword, must equal dir name
 :name         "Full-stack Reagent"             ; display name
 :description  "Clojure backend + Reagent frontend with c3kit, Datomic, opt SSR/CSP/content"
 :version      "0.1.0"                          ; template semver (independent of CLI)
 :min-cli      "0.1.0"                          ; oldest CLI that can scaffold this template
 :test-only?   false                            ; true → hidden from --list and wizard

 ;; --- Rename tokens ---
 :tokens       {"acme"  {:hyphen     true        ; replace literal "acme"
                         :underscore true        ; replace literal "acme" inside snake_case
                         :pascal     true}       ; replace literal "Acme"
                "ACME_" {:upper-prefix true}}    ; replace "ACME_*" prefixes

 ;; --- Secrets ---
 :secrets      [{:placeholder "ACME_DEV_SECRET"        :bytes 24}
                {:placeholder "ACME_STAGING_SECRET"    :bytes 24}
                {:placeholder "ACME_PRODUCTION_SECRET" :bytes 24}]

 ;; --- Features ---
 :features     [{:id :content
                 :prompt   "Markdown content pipeline?"
                 :default  true
                 :delete-when-off ["content/"
                                   "src/clj/{{acme}}/content.clj"
                                   "spec/clj/{{acme}}/content_spec.clj"]}
                {:id :ssr
                 :prompt   "SSR/prerender (Reagent + Node)?"
                 :default  true
                 :delete-when-off ["resources/prerender/"
                                   "package.json"
                                   "src/clj/{{acme}}/prerender.clj"]}
                {:id :csp
                 :prompt   "Content Security Policy plugin?"
                 :default  false
                 :delete-when-off ["src/clj/{{acme}}/security/csp.clj"]}
                {:id :markdownc
                 :prompt   "Client-side markdown (CLJC)?"
                 :default  true
                 :delete-when-off ["src/cljc/{{acme}}/markdownc.cljc"]}
                {:id :auth
                 :prompt   "JWT auth?"
                 :default  true
                 :delete-when-off ["src/clj/{{acme}}/auth.clj"
                                   "src/clj/{{acme}}/auth/"]}]

 ;; --- Database (optional, only for templates that have a DB layer) ---
 :db           {:prompt  "Database"
                :options [{:id :datomic-pro :label "Datomic Pro"}
                          {:id :sqlite      :label "SQLite"}
                          {:id :postgres    :label "Postgres"}
                          {:id :memory      :label "In-memory (dev only)"}]
                :default :datomic-pro}

 ;; --- Post-scaffold "next steps" ---
 :next-steps   [{:cmd "cd {{name}}"          :doc nil}
                {:cmd "clj -M:test:spec"     :doc "run Clojure specs"}
                {:cmd "clj -M:test:cljs"     :doc "run ClojureScript specs (auto-watch)"}
                {:cmd "clj -M:test:css"      :doc "compile CSS (auto-watch)"}
                {:cmd "clj -M:test:cljss"    :doc "compile CLJS + CSS (auto-watch, combined)"}
                {:cmd "clj -M:test:dev"      :doc "run server + specs + cljs in one process"}
                {:cmd "clj -M:test:run"      :doc "run server only"}]

 ;; --- Optional post-scaffold hook ---
 :hook?        false}
```

**Validation rules** enforced by `manifest.clj` at load time (failure → exit 6):

- `:id` matches `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` and equals dir name.
- `:version` is a valid semver.
- All `:delete-when-off` paths are inside the template dir (no `..`).
- `:tokens` map keys are non-empty strings; no overlap that would create ambiguous replacement order.
- `:secrets` placeholders are unique.
- `:features` ids are unique keywords.
- `:db.options` ids are unique; `:db.default` is one of them.
- `:next-steps[].cmd` required; `:doc` optional.

**Registry:** `registry.clj` is generated from on-disk manifests at uberscript build time (single source of truth). CI fails if a `templates/<id>/c3kit-template.edn` exists but isn't picked up, or vice-versa.

`{{name}}` / `{{name-hyphen}}` / `{{name-underscore}}` / `{{name-pascal}}` substitutions in `:delete-when-off` and `:next-steps` values are applied during scaffold.

---

## 6. Feature Markers, Rename Tokens, Secrets

### 6.1 Feature markers

Markers are matched by literal substring — independent of file's comment syntax.

**Block syntax:**

```clojure
;; @c3kit/feature :ssr {
(ns acme.prerender ...)
(defn prerender! [...] ...)
;; @c3kit/feature :ssr }
```

```javascript
// @c3kit/feature :ssr {
import "./prerender.js";
// @c3kit/feature :ssr }
```

```html
<!-- @c3kit/feature :csp { -->
<meta http-equiv="Content-Security-Policy" content="...">
<!-- @c3kit/feature :csp } -->
```

```yaml
# @c3kit/feature :ssr {
ssr-job:
  runs-on: ubuntu-latest
# @c3kit/feature :ssr }
```

**Rules:**

- Start marker = `@c3kit/feature :<id> {` anywhere on a line.
- End marker = `@c3kit/feature :<id> }`. Same id required.
- Feature **off** → drop every line from start through end, inclusive.
- Feature **on** → drop only the marker lines themselves; preserve block content.
- Nesting is illegal. CLI errors (exit 8) if start without end, mismatched id, or nested markers.
- Markers always on their own line; inline unsupported.

**Line-level toggle:**

```clojure
;; @c3kit/feature :csp = (require '[csp.middleware :refer [wrap-csp]])
```

`@c3kit/feature :<id> = <code>` → if feature **on**, line becomes `<code>` (marker stripped); if **off**, line removed entirely. Useful for `require`s and deps entries.

**File-level toggle:** declared in manifest's `:delete-when-off`. Whole files/dirs removed when feature off; no marker needed inside them.

**Inverse markers** (included when feature **off**): prefix `!` →

```clojure
;; @c3kit/feature !:auth {
(def wrap-handler identity)
;; @c3kit/feature !:auth }
```

**DB selection markers** — same shape, key `:db`:

```clojure
;; @c3kit/db :sqlite {
{:impl :jdbc :dialect :sqlite :file "db/dev.sqlite"}
;; @c3kit/db :sqlite }
```

Only the block matching the user-selected db is kept; others removed.

### 6.2 Rename tokens

Source token (e.g. `"acme"`) is declared per template in `:tokens`. CLI computes user variants from `--name`:

| Variant flag       | Source     | User (e.g. `my-cool-app`)  | Where it appears                       |
|--------------------|------------|-----------------------------|----------------------------------------|
| `:hyphen`          | `acme`     | `my-cool-app`               | Clojure ns, config keys                |
| `:underscore`      | `acme`     | `my_cool_app`               | dir paths, JS/CSS filenames            |
| `:pascal`          | `Acme`     | `MyCoolApp`                 | display name, Java class types         |
| `:upper-prefix`    | `ACME_`    | `MY_COOL_APP_`              | env var prefixes                       |

**Replacement order** (deterministic, most-specific first to avoid clobbering):

1. `ACME_<SUFFIX>` env-var prefix (uppercase, ends with `_`)
2. `AcmeXxx` pascal prefixes (longer pascal forms first if multiple)
3. `Acme` standalone pascal
4. `acme_xxx` snake_case
5. `acme.xxx` Clojure-ns style
6. `acme` standalone kebab

Order is computed from token map; manifest validation rejects ambiguous overlapping tokens.

**Directory renames:** depth-first pass after content rewrite. Any dir named exactly `acme` → `my_cool_app` (underscore variant — dirs use underscores for Clojure ns paths).

**File renames:** files named `acme.css` → `my_cool_app.css`. Same depth-first pass.

**Reserved-name guard:** CLI rejects user names that collide with any template's source token or with reserved Clojure namespaces (`clojure`, `java`, `cljs`, …).

**Why no mustache in template source files:** templates use the literal source token (`acme`) — not `{{name}}` — so the template tree is a runnable Clojure project at HEAD. `clj -M:test:spec` works inside `templates/full-stack-reagent/` to verify the template still works before scaffold.

### 6.3 Secrets

For each entry in manifest's `:secrets`:

1. Generate `:bytes` random bytes via `java.security.SecureRandom` (bb-accessible).
2. Hex-encode.
3. Replace every literal occurrence of `:placeholder` in every text file under the scaffold with the generated hex string.
4. Each placeholder = one secret. Two files containing `ACME_DEV_SECRET` get the **same** dev secret. Different placeholder = different secret.

Secrets generation runs **after** rename — placeholders never contain the source token (by convention `ACME_DEV_SECRET`, not `acme_dev_secret`).

**Failure modes:** no `SecureRandom` → exit 8 with message; placeholder not found in any file → warning, not error (stale manifest, scaffold still valid).

---

## 7. Fetch & Atomic Scaffold

### 7.1 Fetch resolution

First match wins:

1. `--template-dir <path>` CLI flag.
2. `C3KIT_TEMPLATES` env var.
3. Default: `git clone --depth 1 --branch <ref> https://github.com/cleancoders/c3kit-jig <tmp>/c3kit-jig`. Ref defaults to the tag baked into the CLI binary at build time; override with `--template-ref <ref>`.

**Local mode (1–2):** read `<path>/<id>/c3kit-template.edn`. Copy `<path>/<id>/` recursively into staging dir. No network, no git. Errors (exit 5) if dir missing.

**Remote mode (3):** clone monorepo into `<tmp>/c3kit-jig/`, copy out `templates/<id>/`, delete `<tmp>/c3kit-jig/` regardless of outcome.

**Git missing:** `git --version` probe at startup; missing → exit 7 with link to git install docs. Probe skipped in local mode.

### 7.2 Atomic scaffold pipeline

```
[startup]
  resolve final target → $PWD/<name>
  if exists  → exit 3
  if name invalid → exit 4

[stage-1: fetch]
  mktemp -d c3kit-create-<uuid>/    (becomes $STAGE)
  fetch template tree → $STAGE/template/
  validate manifest  → $STAGE/template/c3kit-template.edn

[stage-2: render]
  copy $STAGE/template/        → $STAGE/scaffold/
  rm    $STAGE/scaffold/c3kit-template.edn
  apply rename tokens          (file contents, then paths)
  apply feature toggles        (delete-when-off paths, then markers)
  apply db selection           (db markers)
  generate + replace secrets

[stage-3: hook]
  if manifest :hook? true → bb $STAGE/template/c3kit-template.bb $STAGE/scaffold

[stage-4: commit]
  mv $STAGE/scaffold/ → $PWD/<name>/
  rm -rf $STAGE/

[stage-5: post-scaffold]
  unless --no-git:  cd <name> && git init -b main && git add -A
                    && git commit -m "chore: initial scaffold"
  if --install:     cd <name> && clj -P
                    && (test -f package.json && npm install || true)
  render :next-steps from manifest

exit 0
```

**Failure recovery (stages 1–3):** print friendly error + stage name, `rm -rf $STAGE/`, target dir was never created, exit with stage-specific code (7/8).

**Failure recovery (stage 4 mv):** the only step that touches the final target. On filesystem error, attempt best-effort cleanup of partial target, print state, exit 8. Mitigation: detect when `$TMPDIR` is on a different filesystem from `$PWD`; in that case stage inside `$PWD/.c3kit-create-stage-<uuid>/` so the `mv` stays within one filesystem. Fall back to copy-then-verify-then-remove only when unavoidable.

**Failure recovery (stage 5):** scaffold is already on disk and valid. Post-step failure → warning ("⚠ git init failed: <err>. Files are at <target>. Run `git init` manually."), exit 9.

**SIGINT:** trap, print "Aborted.", `rm -rf $STAGE/` if present, leave target untouched if stage 4 hasn't run, exit 10.

**Logging:** one line per stage. `--debug` adds per-file rename/toggle tracing to stderr.

---

## 8. Release Process

**Versioning:** semver. Tag format `cli-v<MAJOR>.<MINOR>.<PATCH>`. Independent of any template's version.

**Build:**

```sh
cd cli && bb uberscript dist/c3kit-create.bb -m c3kit-create.main
```

Single-file bb uberscript with all source inlined. `tools.cli` is bb-bundled at runtime (no inlining needed); `speclj` is test-only and excluded.

**CI release workflow** (`.github/workflows/release.yml`, added when CLI work begins):

1. Triggered on `cli-v*` tags.
2. `bb test` on matrix `{bb-stable, bb-canary} × {ubuntu, macos}`. All Speclj suites green.
3. `bb lint` (clj-kondo).
4. Build uberscript.
5. Smoke test against `test-fixtures/tiny-fixture/`: scaffold into temp dir, verify `git log`, grep for `acme` remnants, assert expected file tree.
6. Regenerate `registry.clj` from on-disk manifests; assert generated == checked-in (drift fails the build).
7. Create GitHub Release, attach `c3kit-create.bb` + `c3kit-create.bb.sha256`.
8. Bake the release tag into `c3kit_create/version.clj` so `--upgrade` knows "latest known".

**`--upgrade` flow:**

1. GET `https://api.github.com/repos/cleancoders/c3kit-jig/releases/latest`, parse tag.
2. If tag matches `(version/current)`, print "already on latest" and exit 0.
3. Else download `c3kit-create.bb` + `.sha256`, verify hash.
4. Locate running binary path (`$0`); atomic write to `$0.new`, `chmod +x`, `mv $0.new $0`.
5. Print "upgraded to <tag>. Re-run your command."

Any failure (network, hash, perms) → exit 11, existing binary intact.

**Installer (`cli/install.sh`):** per roadmap spec §3 (detect OS, install bb if missing, download release asset to `~/.c3kit/bin/c3kit-create`, ensure PATH).

---

## 9. Testing Strategy

Unit tests on pure fns + one end-to-end fixture, all under Speclj (with `clojure.test` fallback if the bb-spike fails).

**Unit suites (`cli/spec/c3kit_create/`):**

| File              | Covers                                                        |
|-------------------|---------------------------------------------------------------|
| `args_spec.clj`   | tools.cli wiring, flag/env-var precedence, `--help` output    |
| `wizard_spec.clj` | prompt loop with stubbed stdin (string in → answer out)       |
| `manifest_spec.clj` | EDN parse, schema validation, all error paths               |
| `rename_spec.clj` | token variant generation, replacement ordering, ns guards     |
| `features_spec.clj` | marker stripping (on/off, inverse, line-level, db variant)  |
| `secrets_spec.clj` | random gen, placeholder replace, dup-placeholder dedup       |
| `fs_spec.clj`     | atomic move, cross-fs fallback, temp dir cleanup              |
| `ui_spec.clj`     | tty detection, color toggling, friendly-error wrapping        |
| `version_spec.clj`| semver parse/compare, `--upgrade` hash verify (mocked HTTP)   |

**End-to-end (`cli/spec/c3kit_create/e2e_spec.clj`):** scaffolds `test-fixtures/tiny-fixture/` into a temp dir under several option matrices:

- defaults, `--yes`
- each feature toggled off individually
- each db option
- `--no-git`, `--install` (mocked to no-op since fixture has no real deps)
- `--template-dir` local mode (no clone)

Per run, asserts:

- exit code 0
- expected file tree (golden compare)
- no `acme`/`Acme`/`acme_` strings remain (grep)
- secrets present, hex-shaped, unique
- `.git` exists with single commit and expected subject
- feature-off → marker blocks gone + `:delete-when-off` paths gone
- feature-on → marker lines gone but content preserved

**Tiny fixture (`cli/test-fixtures/tiny-fixture/`):**

```
c3kit-template.edn          (manifest: 1 token, 1 secret, 2 features, 2 db options, 1 next-step)
src/acme/core.clj           (kebab token + feature marker + db marker)
src/acme_legacy/util.clj    (snake-case token, kept on toggle)
resources/Acme.css          (pascal token in filename + body)
config/dev.env              (ACME_DEV_SECRET placeholder)
README.md                   (plain `{acme}` string in text — verifies rename)
```

`test-only? true` in manifest hides it from `--list` in shipped CLI.

---

## 10. Risks & Mitigations

| Risk                                                                                  | Mitigation                                                                                                                              |
|---------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| Speclj-on-bb breaks (macros / runner under sci)                                       | Plan starts with a 1-task spike. If blocked → fall back to clojure.test. No design change required.                                     |
| Token replacement collides on a non-template string (user project happens to contain `Acme`) | Templates declare exact token set in manifest. Validation rejects user names matching source token. Replacement order is deterministic and tested. |
| Cross-filesystem `mv` not atomic                                                      | Probe `$TMPDIR` filesystem; if differs from `$PWD`, stage inside `$PWD/.c3kit-create-stage-<uuid>/`. Verified in `fs_spec`.              |
| Template ref pinned at CLI build time goes stale                                      | Release pipeline updates the pin. Users can override with `--template-ref`. Documented.                                                 |
| Feature marker syntax conflicts with real comment in template source                  | Markers are namespaced (`@c3kit/feature`); collision implausible. CI greps for stray markers in scaffolded output.                      |
| `--upgrade` writes a corrupted binary mid-replacement                                  | Download to `$0.new`, verify SHA, atomic `mv $0.new $0`. Existing binary intact on any failure.                                         |
| User runs `c3kit-create` from inside the monorepo with `--template-dir templates/`    | Works as expected — that *is* the dev-loop workflow. Documented.                                                                        |
| Friendly-error wrapping hides real bugs                                               | `--debug` always available, exception types preserved, stack traces shown when set.                                                     |
| Speclj fixture for e2e creates many files, slow CI                                    | Scaffolds run in `$TMPDIR` with in-memory bb where possible. Target <10 s total.                                                        |

---

## 11. Success Criteria

End of CLI sub-spec implementation:

- `bb test` green on macOS + Linux runners under bb stable + bb canary.
- `bb uberscript` produces `cli/dist/c3kit-create.bb`, single file, ≤200 KB, runs without args showing `--help`.
- `cli/install.sh` on a clean Ubuntu / macOS / WSL VM installs bb if missing, drops `c3kit-create` on PATH; `c3kit-create --version` works.
- E2E scaffold against `tiny-fixture` produces a tree matching the golden, with green `git log`, no `acme` remnants.
- `c3kit-create --template-dir <local> my-app -t tiny-fixture --yes` succeeds in <2 s; with `--install` mocked, also <2 s.
- `c3kit-create --upgrade` against a mocked GitHub releases endpoint downloads, verifies hash, replaces `$0`.
- Every exit code in §3 is exercised by at least one test.

---

## 12. Out of Scope (Deferred)

- Native Windows / PowerShell installer.
- Homebrew tap / bbin manifest.
- `--force` collision override.
- `--resume` for partial scaffolds.
- Telemetry.
- Arrow-key TUI prompts.
- Plugin / community template registry.
- Multiple templates in one scaffold run.
- `--answers <file>` replay.
