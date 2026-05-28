# Template no-features cruft cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the nine cruft items reported on a no-features scaffold of the `full-stack-reagent` template, and tighten the verification harness so each item is caught by combo snapshots.

**Architecture:** Each item lands as a single commit that touches the template under `templates/full-stack-reagent/` and the matching `verification/templates/full-stack-reagent/combos/*.expected.edn` snapshots. TDD per item: combo `.expected.edn` updated first to assert the desired post-scaffold shape (RED — `bb verify` fails on current template), template fix lands second (GREEN). Item 9 (CLI marker-stripper indent bug) is the lone exception — it ships with new unit tests in `cli/spec/c3kit_jig/features_spec.clj` plus a combo snapshot assertion.

**Tech Stack:** Clojure, ClojureScript, c3kit (apron/bucket/wire/scaffold), Babashka (verification harness), speclj, cljfmt, clj-kondo.

**Spec:** `docs/superpowers/specs/2026-05-28-template-no-features-cruft-design.md`

---

## File Structure

### Template files modified or moved

| Path | Change |
|---|---|
| `templates/full-stack-reagent/dev/acme/compile_cljs.clj` | Wrap whole file in `;; @c3kit/feature :ssr {` block markers |
| `templates/full-stack-reagent/spec/clj/acme/compile_cljs_spec.clj` | Wrap whole file in `;; @c3kit/feature :ssr {` block markers |
| `templates/full-stack-reagent/dev/acme/seed.clj` | Remove debug `;(prn …)` lines; add docstring on `entity` |
| `templates/full-stack-reagent/resources/config/cljs.edn` | Wrap `:prerender { … }` key-value in `;; @c3kit/feature :ssr {` block markers |
| `templates/full-stack-reagent/src/cljs/acme/config.cljs` | Wrap `ws-csrf-token` defn in `;; @c3kit/feature :websocket {` block markers |
| `templates/full-stack-reagent/src/cljc/acme/schema.cljc` | **Deleted** — replaced by `schema/full.cljc` |
| `templates/full-stack-reagent/src/cljc/acme/bg_task.cljc` | **Moved** → `src/cljc/acme/schema/bg_task.cljc`, ns becomes `acme.schema.bg-task` |
| `templates/full-stack-reagent/src/cljc/acme/auth/user/schema.cljc` | **Moved** → `src/cljc/acme/schema/user.cljc`, ns becomes `acme.schema.user`, still auth-feature-gated by directory |
| `templates/full-stack-reagent/src/cljc/acme/schema/full.cljc` | **Created** — aggregator namespace exposing `(def full …)` |
| `templates/full-stack-reagent/src/cljc/acme/init.cljc` | Update require: `acme.schema` → `acme.schema.full` |
| `templates/full-stack-reagent/spec/cljc/acme/test_data.cljc` | Update require: `acme.schema` → `acme.schema.full` |
| `templates/full-stack-reagent/src/clj/acme/config.clj` | Update `'acme.schema/full` symbol → `'acme.schema.full/full` (4 occurrences) |
| `templates/full-stack-reagent/src/clj/acme/main.clj` | Replace `#_bg-tasks` with real `bg-tasks` inclusion in both `all-services` variants |
| `templates/full-stack-reagent/dev/acme/repl.clj` | Add `config-from-service`, `-ensure-migration-schema!`, call after `start-db` |
| `templates/full-stack-reagent/deps.edn` | Gate `:cljs` alias by `:ssr`; gate `jbcrypt`/`google-api-client`/`commonmark-*`/`markdown-to-hiccup` deps |
| `templates/full-stack-reagent/README.scaffold.md` | Add "Seeding dev data" section pointing at `dev/<app>/seed.clj` |
| `templates/full-stack-reagent/c3kit-template.bb` | Update hook (`:seed` alias drop logic stays; verify no stale path references) |

### CLI marker-stripper fix (item 9)

| Path | Change |
|---|---|
| `cli/src/c3kit_jig/features.clj` | Capture leading whitespace before `;;` in `LINE-EQ-RE` and `DB-LINE-EQ-RE`; prepend captured indent to emitted `code` |
| `cli/spec/c3kit_jig/features_spec.clj` | Add tests asserting line-toggle preserves indent for both feature and db forms |

### Combo snapshots updated

All eight files under `verification/templates/full-stack-reagent/combos/`:

- `memory-defaults.expected.edn`
- `memory-minimal.expected.edn`
- `memory-no-auth.expected.edn`
- `memory-no-ssr-no-content.expected.edn`
- `memory-no-websocket.expected.edn`
- `sqlite-defaults.expected.edn`
- `postgres-defaults.expected.edn`
- `datomic-pro-defaults.expected.edn`

Per-item additions detailed inside each task.

### Verification command (reused across tasks)

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig/verification && bb verify --combo <combo-name> --tier light
```

`memory-defaults` runs at `--tier full` by descriptor; for sanity also run:

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig/verification && bb verify-all
```

---

## Task 0: Branch setup

**Files:**
- No files yet — git only.

- [ ] **Step 1: Create a working branch**

Run from `/Users/alex-root-roatch/current-projects/c3kit-jig/`:

```bash
git checkout -b template-no-features-cleanup
```

Expected: `Switched to a new branch 'template-no-features-cleanup'`.

- [ ] **Step 2: Confirm starting baseline passes the harness on memory-defaults**

```bash
cd verification && bb verify --combo memory-defaults --tier light
```

Expected: all PASS. If this already fails, fix the existing breakage before proceeding (out of scope for this plan otherwise).

- [ ] **Step 3: Confirm `memory-minimal` baseline status**

```bash
cd verification && bb verify --combo memory-minimal --tier light
```

Expected: PASS. We will tighten this combo over the course of the plan, but it should pass today.

---

## Task 1: `compile_cljs.clj` is SSR-only

**Files:**
- Modify: `verification/templates/full-stack-reagent/combos/memory-minimal.expected.edn`
- Modify: `verification/templates/full-stack-reagent/combos/memory-no-ssr-no-content.expected.edn`
- Modify: `templates/full-stack-reagent/dev/acme/compile_cljs.clj`
- Modify: `templates/full-stack-reagent/spec/clj/acme/compile_cljs_spec.clj`
- Modify: `templates/full-stack-reagent/deps.edn`

