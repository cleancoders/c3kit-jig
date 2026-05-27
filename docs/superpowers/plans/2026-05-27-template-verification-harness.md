# Template Verification Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a babashka verification harness that scaffolds the full-stack-reagent template per combo and asserts no-cruft, combo files, no residual markers, hyphenated ns forms, clean clj specs, clj-kondo lint, cljfmt formatting, one-shot cljs specs, and dev-server boot.

**Architecture:** A template-agnostic (Clojure/c3kit family) engine under `verification/`, outside `templates/` so it cannot leak into scaffolds. The engine reads a per-template descriptor (`verify.edn`), scaffolds a combo by shelling out to the CLI (`bb -cp ../cli/src -m c3kit-jig.main create …`), then runs the checks enabled for the tier. Pure decision cores (ns-form detection, log-line filter, tool-output parsing) are unit-tested with speclj; effectful shells (FS walks, process runs) are tested against temp-dir fixtures or exercised by a real run.

**Tech Stack:** Babashka, `babashka.fs`, `babashka.process`, `clojure.tools.cli`, speclj (test runner), clj-kondo + cljfmt binaries, Playwright (via c3kit scaffold cljs).

**Scope guard:** This is the **harness only**. It surfaces template defects as failing checks. Do **not** fix the template's namespace rendering, log noise, cljs runner, copy mechanism, or add `.clj-kondo`/`cljfmt.edn` configs to the template — those are a later pass. The harness is expected to run partially **red** on first execution; that is success.

---

## File Structure

Create:
- `verification/bb.edn` — paths, deps, tasks (`test`, `verify`, `verify-all`).
- `verification/src/c3kit_verify/checks.clj` — pure decision cores + effectful check shells.
- `verification/src/c3kit_verify/engine.clj` — descriptor load, scaffold invocation, dispatch, report, `-main`.
- `verification/spec/c3kit_verify/checks_spec.clj` — speclj tests for pure cores.
- `verification/templates/full-stack-reagent/verify.edn` — per-template descriptor.
- `verification/templates/full-stack-reagent/combos/*.expected.edn` — **moved** from `templates/full-stack-reagent/spec/combos/`.

Modify:
- `.github/workflows/template-full-stack-reagent.yml` — rewrite `scaffold-matrix` to call the engine.

Delete:
- `templates/full-stack-reagent/dev/verify-scaffold.bb` — relocated/superseded by the engine.

---

## Task 1: Verification project skeleton

**Files:**
- Create: `verification/bb.edn`
- Create: `verification/src/c3kit_verify/checks.clj`
- Create: `verification/spec/c3kit_verify/checks_spec.clj`

- [ ] **Step 1: Create `verification/bb.edn`**

```clojure
{:paths ["src" "spec"]
 :deps  {org.clojure/tools.cli {:mvn/version "1.1.230"}
         speclj/speclj         {:mvn/version "3.12.0"}}
 :tasks
 {test       {:doc      "Run harness unit tests"
              :requires ([speclj.cli])
              :task     (speclj.cli/run "-c" "spec")}
  verify     {:doc      "Verify one combo. e.g. bb verify --combo memory-defaults --tier full"
              :requires ([c3kit-verify.engine])
              :task     (apply c3kit-verify.engine/-main *command-line-args*)}
  verify-all {:doc      "Verify every combo at its declared tier"
              :requires ([c3kit-verify.engine])
              :task     (c3kit-verify.engine/run-all *command-line-args*)}}}
```

- [ ] **Step 2: Create a minimal `verification/src/c3kit_verify/checks.clj`**

```clojure
(ns c3kit-verify.checks
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

;; Pure cores and effectful shells are added in later tasks.
```

- [ ] **Step 3: Create a placeholder spec proving the runner works**

`verification/spec/c3kit_verify/checks_spec.clj`:

```clojure
(ns c3kit-verify.checks-spec
  (:require [speclj.core :refer [describe it should=]]
            [c3kit-verify.checks :as sut]))

(describe "harness test runner"
  (it "runs" (should= 1 1)))
```

- [ ] **Step 4: Run the test suite to confirm it works**

Run: `cd verification && bb test`
Expected: PASS — `1 examples, 0 failures`.

- [ ] **Step 5: Commit**

```bash
git add verification/
git commit -m "chore(verification): scaffold harness project skeleton

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Pure decision cores (ns-form, clean-spec, tool-output, cljs-result)

**Files:**
- Modify: `verification/src/c3kit_verify/checks.clj`
- Test: `verification/spec/c3kit_verify/checks_spec.clj`

- [ ] **Step 1: Write failing tests for the four pure cores**

Replace the body of `checks_spec.clj` with:

```clojure
(ns c3kit-verify.checks-spec
  (:require [speclj.core :refer [describe context it should= should should-not should-be-nil]]
            [c3kit-verify.checks :as sut]))

