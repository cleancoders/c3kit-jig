# Security CI Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adopt the reusable `cleancoders/github-actions` security-scan workflow across the c3kit-jig monorepo — the jig repo's own CI, the scaffolded-project template output, and the verification harness.

**Architecture:** Three surfaces. (1) `ci.yml` gains a `security` job calling the reusable workflow with monorepo path overrides. (2) The `full-stack-reagent` template ships a static caller workflow plus the `:clj-watson` alias its scanner needs, copied through untouched by the scaffolder. (3) The verification harness gains a per-combo `:security-workflow` presence check (bb), a `render` task that materializes a combo to a known dir, and CI jobs that run the heavy scanners (semgrep + clj-holmes on one representative combo; clj-watson per DB backend) against rendered output.

**Tech Stack:** GitHub Actions (reusable `workflow_call`), Babashka (`bb`) verification harness (speclj, babashka.fs/process), Clojure CLI (`deps.edn` aliases), clj-watson v6.1.0, semgrep, clj-holmes-action.

## Global Constraints

- Reusable workflow ref is pinned to the moving tag: `cleancoders/github-actions/.github/workflows/security.yml@v1`. Use this exact ref everywhere.
- `clj-watson` alias pin: `io.github.clj-holmes/clj-watson {:git/tag "v6.1.0" :git/sha "be98e4d"}`, `:main-opts ["-m" "clj-watson.cli"]`.
- `clj-holmes-action` pin: `clj-holmes/clj-holmes-action@53daa4da4ff495cccf791e4ba4222a8317ddae9e` (same SHA already used upstream in `security.yml`).
- Baked template scanners are **blocking**: `clj-watson-blocking: true`, `semgrep-blocking: true`.
- clj-kondo `src-paths` for the jig repo: `"cli/src cli/spec"` (the `verification/` harness is non-shipping build tooling and is excluded).
- Harness heavy scans and the presence check are **blocking** (a finding fails verify / the release).
- Follow TDD: RED → GREEN → REFACTOR. Run existing tests before touching production code. Commit after each green step.
- Harness check convention: a pure `*`-suffixed decision core (unit-tested in `spec/c3kit_verify/checks_spec.clj`) plus an effectful wrapper that runs IO and delegates to the core.

---

### Task 1: Fold the `security` job into `ci.yml`, drop the redundant shellcheck job

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `cleancoders/github-actions/.github/workflows/security.yml@v1` (reusable workflow; inputs `src-paths`, `shellcheck-dir`).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Read the current file**

Run: `cat .github/workflows/ci.yml`
Confirm it has jobs `cli` and `shellcheck` (the standalone `shellcheck cli/install.sh` job).

- [ ] **Step 2: Add the `security` job and remove the standalone `shellcheck` job**

In `.github/workflows/ci.yml`, delete the entire `shellcheck:` job block:

```yaml
  shellcheck:
    name: shellcheck install.sh
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - name: shellcheck
        run: shellcheck cli/install.sh
```

and replace it with:

```yaml
  security:
    name: security scan
    uses: cleancoders/github-actions/.github/workflows/security.yml@v1
    with:
      src-paths: "cli/src cli/spec"
      shellcheck-dir: "."
    secrets: inherit
```

(A job that calls a reusable workflow uses top-level `uses:`/`with:`/`secrets:` and has no `runs-on`/`steps:` — that is expected.)

- [ ] **Step 3: Validate the YAML parses**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('ci.yml: valid YAML')"`
Expected: `ci.yml: valid YAML`

- [ ] **Step 4: Confirm the reusable-workflow contract matches**

Run: `grep -n "shellcheck-dir\|src-paths\|semgrep-blocking\|clj-watson-blocking" ../github-actions/.github/workflows/security.yml`
Expected: all four input names are defined in the reusable workflow (so our `with:` keys are valid). Verify `src-paths` and `shellcheck-dir` appear.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add reusable security scan job, drop redundant shellcheck job"
```

---

### Task 2: Add the `:clj-watson` alias to the template `deps.edn`

The baked security workflow (Task 3) invokes `clojure -M:clj-watson`, which requires the scaffolded project's `deps.edn` to define the alias. Prove it via a harness combo assertion.

**Files:**
- Modify: `templates/full-stack-reagent/deps.edn`
- Modify: `verification/templates/full-stack-reagent/combos/memory-defaults.expected.edn`

**Interfaces:**
- Consumes: nothing.
- Produces: a `:clj-watson` alias in every scaffolded `deps.edn`, relied on by Task 3 (baked workflow) and Task 7 (harness dep-CVE job).

- [ ] **Step 1: Write the failing assertion**

In `verification/templates/full-stack-reagent/combos/memory-defaults.expected.edn`, add to the `:file-contains` entry for `"deps.edn"` the string `"clj-watson.cli"` (append to the existing vector of substrings for that file):

```clojure
                  "deps.edn"                   ["com.cleancoders.c3kit/apron"
                                                "org.mindrot/jbcrypt"
                                                "google-api-client"
                                                "ring-anti-forgery"
                                                "nextjournal/markdown"
                                                "my-app.compile-cljs"
                                                "clj-watson.cli"]