- [ ] **Step 1: Tighten `memory-minimal.expected.edn` — RED snapshot**

Open `verification/templates/full-stack-reagent/combos/memory-minimal.expected.edn`. Add to `:must-not-exist`:

```clojure
"dev/my_app/compile_cljs.clj"
"spec/clj/my_app/compile_cljs_spec.clj"
```

Add to `:file-not-contains` under the `"deps.edn"` key (create the key if not present):

```clojure
"deps.edn" ["my-app.compile-cljs"
            "my_app.compile-cljs"]
```

Add to `:file-contains` under `"deps.edn"`:

```clojure
"c3kit.scaffold.cljs"
```

- [ ] **Step 2: Tighten `memory-no-ssr-no-content.expected.edn` — RED snapshot**

Open `verification/templates/full-stack-reagent/combos/memory-no-ssr-no-content.expected.edn`. Read the file first to learn its `:name`; assume `my-app` unless different, in which case use the snake_case form (e.g. `my_cool_app`). Add the same entries adjusted to that name:

```clojure
;; In :must-not-exist
"dev/<snake-name>/compile_cljs.clj"
"spec/clj/<snake-name>/compile_cljs_spec.clj"

;; In :file-not-contains under "deps.edn"
"<hyphen-name>.compile-cljs"
"<snake-name>.compile-cljs"

;; In :file-contains under "deps.edn"
"c3kit.scaffold.cljs"
```

- [ ] **Step 3: Run harness — expect RED**

```bash
cd verification && bb verify --combo memory-minimal --tier light
cd verification && bb verify --combo memory-no-ssr-no-content --tier light
```

Expected: both FAIL with `[FAIL] combo` reporting `must-not-exist present: dev/my_app/compile_cljs.clj` and similar.

- [ ] **Step 4: Block-mark whole `compile_cljs.clj` with `:ssr`**

Edit `templates/full-stack-reagent/dev/acme/compile_cljs.clj` — wrap the entire file body:

```clojure
;; @c3kit/feature :ssr {
(ns acme.compile-cljs
  (:require [c3kit.scaffold.cljs :as cljs]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; (existing defns unchanged: prerender-ns?, extract-ns, find-prerender-namespaces,
;; generate-prerender-requires!, patch-prerender!, -main)
;; @c3kit/feature :ssr }
```

Concretely:
- Insert one line `;; @c3kit/feature :ssr {` as the new line 1.
- Append one line `;; @c3kit/feature :ssr }` as the final line.

- [ ] **Step 5: Block-mark whole `compile_cljs_spec.clj` with `:ssr`**

Edit `templates/full-stack-reagent/spec/clj/acme/compile_cljs_spec.clj` the same way: top line `;; @c3kit/feature :ssr {`, bottom line `;; @c3kit/feature :ssr }`. Existing contents unchanged.

- [ ] **Step 6: Gate `:cljs` alias in `deps.edn`**

Edit `templates/full-stack-reagent/deps.edn`. Replace the current `:cljs` alias block (lines 29-30):

```clojure
             :cljs     {:main-opts ["-m" "acme.compile-cljs"]
                        :extra-paths ["dev"]}
```

with:

```clojure
             ;; @c3kit/feature :ssr {
             :cljs     {:main-opts   ["-m" "acme.compile-cljs"]
                        :extra-paths ["dev"]}
             ;; @c3kit/feature :ssr }
             ;; @c3kit/feature !:ssr {
             :cljs     {:main-opts ["-m" "c3kit.scaffold.cljs"]}
             ;; @c3kit/feature !:ssr }
```

- [ ] **Step 7: Run harness — expect GREEN**

```bash
cd verification && bb verify --combo memory-minimal --tier light
cd verification && bb verify --combo memory-no-ssr-no-content --tier light
```

Expected: both PASS.

- [ ] **Step 8: Run wider verification to catch fallout**

```bash
cd verification && bb verify-all
```

Expected: all combos PASS. If `memory-defaults` (auth+ssr+content+ws on) fails because its existing snapshot tries to assert what we just dropped, update its `:file-contains` for `deps.edn` to keep `"acme.compile-cljs"` or `"<snake-name>.compile-cljs"` so SSR-on combos still pin the SSR-side alias. Add that entry only if the failure surfaces.

- [ ] **Step 9: Commit**

