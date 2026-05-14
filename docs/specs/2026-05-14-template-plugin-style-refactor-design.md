# Plugin-Style Template Refactor — Design

**Date:** 2026-05-14
**Status:** Approved
**Scope:** `templates/full-stack-reagent/`

## Motivation

The current template-authoring model is unpleasant to reason about and produces output that mingles starter-provided files with whatever a consumer adds. Two concrete pains:

1. **Authoring pain.** `c3kit-template.edn` carries long `:delete-when-off` file lists per feature (auth: 15 paths, content: 7, ssr: 8). `@c3kit/feature :foo {…}` marker comments are scattered across many files. Adding or moving a feature file requires editing both the file tree and the manifest.
2. **Consumer pain.** After scaffolding, files from optional features land directly under the consumer's top-level namespace (`myapp.user`, `myapp.session`, `myapp.destination`, `myapp.prerender`, …), interleaved with files the consumer adds themselves. There is no visual cue distinguishing starter-generated code from new code.

This refactor adopts a "plugin-style" layout — not literal plugins or published packages, just a directory convention where each optional feature owns a namespace under the consumer's app namespace.

## Decisions

- **Plugin-style layout, not actual plugins.** Features are not extracted into separate libraries or git repos. The starter remains a single template; features become subdirectories under the consumer's namespace.
- **Mirror-tree template.** Template directory structure mirrors output exactly. No `base/`/`auth/` split at the template root. Path under `templates/<id>/` = path in scaffolded output (after token rename).
- **Feature = subdirectory under `{{acme}}`.** Each optional feature owns a directory named after its ID inside every namespace branch (`src/clj/{{acme}}/auth/`, `src/cljs/{{acme}}/auth/`, `spec/clj/{{acme}}/auth/`, …). Removal rule: feature off → delete all paths matching `**/{{acme}}/<feature>/**` and `**/{{acme}}/<feature>.{clj,cljc,cljs}`.
- **Marker comments retained for shared wiring files.** `deps.edn`, `src/clj/{{acme}}/main.clj`, `src/clj/{{acme}}/routes.clj`, `src/clj/{{acme}}/config.clj` continue to use `@c3kit/feature` markers. Three to four files is a small, finite set; per-file markers stay readable.
- **`@c3kit/db` marker syntax for mutex picks.** Same processor handles both `feature` and `db` selector namespaces.
- **Per-DB script via filesystem.** `bin/db.template.<id>` files live at canonical paths. Scaffolder copies the chosen one to `bin/db` and deletes siblings. No hook step for DB install.
- **Central `:features` list in `c3kit-template.edn`.** Feature metadata stays centralized (not per-dir manifests).
- **Hook shrinks to residue grep.** Post-scaffold hook becomes a generic ~30-LOC safety net.

## §1. Template Layout

```
templates/full-stack-reagent/
  c3kit-template.edn
  c3kit-template.bb            # residue grep only
  deps.edn                     # @c3kit/feature + @c3kit/db markers
  bb.edn
  README.md, README.scaffold.md, LICENSE, .gitignore, full-stack-reagent.iml
  package.json                 # :ssr extra (see §4)
  bin/
    db.template.datomic-pro
    db.template.sqlite
    db.template.postgres
    db.template.memory
  resources/
    public/...                 # base assets
    prerender/...               # :ssr extra
    prerendered/...             # :ssr extra
  src/
    clj/{{acme}}/
      main.clj                 # @c3kit/feature markers
      routes.clj               # @c3kit/feature markers
      config.clj               # @c3kit/db markers
      http.clj
      layouts.clj
      auth/                    # :auth
        user.clj
        session.clj
        destination.clj
        user/web.clj
        user/...
      content/                 # :content
        core.clj               # was acme.content
        markdown.clj           # was acme.markdown
      ssr/                     # :ssr
        prerender.clj
    cljc/{{acme}}/
      init.cljc
      bg_task.cljc
      schema.cljc
      formatc.cljc
      layoutc.cljc
      corec.cljc
      markdownc.cljc           # :markdownc — single file
      auth/                    # :auth shared
        schema.cljc
        corec.cljc
        user/...
    cljs/{{acme}}/
      main.cljs
      routes.cljs
      forms.cljs
      auth/                    # :auth
        user.cljs
        forgot_password.cljs
        recover_password.cljs
      content/                 # :content
        page.cljs              # was acme.content-page
  spec/
    clj/{{acme}}/
      spec_helper.clj
      routes_spec.clj
      layouts_spec.clj
      auth/...
      content/...
      ssr/
        prerender_spec.clj
    cljc/{{acme}}/
      test_data.cljc
      markdownc_spec.cljc
      auth/...
    cljs/{{acme}}/
      main_spec.cljs
      routes_spec.cljs
      auth/...
      content/...
  dev/{{acme}}/
    seed.clj                   # always present; placeholder when no auth
    ssr/
      prerender.cljs
      prerender_pages.cljs
      prerender_preamble.js
```

