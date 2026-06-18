# CLI Versioning & Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adopt c3kit's VERSION + CHANGES + bare-semver-tag model for the jig CLI, bake the version into the uberscript at build, add a `bb release` task, and cut the first release so the installer works.

**Architecture:** Root `VERSION` is the single source of truth. `bb build` bakes it into the standalone uberscript via placeholder substitution. A `bb release` task (guards in a testable `c3kit-jig.release` namespace) tags bare semver and pushes; the existing `release.yml` workflow — retriggered on a bare-semver tag glob — builds and publishes the assets.

**Tech Stack:** Babashka (bb tasks + sci), Speclj, GitHub Actions, bash.

## Global Constraints

- Tags are **bare semver**, no `v`/`cli-` prefix (e.g. `1.0.0`). Verbatim from spec.
- Root `VERSION` holds bare semver; it is the source of truth.
- `CHANGES.md` top heading must equal `### <VERSION>` at release time.
- Uberscript runs standalone post-install — it cannot read `VERSION` at runtime; the version is baked at build via the placeholder literal `0.0.0-DEV`.
- TDD: failing test first, then minimal code. Small commits.
- All `bb` tasks run from `cli/` (that is where `cli/bb.edn` lives); root files are reached as `../VERSION`, `../CHANGES.md`.
- `bb lint` (clj-kondo) must stay green: no `:refer :all`, every `:require` lists referred symbols.

---

## File Structure

- `VERSION` (new, root) — bare semver source of truth.
- `CHANGES.md` (root, modify) — heading discipline.
- `cli/src/c3kit_jig/version.clj` (modify) — placeholder constant, drop `cli-v` compare.
- `cli/spec/c3kit_jig/version_spec.clj` (modify) — match new constant + compare.
- `cli/src/c3kit_jig/release.clj` (new) — pure release-guard logic.
- `cli/spec/c3kit_jig/release_spec.clj` (new) — guard tests.
- `cli/bb.edn` (modify) — `build` bakes VERSION; new `release` task.
- `.github/workflows/release.yml` (modify) — bare-semver tag trigger.
- `CONTRIBUTING.md` (modify) — Releasing section; CHANGES reconciliation.
- `README.md` (modify) — Versioning note; repo-layout block.

---

## Task 1: VERSION file + CHANGES heading discipline

**Files:**
- Create: `VERSION`
- Modify: `CHANGES.md`

**Interfaces:**
- Produces: root `VERSION` containing `1.0.0`; `CHANGES.md` whose first `### ` heading is `### 1.0.0`.

- [ ] **Step 1: Create the VERSION file**

Create `VERSION` (repo root) with exactly one line:

```
1.0.0
```

- [ ] **Step 2: Fold `### Unreleased` and the never-shipped `### 0.1.0` into `### 1.0.0` in CHANGES.md**

The file currently starts:

```markdown
### Unreleased
 * Initial OSS scaffolding: CODE_OF_CONDUCT, SECURITY, CHANGES, issue and PR templates, require-linked-issue workflow.

### 0.1.0
 * Initial CLI: `c3kit-jig create`, `list`, `upgrade`, `version`.
 * Installer script (`cli/install.sh`) — detects Babashka and git, installs Babashka if missing.
 * `templates/full-stack-reagent` scaffolded; phase-1 template work in progress.
```

Replace that block with (top heading now matches VERSION; the `0.1.0` entry never shipped, so it collapses into `1.0.0`):

```markdown
### 1.0.0
 * Initial CLI: `c3kit-jig create`, `list`, `upgrade`, `version`.
 * Installer script (`cli/install.sh`) — detects Babashka and git, installs Babashka if missing.
 * `templates/full-stack-reagent` scaffolded; phase-1 template work in progress.
 * Initial OSS scaffolding: CODE_OF_CONDUCT, SECURITY, CHANGES, issue and PR templates, require-linked-issue workflow.
```

- [ ] **Step 3: Verify the heading matches VERSION**

Run:
```sh
test "### $(cat VERSION)" = "$(grep -m1 '^### ' CHANGES.md)" && echo OK
```
Expected: `OK`

- [ ] **Step 4: Commit**

```bash
git add VERSION CHANGES.md
git commit -m "chore: add root VERSION; align CHANGES top heading to 1.0.0"
```

---

## Task 2: version.clj — placeholder constant + drop `cli-v` compare

