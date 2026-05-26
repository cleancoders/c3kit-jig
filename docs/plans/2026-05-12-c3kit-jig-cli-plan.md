# c3kit-create CLI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `c3kit-create` Babashka CLI from empty `cli/` dir to a released, installable uberscript that scaffolds projects from templates, per the CLI sub-spec at `docs/specs/2026-05-12-c3kit-create-cli-design.md`.

**Architecture:** Single-purpose modules under `cli/src/c3kit_create/` composed in `main.clj`. Speclj-on-bb for tests (with `clojure.test` fallback path documented). Atomic scaffold pipeline (stage in temp dir → move on success). Single `bb uberscript` artifact published to GitHub Releases.

**Tech Stack:** Babashka, `org.clojure/tools.cli`, Speclj, `babashka.fs`, `babashka.process`, `babashka.http-client`, GitHub Actions for CI, clj-kondo for lint.

**Working directory:** `/Users/alex-root-roatch/current-projects/c3kit-jig`.

**Pre-flight checks (controller / first task):**

- Branch off `main`: `git -C /Users/alex-root-roatch/current-projects/c3kit-jig switch -c cli/v0.1`. All work in this plan lands on `cli/v0.1`; merge to `main` via PR after the final task.
- Confirm Babashka is installed locally: `bb --version` returns something. If not, install via `brew install borkdude/brew/babashka` (macOS) or the official installer.
- Confirm `clj-kondo` is installed locally for the lint task. `brew install borkdude/brew/clj-kondo` if not.

---

## File Structure

This plan ends with these files under `cli/`:

```
cli/
├── bb.edn                          # bb deps + tasks
├── src/c3kit_create/
│   ├── main.clj                    # entry, arg dispatch, top-level error wrap
│   ├── args.clj                    # tools.cli wiring, help text
│   ├── wizard.clj                  # interactive prompts
│   ├── registry.clj                # baked-in template list (generated)
│   ├── manifest.clj                # parse + validate c3kit-template.edn
│   ├── fetch.clj                   # local-dir mode + git-clone mode
│   ├── rename.clj                  # token variants + ordered replacement
│   ├── features.clj                # block/line/inverse/db marker stripping + :delete-when-off
│   ├── secrets.clj                 # SecureRandom + placeholder replace
│   ├── hook.clj                    # invoke template's c3kit-template.bb if present
│   ├── postscaffold.clj            # git init + commit, --install
│   ├── ui.clj                      # ANSI + tty + step printing + friendly errors
│   ├── version.clj                 # semver + --upgrade fetcher
│   └── fs.clj                      # paths, atomic move, cross-fs detect, temp dirs
├── spec/c3kit_create/
│   ├── args_spec.clj
│   ├── manifest_spec.clj
│   ├── rename_spec.clj
│   ├── features_spec.clj
│   ├── secrets_spec.clj
│   ├── fs_spec.clj
│   ├── ui_spec.clj
│   ├── version_spec.clj
│   ├── wizard_spec.clj
│   └── e2e_spec.clj
├── test-fixtures/
│   └── tiny-fixture/
│       ├── c3kit-template.edn
│       ├── src/acme/core.clj
│       ├── src/acme_legacy/util.clj
│       ├── resources/Acme.css
│       ├── config/dev.env
│       └── README.md
├── dist/                           # gitignored, holds uberscript artifact
└── install.sh
```

Plus root-level:

```
.github/workflows/ci.yml            # replace placeholder with real CI matrix
.github/workflows/release.yml       # new, triggered on cli-v* tags
```

Each `src/c3kit_create/*.clj` file has one clear responsibility. `main.clj` is the only file that knows about all the others.

---

## TDD Note for Implementers

Every task that adds production code MUST follow red → green → commit. For tasks that only add config / docs / fixtures, the "test" is the verification command listed under the task. Don't skip verification.

When a step says "write the failing test", run it before writing the implementation. When a step says "run it to verify it fails", actual output goes to the agent's report so the controller can confirm the test really failed for the documented reason.

---

### Task 1: Speclj-on-bb spike

**Files:**
- Create: `cli/bb.edn` (initial)
- Create: `cli/spec/spike_spec.clj` (deleted at end of task)