**Feature → namespace map:**

| Feature       | Owns namespace                              |
|---------------|---------------------------------------------|
| `:auth`       | `{{acme}}.auth.*` (clj + cljc + cljs)       |
| `:content`    | `{{acme}}.content.*` (clj + cljs)           |
| `:ssr`        | `{{acme}}.ssr.*` (clj + dev cljs)           |
| `:markdownc`  | `{{acme}}.markdownc` (single cljc file)     |
| `:websocket`  | none (marker-only in shared files)          |

**Namespace renames** (one-time refactor; no functional change):

- `acme.user.*` → `acme.auth.user.*`
- `acme.session` → `acme.auth.session`
- `acme.destination` → `acme.auth.destination`
- `acme.forgot-password` → `acme.auth.forgot-password`
- `acme.recover-password` → `acme.auth.recover-password`
- `acme.content` → `acme.content.core`
- `acme.markdown` → `acme.content.markdown`
- `acme.content-page` → `acme.content.page`
- `acme.prerender` → `acme.ssr.prerender`

## §2. Marker Syntax

Existing syntax stays unchanged in `deps.edn`, `main.clj`, `routes.clj`:

```clojure
;; require-line form (line retained iff selector matches)
;; @c3kit/feature :auth = [{{acme}}.auth.destination :as destination]

;; block form (block contents retained iff selector matches)
;; @c3kit/feature :auth {
(wrap-anti-forgery handler)
;; @c3kit/feature :auth }

;; negation (block retained iff feature OFF)
;; @c3kit/feature !:websocket {
(no-op-handler)
;; @c3kit/feature !:websocket }
```

**New: `@c3kit/db` namespace** for mutex picks (config.clj `:bucket` line, deps.edn db driver coords):

```clojure
;; @c3kit/db :datomic-pro = com.datomic/local {:mvn/version "..."}
;; @c3kit/db :sqlite      = org.xerial/sqlite-jdbc {:mvn/version "..."}
;; @c3kit/db :postgres    = org.postgresql/postgresql {:mvn/version "..."}

;; @c3kit/db :memory {
:bucket bucket-memory-local
;; @c3kit/db }
;; @c3kit/db :sqlite {
:bucket bucket-sqlite
;; @c3kit/db }
```

Same processor handles both namespaces with different selector sets.

## §3. Scaffolder Algorithm

Inputs:
- selected feature set (e.g. `#{:auth :ssr :websocket}`)
- chosen db (e.g. `:sqlite`)
- token map (e.g. `{"acme" "myapp", "ACME_" "MYAPP_"}` with variants)

Steps:

1. **Walk template tree.** For every file/dir under `templates/<id>/`:
   - Compute relative path. Apply token rename to each path segment.
   - If any path segment matches `<feature>` where feature is OFF → skip.
   - If a path-leaf matches `<feature>.{clj,cljc,cljs}` where feature is OFF → skip (single-file features like `:markdownc`).
   - If path matches a `:extras` entry (see §4) of an OFF feature → skip.
   - If path is `bin/db.template.<id>` where `id ≠ chosen-db` → skip.
2. **Copy file.** Apply token rename to file contents. Run marker processor on textual files (extensions `.clj` `.cljc` `.cljs` `.edn` `.md` `.html` `.js` `.yml`).
3. **Marker processor** — line-oriented stream:
   - Track current block state (namespace `feature`/`db` + selector + on/off).
   - Drop block contents when selector doesn't match active set.
   - Drop the marker comment lines themselves.
   - For `=`-form lines: emit RHS as bare code if selector matches; drop entire line otherwise.
4. **DB script rename.** `bin/db.template.<chosen-db>` is written to output as `bin/db` (executable bit preserved or set).
5. **Post-scaffold hook** runs once on the scaffold directory. Residue grep only (see §5).

## §4. `c3kit-template.edn` Shape

