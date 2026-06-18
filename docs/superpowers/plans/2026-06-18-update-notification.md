# CLI Update-Availability Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Proactively tell users when a newer CLI release exists and point them at `c3kit-jig upgrade`, up front (before `create`'s Q&A/scaffold), without slowing or breaking normal use, CI, or offline runs.

**Architecture:** A new `c3kit-jig.update-check` namespace holds pure decision logic (unit-tested) plus thin IO glue (cache file, guards, timed fetch). `main.clj` calls it up front: `create` prompts to continue, `list` notifies. Reuses `version.clj` (`current`, `fetch-latest-tag!`, `semver-compare`).

**Tech Stack:** Babashka (sci), Speclj, `babashka.fs`, `clojure.edn`, `babashka.http-client` (via `version.clj`).

## Global Constraints

- Notices/prompts must not pollute stdout: notices go to **stderr** (`ui/warn`); the `create` continue-prompt uses `wizard/prompt-yn` (interactive, stdout).
- Checking is skipped entirely when `C3KIT_NO_UPDATE_CHECK` is set to any non-empty value, OR stdout is not a TTY (`ui/tty?` is false).
- Cache file: `~/.c3kit/update-check.edn`, shape `{:checked-at <epoch-ms> :latest-tag "1.2.0"}`. TTL = `86400000` ms (24h).
- Stale/missing cache → fetch latest tag with a 2-second ceiling; on timeout/offline fall back to the stale cached tag or stay silent. The check NEVER throws and never changes a command's exit status (except the explicit `create` "continue?" prompt the user controls).
- Eligible commands: `create` (prompt) and `list` (notify). Excluded: `upgrade`, `version`, `help`, errors.
- This ships in the as-yet-uncut `1.0.0` release: the CHANGES entry goes under `### 1.0.0`.
- `bb lint` (clj-kondo) must stay green: no `:refer :all`; every `:require` lists referred symbols.
- `bb` tasks run from `cli/`.

---

## File Structure

- `cli/src/c3kit_jig/update_check.clj` (new) — pure decision logic + IO glue. One responsibility: "is there an update, and may we say so?"
- `cli/spec/c3kit_jig/update_check_spec.clj` (new) — unit tests for the pure fns.
- `cli/src/c3kit_jig/main.clj` (modify) — require the ns; wire `:scaffold` (create) and `:list`.
- `README.md` (modify) — note the auto-notice.
- `CHANGES.md` (modify) — entry under `### 1.0.0`.

---

## Task 1: `c3kit-jig.update-check` namespace

**Files:**
- Create: `cli/src/c3kit_jig/update_check.clj`
- Test: `cli/spec/c3kit_jig/update_check_spec.clj`

**Interfaces:**
- Produces:
  - `update-message [current latest] -> String|nil` — notice string when `latest` is strictly newer than `current` (via `version/semver-compare`), else nil.
  - `stale? [checked-at now ttl-ms] -> boolean` — true when `checked-at` is nil or older than `ttl-ms` before `now`.
  - `parse-cache [edn-string] -> map|nil` (nil on non-map / parse failure); `render-cache [m] -> String`.
  - `disabled-by-env? [env-value] -> boolean` — true when `env-value` is non-blank.
  - `available-update [] -> {:current String :latest String}|nil` — update exists AND checking enabled; never throws.
  - `notify! [] -> nil` — prints the notice to stderr when `available-update` is non-nil (used by `list`).

- [ ] **Step 1: Write the failing tests**

Create `cli/spec/c3kit_jig/update_check_spec.clj`:

```clojure
(ns c3kit-jig.update-check-spec
  (:require [speclj.core :refer [describe it should= should-be-nil should should-not should-contain]]
            [c3kit-jig.update-check :as uc]))

(describe "c3kit-jig.update-check"

  (it "update-message returns a notice mentioning both versions and upgrade when newer"
    (let [msg (uc/update-message "1.0.0" "1.2.0")]
      (should-contain "1.0.0" msg)
      (should-contain "1.2.0" msg)
      (should-contain "upgrade" msg)))

  (it "update-message is nil when equal or older"
    (should-be-nil (uc/update-message "1.2.0" "1.2.0"))
    (should-be-nil (uc/update-message "1.2.0" "1.1.0")))

  (it "update-message is nil when latest is nil"
    (should-be-nil (uc/update-message "1.0.0" nil)))

  (it "stale? is true when checked-at is nil"
    (should (uc/stale? nil 1000 100)))

  (it "stale? is true when older than ttl"
    (should (uc/stale? 0 101 100)))

  (it "stale? is false when within ttl"
    (should-not (uc/stale? 50 100 100)))

  (it "parse-cache reads a map and rejects non-maps / garbage"
    (should= {:checked-at 5 :latest-tag "1.2.0"}
             (uc/parse-cache "{:checked-at 5 :latest-tag \"1.2.0\"}"))
    (should-be-nil (uc/parse-cache "42"))
    (should-be-nil (uc/parse-cache "}{ not edn")))

  (it "render-cache round-trips with parse-cache"
    (let [m {:checked-at 5 :latest-tag "1.2.0"}]
      (should= m (uc/parse-cache (uc/render-cache m)))))

  (it "disabled-by-env? is true only for a non-blank value"
    (should-not (uc/disabled-by-env? nil))
    (should-not (uc/disabled-by-env? ""))
    (should (uc/disabled-by-env? "1"))
    (should (uc/disabled-by-env? "yes"))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd cli && bb test`
Expected: FAIL — namespace `c3kit-jig.update-check` does not exist.

- [ ] **Step 3: Write the namespace**

Create `cli/src/c3kit_jig/update_check.clj`:

```clojure
(ns c3kit-jig.update-check
  "Detect whether a newer CLI release is available and, when allowed, surface
   it. Pure decision logic plus thin, non-throwing IO glue."
  (:require [babashka.fs :as fs]
            [c3kit-jig.ui :as ui]
            [c3kit-jig.version :as version]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private ttl-ms 86400000)

(def ^:private cache-file
  (str (fs/path (System/getProperty "user.home") ".c3kit" "update-check.edn")))

;; --- pure ---

(defn update-message
  "Notice string when `latest` is strictly newer than `current`, else nil."
  [current latest]
  (when (and latest (neg? (version/semver-compare current latest)))
    (str "A new version of c3kit-jig is available: " current " → " latest
         ". Run `c3kit-jig upgrade` to update.")))

(defn stale?
  "True when `checked-at` is nil or older than `ttl-ms` before `now`."
  [checked-at now ttl-ms]
  (or (nil? checked-at) (> (- now checked-at) ttl-ms)))

(defn parse-cache
  "Parse cache edn; nil on non-map or parse failure."
  [s]
  (try
    (let [m (edn/read-string s)]
      (when (map? m) m))
    (catch Exception _ nil)))

(defn render-cache [m] (pr-str m))

(defn disabled-by-env?
  "True when the opt-out env value is non-blank."
  [env-value]
  (not (str/blank? env-value)))

;; --- io glue (never throws) ---

(defn- now [] (System/currentTimeMillis))

(defn- read-cache []
  (try
    (when (fs/exists? cache-file) (parse-cache (slurp cache-file)))
    (catch Exception _ nil)))

(defn- write-cache! [m]
  (try
    (fs/create-dirs (fs/parent cache-file))
    (spit cache-file (render-cache m))
    (catch Exception _ nil)))

(defn- fetch-tag! []
  (deref (future (try (version/fetch-latest-tag!) (catch Exception _ nil)))
         2000 nil))

(defn- latest-tag []
  (let [cache (read-cache)]
    (if (and cache (not (stale? (:checked-at cache) (now) ttl-ms)))
      (:latest-tag cache)
      (if-let [tag (fetch-tag!)]
        (do (write-cache! {:checked-at (now) :latest-tag tag}) tag)
        (:latest-tag cache)))))

(defn- enabled? []
  (and (not (disabled-by-env? (System/getenv "C3KIT_NO_UPDATE_CHECK")))
       (ui/tty?)))

(defn available-update
  "Return {:current .. :latest ..} when an update is available and checking is
   enabled, else nil. Never throws."
  []
  (when (enabled?)
    (let [current (version/current)
          latest  (latest-tag)]
      (when (update-message current latest)
        {:current current :latest latest}))))

(defn notify!
  "Print an update notice to stderr when an update is available."
  []
  (when-let [{:keys [current latest]} (available-update)]
    (ui/warn (update-message current latest))))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd cli && bb test`