```bash
git add templates/full-stack-reagent/dev/acme/compile_cljs.clj \
        templates/full-stack-reagent/spec/clj/acme/compile_cljs_spec.clj \
        templates/full-stack-reagent/deps.edn \
        verification/templates/full-stack-reagent/combos/memory-minimal.expected.edn \
        verification/templates/full-stack-reagent/combos/memory-no-ssr-no-content.expected.edn
git commit -m "feat(template): drop compile_cljs.clj when :ssr off; gate :cljs alias

Wrap dev/acme/compile_cljs.clj and its spec in :ssr block markers so
they vanish on no-SSR scaffolds. Switch the :cljs alias to invoke
c3kit.scaffold.cljs directly when SSR is off, keeping the alias name
stable. Harness combos for memory-minimal and memory-no-ssr-no-content
now reject the dead file and the dead alias body.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Clean `seed.clj`, document seed pattern

**Files:**
- Modify: all eight `verification/templates/full-stack-reagent/combos/*.expected.edn`
- Modify: `templates/full-stack-reagent/dev/acme/seed.clj`
- Modify: `templates/full-stack-reagent/README.scaffold.md`

- [ ] **Step 1: Add `:file-not-contains` assertions to all combos — RED**

For each `combos/<combo>.expected.edn`, look up the `:name` field at the top. Add (or extend) the `:file-not-contains` entry for `dev/<snake-name>/seed.clj` with both debug-print substrings:

```clojure
"dev/<snake-name>/seed.clj" ["(prn \"other-fields: "
                              "(prn \"e: "]
```

Do this for all eight combos.

- [ ] **Step 2: Run harness — expect RED on at least one combo**

```bash
cd verification && bb verify --combo memory-minimal --tier light
```

Expected: FAIL with `file-not-contains hit: dev/my_app/seed.clj -> "(prn \"other-fields: "`.

- [ ] **Step 3: Strip debug `prn` lines from `seed.clj`**

Edit `templates/full-stack-reagent/dev/acme/seed.clj`. Delete the two commented-out debug lines inside the `Entity` deftype's `deref` body (currently at lines 35-36):

```clojure
            ;(prn "other-fields: " other-fields)
            ;(prn "e: " e)
```

Resulting fragment (showing context):

```clojure
          (do
            (println "UPDATING: " (pr-str kind search-fields))
            (reset! atm (db/tx (merge e other-fields)))))
```

- [ ] **Step 4: Add docstring to `entity`**

In the same file, replace the existing `entity` defn:

```clojure
(defn entity [kind search-fields other-fields] (Entity. (atom nil) kind search-fields other-fields))
```

with:

```clojure
(defn entity
  "Declare a seed entity.

   `kind`          — entity kind keyword (matches your schema)
   `search-fields` — map of attributes used to look an existing row up;
                     idempotent seeding compares by these
   `other-fields`  — every other attribute on the entity

   Returns an IDeref. Deref it inside `-main` to upsert: existing rows
   whose `other-fields` already match are reported as EXISTS, mismatches
   are UPDATEd, and missing rows are CREATEd. See `acme.seed/-main`
   below for the canonical pattern."
  [kind search-fields other-fields]
  (Entity. (atom nil) kind search-fields other-fields))
```

- [ ] **Step 5: Add seed section to `README.scaffold.md`**

Read `templates/full-stack-reagent/README.scaffold.md` first to learn its current heading structure. Add a new H2 section `## Seeding dev data` placed just below the existing "Running locally" / "Migrations" area (wherever fits — match the surrounding heading depth). Body:

```markdown
## Seeding dev data

The scaffold ships a starter seed namespace at `dev/<app>/seed.clj`.
Add entities with the `entity` helper — each call returns an `IDeref`
that the `-main` body derefs to upsert idempotently:

```clojure
(def admin (entity :user
                   {:email "admin@example.com"}
                   {:name "Admin User" :role :admin}))

(defn -main []
  (init!)
  @admin
  (System/exit 0))
```

Run it with:

```sh
clj -M:test:seed
```

Repeated runs leave existing rows untouched unless `other-fields`
diverge, in which case the row is updated in place.
```

Heads-up: this section references the `:seed` alias. That alias is
auth-gated (the hook drops it when `:auth` is off). For non-auth
scaffolds the section is still useful — keep it; users can wire their
own alias if needed. The hook leaves the README alone.

- [ ] **Step 6: Run harness — expect GREEN**

```bash
cd verification && bb verify --combo memory-minimal --tier light
cd verification && bb verify-all
```

Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add templates/full-stack-reagent/dev/acme/seed.clj \
        templates/full-stack-reagent/README.scaffold.md \
        verification/templates/full-stack-reagent/combos/*.expected.edn
git commit -m "feat(template): clean seed.clj noise; document seed pattern

Drop the two commented-out debug \`prn\` lines inside Entity/deref.
Add a docstring to \`entity\` explaining the idempotent
search-fields/other-fields upsert pattern. New 'Seeding dev data'
section in README.scaffold.md links to dev/<app>/seed.clj and
\`clj -M:test:seed\`. Harness combos reject the debug-print substrings.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Drop `:prerender` block from `cljs.edn`

**Files:**
- Modify: `verification/templates/full-stack-reagent/combos/memory-minimal.expected.edn`
- Modify: `verification/templates/full-stack-reagent/combos/memory-no-ssr-no-content.expected.edn`
- Modify: `templates/full-stack-reagent/resources/config/cljs.edn`

- [ ] **Step 1: Tighten the two no-SSR combos — RED**

In both `memory-minimal.expected.edn` and `memory-no-ssr-no-content.expected.edn`, extend (or create) `:file-not-contains` for `resources/config/cljs.edn`:

```clojure
"resources/config/cljs.edn" [":prerender"
                              "acme.ssr.prerender"
                              "my_app.ssr.prerender"]
```

(Adjust `my_app` to the combo's actual snake-case `:name`.)

- [ ] **Step 2: Run harness — expect RED**

```bash
cd verification && bb verify --combo memory-minimal --tier light
```

Expected: FAIL with `file-not-contains hit: resources/config/cljs.edn -> ":prerender"`.

- [ ] **Step 3: Block-mark `:prerender` in `cljs.edn`**

Edit `templates/full-stack-reagent/resources/config/cljs.edn`. The current `:prerender` map spans lines 46-60. Wrap the entire key-value pair:

```clojure
 ;; @c3kit/feature :ssr {
 :prerender   {:cache-analysis false
               :npm-deps       false
               :infer-externs  true
               :libs           []
               :main           "acme.ssr.prerender"
               :optimizations  :simple
               :output-dir     "resources/prerender/"
               :output-to      "resources/prerender/prerender.js"
               :pretty-print   false
               :sources        ["src/cljc" "src/cljs" "dev"]
               :specs          false
               :target         :nodejs
               :language-in    :es-next
               :language-out   :no-transpile
               :verbose        false}
 ;; @c3kit/feature :ssr }
```

Marker lines are EDN comments (`;;`) — safe inside the top-level map; the EDN reader ignores them.

- [ ] **Step 4: Run harness — expect GREEN**

```bash
cd verification && bb verify --combo memory-minimal --tier light
cd verification && bb verify --combo memory-no-ssr-no-content --tier light
cd verification && bb verify-all
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add templates/full-stack-reagent/resources/config/cljs.edn \
        verification/templates/full-stack-reagent/combos/memory-minimal.expected.edn \
        verification/templates/full-stack-reagent/combos/memory-no-ssr-no-content.expected.edn
git commit -m "feat(template): gate :prerender cljs config by :ssr feature

Wrap the :prerender block in resources/config/cljs.edn in :ssr block
markers so no-SSR scaffolds ship a clean cljs.edn. Harness combos
reject :prerender and acme.ssr.prerender substrings.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Drop `ws-csrf-token` when no websocket

**Files:**
- Modify: `verification/templates/full-stack-reagent/combos/memory-minimal.expected.edn`
- Modify: `verification/templates/full-stack-reagent/combos/memory-no-websocket.expected.edn`
- Modify: `templates/full-stack-reagent/src/cljs/acme/config.cljs`

- [ ] **Step 1: Tighten no-websocket combos — RED**

In both `memory-minimal.expected.edn` and `memory-no-websocket.expected.edn`, extend (or create) `:file-not-contains` for the cljs config file (path uses snake_case `:name`):

```clojure
"src/cljs/my_app/config.cljs" ["ws-csrf-token"]
```

(Adjust `my_app` to the combo's actual snake-case `:name`.)

- [ ] **Step 2: Run harness — expect RED**

```bash
cd verification && bb verify --combo memory-no-websocket --tier light
```

Expected: FAIL with `file-not-contains hit: src/cljs/my_app/config.cljs -> "ws-csrf-token"`.

- [ ] **Step 3: Block-mark `ws-csrf-token` defn**

Edit `templates/full-stack-reagent/src/cljs/acme/config.cljs`. Replace the `ws-csrf-token` line (currently line 13):

```clojure
(def ws-csrf-token (reagent/track #(:ws-csrf-token @state)))
```

with the block-marked form:

```clojure
;; @c3kit/feature :websocket {
(def ws-csrf-token (reagent/track #(:ws-csrf-token @state)))
;; @c3kit/feature :websocket }
```

- [ ] **Step 4: Run harness — expect GREEN**

```bash
cd verification && bb verify --combo memory-no-websocket --tier light
cd verification && bb verify --combo memory-minimal --tier light
cd verification && bb verify-all
```

Expected: all PASS. If any websocket-on combo (`memory-defaults`, `sqlite-defaults`, etc.) starts failing because the snapshot was implicitly relying on the defn order, add `:file-contains` `"src/cljs/<snake>/config.cljs" ["ws-csrf-token"]` to those combos to keep the assertion symmetric.

- [ ] **Step 5: Commit**

```bash
git add templates/full-stack-reagent/src/cljs/acme/config.cljs \
        verification/templates/full-stack-reagent/combos/memory-minimal.expected.edn \
        verification/templates/full-stack-reagent/combos/memory-no-websocket.expected.edn
git commit -m "feat(template): gate ws-csrf-token by :websocket feature

Wrap the ws-csrf-token cljs def in :websocket block markers so no-ws
scaffolds drop the dead reagent track. Harness rejects the name in
no-ws combos.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Schema package restructure

This task is the largest. Multiple file moves + every consumer updated. Snapshot updates touch all eight combos.

**Files:**
- All eight `verification/templates/full-stack-reagent/combos/*.expected.edn`
- Delete: `templates/full-stack-reagent/src/cljc/acme/schema.cljc`
- Delete: `templates/full-stack-reagent/src/cljc/acme/bg_task.cljc`
- Delete: `templates/full-stack-reagent/src/cljc/acme/auth/user/schema.cljc`
- Create: `templates/full-stack-reagent/src/cljc/acme/schema/bg_task.cljc`
- Create: `templates/full-stack-reagent/src/cljc/acme/schema/user.cljc`
- Create: `templates/full-stack-reagent/src/cljc/acme/schema/full.cljc`
- Modify: `templates/full-stack-reagent/src/cljc/acme/init.cljc`
- Modify: `templates/full-stack-reagent/spec/cljc/acme/test_data.cljc`
- Modify: `templates/full-stack-reagent/src/clj/acme/config.clj`

- [ ] **Step 1: Update combo snapshots — RED**

For every `combos/<combo>.expected.edn`:

In `:must-exist`, replace `"src/cljc/<snake>/schema.cljc"` with:

```clojure
"src/cljc/<snake>/schema/full.cljc"
"src/cljc/<snake>/schema/bg_task.cljc"
```

Append to `:must-not-exist`:

```clojure
"src/cljc/<snake>/schema.cljc"
"src/cljc/<snake>/bg_task.cljc"
"src/cljc/<snake>/auth/user/schema.cljc"
```

For auth-ON combos (everything except `memory-minimal`, `memory-no-auth`), append to `:must-exist`:

```clojure
"src/cljc/<snake>/schema/user.cljc"
```

For auth-OFF combos (`memory-minimal`, `memory-no-auth`), append to `:must-not-exist`:

```clojure
"src/cljc/<snake>/schema/user.cljc"
```

In `:file-not-contains` for `deps.edn` and `src/clj/<snake>/main.clj` add (these stop us from leaving stale namespace symbols anywhere):

```clojure
;; Inside the deps.edn or main.clj entry as appropriate
"acme.schema/full"
"acme.bg-task"
"acme.auth.user.schema"
```

Also tighten `src/clj/<snake>/config.clj` entry (auth or not):

```clojure
"src/clj/<snake>/config.clj" {:file-contains    ["acme.schema.full/full"]
                              :file-not-contains ["acme.schema/full"]}
```

Note the existing combos use a `:file-contains` map keyed by path with a vector of substrings. Merge into that shape — do not introduce a new schema.

- [ ] **Step 2: Run harness — expect RED**

```bash
cd verification && bb verify --combo memory-minimal --tier light
```

Expected: FAIL with `must-exist missing: src/cljc/my_app/schema/full.cljc` and similar.

- [ ] **Step 3: Create `schema/bg_task.cljc`**

Create file `templates/full-stack-reagent/src/cljc/acme/schema/bg_task.cljc`:

```clojure
(ns acme.schema.bg-task
  (:require [c3kit.apron.schema :as schema]))

(def bg-task
  {:kind        (schema/kind :bg-task)
   :id          schema/id
   :key         {:type :keyword}
   :last-ran-at {:type :instant :db [:no-history]}})
```

- [ ] **Step 4: Create `schema/user.cljc`**

Copy the existing contents of `templates/full-stack-reagent/src/cljc/acme/auth/user/schema.cljc` to a new file `templates/full-stack-reagent/src/cljc/acme/schema/user.cljc`. Update the ns form to `(ns acme.schema.user …)`. Keep the rest of the file (the `user` schema, the `all` aggregator def, etc.) byte-identical.

The destination path lives outside `src/cljc/acme/auth/`, so the existing `:auth` feature-dir delete that purges `src/cljc/acme/auth/` will NOT touch this new file. We need a different gate. Two equivalent options:

- (a) Wrap the entire file body in `;; @c3kit/feature :auth { … }` block markers so it gets emptied to ns-only or zero-byte when auth is off.
- (b) Add a top-level marker `;; @c3kit/feature :auth = (ns acme.schema.user …)` line-toggles only work for single lines; not suitable for the whole file.

Use option (a): wrap the file body. After the move, the file shape is:

```clojure
;; @c3kit/feature :auth {
(ns acme.schema.user
  (:require [c3kit.apron.schema :as schema]))

;; ... existing user schema defns and `all` aggregator ...
;; @c3kit/feature :auth }
```

When `:auth` is off the file becomes empty (no remaining lines). An empty `.cljc` is harmless to ship, but the combo snapshot reads `must-not-exist src/cljc/<snake>/schema/user.cljc`. To make the file actually vanish under `:auth` off, leverage the existing `apply-feature-dir-deletes!` mechanism by placing the file under an `auth/`-named subdirectory. BUT we explicitly want the package to live at `schema/user.cljc`, not `schema/auth/user.cljc`.

Resolution: extend the existing manifest. Open `templates/full-stack-reagent/c3kit-template.edn`, find the `:auth` feature entry, and add an `:extras` vector entry pointing at the single file:

```clojure
{:id      :auth
 :prompt  "JWT auth?"
 :default true
 :extras  ["src/cljc/acme/schema/user.cljc"]}
```

(Render.clj already honours `:extras` for path deletion under `apply-extras-deletes!`.) This makes the file disappear when auth is off — no marker wrapping needed inside the file body. Remove option (a) wrapping and ship the file with a plain `(ns acme.schema.user ...)` header.

- [ ] **Step 5: Create `schema/full.cljc` aggregator**

Create `templates/full-stack-reagent/src/cljc/acme/schema/full.cljc`:

```clojure
(ns acme.schema.full
  (:require [acme.schema.bg-task :as bg-task]
            ;; @c3kit/feature :auth {
            [acme.schema.user :as user]
            ;; @c3kit/feature :auth }
            ))

;; @c3kit/feature !:auth {
(def full [bg-task/bg-task])
;; @c3kit/feature !:auth }
;; @c3kit/feature :auth {
(def full (concat [bg-task/bg-task] user/all))
;; @c3kit/feature :auth }
```

- [ ] **Step 6: Delete the legacy files**

```bash
rm templates/full-stack-reagent/src/cljc/acme/schema.cljc
rm templates/full-stack-reagent/src/cljc/acme/bg_task.cljc
rm templates/full-stack-reagent/src/cljc/acme/auth/user/schema.cljc
```

Also remove the now-empty parent directory `src/cljc/acme/auth/user/` if it is otherwise empty (run `rmdir` and ignore failure if siblings remain).

- [ ] **Step 7: Update `init.cljc`**

Edit `templates/full-stack-reagent/src/cljc/acme/init.cljc`. Replace the require `[acme.schema :as schema]` with `[acme.schema.full :as schema]`. The alias stays `schema` so call sites like `schema/full` are unchanged.

- [ ] **Step 8: Update `test_data.cljc`**

Edit `templates/full-stack-reagent/spec/cljc/acme/test_data.cljc`. Same swap: `[acme.schema :as schema]` → `[acme.schema.full :as schema]`.

- [ ] **Step 9: Update `config.clj`**

Edit `templates/full-stack-reagent/src/clj/acme/config.clj`. Replace each of the four `:full-schema 'acme.schema/full` occurrences with `:full-schema 'acme.schema.full/full`. Lines 18, 32, 46, 59.

- [ ] **Step 10: Search for any other consumers**

Run:

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig/templates/full-stack-reagent
grep -rn "acme\.schema[^.]\|acme\.bg-task\|acme\.auth\.user\.schema" src spec dev
```

Expected: no matches. If any surface, update them to the new namespace (`acme.schema.full`, `acme.schema.bg-task`, `acme.schema.user`).

- [ ] **Step 11: Confirm template still self-tests on HEAD (auth-on, memory backend)**

```bash
cd templates/full-stack-reagent
clj -M:test:spec
```

Expected: all specs pass. If a spec hardcodes `acme.schema` somewhere we missed, fix it here.

- [ ] **Step 12: Run harness — expect GREEN**

```bash
cd verification && bb verify-all
```

Expected: all combos PASS.

- [ ] **Step 13: Commit**

```bash
git add templates/full-stack-reagent/src/cljc/acme/schema/ \
        templates/full-stack-reagent/src/cljc/acme/init.cljc \
        templates/full-stack-reagent/spec/cljc/acme/test_data.cljc \
        templates/full-stack-reagent/src/clj/acme/config.clj \
        templates/full-stack-reagent/c3kit-template.edn \
        verification/templates/full-stack-reagent/combos/*.expected.edn
git add -u  # picks up the deletes
git commit -m "refactor(template): move schemas into schema/ package

Restructure schemas under src/cljc/<app>/schema/:

  schema/
    bg_task.cljc  ; acme.schema.bg-task
    user.cljc     ; acme.schema.user        — :auth-gated via :extras
    full.cljc     ; acme.schema.full        — aggregator (def full ...)

Old src/cljc/acme/{schema,bg_task}.cljc and src/cljc/acme/auth/user/
schema.cljc are removed. All consumers (init, test_data, config) point
at acme.schema.full now. The auth user schema is dropped on no-auth
scaffolds via a new :extras entry in c3kit-template.edn.

Combo snapshots assert the new layout and reject the legacy paths
plus stale 'acme.schema/full' / 'acme.bg-task' substrings.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Remove `#_bg-tasks` from `main.clj`

**Files:**
- Modify: all eight `verification/templates/full-stack-reagent/combos/*.expected.edn`
- Modify: `templates/full-stack-reagent/src/clj/acme/main.clj`

- [ ] **Step 1: Tighten all combos — RED**

For each `combos/<combo>.expected.edn`, in the `:file-not-contains` map under `"src/clj/<snake>/main.clj"`, add:

```clojure
"#_bg-tasks"
```

And in `:file-contains` under the same key, add:

```clojure
"bg-tasks"
```

(`bg-tasks` is already in the file as a defn `(def bg-tasks ...)`, but the assertion is cheap insurance.)

- [ ] **Step 2: Run harness — expect RED**

```bash
cd verification && bb verify --combo memory-minimal --tier light
```

Expected: FAIL with `file-not-contains hit: src/clj/my_app/main.clj -> "#_bg-tasks"`.

- [ ] **Step 3: Replace `#_bg-tasks` with real `bg-tasks` inclusion**

Edit `templates/full-stack-reagent/src/clj/acme/main.clj`. Both `all-services` defs (websocket and no-websocket variants, lines 26 and 29) currently end with `... bg/service #_bg-tasks]`. Replace with `... bg/service bg-tasks]`:

```clojure
;; @c3kit/feature :websocket {
(def all-services [env db/service http @(util/resolve-var 'c3kit.wire.websocket/service) bg/service bg-tasks])
;; @c3kit/feature :websocket }
;; @c3kit/feature !:websocket {
(def all-services [env db/service http bg/service bg-tasks])
;; @c3kit/feature !:websocket }
```

`scheduled-tasks` is `[]` so `bg-tasks` is a no-op service. Including it real removes the discard tell and gives consumers a working hook (populate `scheduled-tasks` and restart).

- [ ] **Step 4: Run harness — expect GREEN**

```bash
cd verification && bb verify-all
```

Expected: all PASS, including `memory-defaults` at `:full` tier (server-boot included).

- [ ] **Step 5: Commit**

```bash
git add templates/full-stack-reagent/src/clj/acme/main.clj \
        verification/templates/full-stack-reagent/combos/*.expected.edn
git commit -m "feat(template): include bg-tasks service real, drop #_ discard

scheduled-tasks is empty out of the box so bg-tasks is a no-op until
the consumer populates it; including the service for real removes the
reader-discard tell and gives users a ready hook. Harness rejects the
#_bg-tasks substring across all combos.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: REPL `ensure-migration-schema!`

**Files:**
- Modify: all eight `verification/templates/full-stack-reagent/combos/*.expected.edn`
- Modify: `templates/full-stack-reagent/dev/acme/repl.clj`

- [ ] **Step 1: Tighten all combos — RED**

For each `combos/<combo>.expected.edn`, add (or extend) `:file-contains` under `"dev/<snake>/repl.clj"`:

```clojure
"dev/<snake>/repl.clj" ["config-from-service"
                         "-ensure-migration-schema!"]
```

- [ ] **Step 2: Run harness — expect RED**

```bash
cd verification && bb verify --combo memory-minimal --tier light
```

Expected: FAIL with `file-contains miss: dev/my_app/repl.clj <- "config-from-service"`.

- [ ] **Step 3: Rewrite `repl.clj`**

Overwrite `templates/full-stack-reagent/dev/acme/repl.clj`:

```clojure
(ns acme.repl
  (:require [acme.init :as init]
            [acme.main :as main]
            [c3kit.apron.app :as app]
            [c3kit.apron.log :as log]
            [c3kit.bucket.migrator :as m]))

(defn config-from-service []
  (let [config  (:bucket/config app/app)
        schemas (:bucket/schemas app/app)
        impl    (:bucket/impl app/app)]
    (when-not (some? config) (throw (ex-info "bucket service must be started in order to sync-schemas" {})))
    (assoc config :-db impl :-schemas schemas)))

(defn -ensure-migration-schema! [{:keys [-db] :as config}]
  (let [schema (m/migration-schema config)]
    (swap! (.-legend -db) assoc (-> schema :kind :value) schema)
    (when-not (m/-schema-exists? -db schema)
      (m/-install-schema! -db schema)
      (log/warn "Installed 'migration' schema because it was missing."))))

(println "Welcome to the Acme REPL!")
(println "Initializing")
(init/install-legend!)
(main/start-db)
(-ensure-migration-schema! (config-from-service))
(require '[c3kit.bucket.api :as db])
```

The string `"Welcome to the Acme REPL!"` contains the literal token `Acme` (the `pascal` token variant from the manifest), so the CLI's token replacement will swap it to the user's pascal name during scaffold. No manual change needed.

- [ ] **Step 4: Confirm template still boots its REPL at HEAD**

```bash
cd templates/full-stack-reagent
clj -M:repl < /dev/null
```

Expected: prints the welcome banner, runs `init/install-legend!`, `main/start-db`, `-ensure-migration-schema!`, then exits cleanly when stdin closes. If `m/-schema-exists?` or `m/-install-schema!` throws on the in-memory backend, capture the stack trace and report it — the design assumed these protocols are implemented for memory bucket; if they aren't, this task pivots to a marker-gated call (only run for sqlite/postgres/datomic).

- [ ] **Step 5: Run harness — expect GREEN**

```bash
cd verification && bb verify-all
```

Expected: all combos PASS. The `:server-boot` check on `memory-defaults` exercises the migrate path; if `-ensure-migration-schema!` interferes, surface it here.

- [ ] **Step 6: Commit**

```bash
git add templates/full-stack-reagent/dev/acme/repl.clj \
        verification/templates/full-stack-reagent/combos/*.expected.edn
git commit -m "feat(template): ensure migration schema on REPL boot

dev/<app>/repl.clj now defines config-from-service and
-ensure-migration-schema! and calls them after start-db. The call is
unconditional across backends — memory bucket implements the
migrator protocols so the path is safe. Harness asserts both helper
names exist in repl.clj across all combos.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Feature-gate `deps.edn` dependencies

**Files:**
- Modify: all eight `verification/templates/full-stack-reagent/combos/*.expected.edn`
- Modify: `templates/full-stack-reagent/deps.edn`

- [ ] **Step 1: Update combos — RED**

For each combo, edit the `:file-not-contains` and `:file-contains` lists under `"deps.edn"`:

- `memory-minimal` (auth off, content off, markdownc off, ssr off, ws off):
  - `:file-not-contains` += `"jbcrypt"`, `"google-api-client"`, `"commonmark"`, `"markdown-to-hiccup"`
- `memory-no-auth` (auth off, others default-on):
  - `:file-not-contains` += `"jbcrypt"`, `"google-api-client"`
  - `:file-contains` += `"commonmark"`, `"markdown-to-hiccup"` (content + markdownc still on)
- `memory-no-ssr-no-content` (ssr off, content off):
  - `:file-not-contains` += `"commonmark"`
  - `:file-contains` += `"jbcrypt"`, `"google-api-client"`, `"markdown-to-hiccup"` (auth + markdownc still on)
- `memory-no-websocket` (only ws off):
  - `:file-contains` += `"jbcrypt"`, `"google-api-client"`, `"commonmark"`, `"markdown-to-hiccup"`
- `memory-defaults`, `sqlite-defaults`, `postgres-defaults`, `datomic-pro-defaults` (everything on):
  - `:file-contains` += `"jbcrypt"`, `"google-api-client"`, `"commonmark"`, `"markdown-to-hiccup"`

- [ ] **Step 2: Run harness — expect RED on `memory-minimal`**

```bash
cd verification && bb verify --combo memory-minimal --tier light
```

Expected: FAIL with `file-not-contains hit: deps.edn -> "jbcrypt"`.

- [ ] **Step 3: Convert deps to line-toggle markers**

Edit `templates/full-stack-reagent/deps.edn`. Replace the unconditional lines:

```clojure
             com.atlassian.commonmark/commonmark                       {:mvn/version "0.17.0"} ;; Markdown
             com.atlassian.commonmark/commonmark-ext-gfm-strikethrough {:mvn/version "0.17.0"} ;; Github flavor markdown (strikethrough)
             com.atlassian.commonmark/commonmark-ext-gfm-tables        {:mvn/version "0.17.0"} ;; Github flavor markdown (tables)
             ...
             com.google.api-client/google-api-client                   {:mvn/version "2.8.1"} ; consumed by user.web; harmless if :auth off
             ...
             org.mindrot/jbcrypt                                       {:mvn/version "0.4"} ; consumed by seed.clj; harmless if :auth off
             markdown-to-hiccup/markdown-to-hiccup                       {:mvn/version "0.6.2"}
```

with line-toggle markers (preserve the leading 13 spaces so the rendered output stays aligned — this is exactly the indent that item 9 protects):

```clojure
             ;; @c3kit/feature :content   = com.atlassian.commonmark/commonmark                       {:mvn/version "0.17.0"}
             ;; @c3kit/feature :content   = com.atlassian.commonmark/commonmark-ext-gfm-strikethrough {:mvn/version "0.17.0"}
             ;; @c3kit/feature :content   = com.atlassian.commonmark/commonmark-ext-gfm-tables        {:mvn/version "0.17.0"}
             ...
             ;; @c3kit/feature :auth      = com.google.api-client/google-api-client                   {:mvn/version "2.8.1"}
             ...
             ;; @c3kit/feature :auth      = org.mindrot/jbcrypt                                       {:mvn/version "0.4"}
             ;; @c3kit/feature :markdownc = markdown-to-hiccup/markdown-to-hiccup                     {:mvn/version "0.6.2"}
```

Drop the trailing `;; harmless if :auth off`-style comments — the marker makes them redundant.

NOTE: this step depends on item 9 (CLI indent preservation) for the rendered output to keep the 13-space prefix. Sequence-wise, you can either:

- Land task 9 first, then this task (clean), OR
- Land this task knowing the rendered alignment will be wrong until task 9 lands, in which case the harness fails on `sqlite-defaults` alignment assertion until 9 commits.

Recommendation: reorder — do **task 9 before task 8**. The plan section order keeps 8 before 9 for narrative reasons but **the implementation order is 1→7, then 9, then 8**. Note this when executing.

- [ ] **Step 4: Run harness — expect GREEN**

```bash
cd verification && bb verify-all
```

Expected: all PASS. If any combo with the feature ON fails because the now-marker'd dep got stripped accidentally, double-check the marker spelling (`:auth`, `:content`, `:markdownc` — exact id keys from `c3kit-template.edn`).

- [ ] **Step 5: Commit**

```bash
git add templates/full-stack-reagent/deps.edn \
        verification/templates/full-stack-reagent/combos/*.expected.edn
git commit -m "feat(template): feature-gate deps in deps.edn

jbcrypt + google-api-client are auth-only; commonmark trio is
content-only; markdown-to-hiccup is markdownc-only. Convert each
to a line-toggle marker so disabled features ship a smaller deps.edn.
Combos assert presence/absence per feature flag.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: CLI marker-stripper indent loss (do BEFORE task 8)

**Files:**
- Modify: `cli/spec/c3kit_jig/features_spec.clj` (add failing tests)
- Modify: `cli/src/c3kit_jig/features.clj` (preserve leading whitespace)
- Modify: `verification/templates/full-stack-reagent/combos/sqlite-defaults.expected.edn` (lock in indent)

- [ ] **Step 1: Add failing unit tests**

Edit `cli/spec/c3kit_jig/features_spec.clj`. Inside the existing `(describe "features/strip" …)` block, add new tests just below the existing `"line-level toggle off removes the line"` test:

```clojure
  (it "line-level toggle preserves leading whitespace"
    (should= "             org.xerial/sqlite-jdbc {:mvn/version \"3.46.0.0\"}"
             (f/strip "             ;; @c3kit/db :sqlite = org.xerial/sqlite-jdbc {:mvn/version \"3.46.0.0\"}"
                      {} {:db :sqlite})))

  (it "feature line-eq preserves leading whitespace"
    (should= "    (require '[csp :refer [wrap]])"
             (f/strip "    ;; @c3kit/feature :csp = (require '[csp :refer [wrap]])"
                      {:csp true} {})))
```

- [ ] **Step 2: Run the CLI specs — expect RED**

```bash
cd cli && clj -M:test:spec -d features_spec
```

(or `clj -M:test:spec --tags features` depending on the project's spec runner config — fall back to plain `clj -M:test:spec` if the filter form isn't supported.)

Expected: the two new tests fail with output like:

```
Expected: "             org.xerial/sqlite-jdbc {:mvn/version \"3.46.0.0\"}"
Actual:   "org.xerial/sqlite-jdbc {:mvn/version \"3.46.0.0\"}"
```

- [ ] **Step 3: Capture leading whitespace in the regexes**

Edit `cli/src/c3kit_jig/features.clj`. Replace the `LINE-EQ-RE` and `DB-LINE-EQ-RE` definitions:

```clojure
(def ^:private LINE-EQ-RE
  #"^(\s*);;\s*@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*=\s*(.*)$")

(def ^:private DB-LINE-EQ-RE
  #"^(\s*);;\s*@c3kit/db\s+:([A-Za-z][A-Za-z0-9-]*)\s*=\s*(.*)$")
```

Update the two resolvers to prepend captured indent:

```clojure
(defn- resolve-line-eq [line features]
  (let [[_ indent inv id-str code] (re-find LINE-EQ-RE line)]
    (if (feature-on? features (keyword id-str) (some? inv)) (str indent code) ::drop)))

(defn- resolve-db-line-eq [line db-choice]
  (let [[_ indent id-str code] (re-find DB-LINE-EQ-RE line)]
    (if (= (keyword id-str) (:db db-choice)) (str indent code) ::drop)))
```

- [ ] **Step 4: Re-run CLI specs — expect GREEN**

```bash
cd cli && clj -M:test:spec
```

Expected: all features-spec tests pass, including the two new ones. The full CLI spec suite still passes (no regressions in other tests that exercise line-toggles — verify by reading the existing passing line-toggle tests; they used inputs without leading whitespace, which the new regex still matches because `^(\s*)` captures an empty string).

- [ ] **Step 5: Lock in the indent via the harness**

Edit `verification/templates/full-stack-reagent/combos/sqlite-defaults.expected.edn`. In the `:file-contains` map under `"deps.edn"` add the literal indented-line substring:

```clojure
"             org.xerial/sqlite-jdbc"
```

(13 spaces — match the column of the surrounding `com.cleancoders.c3kit/*` lines.)

- [ ] **Step 6: Run the harness on the sqlite combo — expect GREEN**

```bash
cd verification && bb verify --combo sqlite-defaults --tier light
```

Expected: PASS. The `:file-contains` substring is present at the right indent now that the stripper preserves leading whitespace.

- [ ] **Step 7: Sanity-check the other db combos**

```bash
cd verification && bb verify-all
```

Expected: all PASS. The other db combos do not assert on the sqlite line; postgres/datomic/memory all use their own `;; @c3kit/db :<x>` lines which the same regex change handles uniformly.

- [ ] **Step 8: Commit**

```bash
git add cli/src/c3kit_jig/features.clj \
        cli/spec/c3kit_jig/features_spec.clj \
        verification/templates/full-stack-reagent/combos/sqlite-defaults.expected.edn
git commit -m "fix(cli): preserve leading whitespace on line-toggle markers

LINE-EQ-RE and DB-LINE-EQ-RE now capture the indent prefix before
';;' and the resolvers prepend it to the emitted code. Without this
fix, a marker like

  '             ;; @c3kit/db :sqlite = org.xerial/sqlite-jdbc {...}'

stripped to column-0 'org.xerial/sqlite-jdbc {...}' in the rendered
deps.edn, breaking map alignment. Two new features_spec cases pin
the behavior. The sqlite-defaults harness combo asserts the 13-space
indented form survives.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Final pass — re-render `cli/my-cool-app/` and diff against expectations

This is a sanity step to catch anything the combo snapshots don't currently cover. No code change; report-only.

- [ ] **Step 1: Re-scaffold `my-cool-app` with the same flags the user used**

The original scaffold was created with `--db memory` and no features. Reproduce:

```bash
cd /tmp
rm -rf my-cool-app-fresh
bb -cp /Users/alex-root-roatch/current-projects/c3kit-jig/cli/src \
   -m c3kit-jig.main create my-cool-app-fresh \
   --template-dir /Users/alex-root-roatch/current-projects/c3kit-jig/templates \
   --template full-stack-reagent \
   --db memory \
   --feature content=false --feature ssr=false --feature markdownc=false \
   --feature auth=false --feature websocket=false \
   --yes --no-git
```

(If `bb -cp` is not how the CLI is normally invoked, fall back to running it from a built uberscript at `cli/dist/c3kit-jig.bb`.)

- [ ] **Step 2: Diff against the user's reference**

```bash
diff -ru /Users/alex-root-roatch/current-projects/c3kit-jig/cli/my-cool-app /tmp/my-cool-app-fresh | head -200
```

Expected differences are the nine fixes — review the diff and confirm each item from the spec is reflected:

1. No `dev/my_cool_app/compile_cljs.clj`
2. No `;(prn` lines in `dev/my_cool_app/seed.clj`; docstring added on `entity`
3. No `:prerender` block in `resources/config/cljs.edn`
4. No `ws-csrf-token` in `src/cljs/my_cool_app/config.cljs`
5. Schema package present: `src/cljc/my_cool_app/schema/{bg_task,full}.cljc`; old `schema.cljc`/`bg_task.cljc` absent
6. `main.clj` includes `bg-tasks` real, no `#_bg-tasks`
7. `repl.clj` defines `config-from-service` + `-ensure-migration-schema!`
8. `deps.edn` does not contain `jbcrypt`, `google-api-client`, `commonmark`, `markdown-to-hiccup`
9. `deps.edn` has properly-indented db dep line (no orphan column-0 dep)

Plus the README has the new "Seeding dev data" section.

- [ ] **Step 3: Update the committed `cli/my-cool-app/` if the user wants the in-tree copy refreshed**

The in-tree `cli/my-cool-app/` is untracked (per the gitStatus snapshot). Leave as-is — the user can re-scaffold whenever they want. Skip this step unless the user explicitly asks for the in-tree copy to be replaced.

- [ ] **Step 4: Final commit (only if any tidy-ups surfaced)**

If step 2 surfaced any unexpected diffs (e.g. a stray namespace not caught by the combos), add a follow-up commit fixing them. Otherwise no commit.

- [ ] **Step 5: Push the branch**

```bash
git push -u origin template-no-features-cleanup
```

---

## Self-Review

Spec coverage check (items 1-9 from the design doc):

- Item 1 → Task 1 ✓
- Item 2 → Task 2 ✓
- Item 3 → Task 3 ✓
- Item 4 → Task 4 ✓
- Item 5 → Task 5 ✓
- Item 6 → Task 6 ✓
- Item 7 → Task 7 ✓
- Item 8 → Task 8 (depends on Task 9 landing first; noted)
- Item 9 → Task 9 ✓
- Final verification pass → Task 10 ✓

Placeholder scan: no `TBD`, `TODO`, or "implement later". Every step has either a code block, an exact command with expected output, or a precise filesystem operation.

Type / name consistency:

- `acme.schema.full` consistent everywhere.
- `acme.schema.bg-task` (note hyphen — matches existing namespace style).
- `acme.schema.user` consistent.
- `bg-tasks` (defn name) vs `bg-task` (entity name) — both preserved correctly.
- `config-from-service` and `-ensure-migration-schema!` spelled identically in repl.clj and combo assertions.

Execution ordering note: implement in source order 1→7, then 9, then 8, then 10. Task 8 depends on Task 9's indent fix to keep `deps.edn` rendered alignment correct.
