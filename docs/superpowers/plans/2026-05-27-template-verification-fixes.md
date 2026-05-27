# Full-Stack-Reagent Verification Harness Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `bb verify-all --template full-stack-reagent` go fully green across all 8 combos by fixing the seven defects the harness currently documents.

**Architecture:** Fixes split across two layers. (1) **CLI** (`cli/src/c3kit_jig/`) — copy hardening (cruft) and quote-aware token rendering (hyphen ns symbols vs underscore strings). (2) **Template** (`templates/full-stack-reagent/`) — silence spec logging, fix memory-db migration config, fix a cljs spec `:refer` bug, wire a one-shot headless cljs spec command, and ship + comply with `.clj-kondo/config.edn` and `cljfmt.edn`.

**Tech Stack:** Babashka, Clojure tools.deps, Speclj, ClojureScript, timbre logging, c3kit (apron/bucket/wire/scaffold), clj-kondo, cljfmt.

---

## Current failure inventory (verified 2026-05-27)

Running `bb verify-all --template full-stack-reagent` produces, for every combo:

| Check | Failure | Root cause |
|-------|---------|------------|
| `no-cruft` | `cruft present: .cpcache, full-stack-reagent.iml` | `fetch/from-local-dir` does `fs/copy-tree` of the whole template working dir, including git-ignored dev artifacts. |
| `ns-hyphen` | every `(ns …)` form renders `my_app.*` (underscore) | `rename/replace-token`: for single-word token `acme`, `src-snake` == `src-hyphen` == `"acme"`; `:underscore` runs before `:hyphen` and consumes every match. |
| `lint` | `no .clj-kondo/config.edn shipped` | template ships no clj-kondo config. |
| `fmt` | `no cljfmt.edn shipped` | template ships no cljfmt config. |
| `clj-clean` | `log noise: …Setting log level: :warn …WARN …` | `spec/clj/acme/spec_helper.clj` calls `(log/warn!)`, which emits a timestamped REPORT line and leaves `:warn` logs (CSP / prerender) printing. |
| `cljs-run` (full tier only) | `null` | `:cljs` alias runs `acme.compile-cljs` (a compiler, no speclj summary) **and** `spec/cljs/acme/routes_spec.cljs:3` `:refer`s `with-redefs` from `speclj.core`, which is not a speclj macro → compile error. |
| `server-boot` (full tier only) | `migrate failed (exit 1)` → `:migration-ns is missing from the database config` | `config.clj` memory-db maps (`memory-local/staging/production`) omit `:migration-ns` etc. that the datomic/sqlite/postgres bases carry. |

Combos and tiers (from `verification/templates/full-stack-reagent/verify.edn`): `memory-defaults` runs the **full** tier (adds `cljs-run` + `server-boot`); the other seven run **light**.

## Design decision: quote-aware token rendering (ns-hyphen)

The token `acme` is a single word, so its hyphen and underscore spellings are identical at the source. Occurrences split semantically:

- **Unquoted Clojure symbols** (`(ns acme.x)`, `(:require [acme.y])`, `acme.z/foo`) MUST become the **hyphen** form `my-app.*`. Verified: `clojure -m foo_bar.baz` does **not** load a file declaring `(ns foo-bar.baz)` — symbol references and the ns form must agree, and the idiomatic/required form is hyphen.
- **String literals that are paths or munged JS** (`"public/cljs/acme_dev.js"`, `"goog.require('acme.main')"`, css `.acme`, `resources/config/cljs.edn` `:ns-prefix "acme"`) MUST stay **underscore** `my_app`.
- **String literals that are namespace arguments** (`deps.edn` `:main-opts ["-m" "acme.main"]`, the `:repl` `(require 'acme.repl)` strings) MUST become **hyphen** `my-app.*`, or the alias will fail to load the (now hyphenated) ns.

Chosen approach (user-approved): make rendering **context-aware**.
- In `.clj`/`.cljc`/`.cljs` files: split content on string literals; rewrite the token to **hyphen** in code segments and **underscore** inside string literals.
- In `.edn` files: rewrite token to **hyphen** when it looks like a namespace (token followed by `.` and a symbol char), **underscore** otherwise (bare prefix like `:ns-prefix "acme"`, or path strings). This makes `deps.edn` `-m acme.main` → `my-app.main` while `cljs.edn` `:ns-prefix "acme"` → `my_app`.
- All other text files (`.md`, `.css`, `.html`, `.js`, `.gitignore`, …): unchanged — single-variant underscore replacement as today (these contain only path/munged references).