```clojure
{:id          :full-stack-reagent
 :name        "Full-stack Reagent"
 :description "Clojure backend + Reagent frontend with c3kit; built-in CSP; optional SSR, content pipeline, client-side markdown, JWT auth"
 :version     "0.2.0"
 :min-cli     "0.1.0"
 :test-only?  false

 :tokens   {"acme"  {:hyphen true :underscore true :pascal true}
            "ACME_" {:upper-prefix true}}

 :secrets  [{:placeholder "ACME_DEV_SECRET"        :bytes 24}
            {:placeholder "ACME_STAGING_SECRET"    :bytes 24}
            {:placeholder "ACME_PRODUCTION_SECRET" :bytes 24}]

 :features [{:id :content   :prompt "Markdown content pipeline?"        :default true}
            {:id :ssr       :prompt "SSR/prerender (Reagent + Node)?"   :default true
             :extras ["package.json"
                      "resources/prerender/"
                      "resources/prerendered/"]}
            {:id :markdownc :prompt "Client-side markdown (CLJC)?"      :default true}
            {:id :auth      :prompt "JWT auth?"                         :default true}
            {:id :websocket :prompt "WebSocket support (c3kit.wire.websocket)?" :default true}]

 :db       {:prompt  "Database"
            :options [{:id :datomic-pro :label "Datomic Pro (free, single-jar transactor)"}
                      {:id :sqlite      :label "SQLite (JDBC)"}
                      {:id :postgres    :label "Postgres (JDBC)"}
                      {:id :memory      :label "In-memory (dev only)"}]
            :default :datomic-pro
            :template     "bin/db.template.{{db}}"   ; renamed to bin/db
            :sibling-glob "bin/db.template.*"}

 :next-steps [{:cmd "cd {{name}}"          :doc nil}
              {:cmd "bin/db"               :doc "start the database (per-backend script)"}
              {:cmd "clj -M:test:migrate"  :doc "run migrations"}
              {:cmd "clj -M:test:seed"     :doc "seed dev data"}
              {:cmd "clj -M:test:spec"     :doc "run Clojure specs"}
              {:cmd "clj -M:test:cljs"     :doc "run ClojureScript specs (auto-watch)"}
              {:cmd "clj -M:test:css"      :doc "compile CSS (auto-watch)"}
              {:cmd "clj -M:test:cljss"    :doc "compile CLJS + CSS (auto-watch, combined)"}
              {:cmd "clj -M:test:dev"      :doc "server + specs + cljs in one process"}
              {:cmd "clj -M:test:run"      :doc "server only"}]

 :hook? true}
```

Changes vs. current `c3kit-template.edn`:

- All `:delete-when-off` blocks removed (handled by §3 step 1 + namespace-subdir convention).
- Added `:extras` per feature for paths outside `{{acme}}/<feature>/` (currently only `:ssr`).
- Added `:template` + `:sibling-glob` keys on `:db`.

## §5. Post-Scaffold Hook

Shrinks from 102 LOC to ~30 LOC. Sole responsibility: residue grep — catch any `@c3kit/(feature|db)` marker that survived scaffolding (a marker comment surviving = scaffolder bug; hook is the safety net).

```clojure
(ns c3kit-template
  (:require [clojure.string :as str]))

(def scaffold-dir (or (first *command-line-args*) (System/exit 1)))

(def text-exts #{".clj" ".cljc" ".cljs" ".edn" ".md" ".html" ".js" ".yml"})
(def marker #"@c3kit/(feature|db)\s+!?:?\S*\s*[{}=]")

(let [residues (atom [])]
  (doseq [f (file-seq (java.io.File. scaffold-dir))
          :when (and (.isFile f)
                     (not (str/includes? (.getPath f) "/.git/"))
                     (some #(str/ends-with? (.getName f) %) text-exts))]
    (when (re-find marker (slurp f))
      (swap! residues conj (.getPath f))))
  (when (seq @residues)
    (println "ERROR: residual markers found in:")
    (doseq [p @residues] (println "  " p))
    (System/exit 4)))
```

Removed responsibilities (now handled by scaffolder + markers):

- `install-bin-db` → scaffolder DB script rename (§3 step 4).
- `reconcile-bucket-lines` → `@c3kit/db` markers in `config.clj`.
- `maybe-drop-seed-alias` → `@c3kit/feature :auth` marker around `:seed` alias in `deps.edn`.

## §6. Migration of Existing Template

In-place refactor of `templates/full-stack-reagent/`.

**File moves** (mirror §1 layout). Affected paths:

- `src/clj/{{acme}}/user.clj` → `src/clj/{{acme}}/auth/user.clj`
- `src/clj/{{acme}}/session.clj` → `src/clj/{{acme}}/auth/session.clj`
- `src/clj/{{acme}}/destination.clj` → `src/clj/{{acme}}/auth/destination.clj`
- `src/clj/{{acme}}/user/` (subtree) → `src/clj/{{acme}}/auth/user/`
- `src/cljc/{{acme}}/user/` (subtree) → `src/cljc/{{acme}}/auth/user/`
- `src/cljs/{{acme}}/user.cljs` → `src/cljs/{{acme}}/auth/user.cljs`
- `src/cljs/{{acme}}/forgot_password.cljs` → `src/cljs/{{acme}}/auth/forgot_password.cljs`
- `src/cljs/{{acme}}/recover_password.cljs` → `src/cljs/{{acme}}/auth/recover_password.cljs`
- specs mirror the above
- `src/clj/{{acme}}/content.clj` → `src/clj/{{acme}}/content/core.clj`
- `src/clj/{{acme}}/markdown.clj` → `src/clj/{{acme}}/content/markdown.clj`
- `src/cljs/{{acme}}/content_page.cljs` → `src/cljs/{{acme}}/content/page.cljs`
- `src/clj/{{acme}}/prerender.clj` → `src/clj/{{acme}}/ssr/prerender.clj`
- `dev/{{acme}}/prerender.cljs` → `dev/{{acme}}/ssr/prerender.cljs`
- `dev/{{acme}}/prerender_pages.cljs` → `dev/{{acme}}/ssr/prerender_pages.cljs`
- `dev/{{acme}}/prerender_preamble.js` → `dev/{{acme}}/ssr/prerender_preamble.js`