Expected: PASS (all update-check specs green).

- [ ] **Step 5: Verify the namespace loads and the pure path works from a REPL**

Run:
```sh
cd cli && bb -e "(require '[c3kit-jig.update-check :as uc]) (println (uc/update-message \"1.0.0\" \"1.2.0\")) (println (uc/update-message \"1.2.0\" \"1.2.0\"))"
```
Expected: first line prints the `1.0.0 → 1.2.0 … upgrade` notice; second line prints `nil` (loads clean, no errors).

- [ ] **Step 6: Lint**

Run: `cd cli && bb lint`
Expected: no errors, no warnings.

- [ ] **Step 7: Commit**

```bash
git add cli/src/c3kit_jig/update_check.clj cli/spec/c3kit_jig/update_check_spec.clj
git commit -m "feat(cli): add update-check namespace (detect newer release)"
```

---

## Task 2: Wire the check into `main.clj`

**Files:**
- Modify: `cli/src/c3kit_jig/main.clj`

**Interfaces:**
- Consumes: `update-check/available-update`, `update-check/update-message`, `update-check/notify!` (Task 1); existing `wizard/prompt-yn [label default] -> boolean`, `ui/warn`, `ui/info`, `exit`.

- [ ] **Step 1: Require the namespace**

In `cli/src/c3kit_jig/main.clj`, add to the `:require` vector (alphabetical, after `ui`):

```clojure
            [c3kit-jig.update-check :as update-check]
```

So the relevant lines read:

```clojure
            [c3kit-jig.ui :as ui]
            [c3kit-jig.update-check :as update-check]
            [c3kit-jig.version :as v]
```

- [ ] **Step 2: Notify on `list`**

In `-main`, replace the `:list` branch:

```clojure
        :list     (do (ui/info "List of templates not yet implemented.") (exit 0))
```

with:

```clojure
        :list     (do (update-check/notify!)
                      (ui/info "List of templates not yet implemented.")
                      (exit 0))
```

- [ ] **Step 3: Prompt up front on `create` (the `:scaffold` action)**

In `-main`, the `:scaffold` branch currently is:

```clojure
        :scaffold (cond
                    (and (:yes options) (not (:template options)))
                    (do (ui/fail "Missing required option: --template ID (required with --yes)")
                        (exit 2))
                    :else
                    (let [opts (prompt-missing options)]
                      (try
                        (scaffold! opts)
                        (exit 0)
                        (finally
                          (cfs/cleanup! (::clone-stage opts))))))
```

Replace the `:else` branch body so the update check runs **before** `prompt-missing`:

```clojure
        :scaffold (cond
                    (and (:yes options) (not (:template options)))
                    (do (ui/fail "Missing required option: --template ID (required with --yes)")
                        (exit 2))
                    :else
                    (do
                      (when-let [{:keys [current latest]} (update-check/available-update)]
                        (if (:yes options)
                          (ui/warn (update-check/update-message current latest))
                          (when-not (wizard/prompt-yn
                                     (str "Update available (" current " → " latest "). Continue anyway?")
                                     true)
                            (ui/info "Run `c3kit-jig upgrade` to update.")
                            (exit 0))))
                      (let [opts (prompt-missing options)]
                        (try
                          (scaffold! opts)
                          (exit 0)
                          (finally
                            (cfs/cleanup! (::clone-stage opts)))))))
```