The harness re-run (Task 9) is the empirical gate: `cljs-run` and `server-boot` compile and load the project namespaces and will fail loudly if any symbol/string ends up in the wrong form.

## File structure

CLI (changed):
- `cli/src/c3kit_jig/fetch.clj` — add `prune-cruft!`, call it after `fs/copy-tree`.
- `cli/src/c3kit_jig/rename.clj` — add `classify`/context-aware replacement helpers; keep `replace-token`/`replace-many` for filename rename (underscore) but add `replace-content` for context-aware content.
- `cli/src/c3kit_jig/render.clj` — `rewrite-content!` calls the context-aware function keyed on file extension.
- Tests: `cli/spec/c3kit_jig/fetch_spec.clj`, `cli/spec/c3kit_jig/rename_spec.clj`, `cli/spec/c3kit_jig/render_spec.clj`.

Template (changed):
- `templates/full-stack-reagent/src/clj/acme/config.clj` — memory-db migration keys.
- `templates/full-stack-reagent/spec/clj/acme/spec_helper.clj` (and `spec/cljc`, `spec/cljs` helpers) — silence logging.
- `templates/full-stack-reagent/spec/cljs/acme/routes_spec.cljs` — drop `with-redefs` from the speclj `:refer`.
- `templates/full-stack-reagent/deps.edn` — add a one-shot cljs spec alias.
- `templates/full-stack-reagent/.clj-kondo/config.edn` (new), `templates/full-stack-reagent/cljfmt.edn` (new) — shipped configs.
- `verification/templates/full-stack-reagent/verify.edn` — point `:cljs-once` at the new alias; add `resources/config/cljs.edn` to `:ns-prefix-exempt` if needed.

---

### Task 1: Exclude dev cruft from the scaffold copy (`no-cruft`)

**Files:**
- Modify: `cli/src/c3kit_jig/fetch.clj`
- Test: `cli/spec/c3kit_jig/fetch_spec.clj`

- [ ] **Step 1: Write the failing test**

Add to `cli/spec/c3kit_jig/fetch_spec.clj` inside the top-level `describe`:

```clojure
(it "from-local-dir omits git-ignored dev cruft from the copy"
  (let [root (fs/create-temp-dir {:prefix "tmpl-"})
        src  (fs/path root "templates" "demo")
        dest (fs/path root "out")]
    (fs/create-dirs (fs/path src ".cpcache"))
    (spit (fs/file (fs/path src ".cpcache" "x.json")) "{}")
    (spit (fs/file (fs/path src "demo.iml")) "<module/>")
    (fs/create-dirs (fs/path src "target"))
    (spit (fs/file (fs/path src "target" "out.txt")) "x")
    (spit (fs/file (fs/path src "c3kit-template.edn")) "{:id :demo}")
    (fetch/from-local-dir (fs/path root "templates") "demo" dest)
    (should     (fs/exists? (fs/path dest "c3kit-template.edn")))
    (should-not (fs/exists? (fs/path dest ".cpcache")))
    (should-not (fs/exists? (fs/path dest "demo.iml")))
    (should-not (fs/exists? (fs/path dest "target")))))
```

Ensure the ns `:require` includes `[babashka.fs :as fs]`, `[c3kit-jig.fetch :as fetch]`, and speclj `should-not`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd cli && clojure -M:test:spec -n c3kit-jig.fetch-spec`
Expected: FAIL — `.cpcache`, `demo.iml`, `target` still exist in `dest`.

- [ ] **Step 3: Implement `prune-cruft!` and call it after copy**

In `cli/src/c3kit_jig/fetch.clj`, add a cruft pattern list and a prune function, and call it at the end of `from-local-dir`:

```clojure
(def cruft-globs
  ["**.iml" "**.class" "**.jar" "**/.cpcache" ".cpcache"
   "**/target" "target" "**/node_modules" "node_modules"
   "**/.idea" ".idea" "**/.DS_Store" ".DS_Store"
   "**/.nrepl-port" ".nrepl-port" "**/.specljs-timestamp"
   "**/resources/prerender" "**/resources/prerendered"])

(defn prune-cruft!
  "Delete known dev/VCS artifacts from a freshly copied template tree."
  [dest]
  (doseq [pat cruft-globs
          match (fs/glob dest pat {:hidden true})]
    (when (fs/exists? match)
      (if (fs/directory? match)
        (fs/delete-tree (str match))
        (fs/delete-if-exists (str match))))))
