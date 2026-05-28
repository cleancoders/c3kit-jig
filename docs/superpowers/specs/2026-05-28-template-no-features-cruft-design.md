# Design — Template cruft cleanup (no-features scaffold)

Date: 2026-05-28
Template: `full-stack-reagent`
Scope: clean cruft a user reported after scaffolding with all optional
features disabled. Extend the verification harness so each issue is
caught by the combo-expected snapshots.

## Motivation

User scaffolded `full-stack-reagent` with no features selected
(`:content :ssr :markdownc :auth :websocket` all off, `:memory`
backend) and reported nine cruft items in the output. Items 1–7 are
the user's enumerated list; items 8–9 surfaced during design from
reading the same scaffold.

The harness already runs combo snapshots in
`verification/templates/full-stack-reagent/combos/*.expected.edn` via
`must-exist` / `must-not-exist` / `file-contains` /
`file-not-contains`. Each fix below lands with a matching combo
update so the same cruft cannot reappear.

## Approach

Per-issue commits. Each commit touches the template + the combo
`.expected.edn` files affected. TDD: combo update first (RED), template
fix second (GREEN). Existing speclj suite must still pass on HEAD
template (`templates/full-stack-reagent` runs as-is on memory backend).

## Items

### 1. `compile_cljs.clj` is SSR-only

Wrap whole file `dev/acme/compile_cljs.clj` and
`spec/clj/acme/compile_cljs_spec.clj` in
`;; @c3kit/feature :ssr { … }` block markers. `deps.edn` `:cljs`
alias gets feature-gated:

```clojure
;; @c3kit/feature :ssr {
:cljs     {:main-opts ["-m" "acme.compile-cljs"]
           :extra-paths ["dev"]}
;; @c3kit/feature :ssr }
;; @c3kit/feature !:ssr {
:cljs     {:main-opts ["-m" "c3kit.scaffold.cljs"]}
;; @c3kit/feature !:ssr }
```

Combo update (`memory-no-ssr-no-content`, `memory-minimal`):
- `:must-not-exist` += `dev/my_app/compile_cljs.clj`,
  `spec/clj/my_app/compile_cljs_spec.clj`
- `:file-contains` `deps.edn` += `"c3kit.scaffold.cljs"`
- `:file-not-contains` `deps.edn` += `my-cool-app.compile-cljs`

### 2. `seed.clj` debug noise + missing docs

- Remove `;(prn "other-fields: " other-fields)` and `;(prn "e: " e)`
  inside the `Entity` deftype `deref` method.
- Add docstring on `entity` defn explaining seed pattern
  (kind / search-fields / other-fields, deref to upsert).
- Add brief "Seeding dev data" section to `README.scaffold.md`
  pointing at `dev/<app>/seed.clj` and the `:seed` alias.

Combo update (all combos):
- `:file-not-contains` `dev/my_app/seed.clj` += `(prn "other-fields:`,
  `(prn "e: `

### 3. `:prerender` block in `resources/config/cljs.edn`

Wrap the `:prerender { … }` key-value pair in
`;; @c3kit/feature :ssr { … }` block markers (EDN line comments are
ignored by the reader, so this works without breaking parse).

Combo update (`memory-no-ssr-no-content`, `memory-minimal`):
- `:file-not-contains` `resources/config/cljs.edn` += `:prerender`,
  `acme.ssr.prerender`

### 4. `ws-csrf-token` in `config.cljs` without websocket

Wrap `(def ws-csrf-token …)` defn with
`;; @c3kit/feature :websocket { … }` block markers.

Combo update (`memory-no-websocket`, `memory-minimal`):
- `:file-not-contains` `src/cljs/my_app/config.cljs` += `ws-csrf-token`

### 5. Schema package restructure

Target shape (per user confirmation):

```
src/cljc/<app>/schema/
  bg_task.cljc          ; (ns <app>.schema.bg-task) — defines bg-task schema
  user.cljc             ; (ns <app>.schema.user)    — defines user schemas, :auth-gated dir
  full.cljc             ; (ns <app>.schema.full)    — aggregator, defines `full`
```

Files removed: `src/cljc/<app>/schema.cljc`,
`src/cljc/<app>/bg_task.cljc`, `src/cljc/<app>/auth/user/schema.cljc`.

`full.cljc`:

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

Consumer updates: every site that requires `acme.schema` or
`acme.auth.user.schema` switches to `acme.schema.full` /
`acme.schema.user`. Sites to update: `init.cljc`, auth-side legend
loaders, any spec helpers.

Combo update (all combos):
- `:must-exist` swap `src/cljc/my_app/schema.cljc` →
  `src/cljc/my_app/schema/full.cljc` +
  `src/cljc/my_app/schema/bg_task.cljc`
- `:must-not-exist` += `src/cljc/my_app/schema.cljc`,
  `src/cljc/my_app/bg_task.cljc`,
  `src/cljc/my_app/auth/user/schema.cljc`