**Files:**
- Modify: `cli/src/c3kit_jig/version.clj`
- Test: `cli/spec/c3kit_jig/version_spec.clj`

**Interfaces:**
- Produces: `c3kit-jig.version/CURRENT` = `"0.0.0-DEV"` (placeholder, baked at build); `(v/current)` returns it; `check-and-download!` treats a bare-semver tag equal to `CURRENT` as `:up-to-date`.

- [ ] **Step 1: Update the failing tests**

In `cli/spec/c3kit_jig/version_spec.clj`, replace the `current` test and the `check-and-download!` test:

```clojure
  (it "current returns the current CLI semver as a string"
    (should= "0.0.0-DEV" (v/current)))
```

```clojure
  (it "check-and-download! no-ops when latest matches current"
    (with-redefs [v/fetch-latest-tag! (constantly (v/current))]
      (should= :up-to-date (v/check-and-download! "/tmp/whatever")))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd cli && bb test`
Expected: FAIL — `current` test expected `"0.0.0-DEV"` got `"0.1.0-SNAPSHOT"`; the no-op test fails because `fetch-latest-tag!` returns `"0.1.0-SNAPSHOT"` while code compares against `(str "cli-v" CURRENT)` and falls through to the download branch (network/throw).

- [ ] **Step 3: Change the constant**

In `cli/src/c3kit_jig/version.clj`, replace:

```clojure
(def ^:const CURRENT "0.1.0-SNAPSHOT")
```

with:

```clojure
;; Placeholder baked to the real version by `bb build` (reads root VERSION).
(def CURRENT "0.0.0-DEV")
```

- [ ] **Step 4: Drop the `cli-v` prefix in the up-to-date check**

In the same file, in `check-and-download!`, replace:

```clojure
    (if (= tag (str "cli-v" CURRENT))
```

with:

```clojure
    (if (= tag CURRENT)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd cli && bb test`
Expected: PASS (all version specs green).

- [ ] **Step 6: Lint**

Run: `cd cli && bb lint`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add cli/src/c3kit_jig/version.clj cli/spec/c3kit_jig/version_spec.clj
git commit -m "feat(cli): bare-semver version constant, drop cli-v tag prefix"
```

---

## Task 3: `bb build` bakes VERSION into the uberscript

**Files:**
- Modify: `cli/bb.edn`

**Interfaces:**
- Consumes: root `VERSION` (Task 1); placeholder `0.0.0-DEV` in the uberscript (Task 2).
- Produces: `cli/dist/c3kit-jig.bb` containing the real version literal and no `0.0.0-DEV`.

- [ ] **Step 1: Replace the `build` task in `cli/bb.edn`**

Replace the existing `build` task entry:

```clojure
  build      {:doc      "Build cli/dist/c3kit-jig.bb"
              :requires ([babashka.fs :as fs])
              :task     (let [out "dist/c3kit-jig.bb"]
                          (fs/create-dirs "dist")
                          (fs/delete-if-exists out)
                          (shell "bb" "uberscript" out "-m" "c3kit-jig.main")
                          (spit out (str "#!/usr/bin/env bb\n" (slurp out)))
                          (fs/set-posix-file-permissions out "rwxr-xr-x"))}
```

with (reads `../VERSION`, bakes it, then prepends shebang):

```clojure
  build      {:doc      "Build cli/dist/c3kit-jig.bb (bakes root VERSION)"
              :requires ([babashka.fs :as fs]
                         [clojure.string :as str])
              :task     (let [out     "dist/c3kit-jig.bb"
                              version (str/trim (slurp "../VERSION"))]
                          (fs/create-dirs "dist")
                          (fs/delete-if-exists out)
                          (shell "bb" "uberscript" out "-m" "c3kit-jig.main")
                          (let [baked (str/replace (slurp out) "0.0.0-DEV" version)]
                            (spit out (str "#!/usr/bin/env bb\n" baked)))
                          (fs/set-posix-file-permissions out "rwxr-xr-x")
                          (println (str "built dist/c3kit-jig.bb @ " version)))}