(describe "ns-prefix-violation"
  (it "flags an underscore ns prefix"
    (should= "my_app.main"
             (sut/ns-prefix-violation "(ns my_app.main\n  (:require [x]))" "my_app")))
  (it "flags an underscore ns prefix on a nested ns"
    (should= "my_app.auth.user"
             (sut/ns-prefix-violation "(ns my_app.auth.user)" "my_app")))
  (it "passes a hyphenated ns prefix"
    (should-be-nil (sut/ns-prefix-violation "(ns my-app.main)" "my_app")))
  (it "ignores underscores that are not the ns prefix (env strings in body)"
    (should-be-nil (sut/ns-prefix-violation "(ns my-app.config)\n(def e \"my_app.env\")" "my_app")))
  (it "returns nil when there is no ns form"
    (should-be-nil (sut/ns-prefix-violation ";; just a comment\n(+ 1 2)" "my_app"))))

(describe "clean-spec-output?"
  (it "passes plain speclj output"
    (should (:ok? (sut/clean-spec-output? "....\n\n4 examples, 0 failures\n"))))
  (it "fails on a WARN line"
    (let [r (sut/clean-spec-output? "....\n2024-01-01 12:00:00 WARN something happened\n3 examples, 0 failures")]
      (should-not (:ok? r))
      (should= 1 (count (:offending r)))))
  (it "fails on a bare level marker"
    (should-not (:ok? (sut/clean-spec-output? "INFO booting\n1 examples, 0 failures"))))
  (it "fails on an ISO timestamp line with no level"
    (should-not (:ok? (sut/clean-spec-output? "2026-05-27T10:00:00 starting\n1 examples, 0 failures")))))