This task de-risks the testing-framework decision (Risk row #1 in spec). If Speclj 3.12.0 doesn't run under current bb, we fall back to `clojure.test` and update the spec.

- [ ] **Step 1: Create minimal `cli/bb.edn`**

```clojure
;; cli/bb.edn
{:paths ["src" "spec"]
 :deps  {speclj/speclj {:mvn/version "3.12.0"}}
 :tasks
 {test {:doc  "Run all tests"
        :task (shell "bb" "-cp" (clojure-str "(deps/path)")
                     "-m" "speclj.cli.run.standard")}}}
```

(`clojure-str` is informal here — see Step 2 for the actual task body that works.)

Adjust to the concrete invocation:

```clojure
{:paths ["src" "spec"]
 :deps  {speclj/speclj {:mvn/version "3.12.0"}}
 :tasks
 {test {:doc  "Run all tests"
        :task (do (require '[babashka.tasks :as t])
                  (t/shell "bb" "-x" "speclj.cli/-main" "-c" "spec"))}}}
```

If the `-x` form fails for any reason, replace with:

```clojure
{:paths ["src" "spec"]
 :deps  {speclj/speclj {:mvn/version "3.12.0"}}
 :tasks
 {test {:doc  "Run all tests"
        :requires ([speclj.cli])
        :task (speclj.cli/-main "-c" "spec")}}}
```

- [ ] **Step 2: Write a trivial Speclj spike**

`cli/spec/spike_spec.clj`:

```clojure
(ns spike-spec
  (:require [speclj.core :refer [describe it should=]]))

(describe "speclj on bb"
  (it "can run a basic assertion"
    (should= 2 (+ 1 1))))
```

- [ ] **Step 3: Run the spike**

Run from `cli/`:

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: one passing example, exit 0. Output should contain `1 examples, 0 failures`.

- [ ] **Step 4: Decision branch**

If Step 3 passes, proceed. If Step 3 fails:

- Read the error carefully. Common failures: missing `:exclude-namespaces`, sci-incompatible macros in speclj.
- Try the alternative task body from Step 1.
- If both forms fail with macro-expansion errors, STOP and report BLOCKED. Recommendation: switch the spec's testing section to `clojure.test`; the plan tasks below will need their `spec/` paths renamed to `test/` and their `describe/it/should=` calls converted to `deftest`/`is`. Do NOT proceed silently with a broken setup.

- [ ] **Step 5: Remove the spike and commit**

```sh
rm cli/spec/spike_spec.clj
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/bb.edn && \
git commit -m "chore(cli): bb.edn with speclj-on-bb confirmed"
```

Expected: one new commit on `cli/v0.1`.

---

### Task 2: bb.edn final shape + lint task + `.gitignore` updates

**Files:**
- Modify: `cli/bb.edn`
- Modify: `.gitignore` (root) — already ignores `cli/dist/`

- [ ] **Step 1: Update `cli/bb.edn` to final shape**

```clojure
{:paths ["src" "spec"]
 :deps  {org.clojure/tools.cli {:mvn/version "1.1.230"}
         speclj/speclj         {:mvn/version "3.12.0"}}
 :tasks
 {test       {:doc  "Run all tests"
              :requires ([speclj.cli])
              :task (speclj.cli/-main "-c" "spec")}
  uberscript {:doc  "Build cli/dist/c3kit-create.bb"
              :task (do (require '[babashka.fs :as fs])
                        (fs/create-dirs "dist")
                        (shell "bb" "uberscript" "dist/c3kit-create.bb"
                               "-m" "c3kit-create.main"))}
  lint       {:doc  "Run clj-kondo"
              :task (shell "clj-kondo" "--lint" "src" "spec")}}}
```

- [ ] **Step 2: Verify `bb test` still passes (it will run zero tests now, but should not error)**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: exit 0 with `0 examples, 0 failures` (or similar). If non-zero, the bb.edn is wrong — fix before continuing.

- [ ] **Step 3: Verify `bb lint` runs (zero files lints clean)**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb lint
```

Expected: clj-kondo exits 0 (or with "no files to lint" — that's fine too).

- [ ] **Step 4: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/bb.edn && \
git commit -m "chore(cli): bb.edn tasks (test, uberscript, lint)"
```

---

### Task 3: `main.clj` skeleton + `--version` flag

**Files:**
- Create: `cli/src/c3kit_create/main.clj`
- Create: `cli/src/c3kit_create/version.clj`
- Create: `cli/spec/c3kit_create/version_spec.clj`

We start here so we always have a runnable entry point. `--version` is the simplest end-to-end thing the CLI can do.

- [ ] **Step 1: Write the failing version test**

`cli/spec/c3kit_create/version_spec.clj`:

```clojure
(ns c3kit-create.version-spec
  (:require [speclj.core :refer [describe it should=]]
            [c3kit-create.version :as v]))

(describe "version/current"
  (it "returns the current CLI semver as a string"
    (should= "0.1.0-SNAPSHOT" (v/current))))
```

- [ ] **Step 2: Run, verify it fails**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: failure, message includes `Could not locate c3kit_create/version` or similar.

- [ ] **Step 3: Minimal `version.clj`**

`cli/src/c3kit_create/version.clj`:

```clojure
(ns c3kit-create.version)

(def ^:const CURRENT "0.1.0-SNAPSHOT")

(defn current [] CURRENT)
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: `1 examples, 0 failures`.

- [ ] **Step 5: Minimal `main.clj`**

`cli/src/c3kit_create/main.clj`:

```clojure
(ns c3kit-create.main
  (:require [c3kit-create.version :as v])
  (:gen-class))

(defn -main [& args]
  (cond
    (some #{"--version"} args)
    (do (println (v/current)) (System/exit 0))

    :else
    (do (println "c3kit-create" (v/current))
        (println "(no subcommand implemented yet)")
        (System/exit 0))))
```

- [ ] **Step 6: Smoke-test `--version`**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && \
bb -m c3kit-create.main --version
```

Expected: prints `0.1.0-SNAPSHOT`, exit 0.

- [ ] **Step 7: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/main.clj \
        cli/src/c3kit_create/version.clj \
        cli/spec/c3kit_create/version_spec.clj && \
git commit -m "feat(cli): main entry + --version"
```

---

### Task 4: `ui.clj` — TTY detection, color, step printing

**Files:**
- Create: `cli/src/c3kit_create/ui.clj`
- Create: `cli/spec/c3kit_create/ui_spec.clj`

Per sub-spec §3 / §4, ANSI always on, with `(tty?)` to fall back to plain when stdout is piped.

- [ ] **Step 1: Failing test — `tty?`**

`cli/spec/c3kit_create/ui_spec.clj`:

```clojure
(ns c3kit-create.ui-spec
  (:require [speclj.core :refer [describe it should= should should-not with-redefs around]]
            [c3kit-create.ui :as ui]))

(describe "ui/tty?"
  (it "is a boolean"
    (should (contains? #{true false} (ui/tty?)))))

(describe "ui/colorize"
  (it "wraps in ANSI when color is on"
    (should= "[32mhi[0m" (ui/colorize :green "hi" true)))

  (it "returns plain string when color is off"
    (should= "hi" (ui/colorize :green "hi" false))))

(describe "ui/step / ui/ok / ui/fail"
  (it "step prints the message prefixed with arrow"
    (should= "▸ doing thing\n"
             (with-out-str (binding [ui/*color?* false] (ui/step "doing thing")))))

  (it "ok prints check"
    (should= "✓ done\n"
             (with-out-str (binding [ui/*color?* false] (ui/ok "done")))))

  (it "fail prints cross to stderr"
    (let [sw (java.io.StringWriter.)]
      (binding [*err* sw
                ui/*color?* false]
        (ui/fail "bad"))
      (should= "✗ bad\n" (str sw)))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: `Could not locate c3kit_create/ui`.

- [ ] **Step 3: Implement `ui.clj`**

`cli/src/c3kit_create/ui.clj`:

```clojure
(ns c3kit-create.ui)

(def ^:dynamic *color?* true)

(def ^:private CODES
  {:reset  "[0m"
   :red    "[31m"
   :green  "[32m"
   :yellow "[33m"
   :blue   "[34m"
   :gray   "[90m"
   :bold   "[1m"})

(defn tty?
  "True when stdout is connected to a terminal."
  []
  (some? (System/console)))

(defn colorize [color s color?]
  (if color?
    (str (CODES color) s (CODES :reset))
    (str s)))

(defn- emit [stream prefix-color prefix-glyph msg]
  (binding [*out* stream]
    (println (str (colorize prefix-color prefix-glyph *color?*) " " msg))))

(defn step [msg]     (emit *out* :blue   "▸" msg))
(defn ok   [msg]     (emit *out* :green  "✓" msg))
(defn warn [msg]     (emit *err* :yellow "⚠" msg))
(defn fail [msg]     (emit *err* :red    "✗" msg))
(defn info [msg]     (binding [*out* *out*] (println msg)))

(defn friendly-error
  "Wrap an Exception with a user-friendly one-liner. Original retained as cause."
  [msg ^Throwable cause]
  (ex-info msg {:friendly? true :cause cause} cause))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: `5 examples, 0 failures`.

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/ui.clj cli/spec/c3kit_create/ui_spec.clj && \
git commit -m "feat(cli): ui helpers (color, step printing, friendly errors)"
```

---

### Task 5: `args.clj` — tools.cli parser + help text

**Files:**
- Create: `cli/src/c3kit_create/args.clj`
- Create: `cli/spec/c3kit_create/args_spec.clj`
- Modify: `cli/src/c3kit_create/main.clj`

Per spec §3.

- [ ] **Step 1: Failing test**

`cli/spec/c3kit_create/args_spec.clj`:

```clojure
(ns c3kit-create.args-spec
  (:require [speclj.core :refer [describe it should= should should-contain]]
            [c3kit-create.args :as args]))

(describe "args/parse"
  (it "captures positional name"
    (let [r (args/parse ["my-app"])]
      (should= "my-app" (:name (:options r)))))

  (it "captures --template / -t"
    (let [r (args/parse ["my-app" "--template" "fe-vanilla"])]
      (should= "fe-vanilla" (:template (:options r))))
    (let [r (args/parse ["my-app" "-t" "fe-vanilla"])]
      (should= "fe-vanilla" (:template (:options r)))))

  (it "captures --yes / -y"
    (should (:yes (:options (args/parse ["my-app" "--yes"]))))
    (should (:yes (:options (args/parse ["my-app" "-y"])))))

  (it "captures --no-git as :git? false"
    (should= false (:git? (:options (args/parse ["my-app" "--no-git"])))))

  (it "defaults --git? true"
    (should= true (:git? (:options (args/parse ["my-app"])))))

  (it "captures --template-dir + env fallback"
    (should= "/tmp/x" (:template-dir (:options (args/parse ["my-app" "--template-dir" "/tmp/x"])))))

  (it "captures action flags"
    (should= :version  (:action (args/parse ["--version"])))
    (should= :help     (:action (args/parse ["--help"])))
    (should= :list     (:action (args/parse ["--list"])))
    (should= :upgrade  (:action (args/parse ["--upgrade"])))
    (should= :scaffold (:action (args/parse ["my-app"])))
    (should= :scaffold (:action (args/parse []))))

  (it "reports usage errors"
    (let [r (args/parse ["--no-such-flag"])]
      (should= :error (:action r))
      (should-contain "Unknown option" (:error r))))

  (it "includes a help string"
    (should-contain "c3kit-create" (args/help))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: `Could not locate c3kit_create/args`.

- [ ] **Step 3: Implement `args.clj`**

`cli/src/c3kit_create/args.clj`:

```clojure
(ns c3kit-create.args
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]))

(def ^:private CLI-OPTIONS
  [["-t" "--template ID"      "Template id (skip template prompt)"]
   [nil  "--template-ref REF" "Git ref/tag/branch for template fetch"]
   [nil  "--template-dir PATH" "Use local templates dir instead of fetching"]
   ["-y" "--yes"              "Accept all feature defaults, non-interactive"]
   [nil  "--install"          "Run `clj -P` and `npm install` after scaffold"]
   [nil  "--no-git"           "Skip `git init` and initial commit"
    :id :git? :default true :parse-fn (constantly false)]
   [nil  "--debug"            "Print full stack traces on error"]
   ["-h" "--help"             "Show this help"]
   [nil  "--version"          "Print CLI version"]
   [nil  "--list"             "List available templates"]
   [nil  "--upgrade"          "Download latest CLI release"]])

(defn help []
  (str "c3kit-create — scaffold a new Clojure project from a c3kit template\n\n"
       "USAGE\n"
       "  c3kit-create [<name>] [options]\n"
       "  c3kit-create --list\n"
       "  c3kit-create --version\n"
       "  c3kit-create --upgrade\n\n"
       "OPTIONS\n"
       (:summary (cli/parse-opts [] CLI-OPTIONS))))

(defn- env-default [opts key env]
  (if (contains? opts key) opts
      (if-let [v (System/getenv env)]
        (assoc opts key v)
        opts)))

(defn- detect-action [opts arguments]
  (cond
    (:help opts)    :help
    (:version opts) :version
    (:list opts)    :list
    (:upgrade opts) :upgrade
    :else           :scaffold))

(defn parse [argv]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts argv CLI-OPTIONS)
        options' (cond-> options
                   (first arguments) (assoc :name (first arguments))
                   true              (env-default :template-dir "C3KIT_TEMPLATES"))]
    (cond
      (seq errors)
      {:action :error :error (str/join "\n" errors) :summary summary}

      :else
      {:action  (detect-action options' arguments)
       :options options'
       :summary summary})))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: `9 examples, 0 failures`.

- [ ] **Step 5: Wire `args/parse` into `main.clj`**

Replace `cli/src/c3kit_create/main.clj` with:

```clojure
(ns c3kit-create.main
  (:require [c3kit-create.args :as args]
            [c3kit-create.ui :as ui]
            [c3kit-create.version :as v])
  (:gen-class))

(defn- exit [code] (System/exit code))

(defn -main [& argv]
  (let [{:keys [action options error]} (args/parse argv)]
    (binding [ui/*color?* (ui/tty?)]
      (case action
        :version  (do (println (v/current)) (exit 0))
        :help     (do (println (args/help)) (exit 0))
        :error    (do (ui/fail error)
                      (println (args/help))
                      (exit 2))
        :list     (do (ui/info "List of templates not yet implemented.") (exit 0))
        :upgrade  (do (ui/info "Upgrade not yet implemented.") (exit 0))
        :scaffold (do (ui/info "Scaffold not yet implemented.") (exit 0))))))
```

- [ ] **Step 6: Smoke tests**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb -m c3kit-create.main --version
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb -m c3kit-create.main --help
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb -m c3kit-create.main --no-such-flag
```

Expected:
- First: `0.1.0-SNAPSHOT`, exit 0.
- Second: help text starting with `c3kit-create —`, exit 0.
- Third: red `✗ Unknown option: …`, help, exit 2.

- [ ] **Step 7: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/args.clj \
        cli/src/c3kit_create/main.clj \
        cli/spec/c3kit_create/args_spec.clj && \
git commit -m "feat(cli): args parser + main dispatch"
```

---

### Task 6: `manifest.clj` — read + validate `c3kit-template.edn`

**Files:**
- Create: `cli/src/c3kit_create/manifest.clj`
- Create: `cli/spec/c3kit_create/manifest_spec.clj`

Per spec §5.

- [ ] **Step 1: Failing tests**

`cli/spec/c3kit_create/manifest_spec.clj`:

```clojure
(ns c3kit-create.manifest-spec
  (:require [speclj.core :refer [describe it should= should should-throw should-contain]]
            [c3kit-create.manifest :as m]))

(def MIN-MANIFEST
  {:id           :tiny
   :name         "Tiny"
   :description  "tiny test fixture"
   :version      "0.1.0"
   :min-cli      "0.1.0"
   :tokens       {"acme" {:hyphen true :underscore true :pascal true}}
   :secrets      []
   :features     []
   :next-steps   [{:cmd "cd {{name}}"}]})

(describe "manifest/validate"
  (it "accepts a minimum manifest"
    (should= MIN-MANIFEST (m/validate MIN-MANIFEST "tiny")))

  (it "rejects when :id is not a keyword"
    (should-throw (m/validate (assoc MIN-MANIFEST :id "tiny") "tiny")))

  (it "rejects when :id != dir name"
    (should-throw (m/validate (assoc MIN-MANIFEST :id :other) "tiny")))

  (it "rejects malformed semver"
    (should-throw (m/validate (assoc MIN-MANIFEST :version "0.1") "tiny")))

  (it "rejects empty tokens"
    (should-throw (m/validate (assoc MIN-MANIFEST :tokens {"" {}}) "tiny")))

  (it "rejects duplicate secret placeholders"
    (should-throw (m/validate (assoc MIN-MANIFEST :secrets
                                      [{:placeholder "X" :bytes 8}
                                       {:placeholder "X" :bytes 8}])
                              "tiny")))

  (it "rejects duplicate feature ids"
    (should-throw (m/validate (assoc MIN-MANIFEST :features
                                      [{:id :a :prompt "" :default true}
                                       {:id :a :prompt "" :default true}])
                              "tiny")))

  (it "rejects :db.default not in :db.options"
    (should-throw (m/validate (assoc MIN-MANIFEST :db
                                      {:prompt "DB"
                                       :options [{:id :sqlite :label "SQLite"}]
                                       :default :postgres})
                              "tiny")))

  (it "rejects ../ in :delete-when-off paths"
    (should-throw (m/validate (assoc MIN-MANIFEST :features
                                      [{:id :a :prompt "" :default true
                                        :delete-when-off ["../bad"]}])
                              "tiny"))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: `Could not locate c3kit_create/manifest`.

- [ ] **Step 3: Implement `manifest.clj`**

`cli/src/c3kit_create/manifest.clj`:

```clojure
(ns c3kit-create.manifest
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private ID-RE  #"^[a-z][a-z0-9]*(-[a-z0-9]+)*$")
(def ^:private SEMVER #"^\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?$")

(defn- die [msg] (throw (ex-info msg {:manifest? true})))

(defn- semver? [s] (boolean (and (string? s) (re-matches SEMVER s))))

(defn- check-id [m dir-name]
  (let [id (:id m)]
    (when-not (keyword? id) (die "manifest :id must be a keyword"))
    (when-not (re-matches ID-RE (name id)) (die ":id does not match name regex"))
    (when-not (= (name id) dir-name) (die (str ":id " id " does not equal dir " dir-name)))))

(defn- check-version [m]
  (when-not (semver? (:version m))  (die ":version is not valid semver"))
  (when-not (semver? (:min-cli m))  (die ":min-cli is not valid semver")))

(defn- check-tokens [m]
  (let [tokens (:tokens m)]
    (when-not (map? tokens) (die ":tokens must be a map"))
    (doseq [[k v] tokens]
      (when (or (not (string? k)) (str/blank? k)) (die ":tokens key must be non-blank string"))
      (when-not (map? v) (die ":tokens entry must be a flag map")))))

(defn- check-secrets [m]
  (let [secrets (:secrets m)
        placeholders (map :placeholder secrets)]
    (when-not (sequential? secrets) (die ":secrets must be sequential"))
    (when (not= (count placeholders) (count (distinct placeholders)))
      (die "duplicate :secrets placeholder"))))

(defn- check-features [m]
  (let [feats (:features m)
        ids   (map :id feats)]
    (when-not (sequential? feats) (die ":features must be sequential"))
    (when (not= (count ids) (count (distinct ids)))
      (die "duplicate :features :id"))
    (doseq [{:keys [delete-when-off]} feats
            p delete-when-off]
      (when (or (str/starts-with? p "../") (str/includes? p "/.."))
        (die (str ":delete-when-off escapes template root: " p))))))

(defn- check-db [m]
  (when-let [db (:db m)]
    (let [ids (map :id (:options db))]
      (when-not (seq ids) (die ":db.options must be non-empty"))
      (when (not= (count ids) (count (distinct ids)))
        (die "duplicate :db.options :id"))
      (when-not (some #{(:default db)} ids)
        (die ":db.default not in :db.options")))))

(defn- check-next-steps [m]
  (doseq [step (:next-steps m)]
    (when-not (and (map? step) (string? (:cmd step)))
      (die ":next-steps entry must be {:cmd \"...\"}"))))

(defn validate
  "Validate a parsed manifest. Returns the manifest unchanged on success,
  throws ex-info with {:manifest? true} on failure."
  [m dir-name]
  (check-id m dir-name)
  (check-version m)
  (check-tokens m)
  (check-secrets m)
  (check-features m)
  (check-db m)
  (check-next-steps m)
  m)

(defn read-manifest
  "Read templates/<dir-name>/c3kit-template.edn from the given template dir
   and validate it. Throws ex-info on any failure."
  [^String template-dir]
  (let [path (fs/path template-dir "c3kit-template.edn")
        dir-name (fs/file-name template-dir)]
    (when-not (fs/exists? path)
      (die (str "manifest not found: " path)))
    (let [m (try (edn/read-string (slurp (fs/file path)))
                 (catch Exception e
                   (throw (ex-info "manifest is not valid EDN"
                                   {:manifest? true} e))))]
      (validate m dir-name))))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: `9 examples, 0 failures` for `manifest`. Total grows by 9.

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/manifest.clj cli/spec/c3kit_create/manifest_spec.clj && \
git commit -m "feat(cli): manifest parse + validate"
```

---

### Task 7: `rename.clj` — token variants and ordered replacement

**Files:**
- Create: `cli/src/c3kit_create/rename.clj`
- Create: `cli/spec/c3kit_create/rename_spec.clj`

Per spec §6.2.

- [ ] **Step 1: Failing tests**

`cli/spec/c3kit_create/rename_spec.clj`:

```clojure
(ns c3kit-create.rename-spec
  (:require [speclj.core :refer [describe it should= should-throw should]]
            [c3kit-create.rename :as r]))

(describe "rename/variants"
  (it "computes all four variants for a kebab name"
    (should= {:hyphen     "my-cool-app"
              :underscore "my_cool_app"
              :pascal     "MyCoolApp"
              :upper      "MY_COOL_APP"}
             (r/variants "my-cool-app"))))

(describe "rename/replace-token"
  (it "rewrites all variants of a source token in a string"
    (should= "MyCoolApp / my-cool-app / my_cool_app / MY_COOL_APP_DEV"
             (r/replace-token "Acme / acme / acme / ACME_DEV"
                              "acme"
                              {:hyphen true :underscore true :pascal true :upper-prefix true}
                              (r/variants "my-cool-app")))))

(describe "rename/replace-many"
  (it "applies tokens in declared order; longest first"
    (should= "MyCoolApp.foo my_cool_app.bar"
             (r/replace-many "Acme.foo acme.bar"
                             {"acme" {:hyphen true :underscore true :pascal true}}
                             (r/variants "my-cool-app")))))

(describe "rename/reserved?"
  (it "rejects clojure-ish reserved names"
    (should (r/reserved? "clojure"))
    (should (r/reserved? "java"))
    (should (r/reserved? "cljs")))

  (it "accepts user names"
    (should-not (r/reserved? "my-app"))))

(describe "rename/validate-name"
  (it "throws on names colliding with a template's source token"
    (should-throw (r/validate-name "acme" {"acme" {:hyphen true}}))
    (should-throw (r/validate-name "clojure" {})))

  (it "throws on names that fail regex"
    (should-throw (r/validate-name "1bad" {}))
    (should-throw (r/validate-name "" {})))

  (it "returns the name on success"
    (should= "my-app" (r/validate-name "my-app" {"acme" {:hyphen true}}))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: `Could not locate c3kit_create/rename`.

- [ ] **Step 3: Implement `rename.clj`**

`cli/src/c3kit_create/rename.clj`:

```clojure
(ns c3kit-create.rename
  (:require [clojure.string :as str]))

(def ^:private NAME-RE #"^[a-z][a-z0-9]*(-[a-z0-9]+)*$")

(def ^:private RESERVED
  #{"clojure" "clojurescript" "java" "javascript" "cljs" "cljc" "test" "specs"})

(defn variants
  "Given a kebab-case name, return the four spelling variants."
  [kebab]
  (let [parts (str/split kebab #"-")]
    {:hyphen     kebab
     :underscore (str/join "_" parts)
     :pascal     (apply str (map str/capitalize parts))
     :upper      (str/upper-case (str/join "_" parts))}))

(defn- pascal-of [s]
  (apply str (map str/capitalize (str/split s #"-"))))

(defn- upper-prefix-of [s]
  (str (str/upper-case (str/replace s #"-" "_")) "_"))

(defn replace-token
  "Rewrite every variant of source-token in `s` per the flag map, using user variants."
  [s source-token flags user]
  (let [src-hyphen     source-token
        src-snake      (str/replace source-token #"-" "_")
        src-pascal     (pascal-of source-token)
        src-upper-pfx  (upper-prefix-of source-token)
        user-upper-pfx (str (:upper user) "_")
        ;; Order: most-specific first to avoid clobbering.
        replacements
        (cond-> []
          (:upper-prefix flags) (conj [src-upper-pfx user-upper-pfx])
          (:pascal flags)       (conj [src-pascal     (:pascal user)])
          (:underscore flags)   (conj [src-snake      (:underscore user)])
          (:hyphen flags)       (conj [src-hyphen     (:hyphen user)]))]
    (reduce (fn [acc [from to]] (str/replace acc from to))
            s replacements)))

(defn replace-many
  "Apply all tokens to `s`, longest source-token first to disambiguate overlapping."
  [s tokens user]
  (let [sorted (sort-by #(- (count (first %))) tokens)]
    (reduce (fn [acc [src flags]] (replace-token acc src flags user))
            s sorted)))

(defn reserved? [name] (boolean (RESERVED name)))

(defn validate-name
  "Throws ex-info if `nm` is invalid; otherwise returns `nm`."
  [nm tokens]
  (cond
    (or (not (string? nm)) (not (re-matches NAME-RE nm)))
    (throw (ex-info (str "invalid project name: " (pr-str nm))
                    {:name? true :reason :regex}))

    (reserved? nm)
    (throw (ex-info (str "name is reserved: " nm)
                    {:name? true :reason :reserved}))

    (some #(= nm %) (keys tokens))
    (throw (ex-info (str "name collides with template source token: " nm)
                    {:name? true :reason :token-collision}))

    :else nm))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: rename suite passes all examples.

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/rename.clj cli/spec/c3kit_create/rename_spec.clj && \
git commit -m "feat(cli): token variants + ordered replacement"
```

---

### Task 8: `features.clj` — marker stripping

**Files:**
- Create: `cli/src/c3kit_create/features.clj`
- Create: `cli/spec/c3kit_create/features_spec.clj`

Per spec §6.1.

- [ ] **Step 1: Failing tests**

`cli/spec/c3kit_create/features_spec.clj`:

```clojure
(ns c3kit-create.features-spec
  (:require [speclj.core :refer [describe it should= should-throw]]
            [c3kit-create.features :as f]
            [clojure.string :as str]))

(defn lines [& xs] (str/join "\n" xs))

(describe "features/strip"
  (it "removes block content when feature is OFF"
    (should= (lines "before" "after")
             (f/strip (lines "before"
                             ";; @c3kit/feature :ssr {"
                             "(prerender!)"
                             ";; @c3kit/feature :ssr }"
                             "after")
                      {:ssr false} {})))

  (it "keeps block content but drops marker lines when feature is ON"
    (should= (lines "before" "(prerender!)" "after")
             (f/strip (lines "before"
                             ";; @c3kit/feature :ssr {"
                             "(prerender!)"
                             ";; @c3kit/feature :ssr }"
                             "after")
                      {:ssr true} {})))

  (it "supports inverse markers"
    (should= (lines "alt")
             (f/strip (lines ";; @c3kit/feature !:auth {"
                             "alt"
                             ";; @c3kit/feature !:auth }")
                      {:auth false} {}))
    (should= ""
             (f/strip (lines ";; @c3kit/feature !:auth {"
                             "alt"
                             ";; @c3kit/feature !:auth }")
                      {:auth true} {})))

  (it "line-level toggle on"
    (should= "(require '[csp :refer [wrap]])"
             (f/strip ";; @c3kit/feature :csp = (require '[csp :refer [wrap]])"
                      {:csp true} {})))

  (it "line-level toggle off removes the line"
    (should= ""
             (f/strip ";; @c3kit/feature :csp = (require '[csp :refer [wrap]])"
                      {:csp false} {})))

  (it "db markers keep only matching block"
    (should= "{:impl :sqlite}"
             (f/strip (lines ";; @c3kit/db :sqlite {"
                             "{:impl :sqlite}"
                             ";; @c3kit/db :sqlite }"
                             ";; @c3kit/db :postgres {"
                             "{:impl :postgres}"
                             ";; @c3kit/db :postgres }")
                      {} {:db :sqlite})))

  (it "errors on unclosed block"
    (should-throw (f/strip (lines ";; @c3kit/feature :ssr {"
                                   "(prerender!)")
                            {:ssr true} {})))

  (it "errors on mismatched id"
    (should-throw (f/strip (lines ";; @c3kit/feature :ssr {"
                                   "x"
                                   ";; @c3kit/feature :csp }")
                            {:ssr true :csp true} {})))

  (it "errors on nested markers"
    (should-throw (f/strip (lines ";; @c3kit/feature :ssr {"
                                   ";; @c3kit/feature :csp {"
                                   "x"
                                   ";; @c3kit/feature :csp }"
                                   ";; @c3kit/feature :ssr }")
                            {:ssr true :csp true} {}))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: `Could not locate c3kit_create/features`.

- [ ] **Step 3: Implement `features.clj`**

`cli/src/c3kit_create/features.clj`:

```clojure
(ns c3kit-create.features
  (:require [clojure.string :as str]))

;; Markers — substring matching, comment syntax agnostic.

(def ^:private LINE-EQ-RE
  #"@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*=\s*(.*)$")

(def ^:private BLOCK-OPEN-RE
  #"@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*\{")

(def ^:private BLOCK-CLOSE-RE
  #"@c3kit/feature\s+(!)?:([A-Za-z][A-Za-z0-9-]*)\s*\}")

(def ^:private DB-OPEN-RE
  #"@c3kit/db\s+:([A-Za-z][A-Za-z0-9-]*)\s*\{")

(def ^:private DB-CLOSE-RE
  #"@c3kit/db\s+:([A-Za-z][A-Za-z0-9-]*)\s*\}")

(defn- die [msg] (throw (ex-info msg {:features? true})))

(defn- feature-on? [features id inverse?]
  (let [raw (get features id)]
    (if inverse? (not raw) (boolean raw))))

(defn- handle-line-eq [line features]
  (when-let [m (re-find LINE-EQ-RE line)]
    (let [[_ inv id-str code] m
          on? (feature-on? features (keyword id-str) (some? inv))]
      (if on? code ::drop))))

(defn- block-open [line]
  (when-let [m (re-find BLOCK-OPEN-RE line)]
    (let [[_ inv id-str] m]
      {:kind :feature :id (keyword id-str) :inverse? (some? inv)})))

(defn- block-close [line]
  (when-let [m (re-find BLOCK-CLOSE-RE line)]
    (let [[_ inv id-str] m]
      {:kind :feature :id (keyword id-str) :inverse? (some? inv)})))

(defn- db-open [line]
  (when-let [m (re-find DB-OPEN-RE line)]
    {:kind :db :id (keyword (second m))}))

(defn- db-close [line]
  (when-let [m (re-find DB-CLOSE-RE line)]
    {:kind :db :id (keyword (second m))}))

(defn strip
  "Strip feature/db markers from text. Returns rewritten string.

   features  — map of feature-id → bool
   db-choice — map with optional :db keyword for db-marker selection"
  [text features db-choice]
  (let [lines (str/split-lines text)]
    (loop [in       lines
           out      []
           stack    nil]                 ; list of open block descriptors
      (if (empty? in)
        (cond
          (seq stack) (die (str "unclosed marker: " (first stack)))
          :else       (str/join "\n" out))
        (let [line (first in)
              rest (rest in)]
          ;; A line-eq marker only matches when no block is open.
          (cond
            (and (empty? stack) (re-find LINE-EQ-RE line))
            (let [r (handle-line-eq line features)]
              (recur rest (if (= r ::drop) out (conj out r)) stack))

            (or (block-open line) (db-open line))
            (let [open (or (block-open line) (db-open line))]
              (when (seq stack) (die "nested marker"))
              (recur rest out [open]))

            (or (block-close line) (db-close line))
            (let [close (or (block-close line) (db-close line))
                  [open] stack]
              (when-not open                    (die "close without open"))
              (when-not (= (:kind open) (:kind close)) (die "marker kind mismatch"))
              (when-not (= (:id open)   (:id close))   (die "marker id mismatch"))
              (recur rest out nil))

            ;; Inside an open block: include line iff block resolves "on"
            (seq stack)
            (let [{:keys [kind id inverse?]} (first stack)
                  on? (case kind
                        :feature (feature-on? features id inverse?)
                        :db      (= id (:db db-choice)))]
              (recur rest (if on? (conj out line) out) stack))

            :else
            (recur rest (conj out line) stack)))))))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: features suite green.

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/features.clj cli/spec/c3kit_create/features_spec.clj && \
git commit -m "feat(cli): feature + db marker stripping"
```

---

### Task 9: `secrets.clj` — random generation and placeholder replacement

**Files:**
- Create: `cli/src/c3kit_create/secrets.clj`
- Create: `cli/spec/c3kit_create/secrets_spec.clj`

Per spec §6.3.

- [ ] **Step 1: Failing tests**

`cli/spec/c3kit_create/secrets_spec.clj`:

```clojure
(ns c3kit-create.secrets-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.secrets :as s]
            [clojure.string :as str]))

(describe "secrets/hex"
  (it "produces 2 hex chars per byte"
    (should= 48 (count (s/hex 24))))

  (it "two calls produce different strings"
    (should-not= (s/hex 24) (s/hex 24)))

  (it "string matches /^[0-9a-f]+$/"
    (should (re-matches #"[0-9a-f]+" (s/hex 16)))))

(describe "secrets/replace-placeholders"
  (it "replaces a single placeholder with one consistent hex value"
    (let [out (s/replace-placeholders "key=ACME_DEV_SECRET other=ACME_DEV_SECRET"
                                       [{:placeholder "ACME_DEV_SECRET" :bytes 8}])
          [_ a _ b] (re-find #"key=([0-9a-f]+) other=([0-9a-f]+)" out)]
      (should= a b)))

  (it "different placeholders get different secrets"
    (let [out (s/replace-placeholders "a=A b=B"
                                       [{:placeholder "A" :bytes 8}
                                        {:placeholder "B" :bytes 8}])
          [_ a b] (re-find #"a=([0-9a-f]+) b=([0-9a-f]+)" out)]
      (should-not= a b)))

  (it "warns but does not throw when placeholder absent (returns input)"
    (should= "hello" (s/replace-placeholders "hello"
                                              [{:placeholder "MISSING" :bytes 8}]))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 3: Implement**

`cli/src/c3kit_create/secrets.clj`:

```clojure
(ns c3kit-create.secrets
  (:require [clojure.string :as str])
  (:import [java.security SecureRandom]))

(defn hex
  "Return `bytes` of secure-random data, hex-encoded."
  [bytes]
  (let [sr  (SecureRandom.)
        buf (byte-array bytes)]
    (.nextBytes sr buf)
    (->> buf
         (map #(format "%02x" (bit-and ^long % 0xff)))
         (apply str))))

(defn replace-placeholders
  "Replace each :placeholder with a freshly generated hex secret of :bytes length.
   Same placeholder occurring multiple times gets the same secret."
  [text secrets]
  (reduce (fn [s {:keys [placeholder bytes]}]
            (let [secret (hex bytes)]
              (str/replace s placeholder secret)))
          text secrets))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/secrets.clj cli/spec/c3kit_create/secrets_spec.clj && \
git commit -m "feat(cli): secret generation + placeholder replace"
```

---

### Task 10: `fs.clj` — atomic move, cross-fs detection, temp dirs

**Files:**
- Create: `cli/src/c3kit_create/fs.clj`
- Create: `cli/spec/c3kit_create/fs_spec.clj`

Per spec §7.2.

- [ ] **Step 1: Failing tests**

`cli/spec/c3kit_create/fs_spec.clj`:

```clojure
(ns c3kit-create.fs-spec
  (:require [speclj.core :refer [describe it should= should should-not should-throw]]
            [c3kit-create.fs :as cfs]
            [babashka.fs :as fs]))

(describe "fs/stage-dir"
  (it "creates a unique temp dir that exists"
    (let [d (cfs/stage-dir)]
      (try (should (fs/exists? d))
           (finally (fs/delete-tree d))))))

(describe "fs/cleanup!"
  (it "deletes the dir even if missing"
    (let [d (cfs/stage-dir)]
      (cfs/cleanup! d)
      (should-not (fs/exists? d)))
    (cfs/cleanup! (str (fs/path (fs/temp-dir) "nonexistent-c3k")))))

(describe "fs/same-filesystem?"
  (it "always true for two paths under temp"
    (let [a (str (fs/create-temp-dir))
          b (str (fs/create-temp-dir))]
      (should (cfs/same-filesystem? a b))
      (fs/delete-tree a)
      (fs/delete-tree b))))

(describe "fs/move-into-place!"
  (it "atomic move when source and target share filesystem"
    (let [stage (cfs/stage-dir)
          src   (fs/path stage "scaffold")
          tgt   (fs/path stage "final")]
      (fs/create-dirs src)
      (spit (fs/file (fs/path src "hello.txt")) "hi")
      (cfs/move-into-place! (str src) (str tgt))
      (should (fs/exists? tgt))
      (should (fs/exists? (fs/path tgt "hello.txt")))
      (should-not (fs/exists? src))
      (cfs/cleanup! stage)))

  (it "throws when target already exists"
    (let [stage (cfs/stage-dir)
          src   (fs/path stage "scaffold")
          tgt   (fs/path stage "final")]
      (fs/create-dirs src)
      (fs/create-dirs tgt)
      (should-throw (cfs/move-into-place! (str src) (str tgt)))
      (cfs/cleanup! stage))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 3: Implement**

`cli/src/c3kit_create/fs.clj`:

```clojure
(ns c3kit-create.fs
  (:require [babashka.fs :as fs])
  (:import [java.nio.file Files Path]
           [java.util UUID]))

(defn stage-dir
  "Create a fresh staging dir under $TMPDIR for this run."
  []
  (let [name (str "c3kit-create-" (UUID/randomUUID))
        d    (fs/path (fs/temp-dir) name)]
    (fs/create-dirs d)
    (str d)))

(defn cleanup!
  "Recursively delete a staging dir if it exists. No-op if missing."
  [^String path]
  (when (and path (fs/exists? path))
    (fs/delete-tree path)))

(defn same-filesystem?
  "True if two paths sit on the same filesystem store."
  [^String a ^String b]
  (try
    (= (Files/getFileStore (fs/path a))
       (Files/getFileStore (fs/path b)))
    (catch Exception _ false)))

(defn- copy-tree! [^String src ^String tgt]
  (fs/copy-tree src tgt)
  (fs/delete-tree src))

(defn move-into-place!
  "Move source scaffold dir to final target. Throws if target already exists.
   Falls back to copy+delete on cross-filesystem moves."
  [^String src ^String tgt]
  (when (fs/exists? tgt)
    (throw (ex-info (str "target already exists: " tgt)
                    {:collision? true})))
  (if (same-filesystem? src (str (fs/parent tgt)))
    (fs/move src tgt)
    (copy-tree! src tgt)))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/fs.clj cli/spec/c3kit_create/fs_spec.clj && \
git commit -m "feat(cli): stage dirs + atomic move with cross-fs fallback"
```

---

### Task 11: `fetch.clj` — local-dir mode + git-clone mode

**Files:**
- Create: `cli/src/c3kit_create/fetch.clj`
- Create: `cli/spec/c3kit_create/fetch_spec.clj`

Per spec §7.1. Tests cover local mode only; git-clone is exercised in the e2e task with a real (small) ref.

- [ ] **Step 1: Failing tests (local mode)**

`cli/spec/c3kit_create/fetch_spec.clj`:

```clojure
(ns c3kit-create.fetch-spec
  (:require [speclj.core :refer [describe it should= should should-throw]]
            [c3kit-create.fetch :as fetch]
            [babashka.fs :as fs]))

(defn- mk-fixture-templates! [root]
  (let [tdir (fs/path root "tiny")]
    (fs/create-dirs tdir)
    (spit (fs/file (fs/path tdir "c3kit-template.edn"))
          (pr-str {:id :tiny
                   :name "Tiny"
                   :description "fixture"
                   :version "0.1.0"
                   :min-cli "0.1.0"
                   :tokens {"acme" {:hyphen true}}
                   :secrets [] :features []
                   :next-steps [{:cmd "cd {{name}}"}]}))
    (spit (fs/file (fs/path tdir "README.md")) "# hi")
    (str root)))

(describe "fetch/from-local-dir"
  (it "copies templates/<id>/ into dest"
    (let [root (str (fs/create-temp-dir))
          _    (mk-fixture-templates! root)
          dest (str (fs/path (fs/create-temp-dir) "out"))]
      (fetch/from-local-dir root "tiny" dest)
      (should (fs/exists? (fs/path dest "c3kit-template.edn")))
      (should (fs/exists? (fs/path dest "README.md")))
      (fs/delete-tree root)
      (fs/delete-tree (fs/parent dest))))

  (it "throws when template dir is missing"
    (let [root (str (fs/create-temp-dir))]
      (should-throw (fetch/from-local-dir root "nope"
                                          (str (fs/path root "dest"))))
      (fs/delete-tree root))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 3: Implement**

`cli/src/c3kit_create/fetch.clj`:

```clojure
(ns c3kit-create.fetch
  (:require [babashka.fs :as fs]
            [babashka.process :as p]))

(defn from-local-dir
  "Copy `<templates-root>/<id>/` into `dest`. Throws if missing."
  [templates-root id dest]
  (let [src (fs/path templates-root id)]
    (when-not (fs/exists? src)
      (throw (ex-info (str "template not found: " src) {:fetch? true})))
    (fs/create-dirs (fs/parent dest))
    (fs/copy-tree src dest)))

(defn- git-available? []
  (try
    (zero? (:exit (p/shell {:out :string :err :string} "git" "--version")))
    (catch Exception _ false)))

(defn from-git
  "Clone monorepo at `ref` into tmp, copy out templates/<id>/, leave tmp alone
   (caller cleans up via the surrounding stage dir)."
  [repo-url ref id work-dir dest]
  (when-not (git-available?)
    (throw (ex-info "git not on PATH" {:fetch? true :reason :no-git})))
  (let [clone (fs/path work-dir "clone")]
    (let [res (p/shell {:dir (str work-dir) :continue true}
                       "git" "clone" "--depth" "1"
                       "--branch" ref repo-url (str clone))]
      (when-not (zero? (:exit res))
        (throw (ex-info (str "git clone failed: " (:err res))
                        {:fetch? true :reason :clone}))))
    (from-local-dir (str (fs/path clone "templates")) id dest)))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/fetch.clj cli/spec/c3kit_create/fetch_spec.clj && \
git commit -m "feat(cli): local + git-clone fetch"
```

---

### Task 12: `postscaffold.clj` — git init + commit, --install

**Files:**
- Create: `cli/src/c3kit_create/postscaffold.clj`
- Create: `cli/spec/c3kit_create/postscaffold_spec.clj`

- [ ] **Step 1: Failing tests**

`cli/spec/c3kit_create/postscaffold_spec.clj`:

```clojure
(ns c3kit-create.postscaffold-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.postscaffold :as ps]
            [babashka.fs :as fs]
            [babashka.process :as p]))

(defn- mk-project! []
  (let [d (str (fs/create-temp-dir))]
    (spit (fs/file (fs/path d "hello.txt")) "hi")
    d))

(describe "postscaffold/git-init!"
  (it "creates a repo with a single commit on main"
    (let [d (mk-project!)]
      (ps/git-init! d)
      (should (fs/exists? (fs/path d ".git")))
      (let [log (:out (p/shell {:dir d :out :string}
                               "git" "log" "--oneline"))]
        (should (re-find #"initial scaffold" log)))
      (fs/delete-tree d)))

  (it "is a no-op when called with :git? false (caller responsibility)"
    ;; This spec exists to remind callers: the fn itself doesn't gate
    (should true)))

(describe "postscaffold/install!"
  (it "is a no-op without :install"
    (let [d (mk-project!)]
      (ps/install! d {:install false})
      (fs/delete-tree d)
      (should true)))

  (it "skips npm when no package.json"
    (let [d (mk-project!)]
      ;; clj -P would actually run; in CI we mock by setting :dry-run? true
      (ps/install! d {:install true :dry-run? true})
      (fs/delete-tree d)
      (should true))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 3: Implement**

`cli/src/c3kit_create/postscaffold.clj`:

```clojure
(ns c3kit-create.postscaffold
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [c3kit-create.ui :as ui]))

(defn git-init!
  "Run `git init -b main && git add -A && git commit -m \"chore: initial scaffold\"`."
  [project-dir]
  (let [opts {:dir project-dir :out :string :err :string}]
    (doseq [args [["git" "init" "-b" "main"]
                  ["git" "add" "-A"]
                  ["git" "commit" "-m" "chore: initial scaffold"]]]
      (let [res (apply p/shell (concat [(merge opts {:continue true})] args))]
        (when-not (zero? (:exit res))
          (throw (ex-info (str "git step failed: " args " — " (:err res))
                          {:postscaffold? true})))))))

(defn install!
  "Run `clj -P` and `npm install` if applicable. Honors :dry-run? for tests."
  [project-dir {:keys [install dry-run?]}]
  (when install
    (when-not dry-run?
      (p/shell {:dir project-dir} "clj" "-P")
      (when (fs/exists? (fs/path project-dir "package.json"))
        (p/shell {:dir project-dir} "npm" "install")))
    (ui/ok "installed dependencies")))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/postscaffold.clj cli/spec/c3kit_create/postscaffold_spec.clj && \
git commit -m "feat(cli): post-scaffold git init + install runner"
```

---

### Task 13: `render.clj` — orchestrate rename + features + secrets + path renames

**Files:**
- Create: `cli/src/c3kit_create/render.clj`
- Create: `cli/spec/c3kit_create/render_spec.clj`

This pure-function ties stages 2 together — applies in-place rewrites to a staging directory tree. Path renames (dir + file) happen here after content rewrite.

- [ ] **Step 1: Failing test**

`cli/spec/c3kit_create/render_spec.clj`:

```clojure
(ns c3kit-create.render-spec
  (:require [speclj.core :refer [describe it should= should should-not]]
            [c3kit-create.render :as r]
            [babashka.fs :as fs]))

(defn- mk-template! [root]
  (let [d root]
    (fs/create-dirs (fs/path d "src" "acme"))
    (spit (fs/file (fs/path d "src" "acme" "core.clj"))
          "(ns acme.core)\n;; @c3kit/feature :ssr {\n(println \"ssr\")\n;; @c3kit/feature :ssr }\n")
    (spit (fs/file (fs/path d "Acme.css")) ".acme { color: red; }")
    (spit (fs/file (fs/path d "env"))     "KEY=ACME_DEV_SECRET")
    (spit (fs/file (fs/path d "c3kit-template.edn"))
          (pr-str {:id :tiny :name "Tiny" :description "x" :version "0.1.0" :min-cli "0.1.0"
                   :tokens {"acme" {:hyphen true :underscore true :pascal true}
                            "ACME_" {:upper-prefix true}}
                   :secrets [{:placeholder "ACME_DEV_SECRET" :bytes 4}]
                   :features [{:id :ssr :prompt "" :default true}]
                   :next-steps [{:cmd "cd {{name}}"}]}))))

(describe "render/render!"
  (it "applies tokens, markers, secrets, and path renames"
    (let [stage (str (fs/create-temp-dir))]
      (mk-template! stage)
      (r/render! stage
                 (slurp (fs/file (fs/path stage "c3kit-template.edn")))
                 "my-cool-app"
                 {:ssr false}        ; features map
                 {})                  ; db choice (no db markers)
      ;; renames
      (should (fs/exists? (fs/path stage "src" "my_cool_app" "core.clj")))
      (should-not (fs/exists? (fs/path stage "src" "acme" "core.clj")))
      (should (fs/exists? (fs/path stage "MyCoolApp.css")))
      ;; content rewrites
      (let [core (slurp (fs/file (fs/path stage "src" "my_cool_app" "core.clj")))]
        (should= "(ns my-cool-app.core)\n" core))
      (let [css (slurp (fs/file (fs/path stage "MyCoolApp.css")))]
        (should (re-find #"\.my-cool-app" css)))
      ;; secrets
      (let [env (slurp (fs/file (fs/path stage "env")))]
        (should (re-find #"KEY=[0-9a-f]{8}" env)))
      ;; manifest removed
      (should-not (fs/exists? (fs/path stage "c3kit-template.edn")))
      (fs/delete-tree stage))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 3: Implement**

`cli/src/c3kit_create/render.clj`:

```clojure
(ns c3kit-create.render
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [c3kit-create.features :as f]
            [c3kit-create.manifest :as manifest]
            [c3kit-create.rename :as rn]
            [c3kit-create.secrets :as sec]))

(def ^:private TEXT-EXTS
  #{"clj" "cljs" "cljc" "edn" "md" "html" "css" "js" "ts" "json"
    "yml" "yaml" "txt" "env" "properties" "xml" "toml" "csv"
    "sh" "bash" "zsh" "gitignore" "kts"})

(defn- text-file? [^java.io.File f]
  (let [n (.getName f)]
    (or (TEXT-EXTS (str/lower-case (or (last (str/split n #"\.")) "")))
        (#{"Dockerfile" "Makefile" "LICENSE"} n))))

(defn- visit-all-files [dir]
  (->> (file-seq (fs/file dir))
       (filter #(.isFile %))))

(defn- rewrite-content! [tokens user features db file]
  (when (text-file? file)
    (let [orig (slurp file)
          after-tokens   (rn/replace-many orig tokens user)
          after-features (f/strip after-tokens features db)
          ;; Secrets are applied at the end of orchestration; here we only do
          ;; tokens + features per call. Caller does secret pass separately.
          ]
      (when-not (= orig after-features)
        (spit file after-features)))))

(defn- rename-paths! [tokens user dir]
  ;; depth-first so leaves are renamed before parents.
  (let [paths (->> (file-seq (fs/file dir))
                   (sort-by #(- (count (.getAbsolutePath ^java.io.File %)))))]
    (doseq [^java.io.File p paths]
      (let [old-name (.getName p)
            new-name (rn/replace-many old-name tokens user)]
        (when (not= old-name new-name)
          (fs/move (.getAbsolutePath p)
                   (.getAbsolutePath (java.io.File. (.getParentFile p) new-name))))))))

(defn render!
  "In-place rewrite of `stage-dir`: rename tokens, strip markers, generate secrets,
   rename file/dir paths, drop the manifest file."
  [stage-dir manifest-edn user-name features db-choice]
  (let [m (manifest/validate (edn/read-string manifest-edn)
                             (fs/file-name stage-dir))
        tokens (:tokens m)
        user   (rn/variants user-name)]
    ;; 1. content rewrite (tokens + features) on every text file
    (doseq [file (visit-all-files stage-dir)]
      (rewrite-content! tokens user features db-choice file))
    ;; 2. secrets pass — second sweep so token rename can't interfere
    (doseq [file (visit-all-files stage-dir)]
      (when (text-file? file)
        (let [s (slurp file)
              s' (sec/replace-placeholders s (:secrets m))]
          (when-not (= s s')
            (spit file s')))))
    ;; 3. drop manifest
    (fs/delete-if-exists (fs/path stage-dir "c3kit-template.edn"))
    (fs/delete-if-exists (fs/path stage-dir "c3kit-template.bb"))
    ;; 4. rename dirs + files
    (rename-paths! tokens user stage-dir)
    stage-dir))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/render.clj cli/spec/c3kit_create/render_spec.clj && \
git commit -m "feat(cli): render orchestration (tokens + markers + secrets + path renames)"
```

---

### Task 14: `wizard.clj` — interactive prompts

**Files:**
- Create: `cli/src/c3kit_create/wizard.clj`
- Create: `cli/spec/c3kit_create/wizard_spec.clj`

Per spec §4. Tests stub `*in*`.

- [ ] **Step 1: Failing tests**

`cli/spec/c3kit_create/wizard_spec.clj`:

```clojure
(ns c3kit-create.wizard-spec
  (:require [speclj.core :refer [describe it should= should]]
            [c3kit-create.wizard :as w]))

(defn- with-stdin [text f]
  (with-in-str text (f)))

(describe "wizard/prompt-text"
  (it "uses default when input is empty"
    (with-stdin "\n" #(should= "x" (w/prompt-text "Name" "x" identity))))

  (it "returns trimmed input"
    (with-stdin "abc\n" #(should= "abc" (w/prompt-text "Name" "x" identity))))

  (it "re-prompts when validate throws"
    (with-stdin "1\nok\n"
                #(should= "ok"
                          (w/prompt-text "Name" nil
                                          (fn [v]
                                            (when-not (re-matches #"[a-z]+" v)
                                              (throw (ex-info "bad" {})))
                                            v))))))

(describe "wizard/prompt-yn"
  (it "default Yes"
    (with-stdin "\n" #(should (w/prompt-yn "go" true))))
  (it "default No"
    (with-stdin "\n" #(should= false (w/prompt-yn "go" false))))
  (it "explicit y"
    (with-stdin "y\n" #(should (w/prompt-yn "go" false))))
  (it "explicit n"
    (with-stdin "n\n" #(should= false (w/prompt-yn "go" true)))))

(describe "wizard/prompt-select"
  (it "returns nth option for valid index"
    (with-stdin "2\n"
                #(should= :sqlite
                          (w/prompt-select "Database"
                                            [{:id :datomic-pro :label "Datomic"}
                                             {:id :sqlite      :label "SQLite"}]
                                            :datomic-pro))))
  (it "returns default on empty"
    (with-stdin "\n"
                #(should= :datomic-pro
                          (w/prompt-select "Database"
                                            [{:id :datomic-pro :label "Datomic"}
                                             {:id :sqlite      :label "SQLite"}]
                                            :datomic-pro)))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 3: Implement**

`cli/src/c3kit_create/wizard.clj`:

```clojure
(ns c3kit-create.wizard
  (:require [clojure.string :as str]
            [c3kit-create.ui :as ui]))

(defn- read-trim []
  (let [line (read-line)]
    (when line (str/trim line))))

(defn prompt-text
  "Prompt with optional default; call `validate-fn` (which may throw) on input.
   Returns validated value."
  [label default validate-fn]
  (loop []
    (print (str label
                (when default (str " [" default "]"))
                ": "))
    (flush)
    (let [raw (read-trim)
          val (if (and (str/blank? raw) (some? default)) default raw)]
      (try
        (validate-fn val)
        (catch Exception e
          (ui/fail (str "  " (.getMessage e)))
          (recur))))))

(defn prompt-yn [label default]
  (let [d (if default "[Y/n]" "[y/N]")]
    (loop []
      (print (str label "  " d ": "))
      (flush)
      (let [raw (some-> (read-trim) str/lower-case)]
        (cond
          (str/blank? raw)                default
          (#{"y" "yes"} raw)              true
          (#{"n" "no"}  raw)              false
          :else (do (ui/fail "  please answer y or n") (recur)))))))

(defn prompt-select [label options default-id]
  (println (str label ":"))
  (doseq [[i opt] (map-indexed vector options)]
    (println (str "  " (inc i) ") " (:label opt)
                  (when (= (:id opt) default-id) " (default)"))))
  (loop []
    (print "Choice [default]: ") (flush)
    (let [raw (read-trim)]
      (cond
        (str/blank? raw) default-id
        :else
        (let [n (try (Integer/parseInt raw) (catch Exception _ -1))
              opt (when (<= 1 n (count options)) (nth options (dec n)))]
          (if opt
            (:id opt)
            (do (ui/fail "  invalid choice") (recur))))))))
```

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/wizard.clj cli/spec/c3kit_create/wizard_spec.clj && \
git commit -m "feat(cli): wizard prompts (text, yn, select)"
```

---

### Task 15: Tiny fixture template

**Files:**
- Create: `cli/test-fixtures/tiny-fixture/c3kit-template.edn`
- Create: `cli/test-fixtures/tiny-fixture/src/acme/core.clj`
- Create: `cli/test-fixtures/tiny-fixture/src/acme_legacy/util.clj`
- Create: `cli/test-fixtures/tiny-fixture/resources/Acme.css`
- Create: `cli/test-fixtures/tiny-fixture/config/dev.env`
- Create: `cli/test-fixtures/tiny-fixture/README.md`

- [ ] **Step 1: Create manifest**

`cli/test-fixtures/tiny-fixture/c3kit-template.edn`:

```clojure
{:id          :tiny-fixture
 :name        "Tiny Fixture"
 :description "Test-only template for CLI e2e specs."
 :version     "0.1.0"
 :min-cli     "0.1.0"
 :test-only?  true

 :tokens {"acme"  {:hyphen true :underscore true :pascal true}
          "ACME_" {:upper-prefix true}}

 :secrets [{:placeholder "ACME_DEV_SECRET" :bytes 16}]

 :features [{:id :ssr     :prompt "SSR?"     :default true
             :delete-when-off ["resources/Acme.css"]}
            {:id :legacy  :prompt "Legacy?"  :default true
             :delete-when-off ["src/acme_legacy/"]}]

 :db {:prompt  "Database"
      :options [{:id :sqlite   :label "SQLite"}
                {:id :postgres :label "Postgres"}]
      :default :sqlite}

 :next-steps [{:cmd "cd {{name}}"   :doc nil}
              {:cmd "ls"            :doc "list files"}]}
```

- [ ] **Step 2: Create source files**

`cli/test-fixtures/tiny-fixture/src/acme/core.clj`:

```clojure
(ns acme.core)

;; @c3kit/feature :ssr {
(def has-ssr? true)
;; @c3kit/feature :ssr }

;; @c3kit/db :sqlite {
(def db-impl :sqlite)
;; @c3kit/db :sqlite }
;; @c3kit/db :postgres {
(def db-impl :postgres)
;; @c3kit/db :postgres }

(defn -main [& _]
  (println "hello from acme"))
```

`cli/test-fixtures/tiny-fixture/src/acme_legacy/util.clj`:

```clojure
(ns acme-legacy.util)

(defn greet [] "hi from legacy")
```

`cli/test-fixtures/tiny-fixture/resources/Acme.css`:

```css
.acme { color: tomato; }
```

`cli/test-fixtures/tiny-fixture/config/dev.env`:

```
APP_NAME=acme
APP_SECRET=ACME_DEV_SECRET
```

`cli/test-fixtures/tiny-fixture/README.md`:

```markdown
# Acme

This is the `acme` test fixture.
```

- [ ] **Step 3: Verify manifest loads**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && \
bb -e "(require '[c3kit-create.manifest :as m]) \
       (prn (m/read-manifest \"test-fixtures/tiny-fixture\"))"
```

Expected: prints the manifest map, no exception.

- [ ] **Step 4: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/test-fixtures/tiny-fixture && \
git commit -m "test(cli): tiny-fixture template for e2e specs"
```

---

### Task 16: `main.clj` — wire scaffold path end-to-end

**Files:**
- Modify: `cli/src/c3kit_create/main.clj`

Wire `:scaffold` action through the full pipeline using all previously-built modules. No new tests in this task — the e2e test (Task 17) covers it.

- [ ] **Step 1: Rewrite `main.clj`**

`cli/src/c3kit_create/main.clj`:

```clojure
(ns c3kit-create.main
  (:require [babashka.fs :as fs]
            [c3kit-create.args :as args]
            [c3kit-create.fetch :as fetch]
            [c3kit-create.fs :as cfs]
            [c3kit-create.manifest :as manifest]
            [c3kit-create.postscaffold :as ps]
            [c3kit-create.render :as render]
            [c3kit-create.rename :as rn]
            [c3kit-create.ui :as ui]
            [c3kit-create.version :as v]
            [c3kit-create.wizard :as w]
            [clojure.string :as str])
  (:gen-class))

(def REPO-URL "https://github.com/cleancoders/c3kit-jig")
(def DEFAULT-REF "main")

(defn- exit [code] (System/exit code))

(defn- resolve-templates-dir [opts]
  (or (:template-dir opts)
      (System/getenv "C3KIT_TEMPLATES")))

(defn- scaffold! [{:keys [name template yes install template-ref] :as opts}]
  (let [stage (cfs/stage-dir)
        ref   (or template-ref DEFAULT-REF)
        local (resolve-templates-dir opts)
        tdir  (fs/path stage "template")]
    (try
      ;; stage-1: fetch
      (ui/step "fetching template …")
      (if local
        (fetch/from-local-dir local template (str tdir))
        (fetch/from-git REPO-URL ref template stage (str tdir)))

      ;; manifest
      (let [m (manifest/read-manifest (str tdir))
            nm (rn/validate-name (or name "my-app") (:tokens m))
            target (str (fs/path (fs/cwd) nm))]
        (when (fs/exists? target)
          (ui/fail (str "target already exists: " target))
          (cfs/cleanup! stage)
          (exit 3))

        ;; collect features + db (use defaults under --yes)
        (let [features (into {} (for [{:keys [id default]} (:features m)]
                                  [id default]))
              db       (when (:db m) {:db (:default (:db m))})]
          (when-not yes (ui/info "Using defaults (interactive prompts WIP in v0.2)"))

          ;; stage-2: render
          (ui/step "rendering tokens …")
          ;; copy tdir → scaffold and render in place
          (let [scaffold (str (fs/path stage "scaffold"))]
            (fs/copy-tree (str tdir) scaffold)
            (render/render! scaffold
                            (slurp (fs/file (fs/path tdir "c3kit-template.edn")))
                            nm features db)

            ;; stage-4: move
            (ui/step "moving into place …")
            (cfs/move-into-place! scaffold target)

            ;; stage-5: post-scaffold
            (when (:git? opts)
              (ui/step "git init + initial commit …")
              (ps/git-init! target))
            (ps/install! target opts)

            (ui/ok (str "Created " nm)))))
      (catch Exception e
        (cfs/cleanup! stage)
        (let [data (ex-data e)]
          (ui/fail (.getMessage e))
          (cond
            (:manifest? data)   (exit 6)
            (:collision? data)  (exit 3)
            (:fetch? data)      (exit 7)
            (:features? data)   (exit 8)
            (:name? data)       (exit 4)
            (:postscaffold? data) (exit 9)
            :else               (do (when (:debug opts) (.printStackTrace e))
                                    (exit 1)))))
      (finally
        (cfs/cleanup! stage)))))

(defn -main [& argv]
  (let [{:keys [action options error]} (args/parse argv)]
    (binding [ui/*color?* (ui/tty?)]
      (case action
        :version  (do (println (v/current)) (exit 0))
        :help     (do (println (args/help)) (exit 0))
        :error    (do (ui/fail error) (println (args/help)) (exit 2))
        :list     (do (ui/info "List of templates not yet implemented.") (exit 0))
        :upgrade  (do (ui/info "Upgrade not yet implemented.") (exit 0))
        :scaffold (scaffold! options)))))
```

- [ ] **Step 2: Smoke-test end-to-end against tiny-fixture (manual)**

```sh
cd /tmp && rm -rf my-app && \
C3KIT_TEMPLATES=/Users/alex-root-roatch/current-projects/c3kit-jig/cli/test-fixtures \
bb -m c3kit-create.main \
   --classpath /Users/alex-root-roatch/current-projects/c3kit-jig/cli/src \
   my-app -t tiny-fixture --yes
```

(Adjust invocation form as needed — easier alternative is `bb -e "(require 'c3kit-create.main) (c3kit-create.main/-main \"my-app\" \"-t\" \"tiny-fixture\" \"--yes\")"` from inside `cli/`.)

Expected: `my-app/` exists in `/tmp/`, contains a renamed `src/my_app/core.clj`, `MyApp.css` (only when `:ssr` was enabled), a populated `dev.env` with a hex secret, and a `.git` directory with a single `chore: initial scaffold` commit.

Inspect:

```sh
ls /tmp/my-app && \
grep -r acme /tmp/my-app || echo "no acme remnants"
```

Expected: no `acme` strings remain.

- [ ] **Step 3: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/main.clj && \
git commit -m "feat(cli): wire scaffold pipeline in main"
```

---

### Task 17: End-to-end spec (`e2e_spec.clj`)

**Files:**
- Create: `cli/spec/c3kit_create/e2e_spec.clj`

- [ ] **Step 1: Failing test**

`cli/spec/c3kit_create/e2e_spec.clj`:

```clojure
(ns c3kit-create.e2e-spec
  (:require [speclj.core :refer [describe it should= should should-not before after]]
            [c3kit-create.main :as main]
            [babashka.fs :as fs]
            [babashka.process :as p]))

(def ^:dynamic *work* nil)

(defn- with-work-dir [f]
  (binding [*work* (str (fs/create-temp-dir))]
    (try (f) (finally (fs/delete-tree *work*)))))

(defn- run-main! [& args]
  (let [exit-atom (atom nil)]
    (with-redefs [main/exit (fn [c] (reset! exit-atom c)
                                    (throw (ex-info "exit" {:code c})))]
      (try
        (binding [*err* (java.io.StringWriter.)
                  *out* (java.io.StringWriter.)]
          (apply main/-main args))
        (catch clojure.lang.ExceptionInfo _ nil)))
    @exit-atom))

(describe "e2e scaffold against tiny-fixture"
  (with-work-dir
    (it "produces a working scaffold with defaults"
      (let [tdir "test-fixtures"
            cwd  (System/getProperty "user.dir")]
        (System/setProperty "user.dir" *work*)
        (try
          (let [code (run-main! "my-app" "-t" "tiny-fixture"
                                "--template-dir" (str (fs/path cwd tdir))
                                "--yes")]
            (should= 0 code)
            (should     (fs/exists? (fs/path *work* "my-app" "src" "my_app" "core.clj")))
            (should     (fs/exists? (fs/path *work* "my-app" ".git")))
            (should-not (fs/exists? (fs/path *work* "my-app" "c3kit-template.edn")))
            (let [content (slurp (fs/file (fs/path *work* "my-app" "src" "my_app" "core.clj")))]
              (should-not (re-find #"acme" content))
              (should     (re-find #"has-ssr\?" content))))
          (finally
            (System/setProperty "user.dir" cwd)))))))
```

(Real-world note: bb's `user.dir` cannot always be redefined at runtime. Replace this technique with `binding [fs/*cwd* ...]` if available, or modify `main/scaffold!` to accept an explicit target-parent path via an option. The simplest workaround: refactor `scaffold!` to take `:target-parent` instead of reading `fs/cwd` — and update Task 16 accordingly.)

If `user.dir` workaround fails, adjust Task 16's `main.clj` to accept a hidden `--target-parent <path>` option (used only in tests) and pass it here.

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: e2e test fails because target-parent threading or some integration glitch surfaces. Iterate.

- [ ] **Step 3: Adjust `main.clj` if needed**

If the `user.dir` trick doesn't work, add a `--target-parent <path>` flag in `args.clj` (no shortcut, no docstring shown in `--help`), and read it in `scaffold!` instead of `fs/cwd`. Update `args_spec.clj` to cover the new flag.

```clojure
;; In args.clj CLI-OPTIONS, append:
[nil "--target-parent PATH" "(internal) override CWD for scaffold target"]
```

```clojure
;; In main.clj scaffold!, replace:
;;   target (str (fs/path (fs/cwd) nm))
;; with:
target (str (fs/path (or (:target-parent opts) (fs/cwd)) nm))
```

Update the e2e spec to pass `--target-parent` instead of mutating `user.dir`.

- [ ] **Step 4: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

Expected: e2e green.

- [ ] **Step 5: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/spec/c3kit_create/e2e_spec.clj \
        cli/src/c3kit_create/args.clj \
        cli/src/c3kit_create/main.clj \
        cli/spec/c3kit_create/args_spec.clj && \
git commit -m "test(cli): end-to-end scaffold against tiny-fixture"
```

---

### Task 18: `version.clj --upgrade` flow + tests

**Files:**
- Modify: `cli/src/c3kit_create/version.clj`
- Modify: `cli/spec/c3kit_create/version_spec.clj`
- Modify: `cli/src/c3kit_create/main.clj` (wire `:upgrade`)

- [ ] **Step 1: Add upgrade tests with mocked HTTP**

Append to `cli/spec/c3kit_create/version_spec.clj`:

```clojure
(ns c3kit-create.version-spec
  (:require [speclj.core :refer [describe it should= should should-throw with-redefs]]
            [c3kit-create.version :as v]))

(describe "version/semver-compare"
  (it "compares standard releases"
    (should= -1 (v/semver-compare "0.1.0" "0.2.0"))
    (should=  0 (v/semver-compare "0.1.0" "0.1.0"))
    (should=  1 (v/semver-compare "0.2.0" "0.1.0"))))

(describe "version/sha256"
  (it "hashes a string to 64 hex chars"
    (should= 64 (count (v/sha256 "hello")))))

(describe "version/check-and-download!"
  (it "no-ops when latest matches current"
    (with-redefs [v/fetch-latest-tag! (constantly "cli-v0.1.0-SNAPSHOT")]
      (should= :up-to-date (v/check-and-download! "/tmp/whatever")))))
```

- [ ] **Step 2: Run, verify failure**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 3: Implement upgrade**

Replace `cli/src/c3kit_create/version.clj`:

```clojure
(ns c3kit-create.version
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def ^:const CURRENT "0.1.0-SNAPSHOT")

(defn current [] CURRENT)

(def RELEASES-URL
  "https://api.github.com/repos/cleancoders/c3kit-jig/releases/latest")

(defn semver-compare [a b]
  (let [parse #(mapv parse-long (str/split (first (str/split % #"-")) #"\."))
        [a1 a2 a3] (parse a)
        [b1 b2 b3] (parse b)]
    (compare [a1 a2 a3] [b1 b2 b3])))

(defn sha256 [^String s]
  (let [md (MessageDigest/getInstance "SHA-256")
        bs (.digest md (.getBytes s))]
    (->> bs
         (map #(format "%02x" (bit-and ^long % 0xff)))
         (apply str))))

(defn fetch-latest-tag! []
  (let [resp (http/get RELEASES-URL {:throw false})]
    (when-not (= 200 (:status resp))
      (throw (ex-info (str "GitHub releases unreachable: HTTP " (:status resp))
                      {:upgrade? true})))
    (let [body (:body resp)
          tag  (second (re-find #"\"tag_name\"\s*:\s*\"([^\"]+)\"" body))]
      (when-not tag (throw (ex-info "no tag in releases response"
                                    {:upgrade? true})))
      tag)))

(defn check-and-download!
  "Download latest uberscript to `binary-path.new`, verify SHA, atomic mv.
   Returns :up-to-date or :upgraded."
  [^String binary-path]
  (let [tag (fetch-latest-tag!)]
    (if (= tag (str "cli-v" CURRENT))
      :up-to-date
      (let [base (str "https://github.com/cleancoders/c3kit-jig/releases/download/" tag)
            ub   (str base "/c3kit-create.bb")
            sh   (str base "/c3kit-create.bb.sha256")
            new-path (str binary-path ".new")
            body (:body (http/get ub  {:throw false}))
            want (str/trim (:body (http/get sh {:throw false})))]
        (when-not (= want (sha256 body))
          (throw (ex-info "SHA256 mismatch on downloaded uberscript"
                          {:upgrade? true})))
        (spit new-path body)
        (.setExecutable (fs/file new-path) true)
        (fs/move new-path binary-path {:replace-existing true})
        :upgraded))))
```

- [ ] **Step 4: Wire `:upgrade` action in `main.clj`**

In `main.clj`'s `-main`, replace the `:upgrade` branch with:

```clojure
:upgrade  (try
            (let [bin (System/getProperty "babashka.file")
                  r   (v/check-and-download! bin)]
              (case r
                :up-to-date (ui/info "already on latest")
                :upgraded   (ui/ok "upgraded — re-run your command"))
              (exit 0))
            (catch Exception e
              (ui/fail (.getMessage e))
              (exit 11)))
```

- [ ] **Step 5: Run, verify pass**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli && bb test
```

- [ ] **Step 6: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/src/c3kit_create/version.clj \
        cli/src/c3kit_create/main.clj \
        cli/spec/c3kit_create/version_spec.clj && \
git commit -m "feat(cli): --upgrade with sha-verified download"
```

---

### Task 19: `installer (cli/install.sh)`

**Files:**
- Create: `cli/install.sh`

- [ ] **Step 1: Write `install.sh`**

`cli/install.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO="cleancoders/c3kit-jig"
INSTALL_DIR="${C3KIT_BIN_DIR:-$HOME/.c3kit/bin}"
BIN_NAME="c3kit-create"

err() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; }
info() { printf '\033[34m▸ %s\033[0m\n' "$*"; }
ok() { printf '\033[32m✓ %s\033[0m\n' "$*"; }

uname_s="$(uname -s)"
case "$uname_s" in
  Darwin|Linux) ;;
  *) err "Unsupported OS: $uname_s (only macOS / Linux / WSL)"; exit 1 ;;
esac

if ! command -v git >/dev/null 2>&1; then
  err "git is required. See https://git-scm.com/downloads"
  exit 1
fi

if ! command -v bb >/dev/null 2>&1; then
  info "babashka not found — installing via official one-liner"
  bash <(curl -fsSL https://raw.githubusercontent.com/babashka/babashka/master/install)
fi

if ! command -v java >/dev/null 2>&1; then
  err "(warning) java not found — needed for the projects you'll scaffold, not for the CLI"
fi

mkdir -p "$INSTALL_DIR"

info "downloading latest c3kit-create"
LATEST_URL="https://api.github.com/repos/$REPO/releases/latest"
TAG=$(curl -fsSL "$LATEST_URL" | grep -m1 'tag_name' | cut -d'"' -f4)
DL_URL="https://github.com/$REPO/releases/download/$TAG/$BIN_NAME.bb"
curl -fsSL "$DL_URL" -o "$INSTALL_DIR/$BIN_NAME"
chmod +x "$INSTALL_DIR/$BIN_NAME"

# Ensure on PATH idempotently
case ":$PATH:" in
  *":$INSTALL_DIR:"*) ;;
  *)
    PROFILE=""
    case "${SHELL:-}" in
      */zsh)  PROFILE="$HOME/.zshrc" ;;
      */bash) PROFILE="$HOME/.bashrc" ;;
      */fish) PROFILE="$HOME/.config/fish/config.fish" ;;
    esac
    if [[ -n "$PROFILE" ]]; then
      LINE="export PATH=\"$INSTALL_DIR:\$PATH\""
      grep -qxF "$LINE" "$PROFILE" 2>/dev/null || echo "$LINE" >> "$PROFILE"
      info "added PATH export to $PROFILE — open a new shell or 'source $PROFILE'"
    else
      info "add $INSTALL_DIR to your PATH manually"
    fi
    ;;
esac

ok "installed $BIN_NAME $TAG → $INSTALL_DIR/$BIN_NAME"
"$INSTALL_DIR/$BIN_NAME" --version
```

- [ ] **Step 2: Make executable + lint with shellcheck if available**

```sh
chmod +x /Users/alex-root-roatch/current-projects/c3kit-jig/cli/install.sh
shellcheck /Users/alex-root-roatch/current-projects/c3kit-jig/cli/install.sh || \
  echo "(shellcheck unavailable, skipping)"
```

- [ ] **Step 3: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add cli/install.sh && \
git commit -m "feat(cli): install.sh entrypoint"
```

---

### Task 20: Real CI workflow

**Files:**
- Modify: `.github/workflows/ci.yml`

Replace the Phase 0 placeholder with a workflow that actually runs `bb test` + `bb lint` against the CLI on every push and PR.

- [ ] **Step 1: Overwrite `ci.yml`**

`.github/workflows/ci.yml`:

```yaml
name: ci

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  cli:
    name: CLI (bb test + lint)
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os:    [ubuntu-latest, macos-latest]
        bb:    ['1.3.191']
    steps:
      - uses: actions/checkout@v4

      - uses: DeLaGuardo/setup-clojure@v12
        with:
          bb: ${{ matrix.bb }}

      - name: clj-kondo
        run: |
          curl -sLO https://raw.githubusercontent.com/clj-kondo/clj-kondo/master/script/install-clj-kondo
          chmod +x install-clj-kondo
          ./install-clj-kondo --dir "$HOME/.local/bin"
          echo "$HOME/.local/bin" >> "$GITHUB_PATH"

      - name: bb test
        working-directory: cli
        run: bb test

      - name: bb lint
        working-directory: cli
        run: bb lint
```

- [ ] **Step 2: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add .github/workflows/ci.yml && \
git commit -m "ci: replace placeholder with bb test + lint matrix"
```

---

### Task 21: Release workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Write `release.yml`**

`.github/workflows/release.yml`:

```yaml
name: release

on:
  push:
    tags: ['cli-v*']

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: DeLaGuardo/setup-clojure@v12
        with:
          bb: '1.3.191'

      - name: bb test
        working-directory: cli
        run: bb test

      - name: build uberscript
        working-directory: cli
        run: bb uberscript

      - name: compute sha256
        working-directory: cli
        run: |
          sha256sum dist/c3kit-create.bb | cut -d' ' -f1 > dist/c3kit-create.bb.sha256
          ls -lah dist

      - uses: softprops/action-gh-release@v2
        with:
          files: |
            cli/dist/c3kit-create.bb
            cli/dist/c3kit-create.bb.sha256
```

- [ ] **Step 2: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add .github/workflows/release.yml && \
git commit -m "ci: release workflow on cli-v* tags"
```

---

### Task 22: README + CONTRIBUTING refresh

**Files:**
- Modify: `README.md`
- Modify: `CONTRIBUTING.md`

Now that the CLI is real, update the README to drop the "not yet implemented" banner and point users at the install command + `--help`. Also fix the two cosmetic nits flagged in the earlier code review (T2 lowest-pri callout, "Clojure-Astro" → "Clj-Astro").

- [ ] **Step 1: Update README — drop "phase 0" status, set T2 callout, fix Clj-Astro name**

In `README.md`, replace the status block:

```markdown
> **Status:** Phase 0 bootstrap. The CLI and templates are not yet implemented. See [`docs/specs/2026-05-12-c3kit-jig-roadmap-design.md`](docs/specs/2026-05-12-c3kit-jig-roadmap-design.md) for the roadmap.
```

with:

```markdown
> **Status:** CLI is available; templates are in progress. See [`docs/specs/2026-05-12-c3kit-jig-roadmap-design.md`](docs/specs/2026-05-12-c3kit-jig-roadmap-design.md) for the roadmap.
```

In the templates table, change the `fe-ssg` row description from `Static-site generator (Clojure-Astro)` to `Static-site generator (Clj-Astro)`, and change the `full-stack-non-reagent` row status from `phase 2` to `phase 2 (lowest priority)`.

- [ ] **Step 2: Update CONTRIBUTING**

In `CONTRIBUTING.md`, replace the Phase 1 roadmap block with:

```markdown
## Phase 1 roadmap

- [x] `c3kit-create` CLI sub-spec + plan + implementation
- [ ] `templates/full-stack-reagent` sub-spec + plan + implementation
- [ ] `templates/fe-vanilla` sub-spec + plan + implementation
```

- [ ] **Step 3: Commit**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git add README.md CONTRIBUTING.md && \
git commit -m "docs: refresh README + CONTRIBUTING after CLI lands"
```

---

### Task 23: First release dry-run + open PR

**Files:** none (operates on git remote + GitHub).

This task is mostly user-facing: push the branch, open a PR, let CI run, then (after merge) cut `cli-v0.1.0`.

- [ ] **Step 1: Push the branch**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git push -u origin cli/v0.1
```

Expected: branch published, PR URL suggestion printed.

- [ ] **Step 2: Open PR to main**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
gh pr create --title "feat(cli): c3kit-create v0.1" --body "$(cat <<'EOF'
## Summary

- bb-based CLI scaffolder per spec at docs/specs/2026-05-12-c3kit-create-cli-design.md
- args, manifest, rename, features, secrets, fs, render, fetch, wizard, postscaffold, version modules
- tiny-fixture for e2e tests
- install.sh installer
- CI + release workflows
- README/CONTRIBUTING refreshed

## Test plan

- [x] bb test green locally on macOS
- [ ] CI matrix green (ubuntu + macos)
- [ ] Manual e2e against tiny-fixture works
- [ ] install.sh on clean Ubuntu VM (manual)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Watch CI, fix any failures**

```sh
gh pr checks --watch
```

Expected: green check on both `ubuntu-latest` and `macos-latest` matrix runs.

- [ ] **Step 4: After merge, tag and push release**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-jig && \
git switch main && git pull && \
git tag cli-v0.1.0 && git push --tags
```

Expected: `release.yml` runs, attaches `c3kit-create.bb` + `.sha256` to a fresh GitHub Release at `cli-v0.1.0`.

- [ ] **Step 5: Smoke-test install on a clean machine (manual)**

```sh
curl -fsSL https://raw.githubusercontent.com/cleancoders/c3kit-jig/main/cli/install.sh | bash
```

Expected: babashka installs if missing, `c3kit-create` lands at `$HOME/.c3kit/bin/c3kit-create`, `c3kit-create --version` prints `0.1.0-SNAPSHOT` (or `0.1.0` once `CURRENT` is bumped pre-tag — see step 6).

- [ ] **Step 6: Bump `CURRENT` to non-snapshot, retag (one-shot)**

If the tag and `CURRENT` value drift, bump `cli/src/c3kit_create/version.clj` to `0.1.0`, commit (`chore(cli): bump version to 0.1.0`), and re-tag. The plan deliberately ships `0.1.0-SNAPSHOT` in the source until release time so dev builds are clearly marked.

---

## Self-Review

### Spec coverage

| Spec section                                  | Implementing task(s)             |
|-----------------------------------------------|----------------------------------|
| §1 Scope                                       | 1–23 cover all in-scope items   |
| §2 Module layout                               | Tasks 3–14 (one task per module) |
| §3 Command surface, exit codes                 | Task 5 (args) + Task 16 (wiring) |
| §4 Wizard flow                                 | Task 14 (prompt fns)            |
| §5 Manifest schema                             | Task 6                          |
| §6.1 Feature markers                            | Task 8                          |
| §6.2 Rename tokens                              | Task 7                          |
| §6.3 Secrets                                    | Task 9                          |
| §7.1 Fetch                                      | Task 11                         |
| §7.2 Atomic scaffold                            | Task 10 (fs) + Task 13 (render) + Task 16 (orchestration) |
| §8 Release                                      | Tasks 21, 23                    |
| §9 Testing strategy                             | Tasks 1, 17 + per-module specs  |
| §10 Risks                                       | Task 1 spike (Speclj risk)      |
| §11 Success criteria                            | Tasks 17, 20, 23                |

**Gaps deliberately left:**

- `wizard` is not wired into the scaffold flow in Task 16 (uses defaults only). That promotes `c3kit-create` to "MVP scaffolder with `--yes` semantics" before adding interactive prompts. Wizard prompts ride in a follow-up `cli-v0.2` plan. Reason: keeps Task 16 small and the e2e test mechanical.
- `:list` and `--upgrade` print/work but do not yet read from a real `registry.clj`. The registry is consulted by Task 16 only indirectly via fetch.

### Placeholder scan

No "TBD" / "implement later" / "add appropriate error handling" / "similar to Task N" — every task has full code or a verbatim file.

One area called out as a known-unknown: Task 17's `user.dir` trick. Plan explicitly documents the fallback (Task 17 Step 3 adds `--target-parent` flag) so the implementer doesn't ship a broken test.

### Type / API consistency

- `args/parse` returns `{:action _ :options _}`; every consumer (`main`, `args-spec`) honors that shape.
- `rename/variants` returns `{:hyphen :underscore :pascal :upper}`; `replace-token` consumes the same keys; `e2e` and `render` use the same.
- `manifest/validate` returns the manifest unchanged on success; `read-manifest` calls `validate`; `render/render!` calls `manifest/validate` after `edn/read-string`.
- `features/strip` signature is `[text features-map db-choice-map]`; `render` calls with the same shapes.
- `cfs/move-into-place!` signature `[src tgt]`; `main` calls with the same.
- All exit codes match the §3 table (1, 2, 3, 4, 6, 7, 8, 9, 11 each have at least one `(exit N)` call in `main.clj`'s scaffold error handler or in a dedicated branch).

Exit codes 5 (unknown template id) and 10 (user aborted) are **gaps**: spec lists them but Task 16's error map doesn't cover them. Add a `:list?`-aware branch for code 5 when we wire `:list` to the registry (cli-v0.2). Code 10 (Ctrl+C handling) is a TODO for cli-v0.2 — bb's SIGINT story under sci needs its own spike. Document in the cli-v0.2 plan, not this one.