```

- [ ] **Step 2: Build and verify the version is baked**

Run:
```sh
cd cli && bb build && grep -c '"1.0.0"' dist/c3kit-jig.bb && ! grep -q '0.0.0-DEV' dist/c3kit-jig.bb && echo NO-PLACEHOLDER
```
Expected: prints `built dist/c3kit-jig.bb @ 1.0.0`, a count `>= 1` for the version literal, then `NO-PLACEHOLDER`.

- [ ] **Step 3: Smoke-test the built CLI reports the baked version**

Run:
```sh
cd cli && ./dist/c3kit-jig.bb version
```
Expected: output includes `1.0.0` (not `0.0.0-DEV`).

- [ ] **Step 4: Commit**

```bash
git add cli/bb.edn
git commit -m "feat(cli): bake root VERSION into uberscript at build"
```

---

## Task 4: Release-guard logic + `bb release` task

**Files:**
- Create: `cli/src/c3kit_jig/release.clj`
- Test: `cli/spec/c3kit_jig/release_spec.clj`
- Modify: `cli/bb.edn`

**Interfaces:**
- Produces:
  - `c3kit-jig.release/changes-top-heading [changes-str] -> String|nil` — the first line starting with `### `, or nil.
  - `c3kit-jig.release/release-blockers {:version :changes :dirty? :tag-exists?} -> [String]` — vector of human-readable blocker messages; empty vector means OK.
  - `bb release` task that gathers IO, calls `release-blockers`, and (when clear) tags + pushes.

- [ ] **Step 1: Write the failing tests**

Create `cli/spec/c3kit_jig/release_spec.clj`:

```clojure
(ns c3kit-jig.release-spec
  (:require [speclj.core :refer [describe it should= should-contain should-be-nil]]
            [c3kit-jig.release :as release]))

(describe "c3kit-jig.release"

  (it "changes-top-heading returns the first ### heading"
    (should= "### 0.1.0"
             (release/changes-top-heading "### 0.1.0\n * a thing\n\n### 0.0.9\n * old")))

  (it "changes-top-heading returns nil when there is no heading"
    (should-be-nil (release/changes-top-heading "no headings here\n")))

  (it "release-blockers is empty when everything is in order"
    (should= []
             (release/release-blockers {:version     "0.1.0"
                                        :changes     "### 0.1.0\n * a thing"
                                        :dirty?      false
                                        :tag-exists? false})))

  (it "release-blockers flags a dirty working tree"
    (should-contain "working tree is dirty; commit or stash first"
                    (release/release-blockers {:version     "0.1.0"
                                               :changes     "### 0.1.0\n * a"
                                               :dirty?      true
                                               :tag-exists? false})))

  (it "release-blockers flags a CHANGES heading that does not match VERSION"
    (should-contain "CHANGES.md top heading is \"### 0.0.9\", expected \"### 0.1.0\""
                    (release/release-blockers {:version     "0.1.0"
                                               :changes     "### 0.0.9\n * a"
                                               :dirty?      false
                                               :tag-exists? false})))

  (it "release-blockers flags an existing tag"
    (should-contain "tag 0.1.0 already exists"
                    (release/release-blockers {:version     "0.1.0"
                                               :changes     "### 0.1.0\n * a"
                                               :dirty?      false
                                               :tag-exists? true}))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd cli && bb test`
Expected: FAIL — namespace `c3kit-jig.release` does not exist.

- [ ] **Step 3: Write the minimal implementation**

Create `cli/src/c3kit_jig/release.clj`:

```clojure
(ns c3kit-jig.release
  (:require [clojure.string :as str]))

(defn changes-top-heading
  "First line of `changes` starting with \"### \", or nil."
  [changes]
  (->> (str/split-lines changes)
       (filter #(str/starts-with? % "### "))
       first))

(defn release-blockers
  "Return a vector of human-readable reasons a release is blocked.
   Empty vector => clear to tag and push.
   Expects {:version :changes :dirty? :tag-exists?}."
  [{:keys [version changes dirty? tag-exists?]}]
  (let [top (changes-top-heading changes)]
    (cond-> []
      dirty?
      (conj "working tree is dirty; commit or stash first")
      (not= top (str "### " version))
      (conj (str "CHANGES.md top heading is " (pr-str top)
                 ", expected \"### " version "\""))
      tag-exists?
      (conj (str "tag " version " already exists")))))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd cli && bb test`
Expected: PASS.

- [ ] **Step 5: Add the `release` task to `cli/bb.edn`**

Add this entry to the `:tasks` map (alongside `build`, `install`, etc.):