(describe "tool-result"
  (it "fails when the config file is absent"
    (let [r (sut/tool-result {:config-exists? false :exit 0 :tool "clj-kondo" :config ".clj-kondo/config.edn"})]
      (should-not (:ok? r))
      (should (re-find #"no .clj-kondo/config.edn" (:detail r)))))
  (it "passes on config present + exit 0"
    (should (:ok? (sut/tool-result {:config-exists? true :exit 0 :tool "cljfmt" :config "cljfmt.edn"}))))
  (it "fails on config present + nonzero exit"
    (should-not (:ok? (sut/tool-result {:config-exists? true :exit 2 :tool "clj-kondo" :config ".clj-kondo/config.edn"})))))

(describe "parse-cljs-result"
  (it "passes on examples>0 and 0 failures and exit 0"
    (should (:ok? (sut/parse-cljs-result "12 examples, 0 failures" 0))))
  (it "fails on failures>0"
    (should-not (:ok? (sut/parse-cljs-result "12 examples, 3 failures" 0))))
  (it "fails when no examples ran"
    (should-not (:ok? (sut/parse-cljs-result "0 examples, 0 failures" 0))))
  (it "fails on nonzero exit"
    (should-not (:ok? (sut/parse-cljs-result "5 examples, 0 failures" 1)))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd verification && bb test`
Expected: FAIL — `Unable to resolve symbol: ns-prefix-violation` (and the other cores).

- [ ] **Step 3: Implement the four pure cores**

Append to `verification/src/c3kit_verify/checks.clj`:

```clojure
;; --- Pure decision cores ---

(def ^:private NS-RE #"\(ns\s+([A-Za-z0-9_.*+!?<>=$-]+)")

(defn ns-prefix-violation
  "Return the offending ns symbol string if the file's ns form uses the
   underscore project prefix, else nil. `underscore` is the snake_case project
   name (e.g. \"my_app\"). A single-word project (hyphen == underscore) never
   violates."
  [content underscore]
  (when-let [sym (second (re-find NS-RE content))]
    (when (or (= sym underscore) (str/starts-with? sym (str underscore ".")))
      sym)))

(def ^:private LOG-LINE-RE
  ;; A timestamp (date or clock) OR a standalone log level marker.
  #"(?x)
    (\d{4}-\d{2}-\d{2})        # ISO date
    | (\d{2}:\d{2}:\d{2})      # clock time
    | \b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\b")

(defn clean-spec-output?
  "Pass iff no line looks like a log line (timestamp or level marker).
   speclj progress/summary lines contain neither, so they pass."
  [s]
  (let [offending (->> (str/split-lines (or s ""))
                       (filter #(re-find LOG-LINE-RE %))
                       vec)]
    {:ok? (empty? offending) :offending offending}))

(defn tool-result
  "Decide pass/fail for a config-driven external tool (clj-kondo, cljfmt).
   Missing config is a failure; otherwise pass iff exit 0."
  [{:keys [config-exists? exit tool config]}]
  (cond
    (not config-exists?) {:ok? false :detail (str "no " config " shipped")}
    (zero? exit)         {:ok? true  :detail (str tool " clean")}
    :else                {:ok? false :detail (str tool " reported findings (exit " exit ")")}))

(defn parse-cljs-result
  "Parse a speclj-style 'N examples, M failures' summary. Pass iff exit 0,
   examples>0, failures=0."
  [out exit]
  (let [m (re-find #"(\d+)\s+examples?,\s+(\d+)\s+failures?" (or out ""))
        examples (some-> m (nth 1) parse-long)
        failures (some-> m (nth 2) parse-long)]
    {:ok?      (and (zero? exit) (some? examples) (pos? examples) (= 0 failures))
     :examples examples
     :failures failures}))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd verification && bb test`
Expected: PASS — all examples, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add verification/src/c3kit_verify/checks.clj verification/spec/c3kit_verify/checks_spec.clj
git commit -m "feat(verification): pure decision cores for harness checks

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Effectful check shells (cruft, ns-hyphen, residue, combo)

These read the scaffold directory. Each returns `{:check <kw> :ok? <bool> :detail <str>}`. They are tested against a temp-dir fixture built in the spec.

**Files:**
- Modify: `verification/src/c3kit_verify/checks.clj`
- Test: `verification/spec/c3kit_verify/checks_spec.clj`

- [ ] **Step 1: Write failing tests using a temp-dir fixture**

Append to `checks_spec.clj`:

```clojure
(require '[babashka.fs :as fs])

(defn- spit-file! [root rel content]
  (let [f (fs/file (fs/path root rel))]
    (fs/create-dirs (fs/parent f))
    (spit f content)))

(defn- temp-scaffold! []
  (let [root (str (fs/create-temp-dir {:prefix "harness-test-"}))]
    (spit-file! root "src/clj/my_app/main.clj" "(ns my_app.main)\n")
    (spit-file! root "src/clj/my_app/config.clj" "(ns my-app.config)\n(def e \"my_app.env\")\n")
    (spit-file! root "deps.edn" "{:paths [\"src\"]}\n")
    root))

(describe "cruft-check"
  (it "flags an .iml and a .cpcache dir"
    (let [root (temp-scaffold!)]
      (spit-file! root "full-stack-reagent.iml" "x")
      (spit-file! root ".cpcache/foo.edn" "x")
      (let [r (sut/cruft-check root ["*.iml" ".cpcache"])]
        (should-not (:ok? r))
        (should (re-find #"full-stack-reagent.iml" (:detail r)))
        (fs/delete-tree root))))
  (it "passes a clean scaffold"
    (let [root (temp-scaffold!)
          r    (sut/cruft-check root ["*.iml" ".cpcache" "target"])]
      (should (:ok? r))
      (fs/delete-tree root))))

(describe "ns-hyphen-check"
  (it "flags the underscore ns form, ignores body strings and hyphenated ns"
    (let [root (temp-scaffold!)
          r    (sut/ns-hyphen-check root "my_app" [])]
      (should-not (:ok? r))
      (should (re-find #"my_app.main" (:detail r)))
      (should-not (re-find #"config.clj" (:detail r)))
      (fs/delete-tree root))))

(describe "residue-check"
  (it "flags a surviving @c3kit/feature marker"
    (let [root (temp-scaffold!)]
      (spit-file! root "src/clj/my_app/x.clj" ";; @c3kit/feature :auth = foo")
      (let [r (sut/residue-check root)]
        (should-not (:ok? r))
        (fs/delete-tree root))))
  (it "passes with no markers"
    (let [root (temp-scaffold!)
          r    (sut/residue-check root)]
      (should (:ok? r))
      (fs/delete-tree root))))

(describe "combo-check"
  (it "checks must-exist / must-not-exist / file-contains / file-not-contains"
    (let [root (temp-scaffold!)
          ok   (sut/combo-check root {:must-exist ["deps.edn"]
                                      :must-not-exist ["nope.txt"]
                                      :file-contains {"deps.edn" [":paths"]}
                                      :file-not-contains {"deps.edn" ["banana"]}})
          bad  (sut/combo-check root {:must-exist ["missing.clj"]})]
      (should (:ok? ok))
      (should-not (:ok? bad))
      (fs/delete-tree root))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd verification && bb test`
Expected: FAIL — `Unable to resolve symbol: cruft-check`.

- [ ] **Step 3: Implement the effectful shells**

Append to `verification/src/c3kit_verify/checks.clj`:

```clojure
;; --- Effectful check shells ---

(defn- rel [root ^java.io.File f]
  (str (.relativize (.toPath (fs/file root)) (.toPath f))))

(defn- clj-files [root]
  (->> (file-seq (fs/file root))
       (filter #(.isFile %))
       (filter #(re-find #"\.(clj|cljc|cljs)$" (.getName %)))))

(defn cruft-check
  "Fail if any glob in `globs` matches a path inside the scaffold."
  [root globs]
  (let [hits (mapcat (fn [g] (map str (fs/glob root g))) globs)
        hits (sort (distinct (map #(rel root (fs/file %)) hits)))]
    {:check :no-cruft
     :ok?   (empty? hits)
     :detail (if (empty? hits) "no cruft" (str "cruft present: " (str/join ", " hits)))}))

(defn ns-hyphen-check
  "Fail if any clj/cljc/cljs file's ns form uses the underscore project prefix.
   Files whose relative path is in `exempt` are skipped."
  [root underscore exempt]
  (let [exempt (set exempt)
        viols  (for [^java.io.File f (clj-files root)
                     :let [r (rel root f)]
                     :when (not (exempt r))
                     :let [sym (ns-prefix-violation (slurp f) underscore)]
                     :when sym]
                 (str r " -> " sym))]
    {:check :ns-hyphen
     :ok?   (empty? viols)
     :detail (if (empty? viols) "ns forms hyphenated" (str "underscore ns forms: " (str/join ", " viols)))}))

(defn residue-check
  "Fail if any @c3kit/feature or @c3kit/db marker survived in the scaffold."
  [root]
  (let [{:keys [out]} (p/sh "grep" "-rEl" "@c3kit/(feature|db)" (str root))
        hits (->> (str/split-lines (or out "")) (remove str/blank?) (map #(rel root (fs/file %))) sort)]
    {:check :residue
     :ok?   (empty? hits)
     :detail (if (empty? hits) "no residue" (str "residual markers in: " (str/join ", " hits)))}))

(defn combo-check
  "Port of verify-scaffold's structural assertions for one combo edn."
  [root {:keys [must-exist must-not-exist file-contains file-not-contains]}]
  (let [errs (atom [])
        full (fn [p] (str (fs/path root p)))]
    (doseq [p must-exist]
      (when-not (fs/exists? (full p)) (swap! errs conj (str "must-exist missing: " p))))
    (doseq [p must-not-exist]
      (when (fs/exists? (full p)) (swap! errs conj (str "must-not-exist present: " p))))
    (doseq [[p strs] file-contains s strs]
      (if-not (fs/exists? (full p))
        (swap! errs conj (str "file-contains: missing file " p))
        (when-not (str/includes? (slurp (full p)) s)
          (swap! errs conj (str "file-contains miss: " p " <- " (pr-str s))))))
    (doseq [[p strs] file-not-contains s strs]
      (when (and (fs/exists? (full p)) (str/includes? (slurp (full p)) s))
        (swap! errs conj (str "file-not-contains hit: " p " -> " (pr-str s)))))
    {:check :combo
     :ok?   (empty? @errs)
     :detail (if (empty? @errs) "combo ok" (str/join "; " @errs))}))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd verification && bb test`
Expected: PASS — all examples, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add verification/src/c3kit_verify/checks.clj verification/spec/c3kit_verify/checks_spec.clj
git commit -m "feat(verification): effectful checks (cruft, ns-hyphen, residue, combo)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Effectful spec/lint/fmt/server checks

These run external processes inside the scaffold. They reuse the pure cores from Task 2. Tested with stub commands where practical; the real-process behavior is exercised by the engine run in Task 6.

**Files:**
- Modify: `verification/src/c3kit_verify/checks.clj`
- Test: `verification/spec/c3kit_verify/checks_spec.clj`

- [ ] **Step 1: Write failing tests for the process-running checks**

Append to `checks_spec.clj`:

```clojure
(describe "clj-clean-check (via injected runner)"
  (it "passes clean output"
    (let [r (sut/clj-clean-check* {:exit 0 :out "....\n4 examples, 0 failures"})]
      (should (:ok? r))))
  (it "fails on log noise even with exit 0"
    (let [r (sut/clj-clean-check* {:exit 0 :out "WARN boom\n4 examples, 0 failures"})]
      (should-not (:ok? r))))
  (it "fails on nonzero exit"
    (let [r (sut/clj-clean-check* {:exit 1 :out "4 examples, 1 failures"})]
      (should-not (:ok? r)))))

(describe "tool-check (via injected result)"
  (it "fails when config missing"
    (should-not (:ok? (sut/tool-check* :lint {:config-exists? false :exit 0
                                              :tool "clj-kondo" :config ".clj-kondo/config.edn"}))))
  (it "tags the check keyword"
    (should= :fmt (:check (sut/tool-check* :fmt {:config-exists? true :exit 0
                                                 :tool "cljfmt" :config "cljfmt.edn"})))))

(describe "cljs-check (via injected runner)"
  (it "passes on a green one-shot run"
    (should (:ok? (sut/cljs-check* {:exit 0 :out "12 examples, 0 failures"}))))
  (it "fails when it never ran"
    (should-not (:ok? (sut/cljs-check* {:exit 0 :out "0 examples, 0 failures"})))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd verification && bb test`
Expected: FAIL — `Unable to resolve symbol: clj-clean-check*`.

- [ ] **Step 3: Implement the checks (pure `*`-cores + effectful wrappers)**

Append to `verification/src/c3kit_verify/checks.clj`:

```clojure
;; --- Spec / lint / fmt / server checks ---
;; Each has a pure `*`-core (takes a result map) and an effectful wrapper
;; (runs the process). Tests target the pure cores.

(defn clj-clean-check* [{:keys [exit out]}]
  (let [clean (clean-spec-output? out)]
    {:check :clj-clean
     :ok?   (and (zero? exit) (:ok? clean))
     :detail (cond
               (not (zero? exit)) (str "clj specs exit " exit)
               (:ok? clean)       "clj specs clean"
               :else              (str "log noise: " (str/join " | " (take 3 (:offending clean)))))}))

(defn clj-clean-check [root cmd]
  (let [{:keys [exit out err]} (apply p/sh {:dir root} cmd)]
    (clj-clean-check* {:exit exit :out (str out "\n" err)})))

(defn tool-check* [check result]
  (assoc (tool-result result) :check check))

(defn tool-check
  "check  — :lint or :fmt
   cmd    — command vector run inside the scaffold
   config — relative path that must exist (e.g. \".clj-kondo/config.edn\")
   tool   — display name"
  [check root cmd config tool]
  (let [config-exists? (fs/exists? (fs/path root config))
        {:keys [exit]} (if config-exists?
                         (apply p/sh {:dir root :continue true} cmd)
                         {:exit 0})]
    (tool-check* check {:config-exists? config-exists? :exit exit :tool tool :config config})))

(defn cljs-check* [{:keys [exit out]}]
  (assoc (parse-cljs-result out exit) :check :cljs-run
         :ok? (:ok? (parse-cljs-result out exit))))

(defn cljs-check [root cmd]
  (let [{:keys [exit out err]} (apply p/sh {:dir root :continue true} cmd)]
    (cljs-check* {:exit exit :out (str out "\n" err)})))

(defn server-boot-check
  "Run migrate, start the server in the background, poll its port for any HTTP
   response, then kill it. Pass iff a response arrives before timeout."
  [root {:keys [migrate run port]}]
  (apply p/sh {:dir root :continue true} migrate)
  (let [proc (apply p/process {:dir root :extra-env {"PORT" (str port)}} run)
        url  (str "http://localhost:" port "/")
        deadline (+ (System/currentTimeMillis) 60000)]
    (try
      (loop []
        (let [resp (try (-> (p/sh "curl" "-s" "-o" "/dev/null" "-w" "%{http_code}" url) :out)
                        (catch Exception _ nil))]
          (cond
            (and resp (re-matches #"[1-5]\d\d" (str/trim (or resp "")))) {:check :server-boot :ok? true :detail (str "HTTP " (str/trim resp))}
            (> (System/currentTimeMillis) deadline) {:check :server-boot :ok? false :detail "server did not respond within 60s"}
            :else (do (Thread/sleep 1000) (recur)))))
      (finally (p/destroy-tree proc)))))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd verification && bb test`
Expected: PASS — all examples, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add verification/src/c3kit_verify/checks.clj verification/spec/c3kit_verify/checks_spec.clj
git commit -m "feat(verification): spec/lint/fmt/server-boot checks

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Descriptor + relocate combos

**Files:**
- Create: `verification/templates/full-stack-reagent/verify.edn`
- Move: `templates/full-stack-reagent/spec/combos/*.expected.edn` → `verification/templates/full-stack-reagent/combos/`

- [ ] **Step 1: Move the combo files (git mv preserves history)**

```bash
mkdir -p verification/templates/full-stack-reagent/combos
git mv templates/full-stack-reagent/spec/combos/*.expected.edn \
       verification/templates/full-stack-reagent/combos/
# remove the now-empty source dir if git leaves it
rmdir templates/full-stack-reagent/spec/combos 2>/dev/null || true
```

Run: `ls verification/templates/full-stack-reagent/combos/`
Expected: the 8 `*.expected.edn` files listed.

- [ ] **Step 2: Create the descriptor `verification/templates/full-stack-reagent/verify.edn`**

```clojure
{:cli-templates-dir "../templates"          ; relative to verification/, passed as --template-dir
 :template-id       "full-stack-reagent"

 :combos {:memory-defaults          {:tier :full}
          :memory-minimal           {:tier :light}
          :memory-no-auth           {:tier :light}
          :memory-no-ssr-no-content {:tier :light}
          :memory-no-websocket      {:tier :light}
          :sqlite-defaults          {:tier :light}
          :postgres-defaults        {:tier :light}
          :datomic-pro-defaults     {:tier :light}}

 ;; fs/glob patterns relative to the scaffold root that must NOT be present.
 ;; Template .gitignore patterns + standard template-dev files.
 :denylist ["**.iml"
            ".cpcache"
            "target"
            "node_modules"
            "resources/prerender"
            "resources/prerendered"
            "**/ssr/prerender_pages.cljs"
            "c3kit-template.edn"
            "c3kit-template.bb"
            "spec/hook_test.bb"
            "spec/combos"
            "dev/verify-scaffold.bb"
            ".c3kit-jig-context.edn"]

 ;; Relative paths skipped by :ns-hyphen (none today; .edn files are not scanned).
 :ns-prefix-exempt []

 :commands {:clj-spec ["clojure" "-M:test:spec"]
            :cljs-once ["clojure" "-M:test:cljs" "once"]
            :lint     ["clj-kondo" "--lint" "src" "spec" "dev"]
            :lint-config ".clj-kondo/config.edn"
            :fmt      ["cljfmt" "check" "src" "spec" "dev"]
            :fmt-config "cljfmt.edn"
            :migrate  ["clojure" "-M:test:migrate"]
            :run      ["clojure" "-M:test:run"]
            :port     8123}

 :checks {:no-cruft true :combo true :residue true :ns-hyphen true
          :lint true :fmt true
          :clj-clean true :cljs-run true :server-boot true}}
```

Note on glob patterns: babashka `fs/glob` is recursive-aware; `**.iml` matches `.iml` files at any depth, `**/ssr/prerender_pages.cljs` matches the generated SSR require at any depth. Directory names like `.cpcache`, `target`, `spec/combos` match the directory entry.

- [ ] **Step 3: Verify the descriptor reads as valid EDN**

Run: `cd verification && bb -e '(clojure.edn/read-string (slurp "templates/full-stack-reagent/verify.edn"))' >/dev/null && echo OK`
Expected: `OK`.

- [ ] **Step 4: Commit**

```bash
git add verification/templates/full-stack-reagent/
git commit -m "feat(verification): add full-stack-reagent descriptor, relocate combos out of template

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Engine + first real run

**Files:**
- Create: `verification/src/c3kit_verify/engine.clj`
- Delete: `templates/full-stack-reagent/dev/verify-scaffold.bb`

- [ ] **Step 1: Implement `verification/src/c3kit_verify/engine.clj`**

```clojure
(ns c3kit-verify.engine
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [c3kit-verify.checks :as checks]))

(def ^:private here (System/getProperty "user.dir")) ; verification/ when run via bb task

(defn- descriptor-path [template]
  (str (fs/path here "templates" template "verify.edn")))

(defn- combo-path [template combo]
  (str (fs/path here "templates" template "combos" (str combo ".expected.edn"))))

(defn- read-edn [path] (edn/read-string (slurp path)))

(def ^:private tier-checks
  {:full  #{:no-cruft :combo :residue :ns-hyphen :lint :fmt :clj-clean :cljs-run :server-boot}
   :light #{:no-cruft :combo :residue :ns-hyphen :lint :fmt :clj-clean}})

(defn- feature-flags [features]
  (mapcat (fn [[k v]] ["--feature" (str (name k) "=" (boolean v))]) features))

(defn- scaffold!
  "Invoke the CLI to scaffold one combo into a temp dir; return the scaffold path."
  [{:keys [cli-cp template descriptor combo-edn]}]
  (let [tmp        (str (fs/create-temp-dir {:prefix "verify-"}))
        templates  (str (fs/absolutize (fs/path here (:cli-templates-dir descriptor))))
        args       (concat ["create" (:name combo-edn)
                            "--template-dir" templates
                            "--template" template
                            "--db" (name (:db combo-edn))
                            "--yes" "--no-git"]
                           (feature-flags (:features combo-edn)))
        cli-prefix (if cli-cp ["bb" "-cp" cli-cp "-m" "c3kit-jig.main"] ["bb" "-m" "c3kit-jig.main"])
        {:keys [exit out err]} (apply p/sh {:dir tmp} (concat cli-prefix args))]
    (println out)
    (when (seq err) (println "stderr:" err))
    (when-not (zero? exit) (throw (ex-info (str "CLI scaffold failed, exit " exit) {:exit exit})))
    {:tmp tmp :scaffold (str (fs/path tmp (:name combo-edn)))}))

(defn- run-checks [root descriptor combo-edn enabled]
  (let [{:keys [denylist ns-prefix-exempt commands]} descriptor
        underscore (str/replace (:name combo-edn) "-" "_")
        run? (fn [k] (and (enabled k) (get-in descriptor [:checks k])))]
    (cond-> []
      (run? :no-cruft)    (conj (checks/cruft-check root denylist))
      (run? :combo)       (conj (checks/combo-check root combo-edn))
      (run? :residue)     (conj (checks/residue-check root))
      (run? :ns-hyphen)   (conj (checks/ns-hyphen-check root underscore ns-prefix-exempt))
      (run? :lint)        (conj (checks/tool-check :lint root (:lint commands) (:lint-config commands) "clj-kondo"))
      (run? :fmt)         (conj (checks/tool-check :fmt root (:fmt commands) (:fmt-config commands) "cljfmt"))
      (run? :clj-clean)   (conj (checks/clj-clean-check root (:clj-spec commands)))
      (run? :cljs-run)    (conj (checks/cljs-check root (:cljs-once commands)))
      (run? :server-boot) (conj (checks/server-boot-check root commands)))))

(defn- report! [combo results]
  (println (str "\n=== " combo " ==="))
  (doseq [{:keys [check ok? detail]} results]
    (println (format "  [%s] %-12s %s" (if ok? "PASS" "FAIL") (name check) detail)))
  (every? :ok? results))

(defn verify-combo
  [{:keys [template combo tier cli-cp keep-tmp]}]
  (let [descriptor (read-edn (descriptor-path template))
        combo-edn  (read-edn (combo-path template combo))
        tier-kw    (or (some-> tier keyword) (get-in descriptor [:combos (keyword combo) :tier]) :light)
        enabled    (tier-checks tier-kw)
        {:keys [tmp scaffold]} (scaffold! {:cli-cp cli-cp :template template
                                           :descriptor descriptor :combo-edn combo-edn})]
    (try
      (let [results (run-checks scaffold descriptor combo-edn enabled)]
        (report! combo results))
      (finally
        (when-not keep-tmp (fs/delete-tree tmp))))))

(def ^:private opts-spec
  [[nil "--template ID"  "Template id" :default "full-stack-reagent"]
   [nil "--combo NAME"   "Combo name"]
   [nil "--tier TIER"    "full or light (default: combo's declared tier)"]
   [nil "--cli-cp PATH"  "CLI source classpath dir" :default "../cli/src"]
   [nil "--keep-tmp"     "Keep scaffold temp dir"]])

(defn -main [& argv]
  (let [{:keys [options errors]} (cli/parse-opts argv opts-spec)]
    (when errors (println "args error:" errors) (System/exit 2))
    (when-not (:combo options) (println "missing --combo") (System/exit 2))
    (let [ok? (verify-combo options)]
      (System/exit (if ok? 0 1)))))

(defn run-all [argv]
  (let [{:keys [options]} (cli/parse-opts argv opts-spec)
        template (:template options)
        descriptor (read-edn (descriptor-path template))
        results (for [[combo {:keys [tier]}] (:combos descriptor)]
                  (verify-combo (assoc options :combo (name combo) :tier (name tier))))]
    (System/exit (if (every? true? results) 0 1))))
```

- [ ] **Step 2: Delete the relocated in-template script**

```bash
git rm templates/full-stack-reagent/dev/verify-scaffold.bb
```

- [ ] **Step 3: Run the harness against the light memory-minimal combo**

Run: `cd verification && bb verify --combo memory-minimal --tier light`
Expected output includes a report block. Expected check outcomes (first run, harness-only scope):
- `[FAIL] no-cruft` — `full-stack-reagent.iml`, `.cpcache` present.
- `[PASS] combo`
- `[PASS] residue`
- `[FAIL] ns-hyphen` — underscore ns forms.
- `[FAIL] lint` — `no .clj-kondo/config.edn shipped`.
- `[FAIL] fmt` — `no cljfmt.edn shipped`.
- `[PASS]` or `[FAIL] clj-clean` (PASS structurally; FAIL if log noise present — record whichever it is).

Exit code 1 (some checks red). **This red result is the expected, correct outcome.** Record the exact report in the commit body.

- [ ] **Step 4: Run the full memory-defaults combo to exercise cljs + server**

Run: `cd verification && bb verify --combo memory-defaults --tier full --keep-tmp`
Expected: same red checks as above, plus `[FAIL] cljs-run` (auto-watch/Playwright not wired one-shot) and a `server-boot` result (PASS if memory server boots on 8123, else FAIL with detail). Note: requires `clj-kondo`, `cljfmt`, and a Playwright browser locally; if a binary is missing the corresponding check errors — that is acceptable to observe locally, CI installs them in Task 7.

- [ ] **Step 5: Commit**

```bash
git add verification/src/c3kit_verify/engine.clj
git rm templates/full-stack-reagent/dev/verify-scaffold.bb 2>/dev/null || true
git commit -m "feat(verification): engine + remove relocated in-template verify-scaffold.bb

First run is intentionally red on known template defects (cruft, ns-hyphen,
missing lint/fmt configs, cljs one-shot). Scope is harness-only.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: CI workflow rewrite

**Files:**
- Modify: `.github/workflows/template-full-stack-reagent.yml`

- [ ] **Step 1: Replace the `scaffold-matrix` and `macos-smoke` jobs**

Replace those two jobs (keep `template-at-head` and `hook-iso-test` as-is) with:

```yaml
  verify-light:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        combo:
          - memory-minimal
          - memory-no-auth
          - memory-no-ssr-no-content
          - memory-no-websocket
          - sqlite-defaults
          - postgres-defaults
          - datomic-pro-defaults
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '21' }
      - uses: DeLaGuardo/setup-clojure@13.0
        with: { cli: latest, bb: 1.12.218, clj-kondo: latest, cljfmt: latest }
      - name: Verify (light)
        run: |
          cd verification
          bb verify --combo ${{ matrix.combo }} --tier light \
            --cli-cp ${{ github.workspace }}/cli/src

  verify-full:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '21' }
      - uses: DeLaGuardo/setup-clojure@13.0
        with: { cli: latest, bb: 1.12.218, clj-kondo: latest, cljfmt: latest }
      - name: Install Playwright browser
        run: npx --yes playwright install --with-deps chromium
      - name: Verify (full, memory-defaults)
        run: |
          cd verification
          bb verify --combo memory-defaults --tier full \
            --cli-cp ${{ github.workspace }}/cli/src
```

- [ ] **Step 2: Update the workflow `paths` trigger to include the harness**

In the `on:` block, add `'verification/**'` to both the `push.paths` and `pull_request.paths` lists (alongside the existing `templates/full-stack-reagent/**` and `cli/**`).

- [ ] **Step 3: Validate the YAML parses**

Run: `bb -e '(require (quote [clojure.java.io :as io])) (println (.exists (io/file ".github/workflows/template-full-stack-reagent.yml")))'`
Then visually confirm indentation. (No YAML linter is assumed available; if `yq` is present: `yq '.jobs | keys' .github/workflows/template-full-stack-reagent.yml`.)
Expected: jobs list includes `template-at-head`, `hook-iso-test`, `verify-light`, `verify-full`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/template-full-stack-reagent.yml
git commit -m "ci: run verification harness (light matrix + full memory combo)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review notes (resolved)

- **Spec coverage:** layout/relocation (T1,T5,T6), engine (T6), descriptor (T5), all 9 checks (no-cruft/combo/residue/ns-hyphen T3; lint/fmt/clj-clean/cljs-run/server-boot T4; wired T6), tiers (T6 `tier-checks`), invocation bb tasks (T1) + CI (T7), first-run-red expectation (T6 steps 3–4). Covered.
- **Harness-only scope:** no task edits template source, copy mechanism, or adds `.clj-kondo`/`cljfmt.edn` to the template. `verify-scaffold.bb` deletion + combo move are relocation, not template-behavior fixes.
- **Type/name consistency:** check fns return `{:check :ok? :detail}`; engine `run-checks` calls `cruft-check`, `combo-check`, `residue-check`, `ns-hyphen-check`, `tool-check`, `clj-clean-check`, `cljs-check`, `server-boot-check` — all defined in checks.clj. Pure cores `ns-prefix-violation`, `clean-spec-output?`, `tool-result`, `parse-cljs-result` defined in T2 and used by T3/T4. `tier-checks` keys match `:checks` descriptor keys.
- **Known follow-ups (fix pass, not this plan):** template cruft copy fix, ns rendering fix, log-noise fix, one-shot cljs command, ship `.clj-kondo/config.edn` + `cljfmt.edn`, gate `compile_cljs.clj` SSR code. These turn the red checks green later.
```