```

Then change `from-local-dir`'s last line from `(fs/copy-tree src dest)` to:

```clojure
    (fs/copy-tree src dest)
    (prune-cruft! dest)
    dest))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd cli && clojure -M:test:spec -n c3kit-jig.fetch-spec`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add cli/src/c3kit_jig/fetch.clj cli/spec/c3kit_jig/fetch_spec.clj
git commit -m "fix(cli): prune dev cruft from scaffold copy

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Quote-aware token rendering for source files (`ns-hyphen`)

**Files:**
- Modify: `cli/src/c3kit_jig/rename.clj`
- Test: `cli/spec/c3kit_jig/rename_spec.clj`

- [ ] **Step 1: Write the failing tests**

Add to `cli/spec/c3kit_jig/rename_spec.clj` inside the top-level `describe`:

```clojure
(context "replace-content (context-aware)"
  (let [user (r/variants "my-app")
        toks {"acme" {:hyphen true :underscore true :pascal true}}]

    (it "clj: code symbols hyphenate, string literals stay underscore"
      (should= "(ns my-app.core (:require [my-app.foo]))\n\"public/cljs/my_app_dev.js\""
               (r/replace-content
                 "(ns acme.core (:require [acme.foo]))\n\"public/cljs/acme_dev.js\""
                 toks user "clj")))

    (it "clj: munged JS inside a string stays underscore"
      (should= "\"goog.require('my_app.main')\""
               (r/replace-content "\"goog.require('acme.main')\"" toks user "clj")))

    (it "edn: namespace args hyphenate, bare prefix stays underscore"
      (should= "{:main-opts [\"-m\" \"my-app.main\"] :ns-prefix \"my_app\"}"
               (r/replace-content
                 "{:main-opts [\"-m\" \"acme.main\"] :ns-prefix \"acme\"}"
                 toks user "edn")))

    (it "other ext: single-variant underscore as before"
      (should= ".my_app { color: red }"
               (r/replace-content ".acme { color: red }" toks user "css")))))
```

Also update the existing assertion at `rename_spec.clj:28-32` (`replace-many … "Acme.foo acme.bar"`) — that test targets filename/path replacement, which stays underscore, so it is unchanged. **Leave `replace-many` and `replace-token` as-is** (they drive filename renames in `render/rename-paths!`, which must stay underscore).

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd cli && clojure -M:test:spec -n c3kit-jig.rename-spec`
Expected: FAIL — `r/replace-content` is unresolved.

- [ ] **Step 3: Implement `replace-content`**

Add to `cli/src/c3kit_jig/rename.clj`. The function tokenizes content into string-literal and code segments, then applies the right variant per segment and file kind.

```clojure
(def ^:private STRING-LIT-RE #"\"(?:\\.|[^\"\\])*\"")

(defn- hyphenate-token
  "Replace pascal/upper-prefix as usual, but use the HYPHEN user variant for the base token."
  [s source-token flags user]
  (replace-token s source-token (dissoc flags :underscore) user))

(defn- underscore-token
  "Replace pascal/upper-prefix as usual, but use the UNDERSCORE user variant for the base token."
  [s source-token flags user]
  (replace-token s source-token (dissoc flags :hyphen) user))

(defn- ns-arg-string?
  "True if the literal string `lit` (including quotes) contains the source token
   immediately followed by a dot+symbol char — i.e. a namespace reference like
   \"acme.main\", as opposed to a bare path prefix like \"acme\"."
  [lit source-token]
  (boolean (re-find (re-pattern (str (java.util.regex.Pattern/quote source-token) "\\.[A-Za-z]"))
                    lit)))

(defn- replace-content-one
  "Apply one token to `content` with the segment rule appropriate to `ext`."
  [content [source-token flags] user ext]
  (let [segs (loop [s content acc [] last 0
                    m (re-matcher STRING-LIT-RE content)]
               (if (.find m)
                 (recur s
                        (-> acc
                            (conj [:code (subs content last (.start m))])
                            (conj [:str  (.group m)]))
                        (.end m) m)
                 (conj acc [:code (subs content last)])))]
    (->> segs
         (map (fn [[kind text]]
                (case [ext kind]
                  ;; Clojure source: code -> hyphen, strings -> underscore
                  ["clj" :code] (hyphenate-token text source-token flags user)
                  ["cljc" :code] (hyphenate-token text source-token flags user)
                  ["cljs" :code] (hyphenate-token text source-token flags user)
                  ["clj" :str]  (underscore-token text source-token flags user)
                  ["cljc" :str] (underscore-token text source-token flags user)
                  ["cljs" :str] (underscore-token text source-token flags user)
                  ;; edn: ns-arg strings -> hyphen, everything else -> underscore
                  (if (and (= ext "edn") (= kind :str) (ns-arg-string? text source-token))
                    (hyphenate-token text source-token flags user)
                    (underscore-token text source-token flags user)))))
         (apply str))))

(defn replace-content
  "Context-aware token replacement for file CONTENT. `ext` is the lowercased
   file extension (no dot). For clj/cljc/cljs and edn, distinguishes code/symbol
   context (hyphen) from string/path context (underscore). For any other ext,
   falls back to single-variant underscore replacement (paths/munged refs)."
  [content tokens user ext]
  (let [sorted (sort-by #(- (count (first %))) tokens)]
    (if (#{"clj" "cljc" "cljs" "edn"} ext)
      (reduce (fn [acc tok] (replace-content-one acc tok user ext)) content sorted)
      (reduce (fn [acc [src flags]] (underscore-token acc src flags user)) content sorted))))
```