```clojure
  release    {:doc      "Tag root VERSION as a release and push (CI builds + publishes)"
              :requires ([c3kit-jig.release :as release]
                         [clojure.string :as str])
              :task     (let [version  (str/trim (slurp "../VERSION"))
                              changes  (slurp "../CHANGES.md")
                              dirty?   (-> (shell {:out :string} "git" "status" "--short")
                                           :out str/blank? not)
                              tag?     (-> (shell {:out :string :err :string :continue true}
                                                  "git" "rev-parse" "--verify" "--quiet"
                                                  (str "refs/tags/" version))
                                           :exit zero?)
                              blockers (release/release-blockers
                                        {:version version :changes changes
                                         :dirty? dirty? :tag-exists? tag?})]
                          (if (seq blockers)
                            (do (doseq [b blockers] (println "ERROR:" b))
                                (System/exit 1))
                            (do (shell "git" "tag" version)
                                (shell "git" "push" "origin" version)
                                (println (str "tagged + pushed " version
                                              " — CI will build and publish assets")))))}
```

- [ ] **Step 6: Verify the guards fire (dry, no tag created)**

Working tree is dirty at this point (uncommitted `bb.edn` + new files), so the guard should block:

Run: `cd cli && bb release`
Expected: prints `ERROR: working tree is dirty; commit or stash first` and exits non-zero. No tag created (`git tag -l 1.0.0` is empty).

- [ ] **Step 7: Lint**

Run: `cd cli && bb lint`
Expected: no errors.

- [ ] **Step 8: Commit**

```bash
git add cli/src/c3kit_jig/release.clj cli/spec/c3kit_jig/release_spec.clj cli/bb.edn
git commit -m "feat(cli): add bb release task with pre-flight guards"
```

---

## Task 5: `release.yml` bare-semver tag trigger

**Files:**
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: bare-semver tags pushed by `bb release`.
- Produces: a workflow that triggers on `X.Y.Z` tags and publishes `c3kit-jig.bb` + `.sha256`.

- [ ] **Step 1: Change the tag trigger**

In `.github/workflows/release.yml`, replace:

```yaml
on:
  push:
    tags: ['cli-v*']
```

with:

```yaml
on:
  push:
    tags: ['[0-9]+.[0-9]+.[0-9]+']
```

- [ ] **Step 2: Verify the workflow YAML parses**

Run:
```sh
bb -e '(require (quote [clojure.java.io :as io])) (println (slurp ".github/workflows/release.yml"))' >/dev/null && echo READABLE
```
Expected: `READABLE`. (Visual check: the `build`/`compute sha256`/`action-gh-release` steps still run `bb build` which now bakes VERSION — no change needed there.)

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: trigger release workflow on bare-semver tags"
```

---

## Task 6: Documentation — CONTRIBUTING + README

**Files:**
- Modify: `CONTRIBUTING.md`
- Modify: `README.md`

**Interfaces:** none (docs only).

- [ ] **Step 1: Add a Releasing section to CONTRIBUTING.md**

In `CONTRIBUTING.md`, immediately after the `## Submitting a PR` section (before `## Reporting Bugs / Requesting Features`), insert:

```markdown
## Releasing (maintainers)

c3kit-jig follows the c3kit versioning convention: a bare-semver `VERSION`
file at the repo root is the single source of truth, `CHANGES.md` uses
`### X.Y.Z` headings, and releases are tagged with bare semver (no `v`
prefix). The CLI is the only versioned artifact — templates are fetched from
`main` (or `--template-ref`) at runtime and are not separately versioned.

During development, add changelog entries under `### Unreleased`. To cut a
release:

1. Bump root `VERSION` (semver — patch: bug fix; minor: backward-compatible
   feature; major: breaking change).
2. Rename the `### Unreleased` heading in `CHANGES.md` to `### <VERSION>`.
   The top heading must match `VERSION`.
3. Commit on `main`; ensure the tree is clean and synced with origin.
4. From `cli/`, run `bb release`. It verifies the tree is clean, that the
   `CHANGES.md` top heading matches `VERSION`, and that the tag does not yet
   exist, then `git tag <VERSION>` and pushes it.
5. The push triggers `.github/workflows/release.yml`, which runs the tests,
   builds the uberscript (baking `VERSION`), computes its sha256, and
   publishes `c3kit-jig.bb` + `c3kit-jig.bb.sha256` as release assets.
6. Verify end-to-end by running the installer from the README.
```

- [ ] **Step 2: Reconcile the existing CHANGES instruction in CONTRIBUTING.md**

In the `## Workflow` list, the line currently reads:

```markdown
- Update `CHANGES.md` with a one-line entry under the current `Unreleased` (or pending version) section.
```

Replace it with:

```markdown
- Update `CHANGES.md` with a one-line entry under the `### Unreleased` heading. The release process (see Releasing) renames that heading to the version being shipped.
```

- [ ] **Step 3: Add a Versioning note to README.md**

In `README.md`, immediately after the `## Install` section (before `## Usage`), insert:

```markdown
## Versioning

c3kit-jig uses bare semantic versioning. The root `VERSION` file is the
single source of truth and is baked into the CLI at build time. Releases are
published as GitHub Releases; upgrade an installed CLI with `c3kit-jig
upgrade`. Templates are fetched from `main` at runtime (override with
`--template-ref <branch-or-tag>`) and are not separately versioned.
```

- [ ] **Step 4: Add VERSION and CHANGES to the repo-layout block in README.md**

In the `## Repo layout` code block, replace:

```
cli/           # bb CLI source + installer
templates/     # template trees, one dir per template
verification/  # post-scaffold verification harness
docs/          # design specs and implementation plans
```

with:

```
cli/           # bb CLI source + installer
templates/     # template trees, one dir per template
verification/  # post-scaffold verification harness
docs/          # design specs and implementation plans
VERSION        # bare-semver source of truth for the CLI
CHANGES.md     # changelog (### X.Y.Z headings)
```

- [ ] **Step 5: Commit**

```bash
git add CONTRIBUTING.md README.md
git commit -m "docs: document CLI versioning and release process"
```

---

## Task 7: Cut the first release (maintainer, operational)

> This task pushes a tag to the **public** repo and triggers a real release. Run only when Tasks 1-6 are merged to `main` and you intend to publish `1.0.0`. Requires push access.

**Files:** none (operational).

- [ ] **Step 1: Confirm preconditions on `main`**

Run:
```sh
git switch main && git pull --ff-only origin main
cat VERSION                                   # 1.0.0
grep -m1 '^### ' CHANGES.md                   # ### 1.0.0
git status --short                            # empty
```
Expected: `VERSION` is `1.0.0`, top CHANGES heading is `### 1.0.0`, clean tree.

- [ ] **Step 2: Run the release task**

Run: `cd cli && bb release`
Expected: prints `tagged + pushed 1.0.0 — CI will build and publish assets`.

- [ ] **Step 3: Watch the release workflow**

Run: `gh run watch $(gh run list --workflow=release.yml --limit 1 --json databaseId -q '.[0].databaseId')`
Expected: the `release` workflow succeeds; the run publishes a `1.0.0` release with `c3kit-jig.bb` and `c3kit-jig.bb.sha256` attached.

- [ ] **Step 4: Verify the release exists**

Run: `gh release view 1.0.0 --json assets -q '.assets[].name'`
Expected: lists `c3kit-jig.bb` and `c3kit-jig.bb.sha256`.

- [ ] **Step 5: Verify the installer end-to-end**

Run:
```sh
curl -fsSL https://raw.githubusercontent.com/cleancoders/c3kit-jig/main/cli/install.sh | bash
c3kit-jig version
```
Expected: install completes without the 404; `c3kit-jig version` reports `1.0.0`.

---

## Self-Review

**Spec coverage:**
- §1 single source of truth → Task 1 (VERSION + CHANGES discipline). ✓
- §2 bake VERSION at build → Task 3. ✓
- §3 drop `cli-v` prefix (version.clj + release.yml) → Task 2 + Task 5. ✓
- §4 `bb release` task with guards → Task 4. ✓
- §5 first release → Task 7. ✓
- §6 documentation (CONTRIBUTING Releasing + CHANGES reconcile; README Versioning + layout) → Task 6. ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code; every command has expected output.

**Type consistency:** `changes-top-heading` and `release-blockers` (with keys `:version :changes :dirty? :tag-exists?`) are defined in Task 4 Step 3 and consumed identically by the `release` task in Task 4 Step 5 and the tests in Step 1. The placeholder literal `0.0.0-DEV` is introduced in Task 2 and matched verbatim by the `str/replace` in Task 3. VERSION value `1.0.0` is consistent across Tasks 1, 3, 7. (The `### 0.1.0` / `### 0.0.9` strings in Task 4's tests are arbitrary fixtures exercising the pure guard logic, not the real release version.)