```

- [ ] **Step 2: Run the combo check to verify it fails**

Run: `cd verification && bb verify --combo memory-defaults --tier light --cli-cp ../cli/src`
Expected: FAIL — the `[combo]` line reports `file-contains miss: deps.edn <- "clj-watson.cli"`.

- [ ] **Step 3: Add the alias to the template**

In `templates/full-stack-reagent/deps.edn`, inside the `:aliases` map, add (place it alphabetically before `:clean-db`):

```clojure
             :clj-watson {:replace-deps {io.github.clj-holmes/clj-watson {:git/tag "v6.1.0" :git/sha "be98e4d"}}
                          :main-opts    ["-m" "clj-watson.cli"]}
```

- [ ] **Step 4: Run the combo check to verify it passes**

Run: `cd verification && bb verify --combo memory-defaults --tier light --cli-cp ../cli/src`
Expected: PASS — `[PASS] combo` and overall exit 0.

- [ ] **Step 5: Commit**

```bash
git add templates/full-stack-reagent/deps.edn verification/templates/full-stack-reagent/combos/memory-defaults.expected.edn
git commit -m "feat(template): ship :clj-watson alias for baked security CI"
```

---

### Task 3: Bake the caller security workflow into the template

**Files:**
- Create: `templates/full-stack-reagent/.github/workflows/security.yml`
- Modify: `verification/templates/full-stack-reagent/combos/memory-defaults.expected.edn`

**Interfaces:**
- Consumes: the `:clj-watson` alias from Task 2 (so the baked clj-watson job can run in scaffolded projects).
- Produces: `.github/workflows/security.yml` in every scaffolded project; asserted by Task 5's `:security-workflow` check.

- [ ] **Step 1: Write the failing assertion**

In `verification/templates/full-stack-reagent/combos/memory-defaults.expected.edn`:

- Add `".github/workflows/security.yml"` to the `:must-exist` vector.
- Add a `:file-contains` entry for it:

```clojure
                  ".github/workflows/security.yml"
                  ["cleancoders/github-actions/.github/workflows/security.yml@v1"
                   "clj-watson-blocking: true"
                   "semgrep-blocking: true"]