Note: `replace-token` already replaces `:pascal`/`:upper-prefix` regardless; passing `(dissoc flags :underscore)` keeps pascal/upper while forcing the base token to the hyphen variant (and vice-versa). Verify `replace-token` skips a variant whose flag is absent — it does (`cond->` only conjs enabled variants).

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd cli && clojure -M:test:spec -n c3kit-jig.rename-spec`
Expected: PASS (all four new assertions + existing ones).

- [ ] **Step 5: Commit**

```bash
git add cli/src/c3kit_jig/rename.clj cli/spec/c3kit_jig/rename_spec.clj
git commit -m "feat(cli): context-aware token rendering (hyphen ns symbols, underscore strings)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Wire `replace-content` into `render/rewrite-content!`

**Files:**
- Modify: `cli/src/c3kit_jig/render.clj:23-29`
- Test: `cli/spec/c3kit_jig/render_spec.clj`

- [ ] **Step 1: Update the failing render test**

In `cli/spec/c3kit_jig/render_spec.clj`, change the assertion at line 39 from underscore to hyphen for the ns form, and confirm the css assertion stays underscore:

```clojure
                (let [core (slurp (fs/file (fs/path stage "src" "my_cool_app" "core.clj")))]
                  (should= "(ns my-cool-app.core)" core))
```

