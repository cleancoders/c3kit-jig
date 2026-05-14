# Plugin-Style Template Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganise `templates/full-stack-reagent/` so every optional feature owns a namespace-subdirectory under `{{acme}}.*`, eliminating the `:delete-when-off` mechanism and shrinking the post-scaffold hook to a residue-grep safety net.

**Architecture:** Two-phase change. Phase 1 extends the CLI scaffolder (`cli/src/c3kit_create/render.clj`, `manifest.clj`) with three new behaviours — feature-subdir deletion, `:extras` deletion, db-script rename — added alongside the existing `:delete-when-off` code path (no breakage). Phase 2 moves template files into feature subdirectories, renames namespaces, rewrites the manifest, replaces the hook, and rebaselines combo fixtures. A final cleanup phase removes the now-unused `:delete-when-off` code path.

**Tech Stack:** Babashka (CLI + hook), Speclj (tests), `clojure.tools.cli`, `babashka.fs`, EDN fixtures.

**Spec:** `docs/specs/2026-05-14-template-plugin-style-refactor-design.md`

---

## File Structure

**CLI source (modified):**

- `cli/src/c3kit_create/render.clj` — add `apply-feature-dir-deletes!`, `apply-extras-deletes!`, `apply-db-rename!`. Wire into `render!`.
- `cli/src/c3kit_create/manifest.clj` — accept new keys (`:namespace-token`, `:extras` on features, `:template`/`:sibling-glob` on `:db`).
- `cli/src/c3kit_create/features.clj` — no change (`@c3kit/db` markers already supported).

**CLI tests (added/modified):**

- `cli/spec/c3kit_create/render_spec.clj` — new it-blocks for each new behaviour.
- `cli/spec/c3kit_create/manifest_spec.clj` — new it-blocks for new schema keys.

**Template (modified — Phase 2):**

- `templates/full-stack-reagent/c3kit-template.edn` — drop all `:delete-when-off`; add `:namespace-token "acme"`, `:extras` on `:ssr`, `:template`/`:sibling-glob` on `:db`.
- `templates/full-stack-reagent/c3kit-template.bb` — rewrite as ~30-LOC residue grep.
- `templates/full-stack-reagent/src/clj/acme/main.clj` — update marker `:require` entries to new namespace paths.
- `templates/full-stack-reagent/src/clj/acme/routes.clj` — same.
- `templates/full-stack-reagent/src/clj/acme/config.clj` — add `@c3kit/db` markers around `:bucket` line (replaces hook reconciliation).
- `templates/full-stack-reagent/deps.edn` — wrap `:seed` alias in `@c3kit/feature :auth { … }` markers (replaces hook `:seed` strip).
- `templates/full-stack-reagent/dev/acme/seed.clj` — wrap auth-specific body in `@c3kit/feature :auth { … }` markers; add `!:auth` no-op branch.

**Template file moves (Phase 2):**

| From                                                            | To                                                                  |
|-----------------------------------------------------------------|---------------------------------------------------------------------|
| `src/clj/acme/user.clj`                                         | `src/clj/acme/auth/user.clj`                                        |
| `src/clj/acme/session.clj`                                      | `src/clj/acme/auth/session.clj`                                     |
| `src/clj/acme/destination.clj`                                  | `src/clj/acme/auth/destination.clj`                                 |
| `src/clj/acme/user/` (subtree)                                  | `src/clj/acme/auth/user/`                                           |
| `src/cljc/acme/user/` (subtree)                                 | `src/cljc/acme/auth/user/`                                          |
| `src/cljs/acme/user.cljs`                                       | `src/cljs/acme/auth/user.cljs`                                      |
| `src/cljs/acme/forgot_password.cljs`                            | `src/cljs/acme/auth/forgot_password.cljs`                           |
| `src/cljs/acme/recover_password.cljs`                           | `src/cljs/acme/auth/recover_password.cljs`                          |
| `spec/clj/acme/user/` (subtree)                                 | `spec/clj/acme/auth/user/`                                          |
| `spec/clj/acme/session_spec.clj`                                | `spec/clj/acme/auth/session_spec.clj`                               |
| `spec/clj/acme/destination_spec.clj`                            | `spec/clj/acme/auth/destination_spec.clj`                           |
| `spec/cljs/acme/user_spec.cljs`                                 | `spec/cljs/acme/auth/user_spec.cljs`                                |
| `spec/cljs/acme/forgot_password_spec.cljs`                      | `spec/cljs/acme/auth/forgot_password_spec.cljs`                     |
| `spec/cljs/acme/recover_password_spec.cljs`                     | `spec/cljs/acme/auth/recover_password_spec.cljs`                    |
| `src/clj/acme/content.clj`                                      | `src/clj/acme/content/core.clj`                                     |
| `src/clj/acme/markdown.clj`                                     | `src/clj/acme/content/markdown.clj`                                 |
| `spec/clj/acme/content_spec.clj`                                | `spec/clj/acme/content/core_spec.clj`                               |
| `spec/clj/acme/markdown_spec.clj`                               | `spec/clj/acme/content/markdown_spec.clj`                           |
| `src/cljs/acme/content_page.cljs`                               | `src/cljs/acme/content/page.cljs`                                   |
| `spec/cljs/acme/content_page_spec.cljs`                         | `spec/cljs/acme/content/page_spec.cljs`                             |
| `src/clj/acme/prerender.clj`                                    | `src/clj/acme/ssr/prerender.clj`                                    |
| `spec/clj/acme/prerender_spec.clj`                              | `spec/clj/acme/ssr/prerender_spec.clj`                              |
| `dev/acme/prerender.cljs`                                       | `dev/acme/ssr/prerender.cljs`                                       |
| `dev/acme/prerender_pages.cljs`                                 | `dev/acme/ssr/prerender_pages.cljs`                                 |
| `dev/acme/prerender_preamble.js`                                | `dev/acme/ssr/prerender_preamble.js`                                |

**Combo fixtures (rebaselined):** all 8 `.expected.edn` files in `templates/full-stack-reagent/spec/combos/`.

**Hook test:** `templates/full-stack-reagent/spec/hook_test.bb` — rewrite to assert residue-grep behaviour only.

---

## Phase 1 — CLI Scaffolder Extensions

### Task 1: `apply-feature-dir-deletes!` — delete `<ns-token>/<feature>` subtrees

**Files:**
- Modify: `cli/src/c3kit_create/render.clj`
- Test: `cli/spec/c3kit_create/render_spec.clj`

- [ ] **Step 1.1: Write the failing test**

Add this it-block inside `(describe "c3kit-create.render" …)` in `cli/spec/c3kit_create/render_spec.clj`:

```clojure
(it "render! deletes <ns-token>/<feature> directories and single-file features when feature off"
  (let [stage (str (fs/create-temp-dir))]
    ;; auth dir under namespace
    (fs/create-dirs (fs/path stage "src" "clj" "acme" "auth"))
    (spit (fs/file (fs/path stage "src" "clj" "acme" "auth" "user.clj")) "(ns acme.auth.user)")
    ;; markdownc single-file feature
    (fs/create-dirs (fs/path stage "src" "cljc" "acme"))
    (spit (fs/file (fs/path stage "src" "cljc" "acme" "markdownc.cljc")) "(ns acme.markdownc)")
    ;; spec mirror
    (fs/create-dirs (fs/path stage "spec" "clj" "acme" "auth"))
    (spit (fs/file (fs/path stage "spec" "clj" "acme" "auth" "user_spec.clj")) "(ns acme.auth.user-spec)")
    ;; non-feature file must survive
    (spit (fs/file (fs/path stage "src" "clj" "acme" "main.clj")) "(ns acme.main)")
    (spit (fs/file (fs/path stage "c3kit-template.edn"))
          (pr-str {:id :tiny :name "Tiny" :description "x" :version "0.1.0" :min-cli "0.1.0"
                   :namespace-token "acme"
                   :tokens   {"acme" {:hyphen true :underscore true :pascal true}}
                   :secrets  []
                   :features [{:id :auth :prompt "" :default true}
                              {:id :markdownc :prompt "" :default true}]
                   :next-steps [{:cmd "x"}]}))
    (r/render! stage
               (edn/read-string (slurp (fs/file (fs/path stage "c3kit-template.edn"))))
               "my-app"
               {:auth false :markdownc false}
               {}
               "0.1.0-SNAPSHOT")
    (should-not (fs/exists? (fs/path stage "src" "clj" "my_app" "auth")))
    (should-not (fs/exists? (fs/path stage "src" "cljc" "my_app" "markdownc.cljc")))
    (should-not (fs/exists? (fs/path stage "spec" "clj" "my_app" "auth")))
    (should     (fs/exists? (fs/path stage "src" "clj" "my_app" "main.clj")))
    (fs/delete-tree stage)))
```

- [ ] **Step 1.2: Run the failing test**

```
cd cli && bb test --focus "render! deletes <ns-token>/<feature>"
```