```

- [ ] **Step 2: Run the combo check to verify it fails**

Run: `cd verification && bb verify --combo memory-defaults --tier light --cli-cp ../cli/src`
Expected: FAIL — `must-exist missing: .github/workflows/security.yml`.

- [ ] **Step 3: Create the baked workflow file**

Create `templates/full-stack-reagent/.github/workflows/security.yml`:

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

- [ ] **Step 4: Run the combo check to verify it passes (proves the scaffolder copies `.github/` through untouched)**

Run: `cd verification && bb verify --combo memory-defaults --tier light --cli-cp ../cli/src`
Expected: PASS — the rendered scaffold contains `.github/workflows/security.yml` verbatim (no `acme`/`@c3kit` tokens to rewrite). If it is MISSING, the scaffolder is filtering `.github/`; in that case inspect `cli/src/c3kit_jig/fs.clj` (staging/copy) and remove the filter, then re-run.

- [ ] **Step 5: Confirm no residue and valid YAML in the rendered output**

Run: `cd verification && bb verify --combo memory-defaults --tier light --cli-cp ../cli/src --keep-tmp` — note the temp path printed on failure, or trust the passing combo/residue checks (the `:residue` check already fails on any surviving `@c3kit` marker). Expected: PASS for both `[combo]` and `[residue]`.

- [ ] **Step 6: Commit**

```bash
git add templates/full-stack-reagent/.github/workflows/security.yml verification/templates/full-stack-reagent/combos/memory-defaults.expected.edn
git commit -m "feat(template): bake security CI caller into scaffolded projects"
```

---

### Task 4: Add the `:security-workflow` presence check core (RED/GREEN, pure)

**Files:**
- Modify: `verification/src/c3kit_verify/checks.clj`
- Test: `verification/spec/c3kit_verify/checks_spec.clj`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `checks/security-workflow-result*` — pure. Takes `{:exists? boolean :content string-or-nil}`, returns `{:check :security-workflow :ok? boolean :detail string}`.
  - `checks/security-workflow-check` — effectful. Takes `root` (scaffold dir string), returns the same result map.

- [ ] **Step 1: Write the failing tests**

In `verification/spec/c3kit_verify/checks_spec.clj`, add:

```clojure
(describe "security-workflow-result*"
  (it "fails when the workflow file is absent"
    (let [r (sut/security-workflow-result* {:exists? false :content nil})]
      (should-not (:ok? r))
      (should (re-find #"missing" (:detail r)))))
  (it "fails when it does not call the reusable workflow @v1"
    (should-not (:ok? (sut/security-workflow-result*
                       {:exists? true :content "name: Security\njobs: {}\n"}))))
  (it "fails when clj-watson-blocking is not enabled"
    (should-not (:ok? (sut/security-workflow-result*
                       {:exists? true
                        :content "uses: cleancoders/github-actions/.github/workflows/security.yml@v1\nsemgrep-blocking: true\n"}))))
  (it "fails when semgrep-blocking is not enabled"
    (should-not (:ok? (sut/security-workflow-result*
                       {:exists? true
                        :content "uses: cleancoders/github-actions/.github/workflows/security.yml@v1\nclj-watson-blocking: true\n"}))))
  (it "passes a complete blocking caller"
    (should (:ok? (sut/security-workflow-result*
                   {:exists? true
                    :content "uses: cleancoders/github-actions/.github/workflows/security.yml@v1\nclj-watson-blocking: true\nsemgrep-blocking: true\n"})))))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd verification && bb test`
Expected: FAIL with `Unable to resolve symbol: security-workflow-result*` (or example failures).

- [ ] **Step 3: Implement the pure core and effectful wrapper**

In `verification/src/c3kit_verify/checks.clj`, add (near the other `*`-core/shell pairs):

```clojure
(def ^:private SECURITY-WORKFLOW-REF
  "cleancoders/github-actions/.github/workflows/security.yml@v1")

(defn security-workflow-result*
  "Decide pass/fail for the baked security caller workflow. Pass iff the file
   exists, calls the reusable workflow @v1, and enables both blocking toggles."
  [{:keys [exists? content]}]
  (let [c (or content "")]
    (cond
      (not exists?)
      {:check :security-workflow :ok? false :detail ".github/workflows/security.yml missing"}
      (not (str/includes? c SECURITY-WORKFLOW-REF))
      {:check :security-workflow :ok? false :detail "does not call security.yml@v1"}
      (not (str/includes? c "clj-watson-blocking: true"))
      {:check :security-workflow :ok? false :detail "clj-watson-blocking not enabled"}
      (not (str/includes? c "semgrep-blocking: true"))
      {:check :security-workflow :ok? false :detail "semgrep-blocking not enabled"}
      :else
      {:check :security-workflow :ok? true :detail "security workflow present + blocking"})))

(defn security-workflow-check [root]
  (let [f       (fs/path root ".github" "workflows" "security.yml")
        exists? (fs/exists? f)]
    (security-workflow-result* {:exists? exists? :content (when exists? (slurp (str f)))})))
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd verification && bb test`
Expected: PASS — all `security-workflow-result*` examples green, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add verification/src/c3kit_verify/checks.clj verification/spec/c3kit_verify/checks_spec.clj
git commit -m "feat(verify): add security-workflow presence check core"
```

---

### Task 5: Wire `:security-workflow` into the engine and descriptor

**Files:**
- Modify: `verification/src/c3kit_verify/engine.clj`
- Modify: `verification/templates/full-stack-reagent/verify.edn`

**Interfaces:**
- Consumes: `checks/security-workflow-check` (Task 4).
- Produces: `:security-workflow` runs on every combo at both tiers.

- [ ] **Step 1: Add the check to both tier sets**

In `verification/src/c3kit_verify/engine.clj`, update `tier-checks` so both tiers include `:security-workflow`:

```clojure
(def ^:private tier-checks
  {:full  #{:no-cruft :combo :residue :ns-hyphen :lint :fmt :clj-clean :cljs-run :server-boot :security-workflow}
   :light #{:no-cruft :combo :residue :ns-hyphen :lint :fmt :clj-clean :cljs-run :security-workflow}})
```

- [ ] **Step 2: Add the thunk to `run-checks!`**

In the `thunks` vector inside `run-checks!`, add (after `:combo`, grouping it with the structural checks):

```clojure
                    [:security-workflow #(checks/security-workflow-check root)]
```

- [ ] **Step 3: Enable the check in the descriptor**

In `verification/templates/full-stack-reagent/verify.edn`, add `:security-workflow true` to the `:checks` map:

```clojure
 :checks {:no-cruft true :combo true :residue true :ns-hyphen true
          :lint true :fmt true
          :clj-clean true :cljs-run true :server-boot true
          :security-workflow true}
```

- [ ] **Step 4: Verify the check runs and passes on a combo**

Run: `cd verification && bb verify --combo memory-defaults --tier light --cli-cp ../cli/src`
Expected: output includes a `[PASS] security-workflow security workflow present + blocking` line; overall exit 0.

- [ ] **Step 5: Confirm the check also runs on a features-off combo**

Run: `cd verification && bb verify --combo memory-minimal --tier light --cli-cp ../cli/src`
Expected: PASS including `[PASS] security-workflow` — confirms the baked workflow is present in a minimal (features-off) combo too, since `.github/` is DB/feature-independent.

- [ ] **Step 6: Commit**

```bash
git add verification/src/c3kit_verify/engine.clj verification/templates/full-stack-reagent/verify.edn
git commit -m "feat(verify): run security-workflow check on every combo"
```

---

### Task 6: Add a `render` bb task that materializes one combo to a known dir

Heavy scanners (Task 7) need a rendered scaffold at a predictable path. Reuse the engine's existing scaffold logic.

**Files:**
- Modify: `verification/src/c3kit_verify/engine.clj`
- Modify: `verification/bb.edn`

**Interfaces:**
- Consumes: the private `scaffold!` helper and combo/descriptor readers already in `engine.clj`.
- Produces: `c3kit-verify.engine/render-combo` and a `bb render` task. CLI: `bb render --combo NAME --out DIR [--cli-cp PATH] [--template ID]`. Prints the rendered project path; exits 0 on success, 1 on failure.

- [ ] **Step 1: Implement `render-combo`**

In `verification/src/c3kit_verify/engine.clj`, add a public fn. It renders into `out` by copying the scaffold produced in a temp dir (reusing `scaffold!`) so no new CLI-invocation code is duplicated:

```clojure
(defn render-combo
  "Scaffold one combo into `out` (created if absent) and return the project path.
   Runs no checks. Used by CI heavy-scan jobs to obtain rendered output."
  [{:keys [template combo cli-cp out] :as _opts}]
  (let [descriptor (read-edn (descriptor-path template))
        combo-edn  (read-edn (combo-path template combo))
        {:keys [tmp scaffold]} (scaffold! {:cli-cp cli-cp :template template
                                           :descriptor descriptor :combo-edn combo-edn
                                           :verbose false})]
    (try
      (fs/create-dirs out)
      (let [dest (str (fs/path out (:name combo-edn)))]
        (fs/delete-tree dest)
        (fs/copy-tree scaffold dest)
        dest)
      (finally
        (fs/delete-tree tmp)))))

(defn render-main [argv]
  (let [{:keys [options errors]} (cli/parse-opts argv opts-spec)]
    (when errors (println "args error:" errors) (System/exit 2))
    (when-not (:combo options) (println "missing --combo") (System/exit 2))
    (when-not (:out options) (println "missing --out") (System/exit 2))
    (println (render-combo options))
    (System/exit 0)))
```

- [ ] **Step 2: Add `--out` to `opts-spec`**

In `opts-spec`, add:

```clojure
   [nil "--out DIR"     "Output dir for `render` (project written to DIR/<name>)"]
```

- [ ] **Step 3: Add the `render` task to `bb.edn`**

In `verification/bb.edn`, inside `:tasks`, add:

```clojure
  render     {:doc      "Scaffold one combo to --out (no checks). e.g. bb render --combo memory-defaults --out /tmp/scan"
              :requires ([c3kit-verify.engine])
              :task     (c3kit-verify.engine/render-main *command-line-args*)}
```

- [ ] **Step 4: Verify render produces a scannable project**

Run: `cd verification && rm -rf /tmp/c3kit-render && bb render --combo memory-defaults --out /tmp/c3kit-render --cli-cp ../cli/src && ls /tmp/c3kit-render/my-app/deps.edn /tmp/c3kit-render/my-app/.github/workflows/security.yml`
Expected: both files listed (exit 0), and the printed path is `/tmp/c3kit-render/my-app`.

- [ ] **Step 5: Confirm the rendered deps.edn carries the clj-watson alias**

Run: `grep -q "clj-watson.cli" /tmp/c3kit-render/my-app/deps.edn && echo "alias present"`
Expected: `alias present`

- [ ] **Step 6: Commit**

```bash
git add verification/src/c3kit_verify/engine.clj verification/bb.edn
git commit -m "feat(verify): add render task to materialize a combo for scanning"
```

---

### Task 7: Add heavy-scanner CI jobs to `template-full-stack-reagent.yml`

Run semgrep + clj-holmes on one features-maximal combo (`memory-defaults`) and clj-watson on one combo per driver-backed DB (`sqlite-no-auth`, `postgres-ssr-no-content`, `datomic-pro-no-ssr-no-content`). All blocking.

**Files:**
- Modify: `.github/workflows/template-full-stack-reagent.yml`

**Interfaces:**
- Consumes: `bb render` (Task 6); the `:clj-watson` alias in rendered `deps.edn` (Task 2).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Add the dependency-CVE job (clj-watson per DB)**

Append to the `jobs:` map in `.github/workflows/template-full-stack-reagent.yml`:

```yaml
  dep-cve:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        combo:
          - sqlite-no-auth
          - postgres-ssr-no-content
          - datomic-pro-no-ssr-no-content
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: '21'
      - uses: DeLaGuardo/setup-clojure@13.6.1
        with:
          cli: latest
          bb: 1.12.218
      - name: Render combo
        run: |
          cd verification
          bb render --combo ${{ matrix.combo }} --out "$GITHUB_WORKSPACE/scan-target" \
            --cli-cp "$GITHUB_WORKSPACE/cli/src"
      - name: clj-watson dependency CVE scan (blocking)
        working-directory: scan-target/my-app
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: clojure -M:clj-watson scan -p deps.edn --database-strategy github-advisory --fail-on-result
```

- [ ] **Step 2: Add the SAST job (semgrep + clj-holmes on the representative combo)**

Append:

```yaml
  sast:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: DeLaGuardo/setup-clojure@13.6.1
        with:
          bb: 1.12.218
      - name: Render features-maximal combo
        run: |
          cd verification
          bb render --combo memory-defaults --out "$GITHUB_WORKSPACE/scan-target" \
            --cli-cp "$GITHUB_WORKSPACE/cli/src"
      - name: clj-holmes SAST (blocking)
        uses: clj-holmes/clj-holmes-action@53daa4da4ff495cccf791e4ba4222a8317ddae9e
        with:
          path: 'scan-target/my-app'
          output-type: 'stdout'
          fail-on-result: 'true'
      - name: Install semgrep
        run: python3 -m pip install --quiet semgrep
      - name: Semgrep (OWASP Top 10 + default, blocking)
        run: semgrep scan --config p/owasp-top-ten --config p/default --error scan-target/my-app
```

- [ ] **Step 3: Validate the YAML parses**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/template-full-stack-reagent.yml')); print('valid YAML')"`
Expected: `valid YAML`

- [ ] **Step 4: Confirm the matrix combos exist**

Run: `ls verification/templates/full-stack-reagent/combos/{sqlite-no-auth,postgres-ssr-no-content,datomic-pro-no-ssr-no-content,memory-defaults}.expected.edn`
Expected: all four files listed.

- [ ] **Step 5: Local smoke test of one scanner path (semgrep on a rendered combo)**

Run:
```bash
cd verification && rm -rf /tmp/c3kit-sast && bb render --combo memory-defaults --out /tmp/c3kit-sast --cli-cp ../cli/src
python3 -m pip install --quiet semgrep && semgrep scan --config p/owasp-top-ten --config p/default --error /tmp/c3kit-sast/my-app; echo "semgrep exit: $?"
```
Expected: semgrep runs to completion; `semgrep exit: 0` means clean. A non-zero exit means a real finding to triage before merge (that is the blocking behavior working).

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/template-full-stack-reagent.yml
git commit -m "ci: scan rendered template output (clj-watson per DB, semgrep + clj-holmes)"
```

---

## Notes / Follow-ups

- **clj-watson network dependency:** the dep-CVE job queries the GitHub Advisory DB and needs `GITHUB_TOKEN` (auto-provided). No NVD key required.
- **First-run backlog:** if the jig-repo gitleaks step (Task 1) surfaces a pre-existing secret backlog, add a `.gitleaksignore` baseline at the repo root — do not weaken the scan.
- **`@v1` retagging:** publishing a new reusable-workflow version is done in the `cleancoders/github-actions` repo; consumers here need no change.