**Namespace updates** — all `ns` forms and `:require` clauses updated to new paths (see §1 rename map). Marker comments in `src/clj/{{acme}}/main.clj`, `src/clj/{{acme}}/routes.clj` are rewritten to reference the new namespaces.

**`dev/{{acme}}/seed.clj`** — always present, even when `:auth` is off. When off, body becomes a placeholder:

```clojure
(ns {{acme}}.seed
  "Seed dev data. Currently empty — add seed transactions as needed.")

(defn -main [& _]
  (println "No seed data to load."))
```

When on, body retains the current auth seeding.

**Websocket** stays marker-only. No `acme.websocket` namespace created. `:websocket` entry in `c3kit-template.edn` has no `:extras`. Removal rule yields zero file matches; toggling the feature only affects marker comments in `main.clj`.

## §7. Combo Tests

`spec/combos/*.expected.edn` baselines are regenerated post-refactor:

1. Run scaffolder against new template for each combo's flag set.
2. Snapshot directory listing → new `.expected.edn`.
3. Diff vs. current baselines. Expected differences:
   - File path changes (`user.clj` → `auth/user.clj`, etc. — mechanical).
   - `seed.clj` always present (was deleted in some combos).
   - No marker comment leakage (already enforced by current baselines).
4. Commit new baselines as part of the refactor.

The CI hook test (`spec/hook_test.bb`, wired in commit `1c65c5d`) needs updating:

- Hook is now a 30-LOC residue grep, not a multi-step thing.
- Test inputs become: scaffold a project with a synthetic residual marker → assert exit code 4.
- Test inputs covering `install-bin-db`, `reconcile-bucket-lines`, `maybe-drop-seed-alias` are removed (those responsibilities moved to the scaffolder; their tests move to scaffolder specs).

## §8. Risks & Non-Goals

**Risks:**

- Namespace renames must be exhaustive. Mitigation: after the refactor, run `grep -rn "acme\.user\b\|acme\.session\b\|acme\.destination\b\|acme\.content\b\|acme\.markdown\b\|acme\.content-page\b\|acme\.prerender\b\|acme\.forgot-password\b\|acme\.recover-password\b"` across the template tree — must return zero hits.
- Combo fixture re-baselining produces a large diff. Mitigation: review one combo carefully, accept the rest as mechanical.
- IntelliJ project file (`full-stack-reagent.iml`) may carry stale path refs. Mitigation: regenerate or scrub during refactor.
- Marker comments in `main.clj` referencing renamed namespaces must be updated together with the file moves.

**Non-goals:**

- No runtime architecture change. No feature-record protocol, no `register!` calls, no runtime feature registry.
- No extraction to separate Clojars libraries.
- No change to CLI behavior (`c3kit-create` interactive flow, prompts, gum menus) — refactor is template-internal.
- No change to feature set. `:auth`, `:content`, `:ssr`, `:markdownc`, `:websocket` all stay; CSP remains built-in.
- No new templates. This work covers `full-stack-reagent` only; other future templates can adopt the same conventions later.

## §9. Acceptance Criteria

- `templates/full-stack-reagent/c3kit-template.edn` contains no `:delete-when-off` keys.
- Every optional feature owns a directory named after its ID under each of `src/clj/{{acme}}/`, `src/cljs/{{acme}}/`, `spec/clj/{{acme}}/`, `spec/cljs/{{acme}}/` (or single-file form for `:markdownc`).
- Scaffolding with every supported flag/db combo produces no residual `@c3kit/(feature|db)` markers (hook exits 0).
- All existing combo fixtures pass against re-baselined `.expected.edn`.
- Hook is ≤ 35 LOC and contains only the residue-grep responsibility.
- `grep -rn "@c3kit/feature\|@c3kit/db" templates/full-stack-reagent/` matches only inside `src/clj/{{acme}}/main.clj`, `src/clj/{{acme}}/routes.clj`, `src/clj/{{acme}}/config.clj`, and `deps.edn`.