- [ ] **Step 4: Run the full suite + lint**

Run: `cd cli && bb test && bb lint`
Expected: all tests pass (153 + the new update-check specs), lint clean. (No new tests here — this step is wiring; behavior is verified manually in Step 5 because the guards require a real TTY.)

- [ ] **Step 5: Manual TTY verification (notice + opt-out + guard)**

The TTY guard means the notice only appears in a real terminal, so verify by hand in a terminal (not a pipe). Pin a fresh, newer cache so no network is needed:

```sh
cd cli
mkdir -p "$HOME/.c3kit"
# Fresh (checked-at = now) so no fetch happens; latest far ahead of source version.
echo "{:checked-at $(($(date +%s)*1000)) :latest-tag \"99.0.0\"}" > "$HOME/.c3kit/update-check.edn"

bb c3kit-jig list                      # expect the "99.0.0 … upgrade" notice on stderr, then the list stub line
C3KIT_NO_UPDATE_CHECK=1 bb c3kit-jig list   # expect NO notice
bb c3kit-jig list | cat                # piped (not a TTY): expect NO notice
```
Expected: notice appears only in the first invocation. Then remove the test cache so it does not linger:
```sh
rm -f "$HOME/.c3kit/update-check.edn"
```

- [ ] **Step 6: Commit**

```bash
git add cli/src/c3kit_jig/main.clj
git commit -m "feat(cli): check for updates up front on create and list"
```

---

## Task 3: Documentation

**Files:**
- Modify: `README.md`
- Modify: `CHANGES.md`

**Interfaces:** none (docs only).

- [ ] **Step 1: Note the auto-notice in README.md**

In `README.md`, the `## Versioning` section ends with a paragraph about templates. Immediately after that paragraph, add:

```markdown
The CLI checks for a newer release at most once a day (cached in
`~/.c3kit/update-check.edn`) and, when one exists, says so before `create`
and `list`. Set `C3KIT_NO_UPDATE_CHECK=1` to disable the check; it is also
skipped automatically when output is not a terminal (CI, pipes).
```

- [ ] **Step 2: Add a CHANGES.md entry under `### 1.0.0`**

In `CHANGES.md`, under the `### 1.0.0` heading, add a new bullet at the end of that section's list:

```markdown
 * Up-front update check: `create` and `list` surface a newer release (cached daily; `C3KIT_NO_UPDATE_CHECK` opts out).
```

- [ ] **Step 3: Commit**

```bash
git add README.md CHANGES.md
git commit -m "docs: document the update-availability notice"
```

---

## Self-Review

**Spec coverage:**
- New `update-check` ns, pure + IO split → Task 1. ✓
- `update-message` / `stale?` / `parse-cache` / `render-cache` pure + tested → Task 1 Steps 1,3. ✓
- Disable guards (env + non-TTY) → `disabled-by-env?` (tested) + `enabled?` using `ui/tty?` → Task 1. ✓
- 24h cache, 2s fetch ceiling, fall back to stale, never throws → Task 1 `latest-tag`/`fetch-tag!`. ✓
- Up-front wiring: `create` prompt (+ `--yes` notify-only), `list` notify, exclusions → Task 2. ✓
- stderr for notices; prompt via `wizard/prompt-yn` → Task 2 Step 3. ✓
- Docs (README + CHANGES under 1.0.0) → Task 3. ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code; commands have expected output. The TTY-dependent path is verified manually (Step 5) and the plan says so explicitly rather than faking automated coverage.

**Type consistency:** `available-update` returns `{:current :latest}`, destructured identically in Task 2 Steps 2/3. `update-message [current latest]`, `stale? [checked-at now ttl-ms]`, `disabled-by-env? [env-value]` match between the ns (Task 1 Step 3), the tests (Step 1), and the callers (Task 2). `wizard/prompt-yn [label default]` returns boolean — `when-not` on it drives the abort path correctly (Enter/`y` → true → proceed; `n` → false → exit 0).