(Line ~41's `(should (re-find #"\.my_cool_app" css))` stays — css is underscore.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd cli && clojure -M:test:spec -n c3kit-jig.render-spec`
Expected: FAIL — file still contains `(ns my_cool_app.core)`.

- [ ] **Step 3: Make `rewrite-content!` use `replace-content`**

In `cli/src/c3kit_jig/render.clj`, replace the body of `rewrite-content!`:

```clojure
(defn- ext-of [^java.io.File f]
  (let [n (.getName f)] (str/lower-case (or (last (str/split n #"\.")) ""))))

(defn- rewrite-content! [tokens user features db file]
  (when (text-file? file)
    (let [orig (slurp file)
          after-tokens   (rn/replace-content orig tokens user (ext-of file))
          after-features (f/strip after-tokens features db)]
      (when-not (= orig after-features)
        (spit file after-features)))))
```

`rename-paths!` is untouched and still calls `rn/replace-many` (underscore) for filenames — correct.

- [ ] **Step 4: Run the full CLI suite**

Run: `cd cli && clojure -M:test:spec`
Expected: PASS. Investigate any other test that asserted underscore ns forms in content and update to hyphen (path/filename assertions stay underscore).

- [ ] **Step 5: Commit**

```bash
git add cli/src/c3kit_jig/render.clj cli/spec/c3kit_jig/render_spec.clj
git commit -m "fix(cli): render content with context-aware token replacement

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Add `:migration-ns` to memory-db config (`server-boot` migrate)

**Files:**
- Modify: `templates/full-stack-reagent/src/clj/acme/config.clj:53-56`

- [ ] **Step 1: Reproduce the failure**

```bash
cd /tmp && rm -rf v4 && mkdir v4 && cd v4
bb -cp /Users/alex-root-roatch/current-projects/c3kit-jig/cli/src -m c3kit-jig.main create my-app \
  --template-dir /Users/alex-root-roatch/current-projects/c3kit-jig/templates \
  --template full-stack-reagent --db memory --yes --no-git
cd my-app && clojure -M:test:migrate
```
Expected: `:migration-ns is missing from the database config.`

- [ ] **Step 2: Add migration keys to the memory configs**

In `templates/full-stack-reagent/src/clj/acme/config.clj`, replace lines 53-56 with:

```clojure
;; memory backend defined unconditionally — HEAD default + valid wizard choice
(def memory-base
  {:impl                :memory
   :migration-dir       "acme.migrations"
   :migration-ns-prefix "m"
   :migration-ns        'acme.migrations
   :full-schema         'acme.schema/full})

(def memory-local      memory-base)
(def memory-staging    memory-base)
(def memory-production memory-base)
```

(After Task 2/3 rendering: the `'acme.migrations` symbol → `my-app.migrations`; the `"acme.migrations"` string → `my_app.migrations`; both internally consistent with c3kit's migrator, which finds zero migrations in a fresh scaffold and exits 0.)

- [ ] **Step 3: Re-scaffold and verify migrate succeeds**

```bash
cd /tmp && rm -rf v4 && mkdir v4 && cd v4
bb -cp /Users/alex-root-roatch/current-projects/c3kit-jig/cli/src -m c3kit-jig.main create my-app \
  --template-dir /Users/alex-root-roatch/current-projects/c3kit-jig/templates \
  --template full-stack-reagent --db memory --yes --no-git
cd my-app && clojure -M:test:migrate; echo "EXIT=$?"
```
Expected: `EXIT=0`, no `:migration-ns is missing` error.

- [ ] **Step 4: Commit**

```bash
git add templates/full-stack-reagent/src/clj/acme/config.clj
git commit -m "fix(template): give memory db a migration-ns so migrate succeeds

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Silence spec logging (`clj-clean`)

**Files:**
- Modify: `templates/full-stack-reagent/spec/clj/acme/spec_helper.clj`
- Check/Modify: `templates/full-stack-reagent/spec/cljc/acme/spec_helperc.cljc`, `templates/full-stack-reagent/spec/cljs/acme/spec_helper.cljs`

The harness `clj-clean` check fails on **any** line containing a timestamp or a level marker. `(log/warn!)` emits a timestamped `REPORT … Setting log level: :warn` line (because the level changes) and leaves `:warn` logs (CSP, prerender) printing. `log/off!` also goes through `set-level!`, which emits the same REPORT line. Set the timbre min-level **directly** (no report message) to the highest level so nothing below `:report` prints, and avoid any `set-level!` call.

- [ ] **Step 1: Reproduce the noise**

```bash
cd /tmp/v4/my-app && clojure -M:test:spec 2>&1 | grep -E '[0-9]{4}-[0-9]{2}-[0-9]{2}|WARN|REPORT' | head
```
Expected: shows the `Setting log level: :warn` REPORT line and/or `WARN` lines.

- [ ] **Step 2: Replace `(log/warn!)` with a direct, silent min-level set**

In `templates/full-stack-reagent/spec/clj/acme/spec_helper.clj`, add `[taoensso.timbre :as timbre]` to the `:require` and replace `(log/warn!)` with:

```clojure
(timbre/set-min-level! :report)   ; silence INFO/WARN/ERROR in specs without emitting a "Setting log level" line
```

Keep the `[c3kit.apron.log :as log]` require if other helpers use it; otherwise remove it to satisfy clj-kondo (Task 7).

- [ ] **Step 3: Verify clj spec output is clean**

```bash
cd /tmp/v4/my-app && clojure -M:test:spec 2>&1 | tee /tmp/spec-out.txt | tail -3
grep -E '[0-9]{4}-[0-9]{2}-[0-9]{2}|\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\b' /tmp/spec-out.txt && echo "NOISE FOUND" || echo "CLEAN"
```
Expected: `CLEAN`, and the speclj summary shows `0 failures`.

- [ ] **Step 4: Apply the same silence to cljc/cljs spec helpers if they log**

Inspect `spec/cljc/acme/spec_helperc.cljc` and `spec/cljs/acme/spec_helper.cljs`. If either calls `log/warn!`/`log/set-level!`, replace with the platform-appropriate silent set (cljs: `(timbre/set-min-level! :report)` works under cljs too). This also keeps `cljs-run` output (Task 6) clean.

- [ ] **Step 5: Commit**

```bash
git add templates/full-stack-reagent/spec
git commit -m "fix(template): silence spec logging so test output is clean

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Fix and wire one-shot headless cljs specs (`cljs-run`)

**Files:**
- Modify: `templates/full-stack-reagent/spec/cljs/acme/routes_spec.cljs:3`
- Modify: `templates/full-stack-reagent/deps.edn`
- Modify: `verification/templates/full-stack-reagent/verify.edn:33`

The `cljs-run` check expects `:cljs-once` to print a speclj summary (`N examples, M failures`) and exit 0. Today `:cljs` runs `acme.compile-cljs` (a compiler, no summary) and the specs don't even compile because of a bad `:refer`.

- [ ] **Step 1: Fix the `with-redefs` `:refer` bug (RED first)**

Reproduce:
```bash
cd /tmp/v4/my-app && clojure -M:test:cljs once 2>&1 | grep -i "with-redefs"
```
Expected: `Invalid :refer, macro speclj.core/with-redefs does not exist in file spec/cljs/my_app/routes_spec.cljs`.

In `templates/full-stack-reagent/spec/cljs/acme/routes_spec.cljs`, remove `with-redefs` from the `speclj.core` `:refer` vector (line 3). `with-redefs` is a `cljs.core` macro and is available without referring it — `main_spec.cljs` already uses it that way. The `:refer` should keep the speclj names only:

```clojure
                   [speclj.core :refer [around before context describe it redefs-around should-be-nil should-have-invoked should= stub with-stubs]])
```

- [ ] **Step 2: Identify the c3kit headless cljs spec entrypoint**

Inspect the `com.cleancoders.c3kit/scaffold` cljs runner for a one-shot spec mode:
```bash
JAR=$(find ~/.m2 -path '*c3kit/scaffold*' -name 'scaffold-*.jar' | sort | tail -1)
unzip -l "$JAR" | grep -iE 'cljs|spec|test'
unzip -p "$JAR" c3kit/scaffold/cljs.cljc 2>/dev/null | grep -nE 'defn -main|spec|once|node|playwright|karma' | head
```
Expected: locate how specs run headless (the design spec calls this "Playwright headless"). Determine the exact main-opts/args that compile the spec build once and run it, emitting the speclj summary.

- [ ] **Step 3: Add a dedicated one-shot cljs-spec alias to `deps.edn`**

In `templates/full-stack-reagent/deps.edn` `:aliases`, add (exact main-ns/args from Step 2 — placeholder `c3kit.scaffold.cljs` shown; replace with the verified spec-runner entry and any Playwright/node deps required):

```clojure
             :cljs-spec {:extra-paths ["dev" "spec/cljs" "spec/cljc"]
                         :main-opts   ["-m" "c3kit.scaffold.cljs" "spec-once"]}
```

If the runner needs Playwright/node, document the prerequisite in `README.scaffold.md` and ensure the CI full-tier job (per the harness design) installs the browser. If headless specs cannot run without node in this environment, set `:cljs-run false` for `memory-defaults` in `verify.edn` and record the gap — but prefer wiring a real runner.

- [ ] **Step 4: Point the harness at the new alias**

In `verification/templates/full-stack-reagent/verify.edn`, change:
```clojure
            :cljs-once ["clojure" "-M:test:cljs-spec"]
```
(from the current `["clojure" "-M:test:cljs" "once"]`).

- [ ] **Step 5: Verify a clean one-shot run**

```bash
cd /tmp && rm -rf v6 && mkdir v6 && cd v6
bb -cp /Users/alex-root-roatch/current-projects/c3kit-jig/cli/src -m c3kit-jig.main create my-app \
  --template-dir /Users/alex-root-roatch/current-projects/c3kit-jig/templates \
  --template full-stack-reagent --db memory --yes --no-git
cd my-app && clojure -M:test:cljs-spec 2>&1 | tail -5; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: a `N examples, 0 failures` summary and `EXIT=0`.

- [ ] **Step 6: Commit**

```bash
git add templates/full-stack-reagent/spec/cljs/acme/routes_spec.cljs templates/full-stack-reagent/deps.edn verification/templates/full-stack-reagent/verify.edn
git commit -m "fix(template): runnable one-shot cljs specs + fix routes_spec refer

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Ship and comply with `.clj-kondo/config.edn` and `cljfmt.edn` (`lint`, `fmt`)

**Files:**
- Create: `templates/full-stack-reagent/.clj-kondo/config.edn`
- Create: `templates/full-stack-reagent/cljfmt.edn`
- Modify: template source files as needed to reach a clean lint/fmt.

The harness `tool-check` fails if the config is missing (current state) **and** if the tool exits non-zero. So shipping the configs is necessary but not sufficient — the rendered template must actually pass them. Commands the harness runs (from `verify.edn`): `clj-kondo --lint src spec dev` and `cljfmt check src spec dev`.

- [ ] **Step 1: Add `.clj-kondo/config.edn`**

Seed from the CLI's own config (`cli/.clj-kondo/config.edn`) as a starting point, then tune for the c3kit/speclj macros used in the template. Create `templates/full-stack-reagent/.clj-kondo/config.edn`:

```clojure
{:linters {:unresolved-symbol {:exclude [(speclj.core/should-not-throw)]}}
 :lint-as {speclj.core/it           clojure.core/fn
           speclj.core/describe     clojure.core/let
           speclj.core/context      clojure.core/let
           speclj.core/before       clojure.core/fn
           speclj.core/around       clojure.core/fn
           speclj.core/redefs-around clojure.core/fn}
 :hooks {}}
```

(Adjust `:lint-as`/`:exclude` based on the actual findings from Step 2 — do not guess away real bugs; only silence false positives for speclj/c3kit macro forms.)

- [ ] **Step 2: Run clj-kondo against a fresh scaffold and drive findings to zero**

```bash
cd /tmp && rm -rf v7 && mkdir v7 && cd v7
bb -cp /Users/alex-root-roatch/current-projects/c3kit-jig/cli/src -m c3kit-jig.main create my-app \
  --template-dir /Users/alex-root-roatch/current-projects/c3kit-jig/templates \
  --template full-stack-reagent --db memory --yes --no-git
cd my-app && clj-kondo --lint src spec dev; echo "EXIT=$?"
```
For each finding: if it is a real issue (unused require, shadowed var), fix it in the **template** source; if it is a macro false positive, extend `.clj-kondo/config.edn`. Iterate until `EXIT=0` (warnings allowed only if clj-kondo still exits 0; otherwise suppress or fix). Re-scaffold after each template edit.

- [ ] **Step 3: Add `cljfmt.edn` and format the template**

Create `templates/full-stack-reagent/cljfmt.edn` (start permissive; the c3kit house style aligns map values in places, so disable the alignment-breaking rules that would fight the existing style):

```clojure
{:remove-surrounding-whitespace? true
 :remove-trailing-whitespace?    true
 :insert-missing-whitespace?     true
 :remove-consecutive-blank-lines? false
 :indents {}}
```

Then bring the template into compliance by formatting the **template source directly** (not just the scaffold), so future scaffolds stay clean:

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig/templates/full-stack-reagent
cljfmt fix --config cljfmt.edn src spec dev
```
Review the diff; if cljfmt reformats `acme.*` token lines, that is fine (tokens render afterward). Ensure no `@c3kit/...` marker comments get mangled (they are line comments; cljfmt leaves them).

- [ ] **Step 4: Verify both tools pass on a fresh scaffold**

```bash
cd /tmp && rm -rf v7 && mkdir v7 && cd v7
bb -cp /Users/alex-root-roatch/current-projects/c3kit-jig/cli/src -m c3kit-jig.main create my-app \
  --template-dir /Users/alex-root-roatch/current-projects/c3kit-jig/templates \
  --template full-stack-reagent --db memory --yes --no-git
cd my-app
clj-kondo --lint src spec dev; echo "KONDO=$?"
cljfmt check src spec dev; echo "FMT=$?"
```
Expected: `KONDO=0` and `FMT=0`.

- [ ] **Step 5: Confirm the configs are NOT on the harness denylist**

Confirm `.clj-kondo/config.edn` and `cljfmt.edn` are not pruned by Task 1's `cruft-globs` (they are not) and not in `verify.edn` `:denylist` (they are not). They are exactly the files the `lint`/`fmt` checks require to exist.

- [ ] **Step 6: Commit**

```bash
git add templates/full-stack-reagent/.clj-kondo/config.edn templates/full-stack-reagent/cljfmt.edn templates/full-stack-reagent/src templates/full-stack-reagent/spec templates/full-stack-reagent/dev
git commit -m "feat(template): ship clj-kondo + cljfmt configs and bring template into compliance

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Gate `compile_cljs.clj` SSR code behind the `:ssr` feature (cleanup)

**Files:**
- Modify: `templates/full-stack-reagent/dev/acme/compile_cljs.clj`

The design spec lists "Gating `compile_cljs.clj`'s SSR-specific code behind the `:ssr` feature." In non-SSR combos (`memory-no-ssr-no-content`) the prerender code still emits `WARN … Skipping prerender …` lines. With Task 5 silencing logs these no longer break `clj-clean`, but the SSR-only `find-prerender-namespaces`/`patch-prerender!`/`generate-prerender-requires!` should not run when `:ssr` is off.

- [ ] **Step 1: Wrap SSR-only code in `@c3kit/feature :ssr { … }` markers**

In `dev/acme/compile_cljs.clj`, surround the prerender-specific defns and the `-main` SSR preamble (the `generate-prerender-requires!` call, `patch-prerender!`, and the `System/setProperty "acme.env" "prerender"` block) with the feature markers the renderer strips:

```clojure
;; @c3kit/feature :ssr {
(defn prerender-ns? [content] …)
;; … prerender helpers …
;; @c3kit/feature :ssr }
```
And in `-main`, guard the SSR preamble so the non-SSR build just calls `(apply cljs/-main args)`.

- [ ] **Step 2: Verify the no-ssr combo scaffolds without prerender code**

```bash
cd /tmp && rm -rf v8 && mkdir v8 && cd v8
bb -cp /Users/alex-root-roatch/current-projects/c3kit-jig/cli/src -m c3kit-jig.main create my-app \
  --template-dir /Users/alex-root-roatch/current-projects/c3kit-jig/templates \
  --template full-stack-reagent --db memory --yes --no-git --feature ssr=false --feature content=false
grep -rn "prerender" /tmp/v8/my-app/dev || echo "no prerender code (correct for ssr=false)"
```
Expected: no prerender code remains in the `ssr=false` scaffold.

- [ ] **Step 3: Commit**

```bash
git add templates/full-stack-reagent/dev/acme/compile_cljs.clj
git commit -m "refactor(template): gate compile-cljs SSR code behind :ssr feature

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Full harness green (verification gate)

**Files:** none (verification only).

- [ ] **Step 1: Run the harness unit tests**

Run: `cd verification && bb test`
Expected: PASS — the `checks_spec.clj` pure-core tests are unaffected by these fixes.

- [ ] **Step 2: Run the full verification across all combos**

Run: `cd verification && bb verify-all --template full-stack-reagent`
Expected: every combo reports `[PASS]` for `no-cruft`, `combo`, `residue`, `ns-hyphen`, `lint`, `fmt`, `clj-clean`; and `memory-defaults` additionally `[PASS]` for `cljs-run` and `server-boot`. Process exit `0`.

- [ ] **Step 3: If any check is still red, debug that check only**

Use `bb verify --combo <name> --tier full --keep-tmp` to retain the scaffold dir, then run the failing command inside it by hand (the harness prints the scaffold path). Apply the systematic-debugging skill — find the root cause before changing code, do not loosen a check to make it pass.

- [ ] **Step 4: Run the CLI suite one more time**

Run: `cd cli && clojure -M:test:spec`
Expected: PASS — confirms the rename/render/fetch changes didn't regress other CLI behavior.

- [ ] **Step 5: Commit any final tweaks**

```bash
git add -A
git commit -m "test: full-stack-reagent verification harness green across all combos

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-review notes

- **Spec coverage:** all 6 "Out of scope (recorded for the fix pass)" bullets from `docs/superpowers/specs/2026-05-27-template-verification-harness-design.md` are covered — cruft (T1), namespace rendering (T2/T3), clj log quieting (T5), one-shot cljs (T6), SSR gating (T8), clj-kondo+cljfmt (T7). The migrate failure (T4) is an additional defect found during this investigation, not in the original out-of-scope list.
- **Type/name consistency:** `replace-content` (T2) is the name wired in T3; `prune-cruft!`/`cruft-globs` (T1) are self-contained; `memory-base` (T4) mirrors the existing `*-base` defs.
- **Ordering:** T1–T3 (CLI) must land before template combos can pass `no-cruft`/`ns-hyphen`; T4–T8 are independent template fixes; T9 gates the whole set. Subagent-driven execution can parallelize T4/T5/T6/T7/T8 after T1–T3 merge, but each must re-scaffold from the updated CLI.
- **Risk:** T6 (headless cljs) and T7 (lint/fmt compliance) carry the most uncertainty (external tooling: Playwright/node, clj-kondo finding volume). Both have explicit investigate-then-wire steps and the harness re-run as the gate.