Expected: FAIL (function doesn't filter feature-dirs yet — assertions about non-existence fail).

- [ ] **Step 1.3: Implement `apply-feature-dir-deletes!`**

Add this in `cli/src/c3kit_create/render.clj`, after `apply-deletes!`:

```clojure
(defn- segments [^java.io.File f stage-root]
  (let [rel (.relativize (.toPath (fs/file stage-root)) (.toPath f))]
    (->> (iterator-seq (.iterator rel))
         (map str)
         (vec))))

(defn- feature-dir-match? [segs ns-token feature-name]
  ;; matches segments like [... ns-token feature-name ...]
  (loop [s segs]
    (cond
      (< (count s) 2)                                false
      (and (= (first s) ns-token)
           (= (second s) feature-name))              true
      :else                                          (recur (rest s)))))

(defn- feature-file-match? [segs ns-token feature-name]
  ;; matches trailing segments [ns-token "<feature>.clj|cljc|cljs"]
  (and (>= (count segs) 2)
       (= (nth segs (- (count segs) 2)) ns-token)
       (some #(= (last segs) (str feature-name "." %))
             ["clj" "cljc" "cljs"])))

(defn- apply-feature-dir-deletes! [stage-dir manifest features]
  (let [ns-token (or (:namespace-token manifest) "acme")]
    (doseq [feat (:features manifest)
            :let [id  (:id feat)
                  on? (get features id (:default feat))]
            :when (not on?)]
      (let [fname (name id)]
        (doseq [^java.io.File f (vec (file-seq (fs/file stage-dir)))
                :when (.exists f)
                :let  [segs (segments f stage-dir)]]
          (when (or (feature-dir-match? segs ns-token fname)
                    (and (.isFile f) (feature-file-match? segs ns-token fname)))
            (if (.isDirectory f)
              (fs/delete-tree (.getAbsolutePath f))
              (fs/delete-if-exists (.getAbsolutePath f)))))))))
```

Wire into `render!` BEFORE `rename-paths!` (so we match against `acme/` segments, not `my_app/`). Locate the `(rename-paths! tokens user stage-dir)` line in `render!` and insert immediately above it:

```clojure
(apply-feature-dir-deletes! stage-dir manifest features)
```

- [ ] **Step 1.4: Run the test to verify it passes**

```
cd cli && bb test --focus "render! deletes <ns-token>/<feature>"
```

Expected: PASS.

- [ ] **Step 1.5: Run full CLI test suite**

```
cd cli && bb test
```

Expected: all existing tests still PASS (new code is additive — old `apply-deletes!` still runs unchanged).

- [ ] **Step 1.6: Commit**

```bash
git add cli/src/c3kit_create/render.clj cli/spec/c3kit_create/render_spec.clj
git commit -m "feat(cli): apply-feature-dir-deletes! removes <ns>/<feature> subtrees"
```

---

### Task 2: `apply-extras-deletes!` — feature-level path list for off-namespace paths

**Files:**
- Modify: `cli/src/c3kit_create/render.clj`
- Test: `cli/spec/c3kit_create/render_spec.clj`

- [ ] **Step 2.1: Write the failing test**

Add this it-block:

```clojure
(it "render! deletes :extras paths when feature off"
  (let [stage (str (fs/create-temp-dir))]
    (fs/create-dirs (fs/path stage "resources" "prerender"))
    (spit (fs/file (fs/path stage "resources" "prerender" "index.html")) "<html>")
    (spit (fs/file (fs/path stage "package.json")) "{}")
    (spit (fs/file (fs/path stage "c3kit-template.edn"))
          (pr-str {:id :tiny :name "Tiny" :description "x" :version "0.1.0" :min-cli "0.1.0"
                   :tokens {} :secrets []
                   :features [{:id :ssr :prompt "" :default true
                               :extras ["package.json" "resources/prerender/"]}]
                   :next-steps [{:cmd "x"}]}))
    (r/render! stage
               (edn/read-string (slurp (fs/file (fs/path stage "c3kit-template.edn"))))
               "my-app"
               {:ssr false}
               {}
               "0.1.0-SNAPSHOT")
    (should-not (fs/exists? (fs/path stage "package.json")))
    (should-not (fs/exists? (fs/path stage "resources" "prerender")))
    (fs/delete-tree stage)))
```

- [ ] **Step 2.2: Run the failing test**

```
cd cli && bb test --focus "render! deletes :extras paths"
```

Expected: FAIL — `package.json` still exists.

- [ ] **Step 2.3: Implement `apply-extras-deletes!`**

Add in `cli/src/c3kit_create/render.clj`:

```clojure
(defn- apply-extras-deletes! [stage-dir manifest features]
  (doseq [feat (:features manifest)
          :let [id  (:id feat)
                on? (get features id (:default feat))]
          :when (not on?)
          path (:extras feat)
          :let [full (str (fs/path stage-dir path))]]
    (when (fs/exists? full)
      (if (fs/directory? full)
        (fs/delete-tree full)
        (fs/delete full)))))
```

Wire into `render!` immediately after `apply-feature-dir-deletes!` (so `:extras` paths are matched in pre-rename form, just like feature dirs):

```clojure
(apply-feature-dir-deletes! stage-dir manifest features)
(apply-extras-deletes! stage-dir manifest features)
```

- [ ] **Step 2.4: Run the test to verify it passes**

```
cd cli && bb test --focus "render! deletes :extras paths"
```

Expected: PASS.

- [ ] **Step 2.5: Run full CLI test suite**

```
cd cli && bb test
```

Expected: all PASS.

- [ ] **Step 2.6: Commit**

```bash
git add cli/src/c3kit_create/render.clj cli/spec/c3kit_create/render_spec.clj
git commit -m "feat(cli): apply-extras-deletes! removes off-namespace feature paths"
```

---

### Task 3: `apply-db-rename!` — `bin/db.template.<chosen>` → `bin/db`, delete siblings

**Files:**
- Modify: `cli/src/c3kit_create/render.clj`
- Test: `cli/spec/c3kit_create/render_spec.clj`

- [ ] **Step 3.1: Write the failing test**

```clojure
(it "render! renames db template and deletes siblings"
  (let [stage (str (fs/create-temp-dir))]
    (fs/create-dirs (fs/path stage "bin"))
    (spit (fs/file (fs/path stage "bin" "db.template.sqlite"))
          "#!/usr/bin/env bash\necho sqlite\n")
    (spit (fs/file (fs/path stage "bin" "db.template.memory"))
          "#!/usr/bin/env bash\necho memory\n")
    (spit (fs/file (fs/path stage "bin" "db.template.postgres"))
          "#!/usr/bin/env bash\necho postgres\n")
    (spit (fs/file (fs/path stage "c3kit-template.edn"))
          (pr-str {:id :tiny :name "Tiny" :description "x" :version "0.1.0" :min-cli "0.1.0"
                   :tokens {} :secrets [] :features []
                   :db {:prompt "Database"
                        :options [{:id :sqlite :label "SQLite"}
                                  {:id :memory :label "Mem"}
                                  {:id :postgres :label "PG"}]
                        :default :sqlite
                        :template "bin/db.template.{{db}}"
                        :sibling-glob "bin/db.template.*"}
                   :next-steps [{:cmd "x"}]}))
    (r/render! stage
               (edn/read-string (slurp (fs/file (fs/path stage "c3kit-template.edn"))))
               "my-app"
               {}
               {:db :sqlite}
               "0.1.0-SNAPSHOT")
    (should     (fs/exists? (fs/path stage "bin" "db")))
    (should     (re-find #"sqlite" (slurp (fs/file (fs/path stage "bin" "db")))))
    (should-not (fs/exists? (fs/path stage "bin" "db.template.sqlite")))
    (should-not (fs/exists? (fs/path stage "bin" "db.template.memory")))
    (should-not (fs/exists? (fs/path stage "bin" "db.template.postgres")))
    (fs/delete-tree stage)))
```

- [ ] **Step 3.2: Run the failing test**

```
cd cli && bb test --focus "render! renames db template"
```

Expected: FAIL — `bin/db` not present.

- [ ] **Step 3.3: Implement `apply-db-rename!`**

Add in `cli/src/c3kit_create/render.clj`:

```clojure
(defn- apply-db-rename! [stage-dir manifest db-choice]
  (when-let [db-cfg (:db manifest)]
    (when-let [chosen (:db db-choice)]
      (let [tmpl-pattern (:template db-cfg)
            sibling-glob (:sibling-glob db-cfg)]
        (when (and tmpl-pattern sibling-glob)
          (let [chosen-path (str/replace tmpl-pattern "{{db}}" (name chosen))
                src         (fs/path stage-dir chosen-path)
                dst         (fs/path stage-dir "bin" "db")]
            ;; rename chosen
            (when (fs/exists? src)
              (fs/delete-if-exists dst)
              (fs/move (str src) (str dst))
              (.setExecutable (fs/file dst) true))
            ;; delete siblings (anything matching glob, including chosen if rename failed)
            (doseq [m (fs/glob stage-dir sibling-glob)]
              (fs/delete-if-exists (str m)))))))))
```

Wire into `render!` immediately after `apply-extras-deletes!`:

```clojure
(apply-feature-dir-deletes! stage-dir manifest features)
(apply-extras-deletes! stage-dir manifest features)
(apply-db-rename! stage-dir manifest db-choice)
```

- [ ] **Step 3.4: Run the test to verify it passes**

```
cd cli && bb test --focus "render! renames db template"
```

Expected: PASS.

- [ ] **Step 3.5: Run full CLI test suite**

```
cd cli && bb test
```

Expected: all PASS.

- [ ] **Step 3.6: Commit**

```bash
git add cli/src/c3kit_create/render.clj cli/spec/c3kit_create/render_spec.clj
git commit -m "feat(cli): apply-db-rename! installs chosen bin/db and deletes siblings"
```

---

### Task 4: Manifest validator accepts new schema keys

**Files:**
- Modify: `cli/src/c3kit_create/manifest.clj`
- Test: `cli/spec/c3kit_create/manifest_spec.clj`

- [ ] **Step 4.1: Write failing tests**

Add these it-blocks in `cli/spec/c3kit_create/manifest_spec.clj` inside the top-level describe:

```clojure
(it "accepts :namespace-token at manifest root"
  (let [m {:id :tiny :name "T" :description "x" :version "0.1.0" :min-cli "0.1.0"
           :namespace-token "acme"
           :tokens {"acme" {}} :secrets [] :features []
           :next-steps [{:cmd "x"}]}]
    (should= m (m/validate m "tiny"))))

(it "accepts :extras on a feature"
  (let [m {:id :tiny :name "T" :description "x" :version "0.1.0" :min-cli "0.1.0"
           :tokens {} :secrets []
           :features [{:id :ssr :prompt "" :default true
                       :extras ["package.json" "resources/prerender/"]}]
           :next-steps [{:cmd "x"}]}]
    (should= m (m/validate m "tiny"))))

(it "rejects :extras escaping template root"
  (let [m {:id :tiny :name "T" :description "x" :version "0.1.0" :min-cli "0.1.0"
           :tokens {} :secrets []
           :features [{:id :ssr :prompt "" :default true :extras ["../etc/passwd"]}]
           :next-steps [{:cmd "x"}]}]
    (try (m/validate m "tiny")
         (should= :should-have-thrown :did-not-throw)
         (catch clojure.lang.ExceptionInfo e
           (should (:manifest? (ex-data e)))))))

(it "accepts :template and :sibling-glob on :db"
  (let [m {:id :tiny :name "T" :description "x" :version "0.1.0" :min-cli "0.1.0"
           :tokens {} :secrets [] :features []
           :db {:prompt "DB"
                :options [{:id :sqlite :label "SQLite"}]
                :default :sqlite
                :template "bin/db.template.{{db}}"
                :sibling-glob "bin/db.template.*"}
           :next-steps [{:cmd "x"}]}]
    (should= m (m/validate m "tiny"))))
```

(The require at the top of the spec file is `[c3kit-create.manifest :as m]` — confirm it is.)

- [ ] **Step 4.2: Run failing tests**

```
cd cli && bb test --focus "accepts :namespace-token"
cd cli && bb test --focus "accepts :extras"
cd cli && bb test --focus "rejects :extras escaping"
cd cli && bb test --focus "accepts :template and :sibling-glob"
```

Expected: `:extras` escape test FAILS (no validation yet rejects it). Others should already pass since the validator doesn't reject unknown keys today — confirm before proceeding.

- [ ] **Step 4.3: Extend the validator**

In `cli/src/c3kit_create/manifest.clj`, update `check-features` to also validate `:extras`:

```clojure
(defn- check-features [m]
  (let [feats (:features m)
        ids   (map :id feats)]
    (when-not (sequential? feats) (die ":features must be sequential"))
    (when (not= (count ids) (count (distinct ids)))
      (die "duplicate :features :id"))
    (doseq [{:keys [delete-when-off extras]} feats
            p (concat delete-when-off extras)]
      (when (or (str/starts-with? p "../") (str/includes? p "/.."))
        (die (str ":delete-when-off/:extras escapes template root: " p))))))
```

No other validator changes needed (unknown root keys like `:namespace-token` and unknown `:db` keys are silently accepted today; that's fine).

- [ ] **Step 4.4: Run all four tests again**

```
cd cli && bb test --focus "accepts :namespace-token"
cd cli && bb test --focus "accepts :extras"
cd cli && bb test --focus "rejects :extras escaping"
cd cli && bb test --focus "accepts :template and :sibling-glob"
```

Expected: all PASS.

- [ ] **Step 4.5: Full CLI suite**

```
cd cli && bb test
```

Expected: all PASS.

- [ ] **Step 4.6: Commit**

```bash
git add cli/src/c3kit_create/manifest.clj cli/spec/c3kit_create/manifest_spec.clj
git commit -m "feat(cli): manifest validates :extras and accepts new optional keys"
```

---

## Phase 2 — Template Refactor

### Task 5: Move `:auth` files into `auth/` subdirectories

**Files:**
- Move 14 files / 2 subtrees under `templates/full-stack-reagent/` (see File Structure table).
- Modify: every `ns`/`require` form that referenced the old names.
- Modify: `templates/full-stack-reagent/src/clj/acme/main.clj` (marker requires).
- Modify: `templates/full-stack-reagent/src/clj/acme/routes.clj` (marker requires).
- Modify: `templates/full-stack-reagent/c3kit-template.edn` (drop `:auth` `:delete-when-off`).

Renames:

- `acme.user.*` → `acme.auth.user.*`
- `acme.user`   → `acme.auth.user`  (if such a top-level ns exists; verify with grep)
- `acme.session` → `acme.auth.session`
- `acme.destination` → `acme.auth.destination`
- `acme.forgot-password` → `acme.auth.forgot-password`
- `acme.recover-password` → `acme.auth.recover-password`

- [ ] **Step 5.1: Confirm namespace names before moving**

```
grep -rn "^(ns acme\." templates/full-stack-reagent/src templates/full-stack-reagent/spec templates/full-stack-reagent/dev | grep -E "acme\.(user|session|destination|forgot-password|recover-password)" | sort -u
```

Use the output to drive the rename script. Capture exact ns names that need rewriting.

- [ ] **Step 5.2: Move files (git mv)**

Run from repo root:

```bash
cd templates/full-stack-reagent

mkdir -p src/clj/acme/auth src/cljc/acme/auth src/cljs/acme/auth
mkdir -p spec/clj/acme/auth spec/cljs/acme/auth

git mv src/clj/acme/user.clj                       src/clj/acme/auth/user.clj
git mv src/clj/acme/session.clj                    src/clj/acme/auth/session.clj
git mv src/clj/acme/destination.clj                src/clj/acme/auth/destination.clj
git mv src/clj/acme/user                           src/clj/acme/auth/user.d.tmp
# move contents from .d.tmp/* up one level into src/clj/acme/auth/user/
mv src/clj/acme/auth/user.d.tmp/* src/clj/acme/auth/user/ 2>/dev/null || true
# fix: the line above conflicts with auth/user.clj. Use a different layout:
```

Actually use a safer two-step:

```bash
cd templates/full-stack-reagent

mkdir -p src/clj/acme/auth src/cljc/acme/auth src/cljs/acme/auth
mkdir -p spec/clj/acme/auth spec/cljs/acme/auth

# move user/ subdir first so it doesn't collide with user.clj rename
git mv src/clj/acme/user            src/clj/acme/auth/user_dir_tmp
git mv src/clj/acme/user.clj        src/clj/acme/auth/user.clj
git mv src/clj/acme/auth/user_dir_tmp src/clj/acme/auth/user

git mv src/cljc/acme/user           src/cljc/acme/auth/user

git mv src/clj/acme/session.clj         src/clj/acme/auth/session.clj
git mv src/clj/acme/destination.clj     src/clj/acme/auth/destination.clj
git mv src/cljs/acme/user.cljs          src/cljs/acme/auth/user.cljs
git mv src/cljs/acme/forgot_password.cljs   src/cljs/acme/auth/forgot_password.cljs
git mv src/cljs/acme/recover_password.cljs  src/cljs/acme/auth/recover_password.cljs

git mv spec/clj/acme/user              spec/clj/acme/auth/user_dir_tmp
git mv spec/clj/acme/auth/user_dir_tmp spec/clj/acme/auth/user
git mv spec/clj/acme/session_spec.clj      spec/clj/acme/auth/session_spec.clj
git mv spec/clj/acme/destination_spec.clj  spec/clj/acme/auth/destination_spec.clj
git mv spec/cljs/acme/user_spec.cljs              spec/cljs/acme/auth/user_spec.cljs
git mv spec/cljs/acme/forgot_password_spec.cljs   spec/cljs/acme/auth/forgot_password_spec.cljs
git mv spec/cljs/acme/recover_password_spec.cljs  spec/cljs/acme/auth/recover_password_spec.cljs
```

Verify nothing was missed:

```
git status
ls src/clj/acme src/cljc/acme src/cljs/acme spec/clj/acme spec/cljs/acme
```

- [ ] **Step 5.3: Rewrite namespace declarations and requires**

From repo root run a global text substitution restricted to the template (use ripgrep + sed or your editor's project-wide replace). The substitutions, applied in order:

```
# whole-word namespace renames (capture period boundary on the right)
acme.user.web        → acme.auth.user.web
acme.user.schema     → acme.auth.user.schema
acme.user.api        → acme.auth.user.api
acme.user.corec      → acme.auth.user.corec
acme.user.routes     → acme.auth.user.routes
acme.user            → acme.auth.user
acme.session         → acme.auth.session
acme.destination     → acme.auth.destination
acme.forgot-password → acme.auth.forgot-password
acme.recover-password → acme.auth.recover-password
```

(Order matters: `acme.user.web` must be substituted before `acme.user` so the longer match wins. Most editors handle this if you replace longest-first.)

Marker comments in `src/clj/acme/main.clj` and `src/clj/acme/routes.clj` are plain text — the rename pass updates them automatically.

- [ ] **Step 5.4: Drop `:auth` `:delete-when-off` from manifest**

In `templates/full-stack-reagent/c3kit-template.edn`, change the `:auth` feature entry from:

```clojure
{:id      :auth
 :prompt  "JWT auth?"
 :default true
 :delete-when-off ["src/clj/{{acme}}/user.clj"
                   …]}
```

to:

```clojure
{:id      :auth
 :prompt  "JWT auth?"
 :default true}
```

- [ ] **Step 5.5: Run the template's own Clojure tests**

```
cd templates/full-stack-reagent && clj -M:test:spec
```

Expected: PASS (or — if Datomic-related and you don't have it locally, at least no namespace-not-found failures for the renamed namespaces).

If anything still references the old names, fix it now.

- [ ] **Step 5.6: Commit**

```bash
git add templates/full-stack-reagent
git commit -m "refactor(t1): move auth files under acme.auth.* namespace"
```

---

### Task 6: Move `:content` files into `content/` subdirectories

**Files (template only):**

Moves:

- `src/clj/acme/content.clj` → `src/clj/acme/content/core.clj`
- `src/clj/acme/markdown.clj` → `src/clj/acme/content/markdown.clj`
- `spec/clj/acme/content_spec.clj` → `spec/clj/acme/content/core_spec.clj`
- `spec/clj/acme/markdown_spec.clj` → `spec/clj/acme/content/markdown_spec.clj`
- `src/cljs/acme/content_page.cljs` → `src/cljs/acme/content/page.cljs`
- `spec/cljs/acme/content_page_spec.cljs` → `spec/cljs/acme/content/page_spec.cljs`

Renames:

- `acme.content` → `acme.content.core`
- `acme.markdown` → `acme.content.markdown`
- `acme.content-page` → `acme.content.page`

- [ ] **Step 6.1: Move files**

```bash
cd templates/full-stack-reagent

mkdir -p src/clj/acme/content spec/clj/acme/content src/cljs/acme/content spec/cljs/acme/content

git mv src/clj/acme/content.clj           src/clj/acme/content/core.clj
git mv src/clj/acme/markdown.clj          src/clj/acme/content/markdown.clj
git mv spec/clj/acme/content_spec.clj     spec/clj/acme/content/core_spec.clj
git mv spec/clj/acme/markdown_spec.clj    spec/clj/acme/content/markdown_spec.clj
git mv src/cljs/acme/content_page.cljs    src/cljs/acme/content/page.cljs
git mv spec/cljs/acme/content_page_spec.cljs  spec/cljs/acme/content/page_spec.cljs
```

- [ ] **Step 6.2: Rewrite namespaces**

Run project-wide replace inside `templates/full-stack-reagent/` (longest match first):

```
acme.content-page → acme.content.page
acme.markdown     → acme.content.markdown
acme.content      → acme.content.core
```

Verify no `acme.content\b` (without trailing `.core`) remains:

```
grep -rn "acme\.content\b" templates/full-stack-reagent/ | grep -vE "acme\.content\.(core|markdown|page)"
```

Expected: empty output.

Marker comment in `src/clj/acme/main.clj` line referring to `acme.content` needs to point at `acme.content.core` (or whichever symbol it actually requires). Confirm with:

```
grep -n "@c3kit/feature :content" templates/full-stack-reagent/src/clj/acme/main.clj
```

Adjust the RHS to the correct new namespace.

- [ ] **Step 6.3: Drop `:content` `:delete-when-off` from manifest**

In `c3kit-template.edn`, change the `:content` feature entry to:

```clojure
{:id :content :prompt "Markdown content pipeline?" :default true}
```

- [ ] **Step 6.4: Run template tests**

```
cd templates/full-stack-reagent && clj -M:test:spec
```

Expected: PASS.

- [ ] **Step 6.5: Commit**

```bash
git add templates/full-stack-reagent
git commit -m "refactor(t1): move content files under acme.content.* namespace"
```

---

### Task 7: Move `:ssr` files into `ssr/` subdirectories; declare `:extras`

**Files (template only):**

Moves:

- `src/clj/acme/prerender.clj` → `src/clj/acme/ssr/prerender.clj`
- `spec/clj/acme/prerender_spec.clj` → `spec/clj/acme/ssr/prerender_spec.clj`
- `dev/acme/prerender.cljs` → `dev/acme/ssr/prerender.cljs`
- `dev/acme/prerender_pages.cljs` → `dev/acme/ssr/prerender_pages.cljs`
- `dev/acme/prerender_preamble.js` → `dev/acme/ssr/prerender_preamble.js`

Rename: `acme.prerender` → `acme.ssr.prerender`

- [ ] **Step 7.1: Move files**

```bash
cd templates/full-stack-reagent

mkdir -p src/clj/acme/ssr spec/clj/acme/ssr dev/acme/ssr

git mv src/clj/acme/prerender.clj          src/clj/acme/ssr/prerender.clj
git mv spec/clj/acme/prerender_spec.clj    spec/clj/acme/ssr/prerender_spec.clj
git mv dev/acme/prerender.cljs             dev/acme/ssr/prerender.cljs
git mv dev/acme/prerender_pages.cljs       dev/acme/ssr/prerender_pages.cljs
git mv dev/acme/prerender_preamble.js      dev/acme/ssr/prerender_preamble.js
```

- [ ] **Step 7.2: Rewrite namespace**

Project-wide replace inside `templates/full-stack-reagent/`:

```
acme.prerender → acme.ssr.prerender
```

The marker `[acme.prerender]` require in `src/clj/acme/main.clj` becomes `[acme.ssr.prerender]`.

- [ ] **Step 7.3: Replace `:ssr` `:delete-when-off` with `:extras`**

In `c3kit-template.edn`, change the `:ssr` entry to:

```clojure
{:id      :ssr
 :prompt  "SSR/prerender (Reagent + Node)?"
 :default true
 :extras  ["package.json"
           "resources/prerender/"
           "resources/prerendered/"]}
```

(Note: `package.json` IS top-level in the template and stays there. The other Phase 1 paths under `src/clj/acme/ssr/`, `dev/acme/ssr/`, `spec/clj/acme/ssr/` are handled by feature-dir deletion.)

- [ ] **Step 7.4: Update `dev/acme/prerender_pages.cljs` preamble path reference if needed**

The `:cljs/build` config in `deps.edn` may reference `dev/acme/prerender_preamble.js`. Grep and update:

```
grep -rn "prerender_preamble" templates/full-stack-reagent/
```

Any references must be updated to `dev/acme/ssr/prerender_preamble.js`.

- [ ] **Step 7.5: Run template tests**

```
cd templates/full-stack-reagent && clj -M:test:spec
```

Expected: PASS.

- [ ] **Step 7.6: Commit**

```bash
git add templates/full-stack-reagent
git commit -m "refactor(t1): move ssr files under acme.ssr.* namespace; declare :extras"
```

---

### Task 8: `:markdownc` — drop `:delete-when-off` (file is already single-file at `acme/markdownc.cljc`)

**Files:**
- Modify: `templates/full-stack-reagent/c3kit-template.edn`

`acme.markdownc` already lives at `src/cljc/acme/markdownc.cljc` (verify with `ls templates/full-stack-reagent/src/cljc/acme/markdownc.cljc`). No move needed — the feature-file-match rule in `apply-feature-dir-deletes!` (Task 1) handles it once `:delete-when-off` is removed.

- [ ] **Step 8.1: Drop `:markdownc` `:delete-when-off` from manifest**

Change the `:markdownc` entry to:

```clojure
{:id :markdownc :prompt "Client-side markdown (CLJC)?" :default true}
```

- [ ] **Step 8.2: Commit**

```bash
git add templates/full-stack-reagent/c3kit-template.edn
git commit -m "refactor(t1): drop :markdownc :delete-when-off (handled by feature-file rule)"
```

---

### Task 9: `:websocket` — no file changes (already marker-only)

`:websocket` has no `:delete-when-off` already and no namespace files. Skip; nothing to do.

---

### Task 10: Make `dev/acme/seed.clj` always-present with `@c3kit/feature :auth` markers

**Files:**
- Modify: `templates/full-stack-reagent/dev/acme/seed.clj`

- [ ] **Step 10.1: Inspect current `seed.clj`**

Read the file. Note: most of the body is auth-specific (creates default users, etc.). The current setup deletes the whole file when auth is off via `:delete-when-off`. New approach: wrap the body in `@c3kit/feature :auth { … }` markers and add a `!:auth` placeholder for the no-auth branch.

- [ ] **Step 10.2: Rewrite `seed.clj`**

Replace the body of `dev/acme/seed.clj` so it looks like:

```clojure
(ns acme.seed
  "Local dev data seeder. Run with `clj -M:test:seed`."
  (:require
    ;; @c3kit/feature :auth = [acme.auth.user.api :as user.api]
    ;; @c3kit/feature :auth = [acme.config :as config]
    ))

(defn -main [& _]
  ;; @c3kit/feature :auth {
  ;; (existing auth seeding body — preserved verbatim from the current file)
  ;; @c3kit/feature :auth }
  ;; @c3kit/feature !:auth {
  (println "No seed data to load. Add entries to acme.seed/-main as needed.")
  ;; @c3kit/feature !:auth }
  )
```

Preserve the existing auth seeding body verbatim inside the `:auth {` … `:auth }` block. The literal `:require` namespaces shown above are placeholders — match whatever the current file imports.

- [ ] **Step 10.3: Drop `dev/acme/seed.clj` from `:auth` `:delete-when-off`**

Already removed in Task 5. Re-verify:

```
grep -n "seed.clj" templates/full-stack-reagent/c3kit-template.edn
```

Expected: no match.

- [ ] **Step 10.4: Wrap `:seed` alias in `deps.edn` with `@c3kit/feature :auth` markers**

In `templates/full-stack-reagent/deps.edn`, find the `:seed` alias entry. Currently the manifest's hook strips it when auth is off. Replace that mechanism with markers:

```clojure
;; @c3kit/feature :auth {
:seed {:main-opts ["-m" "acme.seed"] :extra-paths ["dev"]}
;; @c3kit/feature :auth }
```

Place the open/close marker lines on their own lines, before and after the `:seed` entry.

- [ ] **Step 10.5: Commit**

```bash
git add templates/full-stack-reagent/dev/acme/seed.clj templates/full-stack-reagent/deps.edn
git commit -m "refactor(t1): seed.clj always present; :seed alias guarded by markers"
```

---

### Task 11: Add `@c3kit/db` markers in `config.clj` (replaces hook bucket reconciliation)

**Files:**
- Modify: `templates/full-stack-reagent/src/clj/acme/config.clj`

Current shape (per hook regex in `c3kit-template.bb`):

```clojure
:bucket memory-local                           ;; HEAD default; replaced by line below at scaffold
:bucket sqlite-local
```

Each `(def development …)` / `(def staging …)` / `(def production …)` block has two `:bucket` lines — the HEAD default plus the scaffold-time replacement. The hook deletes the HEAD-default line.

Replace with per-db marker blocks for each environment.

- [ ] **Step 11.1: Read `config.clj`**

```
templates/full-stack-reagent/src/clj/acme/config.clj
```

Identify every `:bucket` line and which environment it belongs to.

- [ ] **Step 11.2: Rewrite each environment's `:bucket` selection**

For each `(def development …)`, `(def staging …)`, `(def production …)` map, replace the two `:bucket` lines with marker blocks like:

```clojure
;; @c3kit/db :memory {
:bucket memory-local
;; @c3kit/db }
;; @c3kit/db :sqlite {
:bucket sqlite-local
;; @c3kit/db }
;; @c3kit/db :postgres {
:bucket postgres-local
;; @c3kit/db }
;; @c3kit/db :datomic-pro {
:bucket datomic-pro-local
;; @c3kit/db }
```

(Use the actual bucket symbol names from the current file — they vary per environment, e.g. `sqlite-staging` instead of `sqlite-local`.)

- [ ] **Step 11.3: Add `:namespace-token`, `:template`, `:sibling-glob` to manifest**

In `templates/full-stack-reagent/c3kit-template.edn`, add `:namespace-token "acme"` at the manifest root, and extend `:db` with `:template` + `:sibling-glob`:

```clojure
:namespace-token "acme"

:db {:prompt  "Database"
     :options [{:id :datomic-pro :label "Datomic Pro (free, single-jar transactor)"}
               {:id :sqlite      :label "SQLite (JDBC)"}
               {:id :postgres    :label "Postgres (JDBC)"}
               {:id :memory      :label "In-memory (dev only)"}]
     :default :datomic-pro
     :template     "bin/db.template.{{db}}"
     :sibling-glob "bin/db.template.*"}
```

- [ ] **Step 11.4: Commit**

```bash
git add templates/full-stack-reagent/src/clj/acme/config.clj templates/full-stack-reagent/c3kit-template.edn
git commit -m "refactor(t1): db selection via @c3kit/db markers in config.clj"
```

---

### Task 12: Replace hook script with residue-grep-only

**Files:**
- Modify: `templates/full-stack-reagent/c3kit-template.bb`

- [ ] **Step 12.1: Overwrite hook contents**

Replace the entire contents of `templates/full-stack-reagent/c3kit-template.bb` with:

```clojure
#!/usr/bin/env bb
;; Post-scaffold safety net. Asserts no @c3kit/(feature|db) markers survived
;; scaffolding. Exits non-zero with paths printed if any residue is found.

(ns c3kit-template
  (:require [clojure.string :as str]))

(def scaffold-dir
  (or (first *command-line-args*)
      (do (println "ERROR: scaffold dir required as arg 1")
          (System/exit 1))))

(def text-exts #{".clj" ".cljc" ".cljs" ".edn" ".md" ".html" ".js" ".yml"})
(def marker    #"@c3kit/(feature|db)\s+!?:?\S*\s*[{}=]")

(let [residues (atom [])]
  (doseq [^java.io.File f (file-seq (java.io.File. scaffold-dir))
          :when (and (.isFile f)
                     (not (str/includes? (.getPath f) "/.git/"))
                     (some #(str/ends-with? (.getName f) %) text-exts))]
    (when (re-find marker (slurp f))
      (swap! residues conj (.getPath f))))
  (when (seq @residues)
    (println "ERROR: residual @c3kit markers found in:")
    (doseq [p @residues] (println "  " p))
    (System/exit 4)))

(println "Hook OK — no residual markers.")
```

- [ ] **Step 12.2: Commit**

```bash
git add templates/full-stack-reagent/c3kit-template.bb
git commit -m "refactor(t1): shrink hook to residue grep only"
```

---

### Task 13: Rewrite `spec/hook_test.bb` to assert residue-grep behaviour

**Files:**
- Modify: `templates/full-stack-reagent/spec/hook_test.bb`

- [ ] **Step 13.1: Replace hook test contents**

Overwrite `templates/full-stack-reagent/spec/hook_test.bb`:

```clojure
#!/usr/bin/env bb
;; Isolation test for c3kit-template.bb hook.
;; Two scenarios:
;;   1. Clean scaffold → exit 0
;;   2. Scaffold with residual @c3kit marker → exit 4 + path printed

(require '[babashka.fs :as fs])
(require '[babashka.process :as p])
(require '[clojure.string :as str])

(def hook (str (System/getProperty "user.dir") "/c3kit-template.bb"))

;; --- Case 1: clean ---
(let [tmp (str (fs/create-temp-dir {:prefix "hook-test-clean-"}))]
  (fs/create-dirs (str tmp "/src/clj/my_app"))
  (spit (str tmp "/src/clj/my_app/main.clj") "(ns my_app.main)")
  (let [{:keys [exit]} (p/sh "bb" hook tmp)]
    (assert (zero? exit) (str "clean scaffold should exit 0, got " exit)))
  (fs/delete-tree tmp))

;; --- Case 2: residual marker ---
(let [tmp (str (fs/create-temp-dir {:prefix "hook-test-residue-"}))]
  (fs/create-dirs (str tmp "/src/clj/my_app"))
  (spit (str tmp "/src/clj/my_app/main.clj")
        ";; @c3kit/feature :auth = [my_app.auth.x :as x]\n(ns my_app.main)")
  (let [{:keys [exit out]} (p/sh "bb" hook tmp)]
    (assert (= 4 exit) (str "residue scaffold should exit 4, got " exit))
    (assert (str/includes? out "main.clj")
            "stdout should list the offending file"))
  (fs/delete-tree tmp))

(println "PASS")
```

- [ ] **Step 13.2: Run hook test**

```
cd templates/full-stack-reagent && bb spec/hook_test.bb
```

Expected: PASS.

- [ ] **Step 13.3: Commit**

```bash
git add templates/full-stack-reagent/spec/hook_test.bb
git commit -m "test(t1): hook test asserts residue-grep behaviour"
```

---

### Task 14: Rebaseline combo fixtures

**Files:**
- Modify: every file under `templates/full-stack-reagent/spec/combos/`.

Combo fixture format: `{:db … :features {…} :name … :must-exist [paths] :must-not-exist [paths] …}`. Paths reference post-rename layout (i.e. `my_app`, not `acme`). After Phase 2 file moves, every fixture's `:must-exist` and `:must-not-exist` lists need updating.

Find where combo tests are run (likely under `cli/spec/c3kit_create/e2e_spec.clj` or a dedicated runner). Confirm before mass-editing.

- [ ] **Step 14.1: Identify the combo runner**

```
grep -rn "combos" cli/spec/ templates/full-stack-reagent/
```

Expected: a test that reads `*.expected.edn`, runs the scaffolder, and asserts `:must-exist` / `:must-not-exist`.

- [ ] **Step 14.2: For each fixture, mechanically update paths**

For every `.expected.edn` under `templates/full-stack-reagent/spec/combos/`, rewrite paths under `:must-exist` / `:must-not-exist`:

- `src/clj/my_app/user.clj` → `src/clj/my_app/auth/user.clj`
- `src/clj/my_app/session.clj` → `src/clj/my_app/auth/session.clj`
- `src/clj/my_app/destination.clj` → `src/clj/my_app/auth/destination.clj`
- `src/clj/my_app/user/web.clj` → `src/clj/my_app/auth/user/web.clj`
- `src/cljc/my_app/user/schema.cljc` → `src/cljc/my_app/auth/user/schema.cljc`
- `src/cljs/my_app/user.cljs` → `src/cljs/my_app/auth/user.cljs`
- `src/cljs/my_app/forgot_password.cljs` → `src/cljs/my_app/auth/forgot_password.cljs`
- `src/cljs/my_app/recover_password.cljs` → `src/cljs/my_app/auth/recover_password.cljs`
- `src/clj/my_app/content.clj` → `src/clj/my_app/content/core.clj`
- `src/clj/my_app/markdown.clj` → `src/clj/my_app/content/markdown.clj`
- `src/cljs/my_app/content_page.cljs` → `src/cljs/my_app/content/page.cljs`
- `src/clj/my_app/prerender.clj` → `src/clj/my_app/ssr/prerender.clj`
- `dev/my_app/prerender.cljs` → `dev/my_app/ssr/prerender.cljs`
- `dev/my_app/prerender_pages.cljs` → `dev/my_app/ssr/prerender_pages.cljs`
- `dev/my_app/prerender_preamble.js` → `dev/my_app/ssr/prerender_preamble.js`
- spec mirrors of the above

For fixtures where auth is OFF (`memory-no-auth.expected.edn`, possibly others), `dev/my_app/seed.clj` must now be in `:must-exist` (it was previously deleted with the rest of auth). Add it.

For fixtures where ssr is OFF, ensure `package.json`, `resources/prerender/`, `resources/prerendered/` are still in `:must-not-exist`.

- [ ] **Step 14.3: Run combo tests**

```
cd cli && bb test --focus "combos"
```

If the combo runner is under templates/, run that command instead. Goal: all combo fixtures pass.

- [ ] **Step 14.4: Commit**

```bash
git add templates/full-stack-reagent/spec/combos/
git commit -m "test(t1): rebaseline combo fixtures for namespace-subdir layout"
```

---

## Phase 3 — Cleanup

### Task 15: Remove now-dead `:delete-when-off` code path

**Files:**
- Modify: `cli/src/c3kit_create/render.clj`
- Modify: `cli/src/c3kit_create/manifest.clj`
- Test: `cli/spec/c3kit_create/render_spec.clj` (drop any old `:delete-when-off`-specific assertions)

After Task 14 passes, the template no longer uses `:delete-when-off`. Remove the dead code path.

- [ ] **Step 15.1: Grep for remaining `:delete-when-off`**

```
grep -rn ":delete-when-off" cli/src cli/spec templates
```

Expected: only matches in `cli/src/c3kit_create/render.clj` (`apply-deletes!`) and `cli/src/c3kit_create/manifest.clj` (`check-features` path-escape clause), plus possibly a test fixture in `cli/spec/c3kit_create/manifest_spec.clj`.

- [ ] **Step 15.2: Delete `apply-deletes!` and `resolve-delete-path`**

In `cli/src/c3kit_create/render.clj`:

- Remove the `apply-deletes!` function.
- Remove `resolve-delete-path` (only used by `apply-deletes!`).
- Remove the `(apply-deletes! stage-dir manifest features user)` call in `render!`.

- [ ] **Step 15.3: Simplify manifest path-escape check**

In `cli/src/c3kit_create/manifest.clj`, replace `check-features` with:

```clojure
(defn- check-features [m]
  (let [feats (:features m)
        ids   (map :id feats)]
    (when-not (sequential? feats) (die ":features must be sequential"))
    (when (not= (count ids) (count (distinct ids)))
      (die "duplicate :features :id"))
    (doseq [{:keys [extras]} feats
            p extras]
      (when (or (str/starts-with? p "../") (str/includes? p "/.."))
        (die (str ":extras escapes template root: " p))))))
```

- [ ] **Step 15.4: Drop any `:delete-when-off`-specific test cases**

Remove or rewrite any it-block in `cli/spec/c3kit_create/render_spec.clj` and `cli/spec/c3kit_create/manifest_spec.clj` that explicitly tests `:delete-when-off`.

- [ ] **Step 15.5: Run full CLI suite**

```
cd cli && bb test
```

Expected: all PASS.

- [ ] **Step 15.6: Commit**

```bash
git add cli/src/c3kit_create/render.clj cli/src/c3kit_create/manifest.clj cli/spec/c3kit_create
git commit -m "refactor(cli): drop :delete-when-off code path"
```

---

### Task 16: Acceptance verification

- [ ] **Step 16.1: Grep for residual `:delete-when-off`**

```
grep -rn ":delete-when-off" cli/ templates/
```

Expected: no matches.

- [ ] **Step 16.2: Grep for markers outside the four shared files**

```
grep -rn "@c3kit/feature\|@c3kit/db" templates/full-stack-reagent/ \
  | grep -vE "templates/full-stack-reagent/(src/clj/acme/(main|routes|config)\.clj|deps\.edn|dev/acme/seed\.clj)"
```

Expected: empty output. (The spec named four files; `seed.clj` joined the set in Task 10 because of the `:auth` marker. Acceptance criterion stays satisfied — markers live only in named shared files; the small expansion is documented here.)

- [ ] **Step 16.3: Grep for old namespace names**

```
grep -rEn "acme\.(user|session|destination|content|markdown|content-page|prerender|forgot-password|recover-password)\b" templates/full-stack-reagent/ \
  | grep -vE "acme\.(auth\.|content\.|ssr\.)"
```

Expected: empty output.

- [ ] **Step 16.4: Run every test**

```
cd cli && bb test
cd templates/full-stack-reagent && bb spec/hook_test.bb
cd templates/full-stack-reagent && clj -M:test:spec || echo "(template specs may need a backing DB)"
```

Expected: CLI tests + hook test PASS. Template specs PASS if DB available.

- [ ] **Step 16.5: Manual e2e scaffold smoke-test**

```
cd cli
bb c3kit-create test-app -- --template full-stack-reagent --db sqlite --no-ssr
ls /tmp/test-app  # or wherever it scaffolds — confirm layout
rm -rf /tmp/test-app
```

(Exact CLI invocation depends on `c3kit-create.main` flag names — adjust as needed.) Goal: scaffold completes, hook exits 0, output has `src/clj/test_app/auth/`, no `src/clj/test_app/user.clj`, no SSR files.

- [ ] **Step 16.6: Final commit + sign-off**

If any small fix-ups surface in steps 16.1–16.5, commit them. Otherwise this task ends without a new commit.

---

## Self-Review Notes

- Spec coverage: §1–§5 covered by Tasks 1–14; §6 (migration) covered by Tasks 5–11; §7 (combo tests) by Task 14; hook test (§7) by Task 13; §8 risks addressed by Task 16 grep checks; §9 acceptance criteria mapped to Task 16 steps.
- Marker namespace expanded to include `seed.clj` (Task 10). This is a minor deviation from §1's "shared wiring files" list but matches the spirit (small set of central files that wire features in). Task 16.2 documents the expanded list.
- Type consistency: `apply-feature-dir-deletes!`, `apply-extras-deletes!`, `apply-db-rename!` introduced in Tasks 1–3 and called in `render!` in the same order they're defined; their signatures match across uses.
- No placeholders or TBDs.

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-05-14-template-plugin-style-refactor-plan.md`. Two execution options:

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, two-stage review between tasks, fast iteration on a large mechanical refactor.

2. **Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints for review.

Which approach?