- auth combos: `:must-exist` += `src/cljc/my_app/schema/user.cljc`
- non-auth combos: `:must-not-exist` +=
  `src/cljc/my_app/schema/user.cljc`

### 6. `#_bg-tasks` reader-discard in `main.clj`

Remove the `#_` discard. Include `bg-tasks` for real in
`all-services`. `scheduled-tasks` is empty by default, so the
service is a no-op until the consumer adds tasks. Both `:websocket`
and `!:websocket` variants of `all-services` updated.

Combo update (all combos):
- `:file-not-contains` `src/clj/my_app/main.clj` += `#_bg-tasks`
- `:file-contains` `src/clj/my_app/main.clj` += `bg-tasks` (no
  discard prefix; tightens snapshot)

### 7. REPL `ensure-migration-schema!`

Add the provided functions to `dev/acme/repl.clj` and call after
`start-db`. Unconditional for all DB backends (memory bucket
implements the protocol methods, so the call is safe).

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

Combo update (all combos):
- `:file-contains` `dev/my_app/repl.clj` += `ensure-migration-schema!`,
  `config-from-service`

### 8. Feature-gate `deps.edn` dependencies

Currently unconditional but feature-coupled:

- `org.mindrot/jbcrypt`            — `:auth` (seed.clj + auth backend)
- `com.google.api-client/google-api-client` — `:auth` (user.web)
- `com.atlassian.commonmark/*` (3 entries) — `:content`
- `markdown-to-hiccup/markdown-to-hiccup` — `:markdownc`

Convert each to a line-toggle marker so the dep disappears when the
feature is off:

```
;; @c3kit/feature :auth      = org.mindrot/jbcrypt                                {:mvn/version "0.4"}
;; @c3kit/feature :auth      = com.google.api-client/google-api-client            {:mvn/version "2.8.1"}
;; @c3kit/feature :content   = com.atlassian.commonmark/commonmark                {:mvn/version "0.17.0"}
;; @c3kit/feature :content   = com.atlassian.commonmark/commonmark-ext-gfm-strikethrough {:mvn/version "0.17.0"}
;; @c3kit/feature :content   = com.atlassian.commonmark/commonmark-ext-gfm-tables {:mvn/version "0.17.0"}
;; @c3kit/feature :markdownc = markdown-to-hiccup/markdown-to-hiccup              {:mvn/version "0.6.2"}
```

Drop the "harmless if X off" trailing comments now that the gate is
real.

Combo update:
- `memory-minimal`, `memory-no-auth`: `:file-not-contains` `deps.edn`
  += `jbcrypt`, `google-api-client`
- `memory-minimal`, `memory-no-ssr-no-content`: `:file-not-contains`
  `deps.edn` += `commonmark`
- markdownc-off combos: `:file-not-contains` `deps.edn` +=
  `markdown-to-hiccup`
- feature-ON combos keep `:file-contains` for the corresponding deps

### 9. CLI marker-stripper indent loss

Bug: line-toggle marker `;; @c3kit/feature X = <code>` strips the
marker prefix but does not preserve the run of leading whitespace
before `;;`. Result: emitted `<code>` lands at column 0 even when
the source line was indented to align with surrounding map entries.

Visible in `cli/my-cool-app/deps.edn:10`:

```
             com.datomic/peer                                          {:mvn/version "1.0.7482"}
org.xerial/sqlite-jdbc {:mvn/version "3.46.0.0"}
```

(should be indented to column 13 like the others).

Fix: locate the line-toggle handler in `cli/src/c3kit_jig/`,
capture the leading whitespace, emit it before the activated
payload. Exact line and shape to be confirmed during impl.

Harness: `sqlite-defaults` combo `:file-contains` `deps.edn` +=
the literal whitespace+token string
`"             org.xerial/sqlite-jdbc"` so an unindented re-emit
fails the snapshot.

## TDD strategy

For each item:

1. Add the matching combo `.expected.edn` entries first; run the
   verification harness against the current template; confirm RED.
2. Update the template (markers + file moves); rerun harness; confirm
   GREEN.
3. Run `clj -M:test:spec` and `clj -M:test:cljs-spec` against the
   HEAD template (memory backend, all features on) to confirm the
   moved/gated files still compile and tests still pass.
4. Commit (one commit per item).

## Out of scope

- Other potential cruft not surfaced by the user or this design
  pass. Tracked in this PR only.
- `bg-tasks` itself remains a no-op until the consumer adds entries
  to `scheduled-tasks`. Documentation pass on background task
  registration not included.

## Open questions

None — confirmed:
1. Schema layout: package + `full.cljc` aggregator inside.
2. `ensure-migration-schema!`: unconditional for all backends.
3. Harness: extend combo `.expected.edn` files; no new check type.
4. Item 6: include `bg-tasks` for real, not removed.
5. Items 8–9 folded into this PR.
