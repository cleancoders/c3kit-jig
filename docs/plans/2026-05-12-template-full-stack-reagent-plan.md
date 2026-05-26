# `templates/full-stack-reagent` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adapt the proprietary Clean Coders starter into `templates/full-stack-reagent/` so the `c3kit-create` CLI can scaffold personalized projects from it with feature/DB toggles.

**Architecture:** Copy the proprietary tree at `/Users/alex-root-roatch/current-projects/starter` (read-only source-of-truth) into `templates/full-stack-reagent/`, strip proprietary refs (AWS SDKs, `my.datomic.com`, `bin/setup`, internal seed data), insert `;; @c3kit/feature :<id>` and `;; @c3kit/db :<id>` markers per the spec, add a manifest (`c3kit-template.edn`) and a post-scaffold hook (`c3kit-template.bb`), and verify with two test layers: (1) `clj -M:test:spec` runs against the un-scaffolded template tree at HEAD, (2) a bb-based scaffold-and-assert harness exercises the CLI across a `{db × feature-combo}` matrix.

**Tech Stack:** Clojure 1.12, ClojureScript, Babashka (for hook + verifier), Speclj (existing test framework), GitHub Actions (CI), c3kit/{apron, bucket, wire, scaffold}, Reagent.

**Parent spec:** [`docs/specs/2026-05-12-template-full-stack-reagent-design.md`](../specs/2026-05-12-template-full-stack-reagent-design.md). Section references in this plan (e.g., §6.3) point to that spec.

**Working tree:** `/Users/alex-root-roatch/current-projects/c3kit-jig` on branch `template/full-stack-reagent` (already created, T1 spec commit `a79361b` is the tip).

**Source-of-truth (read-only):** `/Users/alex-root-roatch/current-projects/starter`. **Do not modify.**

---

## Coordination With CLI Work

`cli/v0.1` is being implemented in parallel by a separate agent. The T1 implementation **depends on** four small CLI additions (spec §10). Task 0 below produces a coordination memo so the CLI agent can pick them up. Tasks 1–11 of this plan can land before the CLI is fully built (they touch only `templates/`); Tasks 12+ require the CLI to be invocable end-to-end.

When the CLI agent finishes Tasks 1–9 of its own plan and lands the prerequisites listed in T1 spec §10, the verifier tasks (Task 13 onward) become runnable.

---

## File Structure

New files under `templates/full-stack-reagent/`:

| Path | Responsibility |
|------|----------------|
| `c3kit-template.edn` | Template manifest declaring tokens, secrets, features, DB options, next-steps, hook flag |
| `c3kit-template.bb` | Post-scaffold hook: generate `bin/db`, reconcile `:bucket` in `config.clj`, drop `:seed` alias when auth-off, residue grep |
| `LICENSE` | MIT |
| `README.md` | Template-dev README (audience: T1 contributors) |
| `README.scaffold.md` | Scaffolded-project README (CLI copies → user's `README.md`) |
| `bin/db.template` | DB-start script template fragments referenced by hook |
| `dev/verify-scaffold.bb` | bb script that scaffolds the template via the CLI and asserts file tree + content |
| `spec/combos/*.expected.edn` | Per-`{db × features}` combo: expected files present / absent / content patterns |

Modified-from-proprietary files (copied + edited):

| Path (relative to `templates/full-stack-reagent/`) | Changes |
|---|---|
| `deps.edn` | Remove `my.datomic.com` mvn-repos; remove all `aws-java-sdk-*` deps; add feature/db markers (§6.1) |
| `src/clj/acme/main.clj` | Feature markers for `:content`, `:ssr`, `:auth` (§6.2) |
| `src/clj/acme/config.clj` | DB markers with distinct var names (§6.3); auth marker for `:jwt-secret` |
| `src/clj/acme/routes.clj` | Feature markers for `:csp`, `:content`, `:auth` (§6.4) |
| `src/clj/acme/http.clj` | Feature markers for `:csp`, `:auth` (§6.5) |
| `src/clj/acme/layouts.clj` | Feature markers for `:ssr`, `:content` (§6.6) |
| `src/clj/acme/email.clj` | Remove `:ses` defmethod entirely; keep `:to-log` only |
| `src/cljc/acme/schema.cljc` | Auth marker for `user.schema/all` (§6.7) |
| `src/cljs/acme/main.cljs` | Feature markers for `:content`, `:auth` (§6.8) |
| `src/cljs/acme/routes.cljs` | Auth markers for forgot/recover/signin pages (§6.9) |
| `dev/acme/seed.clj` | Auth marker for user fixtures (§6.10) |

Removed from proprietary:

| Path | Reason |
|---|---|
| `bin/setup` | CLI owns rename |
| `.idea/`, `starter.iml` | IDE-specific |
| `my.datomic.com` repo entry in `deps.edn` | Datomic Pro free tier on Maven Central |
| `com.amazonaws/aws-java-sdk-*` deps | Drop from OSS; user adds back for AWS deployment |
| `:ses` defmethod in `src/clj/acme/email.clj` | Tied to AWS SDK |

Workflow added in the c3kit-jig repo:

| Path | Responsibility |
|---|---|
| `.github/workflows/template-full-stack-reagent.yml` | Matrix CI: build CLI uberscript → scaffold + verify per combo |

---

## TDD Strategy

Two test layers:

**Layer A — Template-at-HEAD spec run.** After every marker change, `cd templates/full-stack-reagent && clj -M:test:spec` must pass. Markers are line comments, so the Clojure reader sees through them. This catches: malformed marker syntax, accidentally commented-out non-marker code, broken requires.

**Layer B — Scaffold-and-assert (bb).** `templates/full-stack-reagent/dev/verify-scaffold.bb` shells out to the local CLI uberscript, scaffolds the template with a chosen `{db, features}` combo into a temp dir, then asserts against `spec/combos/<combo>.expected.edn`:
- Expected files present.
- Expected files absent (per `:delete-when-off`).
- Expected substring matches in scaffolded files (e.g., the chosen `:bucket` line is present in `config.clj`).
- No `acme` / `Acme` / `ACME_` residue (grep).
- No `@c3kit/feature` / `@c3kit/db` marker residue (grep).
- `clj -M:test:spec` exits 0 inside the scaffolded dir.
- `clj -M:test:cljs once` exits 0 inside the scaffolded dir.

Layer B requires the CLI prerequisites in spec §10 to be landed. Until they are, Layer B tasks are blocked; Layer A keeps the marker work honest.

**Per-task TDD shape (markers):**
1. Add the assertion to the relevant combo `.expected.edn` (red).
2. Run Layer A: `clj -M:test:spec` in the template tree — confirm currently green.
3. Edit markers per the spec.
4. Run Layer A again — confirm still green.
5. (Once Layer B is available) Run `bb dev/verify-scaffold.bb --combo <name>` — confirm green.
6. Commit.

**Per-task TDD shape (verifier code):**
1. Write a Speclj/bb-test for the verifier helper (red).
2. Implement helper (green).
3. Commit.

---

## Task 0: Coordination Memo to CLI Agent

**Why:** T1 needs four small CLI additions (T1 spec §10). The CLI agent works in parallel; surface the needs in a doc the CLI agent can find and incorporate before T1 starts Layer B verification.

**Files:**
- Create: `docs/handoffs/2026-05-12-cli-prerequisites-for-t1.md`

- [ ] **Step 1: Write the memo**

Create `docs/handoffs/2026-05-12-cli-prerequisites-for-t1.md`:

```markdown
# CLI Prerequisites for T1 (templates/full-stack-reagent)

**Date:** 2026-05-12
**From:** T1 (templates/full-stack-reagent) implementation
**To:** CLI implementation
**Status:** Coordination request; not blocking T1 marker work

T1 spec §10 lists four small CLI additions T1 needs before its scaffold-and-assert
verification (Layer B) can run. Land these whenever it fits the CLI plan;
T1 marker work proceeds in parallel without them.

## 1. Per-feature CLI flag

For CI matrix runs, the CLI must accept `--feature <id>=<bool>` repeated:

    c3kit-create test-app \
      --template-dir templates --template full-stack-reagent \
      --feature auth=false --feature csp=true \
      --yes

Precedence (per CLI sub-spec §3): CLI flag > env > wizard > manifest default.
Manifest already names features; this just exposes the override.

## 2. Scaffold context file for hooks

Before invoking `c3kit-template.bb` (CLI sub-spec §7.2 stage 3), write a file at
`$STAGE/scaffold/.c3kit-create-context.edn` containing:

    {:name           "my-app"
     :name-variants  {:hyphen "my-app" :underscore "my_app" :pascal "MyApp"}
     :db             :sqlite
     :features       {:content true :ssr true :csp false :markdownc true :auth true}
     :secrets        [{:placeholder "ACME_DEV_SECRET" :generated "<hex>"} …]
     :template       :full-stack-reagent
     :template-version "0.1.0"
     :cli-version    "0.1.0"}

After the hook completes, the CLI deletes the file before the atomic move.

## 3. README.scaffold.md rename convention

When the template includes a `README.scaffold.md`, the CLI should:
- Delete any existing `README.md` from the scaffolded tree (template-dev README).
- Rename `README.scaffold.md` → `README.md`.

Cleanest implementation: add an optional manifest key `:readme-source "README.scaffold.md"`.
Alternative: convention-based — if `README.scaffold.md` exists, always perform
the rename. Either works for T1; convention is simpler.

## 4. `--db <id>` flag

CLI sub-spec §3 implies `--db` but doesn't list it in the OPTIONS table. Confirm
it's accepted; same precedence as `--feature`. T1 CI uses it heavily.

## Coordination

When all four land, ping the T1 plan (Task 12 onward becomes unblocked). T1
will adapt to whatever shape the CLI ships — these are the minimal asks.
```

- [ ] **Step 2: Commit**

```bash
git add docs/handoffs/2026-05-12-cli-prerequisites-for-t1.md
git commit -m "docs: CLI prerequisites memo for T1 (templates/full-stack-reagent)"
```

---

## Task 1: Copy Proprietary Tree

**Why:** Establish the baseline template directory before any edits.

**Files:**
- Create: `templates/full-stack-reagent/` (mirrors proprietary, minus excluded paths)

- [ ] **Step 1: Verify proprietary source is at expected state**

Run:
```bash
ls -la /Users/alex-root-roatch/current-projects/starter
test -f /Users/alex-root-roatch/current-projects/starter/deps.edn && echo "ok"
```
Expected: `ok` printed; `deps.edn`, `src/`, `dev/`, `spec/`, `resources/`, `content/`, `package.json`, `bin/` listed.

- [ ] **Step 2: Replace the placeholder with the copied tree**

The repo currently has `templates/.gitkeep`. Remove it and copy:

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
rm templates/.gitkeep
mkdir -p templates/full-stack-reagent

rsync -a \
  --exclude='.git/' \
  --exclude='.idea/' \
  --exclude='node_modules/' \
  --exclude='.cpcache/' \
  --exclude='target/' \
  --exclude='out/' \
  --exclude='starter.iml' \
  --exclude='resources/public/cljs/' \
  --exclude='resources/public/specs/' \
  /Users/alex-root-roatch/current-projects/starter/ \
  templates/full-stack-reagent/
```

- [ ] **Step 3: Sanity-check the copy**

```bash
test -f templates/full-stack-reagent/deps.edn && echo "deps ok"
test -f templates/full-stack-reagent/src/clj/acme/main.clj && echo "main ok"
test -f templates/full-stack-reagent/bin/setup && echo "setup still here (will be removed in Task 2)"
test ! -d templates/full-stack-reagent/.idea && echo "no idea"
```
Expected: `deps ok`, `main ok`, `setup still here (will be removed in Task 2)`, `no idea`.

- [ ] **Step 4: Run the template tree's specs as a baseline**

```bash
cd templates/full-stack-reagent
clj -M:test:spec
```
Expected: green (all proprietary specs pass on the copied tree).

If this fails, **stop**: something about the copy is incomplete. Inspect `clj -M:test:spec` output and fix the copy before continuing. Do not proceed until baseline is green.

- [ ] **Step 5: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add -A templates/
git commit -m "chore(t1): copy proprietary starter tree into templates/full-stack-reagent

Direct rsync of the read-only proprietary starter at
/Users/alex-root-roatch/current-projects/starter, excluding IDE files,
build artifacts, and node_modules. Baseline specs (clj -M:test:spec)
pass against the copied tree.

Next tasks strip proprietary refs and add @c3kit/feature markers."
```

---

## Task 2: Strip Proprietary Refs

**Why:** Remove paths and code that are tied to internal Clean Coders infrastructure (AWS SDK, `my.datomic.com` repo, `bin/setup`) before adding markers. Keeps the tree small and aligned with the spec.

**Files:**
- Delete: `templates/full-stack-reagent/bin/setup`
- Modify: `templates/full-stack-reagent/deps.edn` (remove `my.datomic.com` repo + AWS deps)
- Modify: `templates/full-stack-reagent/src/clj/acme/email.clj` (remove `:ses` defmethod + AWS imports)

- [ ] **Step 1: Add a spec asserting the email :to-log path still works**

Edit `templates/full-stack-reagent/spec/clj/acme/email_spec.clj`. If it doesn't already cover `:to-log`, add:

```clojure
(it "to-log client logs subject + body and does not call SES"
  (let [logged (atom [])]
    (with-redefs [c3kit.apron.log/report (fn [& args] (swap! logged conj args))]
      (sut/client-send-email {:client :to-log}
        {:from "a@b" :to "c@d" :subject "Hi" :text "Body" :html "<p>Body</p>"}))
    (should-contain "Hi" (pr-str @logged))
    (should-contain "Body" (pr-str @logged))))
```

(If the existing spec already covers this, skip — just confirm it's present.)

- [ ] **Step 2: Run the spec**

```bash
cd templates/full-stack-reagent
clj -M:test:spec -- -e acme.email_spec
```
Expected: green (uses existing `:ses` defmethod's siblings; the `:to-log` impl already exists).

- [ ] **Step 3: Remove the `:ses` defmethod and AWS imports from `acme.email`**

Edit `templates/full-stack-reagent/src/clj/acme/email.clj`:

Replace the `ns` form, removing AWS imports:

```clojure
(ns acme.email
  (:require [acme.config :as config]
            [acme.markdown :as markdown]
            [c3kit.apron.log :as log]))
```

Delete:
- `(def ses-client …)` entirely.
- `(defn ses-destination …)` entirely.
- `(defn ses-message …)` entirely.
- `(defmethod client-send-email :ses [_ …] …)` entirely.

Keep: `client-send-email` defmulti, `->email-list`, `:to-log` defmethod, `send-email`.

Add a one-line comment marker at the top of the file so future readers know SES was intentional drop:

```clojure
;; OSS template ships only :to-log. To send real email (e.g. via AWS SES),
;; add com.amazonaws/aws-java-sdk-ses to deps.edn and define a (defmethod client-send-email :ses [_ email] …).
```

- [ ] **Step 4: Re-run specs**

```bash
clj -M:test:spec
```
Expected: green. If `email_spec.clj` had `:ses`-specific tests, remove them (they tested deleted code).

- [ ] **Step 5: Strip `my.datomic.com` and AWS deps from `deps.edn`**

Edit `templates/full-stack-reagent/deps.edn`. Replace the entire `:mvn/repos` block (drop `my.datomic.com`):

```clojure
:mvn/repos {"maven_central" {:url "https://repo.maven.apache.org/maven2/"}}
```

Delete these lines from `:deps`:
- `com.amazonaws/aws-java-sdk-dynamodb …`
- `com.amazonaws/aws-java-sdk-s3 …`
- `com.amazonaws/aws-java-sdk-ses …`
- `com.amazonaws/aws-java-sdk-core …`
- `com.google.api-client/google-api-client …` (only used by `acme.user.google`; will stay or be auth-gated; **keep it for now**, it'll move under `:auth` marker in Task 8)
- `com.google.http-client/google-http-client …` (same)

Result: `deps.edn` has only `c3kit/*`, `com.datomic/peer`, `commonmark/*`, `compojure`, `medley`, `hiccup`, `clojure`, `jbcrypt`, `markdown-to-hiccup`, `ring-anti-forgery`, plus `com.google.api-client` (deferred to Task 8).

- [ ] **Step 6: Confirm `clj -M:test:spec` still green**

```bash
clj -M:test:spec
```
Expected: green. If any spec required AWS classes (unlikely), surface and decide: drop the spec, or stub.

- [ ] **Step 7: Remove `bin/setup`**

```bash
rm templates/full-stack-reagent/bin/setup
```

Keep `bin/clean` (developer convenience, not rename-related).

- [ ] **Step 8: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add -A templates/full-stack-reagent/
git commit -m "chore(t1): strip proprietary refs (AWS SDKs, my.datomic.com, bin/setup, :ses)

- Remove com.amazonaws/aws-java-sdk-* deps from templates/full-stack-reagent/deps.edn
- Remove my.datomic.com mvn-repos entry (Datomic Pro free tier is on Maven Central)
- Delete templates/full-stack-reagent/bin/setup (CLI owns rename)
- Delete :ses defmethod + AWS imports from acme.email (:to-log only ships)
- Add a comment pointing users to AWS-SES re-add path

Specs still pass against the template tree at HEAD."
```

---

## Task 3: Write `c3kit-template.edn` Manifest

**Why:** The CLI keys off this file (CLI sub-spec §5). Land it early so subsequent feature marker tasks can be cross-checked against `:delete-when-off` paths.

**Files:**
- Create: `templates/full-stack-reagent/c3kit-template.edn`

- [ ] **Step 1: Write the manifest verbatim from spec §5**

Create `templates/full-stack-reagent/c3kit-template.edn`:

```clojure
{:id          :full-stack-reagent
 :name        "Full-stack Reagent"
 :description "Clojure backend + Reagent frontend with c3kit; optional SSR, CSP, content pipeline, client-side markdown, JWT auth"
 :version     "0.1.0"
 :min-cli     "0.1.0"
 :test-only?  false

 :tokens      {"acme"  {:hyphen     true
                        :underscore true
                        :pascal     true}
               "ACME_" {:upper-prefix true}}

 :secrets     [{:placeholder "ACME_DEV_SECRET"        :bytes 24}
               {:placeholder "ACME_STAGING_SECRET"    :bytes 24}
               {:placeholder "ACME_PRODUCTION_SECRET" :bytes 24}]

 :features    [{:id      :content
                :prompt  "Markdown content pipeline?"
                :default true
                :delete-when-off ["content/"
                                  "src/clj/{{acme}}/content.clj"
                                  "src/clj/{{acme}}/markdown.clj"
                                  "src/cljs/{{acme}}/content_page.cljs"
                                  "spec/clj/{{acme}}/content_spec.clj"
                                  "spec/clj/{{acme}}/markdown_spec.clj"
                                  "spec/cljs/{{acme}}/content_page_spec.cljs"]}

               {:id      :ssr
                :prompt  "SSR/prerender (Reagent + Node)?"
                :default true
                :delete-when-off ["package.json"
                                  "resources/prerender/"
                                  "resources/prerendered/"
                                  "src/clj/{{acme}}/prerender.clj"
                                  "spec/clj/{{acme}}/prerender_spec.clj"
                                  "dev/{{acme}}/prerender.cljs"
                                  "dev/{{acme}}/prerender_pages.cljs"
                                  "dev/{{acme}}/prerender_preamble.js"]}

               {:id      :csp
                :prompt  "Content Security Policy plugin?"
                :default false
                :delete-when-off ["src/clj/{{acme}}/security/"
                                  "spec/clj/{{acme}}/security/"]}

               {:id      :markdownc
                :prompt  "Client-side markdown (CLJC)?"
                :default true
                :delete-when-off ["src/cljc/{{acme}}/markdownc.cljc"
                                  "spec/cljc/{{acme}}/markdownc_spec.cljc"]}

               {:id      :auth
                :prompt  "JWT auth?"
                :default true
                :delete-when-off ["src/clj/{{acme}}/user.clj"
                                  "src/clj/{{acme}}/user/"
                                  "src/clj/{{acme}}/session.clj"
                                  "src/clj/{{acme}}/destination.clj"
                                  "src/cljc/{{acme}}/user/"
                                  "src/cljs/{{acme}}/user.cljs"
                                  "src/cljs/{{acme}}/forgot_password.cljs"
                                  "src/cljs/{{acme}}/recover_password.cljs"
                                  "spec/clj/{{acme}}/user/"
                                  "spec/clj/{{acme}}/session_spec.clj"
                                  "spec/clj/{{acme}}/destination_spec.clj"
                                  "spec/cljs/{{acme}}/user_spec.cljs"
                                  "spec/cljs/{{acme}}/forgot_password_spec.cljs"
                                  "spec/cljs/{{acme}}/recover_password_spec.cljs"]}]

 :db          {:prompt  "Database"
               :options [{:id :datomic-pro :label "Datomic Pro (free, single-jar transactor)"}
                         {:id :sqlite      :label "SQLite (JDBC)"}
                         {:id :postgres    :label "Postgres (JDBC)"}
                         {:id :memory      :label "In-memory (dev only)"}]
               :default :datomic-pro}

 :next-steps  [{:cmd "cd {{name}}"           :doc nil}
               {:cmd "bin/db"                :doc "start the database (per-backend script)"}
               {:cmd "clj -M:test:migrate"   :doc "run migrations"}
               {:cmd "clj -M:test:seed"      :doc "seed dev data (auth only)"}
               {:cmd "clj -M:test:spec"      :doc "run Clojure specs"}
               {:cmd "clj -M:test:cljs"      :doc "run ClojureScript specs (auto-watch)"}
               {:cmd "clj -M:test:css"       :doc "compile CSS (auto-watch)"}
               {:cmd "clj -M:test:cljss"    :doc "compile CLJS + CSS (auto-watch, combined)"}
               {:cmd "clj -M:test:dev"      :doc "server + specs + cljs in one process"}
               {:cmd "clj -M:test:run"      :doc "server only"}]

 :hook?       true}
```

- [ ] **Step 2: Verify it parses as valid EDN**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
bb -e "(clojure.edn/read-string (slurp \"templates/full-stack-reagent/c3kit-template.edn\"))"
```
Expected: prints the parsed map, no exception.

- [ ] **Step 3: Verify every `:delete-when-off` path exists in the template tree**

Run:
```bash
bb <<'EOF'
(let [m (clojure.edn/read-string (slurp "templates/full-stack-reagent/c3kit-template.edn"))
      root "templates/full-stack-reagent"]
  (doseq [feat (:features m)
          path (:delete-when-off feat)]
    (let [resolved (clojure.string/replace path "{{acme}}" "acme")
          full     (str root "/" resolved)
          exists?  (or (.exists (java.io.File. full))
                       (.exists (java.io.File. (clojure.string/replace full #"/$" ""))))]
      (when-not exists?
        (println "MISSING:" feat path "→" full))))
  (println "done"))
EOF
```
Expected: only `done` printed; no `MISSING:` lines.

If any path is missing, **stop**: the manifest is wrong, or the proprietary tree differs from what spec §6 assumed. Fix the manifest path (or surface the discrepancy and ask whether to amend the spec).

- [ ] **Step 4: Commit**

```bash
git add templates/full-stack-reagent/c3kit-template.edn
git commit -m "feat(t1): add c3kit-template.edn manifest

Declares tokens, secrets, 5 features, 4 db options, next-steps, and
hook? = true per spec §5. All :delete-when-off paths verified present
in the template tree."
```

---

## Task 4: Add `:csp` Markers

**Why:** Smallest feature; establishes the marker pattern (block + line-toggle) before tackling heavier features. Verifies Layer A keeps the template runnable.

**Files:**
- Modify: `templates/full-stack-reagent/src/clj/acme/config.clj`
- Modify: `templates/full-stack-reagent/src/clj/acme/http.clj`
- Modify: `templates/full-stack-reagent/src/clj/acme/routes.clj`

- [ ] **Step 1: Confirm Layer A baseline still green**

```bash
cd templates/full-stack-reagent
clj -M:test:spec
```
Expected: green.

- [ ] **Step 2: Add `:csp` block marker around the `:csp` map in `config.clj`**

Edit `templates/full-stack-reagent/src/clj/acme/config.clj`. Wrap the `:csp` entry in the `base` map:

Replace:
```clojure
(def ^:private base
  {:analytics-code "console.log('google analytics would have loaded for this page');"
   :log-level      :info
   :csp            {:enabled?       false
                    :enforce?       false
                    :report-handler nil
                    :policy         nil}})
```

With:
```clojure
(def ^:private base
  {:analytics-code "console.log('google analytics would have loaded for this page');"
   :log-level      :info
   ;; @c3kit/feature :csp {
   :csp            {:enabled?       false
                    :enforce?       false
                    :report-handler nil
                    :policy         nil}
   ;; @c3kit/feature :csp }
   })
```

- [ ] **Step 3: Add `:csp` line-toggle for the `acme.security.csp` require in `http.clj`**

Edit `templates/full-stack-reagent/src/clj/acme/http.clj`. In the `ns` form, replace:

```clojure
            [acme.security.csp :as csp]
```

With:

```clojure
            ;; @c3kit/feature :csp = [acme.security.csp :as csp]
```

- [ ] **Step 4: Add `:csp` block markers around `maybe-wrap-csp` defn and its invocation in `http.clj`**

In `http.clj`, replace:

```clojure
(defn- maybe-wrap-csp [handler]
  (let [{:keys [enabled?] :as csp-cfg} (:csp config/env)]
    (if enabled?
      (csp/wrap-csp handler (merge {:policy csp/default-policy} csp-cfg))
      handler)))
```

With:

```clojure
;; @c3kit/feature :csp {
(defn- maybe-wrap-csp [handler]
  (let [{:keys [enabled?] :as csp-cfg} (:csp config/env)]
    (if enabled?
      (csp/wrap-csp handler (merge {:policy csp/default-policy} csp-cfg))
      handler)))
;; @c3kit/feature :csp }
```

And in the `defonce root-handler` thread, wrap the `maybe-wrap-csp` line:

```clojure
(defonce root-handler
  (-> (app-handler)
      ;; @c3kit/feature :csp {
      maybe-wrap-csp
      ;; @c3kit/feature :csp }
      wrap-security-headers
      ;; … rest unchanged
      ))
```

- [ ] **Step 5: Add `:csp` block markers in `routes.clj` `api-handler`**

Edit `templates/full-stack-reagent/src/clj/acme/routes.clj`. The current `api-handler` def:

```clojure
(def api-handler
  (let [csp-on?   (-> config/env :csp :enabled?)
        primary   (wire.routes/lazy-routes {…})
        csp-extra (wire.routes/lazy-routes
                    {["/v1/csp-report" :post] acme.security.csp/csp-report-handler})]
    (-> (if csp-on? (compojure/routes primary csp-extra) primary)
        (rest/wrap-rest {:keywords? true})
        (wire.routes/wrap-prefix "/api" api-not-found-handler))))
```

This is the tangled case flagged in spec §6.4. Refactor first (small extraction), then markers go on the extracted bits.

Replace with:

```clojure
;; @c3kit/feature :csp {
(def csp-routes
  (wire.routes/lazy-routes
    {["/v1/csp-report" :post] acme.security.csp/csp-report-handler}))

(defn- maybe-add-csp-routes [primary]
  (if (-> config/env :csp :enabled?)
    (compojure/routes primary csp-routes)
    primary))
;; @c3kit/feature :csp }

(def api-handler
  (let [primary (wire.routes/lazy-routes
                  {
                   ["/version" :get]                              acme.version/api-get
                   ["/v1/content/:type/:permalink" :get]          acme.content/api-fetch-post
                   ["/user/signin" :post]                         acme.user.api/api-signin
                   ["/user/signup" :post]                         acme.user.api/api-signup
                   ["/user/forgot-password" :post]                acme.user.api/api-forgot-password
                   ["/user/reset-password/:recovery-token" :post] acme.user.api/api-reset-password
                   ["/user/social/:provider" :post]               acme.user.api/api-social-auth
                   })]
    (-> primary
        ;; @c3kit/feature :csp = maybe-add-csp-routes
        (rest/wrap-rest {:keywords? true})
        (wire.routes/wrap-prefix "/api" api-not-found-handler))))
```

The `;; @c3kit/feature :csp = maybe-add-csp-routes` line-marker becomes `maybe-add-csp-routes` when `:csp` on, and disappears entirely when off. Threading still works in both cases.

(Note for later tasks: `:content` and `:auth` markers will further edit the `primary` map's routes — see Tasks 6 and 8.)

- [ ] **Step 6: Re-run Layer A spec**

```bash
cd templates/full-stack-reagent
clj -M:test:spec
```
Expected: green. The marker comments are line comments, so the Clojure reader is unaffected. The `maybe-add-csp-routes` extraction is a behavioral no-op (same result).

If specs fail: most likely cause is the extraction changed behavior in a way the existing routes_spec catches. Inspect, fix the extraction.

- [ ] **Step 7: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add -A templates/full-stack-reagent/src/clj/acme/config.clj
git add -A templates/full-stack-reagent/src/clj/acme/http.clj
git add -A templates/full-stack-reagent/src/clj/acme/routes.clj
git commit -m "feat(t1): add :csp feature markers

- Block marker around the :csp map in acme.config/base
- Line-toggle for the acme.security.csp require in acme.http
- Block markers around maybe-wrap-csp + its threading slot in acme.http
- Extract maybe-add-csp-routes helper in acme.routes for clean
  marker boundary; threading-level line-toggle

Template-level spec run (clj -M:test:spec) stays green."
```

---

## Task 5: Add `:markdownc` Markers

**Why:** Single-file feature; trivial pass to keep moving.

**Files:**
- Modify: `templates/full-stack-reagent/deps.edn`

(`src/cljc/acme/markdownc.cljc` itself is a whole-file delete via `:delete-when-off`; no marker needed inside it. Its spec at `spec/cljc/acme/markdownc_spec.cljc` likewise. The only inline marker is the dep entry.)

- [ ] **Step 1: Confirm Layer A baseline still green**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green.

- [ ] **Step 2: Add `:markdownc` line-toggle to `deps.edn`**

Edit `templates/full-stack-reagent/deps.edn`. Find the line:

```clojure
markdown-to-hiccup/markdown-to-hiccup {:mvn/version "0.6.2"}
```

Replace with:

```clojure
;; @c3kit/feature :markdownc = markdown-to-hiccup/markdown-to-hiccup {:mvn/version "0.6.2"}
```

- [ ] **Step 3: Re-run Layer A spec**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green. The marker line is a comment; the dep is no longer in the parsed `:deps` map, so `markdown-to-hiccup` won't resolve at HEAD.

If `markdownc.cljc` or its spec requires `markdown-to-hiccup` and the spec is being loaded at HEAD: that's the friction point. Verify by attempting to load the cljc ns:

```bash
clj -M:test -e "(require 'acme.markdownc)"
```
Expected at HEAD: error, because the dep is now hidden behind the marker.

This is the **expected friction**: at HEAD, the markdownc spec must remain runnable. Resolution: move the marker from the dep line into a "load-gated" pattern — keep the dep loadable at HEAD, but mark it as deleted at scaffold time.

Revise Step 2: instead of `;; @c3kit/feature :markdownc =`, leave the dep uncommented in `deps.edn` at HEAD, and instead **rely on `:delete-when-off`** removing `markdownc.cljc` to eliminate the dep's consumer. But the dep itself stays in `deps.edn` for users who opt **in**, which is the default — fine.

If user opts **out**, the dep remains unused in their `deps.edn`. Acceptable cost; users can prune by hand. Spec §6.1 marker was an over-correction; revert.

**Revised Step 2:** Leave `deps.edn` unchanged for `:markdownc`. The `:delete-when-off` paths in the manifest fully cover this feature.

Apply the revision: revert any edit to `deps.edn` for `:markdownc` if you made one.

- [ ] **Step 4: Update the spec to reflect the revised approach**

Edit `docs/specs/2026-05-12-template-full-stack-reagent-design.md` §6.1:

Replace:
```
;; @c3kit/feature :markdownc = markdown-to-hiccup/markdown-to-hiccup {:mvn/version "0.6.2"}
```

With:
```
markdown-to-hiccup/markdown-to-hiccup {:mvn/version "0.6.2"}   ; consumed by acme.markdownc; harmless if :markdownc off
```

And update §11 (Marker Block Audit) to remove the `:markdownc → 1 line` entry (now zero markers inline; whole-file deletes only).

- [ ] **Step 5: Re-run Layer A spec**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green.

- [ ] **Step 6: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add docs/specs/2026-05-12-template-full-stack-reagent-design.md
git commit -m "docs(t1): revise :markdownc to whole-file deletes only (no inline marker)

Discovered during implementation: gating the markdown-to-hiccup dep
behind a :markdownc line-toggle would break the template-at-HEAD
spec run (acme.markdownc would not resolve). Cost is leaving the dep
in deps.edn for users who opt out; benefit is keeping the template
tree fully runnable at HEAD without scaffolding.

:delete-when-off paths in the manifest are unchanged."
```

(If `:markdownc` ends up with no inline markers, the feature is purely a whole-file delete. That's fine — keep it in the manifest's `:features` list because the wizard still prompts for it and the manifest is the single source of truth for what's toggleable.)

---

## Task 6: Add `:content` Markers

**Why:** Touches 3 shared files (`main.clj`, `routes.clj`, `main.cljs`); medium complexity. Locks in the pattern for layouts that consume content.

**Files:**
- Modify: `templates/full-stack-reagent/src/clj/acme/main.clj`
- Modify: `templates/full-stack-reagent/src/clj/acme/routes.clj`
- Modify: `templates/full-stack-reagent/src/clj/acme/layouts.clj`
- Modify: `templates/full-stack-reagent/src/cljs/acme/main.cljs`

- [ ] **Step 1: Confirm Layer A baseline still green**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green.

- [ ] **Step 2: Mark `:content` in `acme.main` (Clojure)**

Edit `templates/full-stack-reagent/src/clj/acme/main.clj`.

In the `ns` form, replace:
```clojure
            [acme.content]
```
With:
```clojure
            ;; @c3kit/feature :content = [acme.content]
```

In `-main`, replace:
```clojure
  (acme.content/load!)
```
With:
```clojure
  ;; @c3kit/feature :content {
  (acme.content/load!)
  ;; @c3kit/feature :content }
```

- [ ] **Step 3: Mark `:content` in `acme.routes`**

Edit `templates/full-stack-reagent/src/clj/acme/routes.clj`.

In `api-handler`'s `primary` lazy-routes map, replace:
```clojure
                     ["/v1/content/:type/:permalink" :get]          acme.content/api-fetch-post
```
With:
```clojure
                     ;; @c3kit/feature :content {
                     ["/v1/content/:type/:permalink" :get]          acme.content/api-fetch-post
                     ;; @c3kit/feature :content }
```

Find `content-routes-handler` and the `(defroutes handler … content-routes-handler …)` body. Wrap both:

```clojure
;; @c3kit/feature :content {
(defn- content-routes-handler [request] ((acme.content/build-routes) request))
;; @c3kit/feature :content }

(defroutes handler
  api-handler
  ajax-routes-handler
  web-routes-handlers
  ;; @c3kit/feature :content {
  content-routes-handler
  ;; @c3kit/feature :content }
  (if config/production? ccc/noop dev-handler))
```

- [ ] **Step 4: Mark `:content` in `acme.layouts`**

Edit `templates/full-stack-reagent/src/clj/acme/layouts.clj`. Identify the markdown-rendering path used for `:seo/preview` (per spec §6.6, this is in `web-rich-client`'s seo-preview branch).

Open the file with `Read` first to locate the exact lines (this plan can't predict them without rereading the file at execution time). Mark the smallest enclosing block of markdown-rendering logic with `;; @c3kit/feature :content {` … `;; @c3kit/feature :content }`.

Verify there are no markdown-consuming lines left unmarked.

- [ ] **Step 5: Mark `:content` in CLJS main**

Edit `templates/full-stack-reagent/src/cljs/acme/main.cljs`.

In the `ns` form, replace:
```clojure
            [acme.content-page]
```
With:
```clojure
            ;; @c3kit/feature :content = [acme.content-page]
```

- [ ] **Step 6: Mark `:content` in CLJS routes**

Edit `templates/full-stack-reagent/src/cljs/acme/routes.cljs`. Find any route that maps to `acme.content-page/...` and wrap with `;; @c3kit/feature :content {` … `;; @c3kit/feature :content }`.

- [ ] **Step 7: Re-run Layer A spec**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green.

Re-run CLJS spec compile:
```bash
clj -M:test:cljs once
```
Expected: compiles + green.

- [ ] **Step 8: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add -A templates/full-stack-reagent/src/
git commit -m "feat(t1): add :content feature markers

- acme.main: gate (acme.content/load!) + the require
- acme.routes: gate the /v1/content/* API route, content-routes-handler,
  and its inclusion in (defroutes handler …)
- acme.layouts: gate markdown-rendering path used for :seo/preview
- acme.main (cljs) + acme.routes (cljs): gate acme.content-page require
  and routes

Template-level spec + cljs compile stay green."
```

---

## Task 7: Add `:ssr` Markers

**Why:** Touches `acme.main` (server) and `acme.layouts` (web-prerendered fn). Most of the SSR feature is whole-file/dir deletes via `:delete-when-off`.

**Files:**
- Modify: `templates/full-stack-reagent/src/clj/acme/main.clj`
- Modify: `templates/full-stack-reagent/src/clj/acme/layouts.clj`

- [ ] **Step 1: Confirm Layer A baseline still green**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green.

- [ ] **Step 2: Mark `:ssr` in `acme.main`**

Edit `templates/full-stack-reagent/src/clj/acme/main.clj`.

In `ns`, replace:
```clojure
            [acme.prerender]
```
With:
```clojure
            ;; @c3kit/feature :ssr = [acme.prerender]
```

In `-main`, wrap the prerender call:
```clojure
  ;; @c3kit/feature :ssr {
  (acme.prerender/prerender!)
  ;; @c3kit/feature :ssr }
```

- [ ] **Step 3: Mark `:ssr` in `acme.layouts`**

Edit `templates/full-stack-reagent/src/clj/acme/layouts.clj`. Locate `web-prerendered` defn (per spec §6.6). Wrap with `;; @c3kit/feature :ssr {` … `;; @c3kit/feature :ssr }`.

If the function is referenced from a route handler elsewhere in this file, also wrap the call site, or refactor the caller to use an indirection that's also marked.

- [ ] **Step 4: Re-run Layer A**

```bash
clj -M:test:spec
```
Expected: green.

- [ ] **Step 5: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add -A templates/full-stack-reagent/src/
git commit -m "feat(t1): add :ssr feature markers

- acme.main: gate require + (acme.prerender/prerender!) call
- acme.layouts: gate web-prerendered fn (and any call site in this ns)

Whole-file deletes (resources/prerender/, package.json, prerender.clj,
dev/acme/prerender_*.cljs) live in :delete-when-off in the manifest."
```

---

## Task 8: Add `:auth` Markers

**Why:** Heaviest feature; spec §6 estimates ~12–14 inline marker blocks. Touches every shared file. If block count exceeds the 14 ceiling (spec §13), refactor by extracting an `acme.auth.middleware` ns so most markers become whole-file deletes.

**Files:**
- Modify: `templates/full-stack-reagent/deps.edn`
- Modify: `templates/full-stack-reagent/src/clj/acme/main.clj`
- Modify: `templates/full-stack-reagent/src/clj/acme/config.clj`
- Modify: `templates/full-stack-reagent/src/clj/acme/routes.clj`
- Modify: `templates/full-stack-reagent/src/clj/acme/http.clj`
- Modify: `templates/full-stack-reagent/src/cljc/acme/schema.cljc`
- Modify: `templates/full-stack-reagent/src/cljs/acme/main.cljs`
- Modify: `templates/full-stack-reagent/src/cljs/acme/routes.cljs`
- Modify: `templates/full-stack-reagent/dev/acme/seed.clj`

- [ ] **Step 1: Confirm Layer A baseline green**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green.

- [ ] **Step 2: Mark auth-only deps in `deps.edn`**

Edit `templates/full-stack-reagent/deps.edn`.

For each of the following deps, replace the bare line with a `;; @c3kit/feature :auth =` line-toggle:
- `org.mindrot/jbcrypt {:mvn/version "0.4"}` (used by user/api password hashing)
- `ring/ring-anti-forgery {:mvn/version "1.4.0" :exclusions [commons-codec/commons-codec]}` (consumed by `acme.http` only in auth path)
- `com.google.api-client/google-api-client {:mvn/version "2.8.1"}` (used by `acme.user.google` only)

Example:
```clojure
;; @c3kit/feature :auth = org.mindrot/jbcrypt {:mvn/version "0.4"}
```

In the `:seed` alias:
```clojure
:seed {:main-opts ["-m" "acme.seed"] :extra-paths ["dev"]}
```
Replace with:
```clojure
;; @c3kit/feature :auth = :seed {:main-opts ["-m" "acme.seed"] :extra-paths ["dev"]}
```

(Note: aliases are an EDN map; the line marker turns this into a comment line and removes it from the parsed map. With `:auth` off, `:seed` isn't in `deps.edn`. With `:auth` on, the CLI's marker stripper preserves the alias.)

If gating dep lines at HEAD breaks Layer A (any of these deps is required by code that loads at HEAD), revisit per Task 5's pattern: keep the dep in `deps.edn`, rely on whole-file deletes. Apply the same revision-and-spec-update pattern.

- [ ] **Step 3: Mark `:auth` in `acme.main` (Clojure)**

Edit `templates/full-stack-reagent/src/clj/acme/main.clj`.

In `ns`:
```clojure
            ;; @c3kit/feature :auth = [acme.destination :as destination]
            ;; @c3kit/feature :auth = [acme.user.web :as user.web]
```

In `-main`, wrap:
```clojure
  ;; @c3kit/feature :auth {
  (destination/configure! (user.web/->AcmeDestinationAdapter))
  ;; @c3kit/feature :auth }
```

- [ ] **Step 4: Mark `:auth` in `acme.config`**

In the `development` def:
```clojure
    ;; @c3kit/feature :auth = :jwt-secret "ACME_DEV_SECRET"
```

Same pattern in `staging` and `production` defs (`ACME_STAGING_SECRET`, `ACME_PRODUCTION_SECRET`).

- [ ] **Step 5: Mark `:auth` in `acme.routes`**

In `api-handler`'s `primary` lazy-routes, wrap the user routes:
```clojure
                     ;; @c3kit/feature :auth {
                     ["/user/signin" :post]                         acme.user.api/api-signin
                     ["/user/signup" :post]                         acme.user.api/api-signup
                     ["/user/forgot-password" :post]                acme.user.api/api-forgot-password
                     ["/user/reset-password/:recovery-token" :post] acme.user.api/api-reset-password
                     ["/user/social/:provider" :post]               acme.user.api/api-social-auth
                     ;; @c3kit/feature :auth }
```

In `ajax-routes-handler`'s map, wrap the auth ajax routes:
```clojure
         ;; @c3kit/feature :auth {
         ["/forgot-password" :post]  acme.user.ajax/ajax-forgot-password
         ["/recover-password" :post] acme.user.ajax/ajax-reset-password
         ["/user/csrf-token" :get]   acme.user.ajax/ajax-csrf-token
         ["/user/signin" :post]      acme.user.ajax/ajax-signin
         ["/user/signup" :post]      acme.user.ajax/ajax-signup
         ;; @c3kit/feature :auth }
```

In `web-routes-handlers`, wrap the auth web routes:
```clojure
     ;; @c3kit/feature :auth {
     ["/forgot-password" :get]                  acme.layouts/web-rich-client
     ["/google/oauth" :post]                    acme.user.web/web-google-oauth-login
     ["/apple/oauth" :post]                     acme.user.web/web-apple-oauth-login
     ["/recover-password/:recovery-token" :get] acme.layouts/web-rich-client
     ["/redirect" :get]                         acme.destination/web-redirect
     ["/signout" :any]                          acme.user.web/web-signout
     ["/signout/:reason" :any]                  acme.user.web/web-signout
     ["/user/websocket" :any]                   acme.user.web/websocket-open
     ;; @c3kit/feature :auth }
```

Wrap `ws-handlers`:
```clojure
;; @c3kit/feature :auth {
(def ws-handlers
  {
   :user/fetch-data 'acme.user.web/ws-fetch-user-data
   })
;; @c3kit/feature :auth }
```

- [ ] **Step 6: Mark `:auth` in `acme.http`**

This is the heaviest single file for `:auth`. Edit `templates/full-stack-reagent/src/clj/acme/http.clj`.

In `ns`, line-toggle the auth-only requires:
```clojure
            ;; @c3kit/feature :auth = [acme.session :as session]
            ;; @c3kit/feature :auth = [c3kit.wire.jwt :as jwt]
            ;; @c3kit/feature :auth = [c3kit.wire.jwt :refer [wrap-jwt]]
            ;; @c3kit/feature :auth = [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
```

In the `defonce root-handler` thread, wrap the three auth middlewares:
```clojure
      ;; @c3kit/feature :auth {
      session/wrap-session
      (wrap-anti-forgery {:strategy (jwt/create-strategy)})
      (wrap-jwt {:cookie-name "acme-token" :secret (:jwt-secret config/env) :lifespan (when config/development? (time/hours 336))})
      ;; @c3kit/feature :auth }
```

**Block count check**: at this point count the markers in `http.clj`. If you've added >4 blocks here, the file is the auth-hotspot the spec warned about (§12 risk row 1). Consider extracting an `acme.auth.middleware` ns containing the three wraps + their wiring, leaving a whole-file delete via `:delete-when-off`. If extraction is non-trivial, defer to a follow-up commit and leave a TODO with the rationale.

- [ ] **Step 7: Mark `:auth` in `acme.schema` (CLJC)**

Edit `templates/full-stack-reagent/src/cljc/acme/schema.cljc`:

```clojure
(ns acme.schema
  (:require [acme.bg-task :as bg-task]
            ;; @c3kit/feature :auth = [acme.user.schema :as user.schema]
            ))

(def full
  (concat
    [bg-task/bg-task]
    ;; @c3kit/feature :auth {
    user.schema/all
    ;; @c3kit/feature :auth }
    ))
```

Note: with `:auth` off and the line stripped, `user.schema/all` is gone from the `concat`. With `:auth` on, the marker comments are stripped and `user.schema/all` remains.

- [ ] **Step 8: Mark `:auth` in `acme.main` (CLJS)**

Edit `templates/full-stack-reagent/src/cljs/acme/main.cljs`.

In `ns`:
```clojure
            ;; @c3kit/feature :auth = [acme.forgot-password]
            ;; @c3kit/feature :auth = [acme.recover-password]
            ;; @c3kit/feature :auth = [acme.user :as user]
```

In `main` fn, wrap:
```clojure
    ;; @c3kit/feature :auth {
    (user/install-and-connect! user)
    ;; @c3kit/feature :auth }
```

- [ ] **Step 9: Mark `:auth` in `acme.routes` (CLJS)**

Edit `templates/full-stack-reagent/src/cljs/acme/routes.cljs`. Open the file to find page-registration calls for `forgot-password`, `recover-password`, and any `user` page; wrap each with `;; @c3kit/feature :auth {` … `;; @c3kit/feature :auth }`.

- [ ] **Step 10: Mark `:auth` in `dev/acme/seed.clj`**

Edit `templates/full-stack-reagent/dev/acme/seed.clj`. Wrap the user fixtures + the `(deref)` calls in `-main`:

```clojure
;; @c3kit/feature :auth {
(def pw-salt "$2a$11$3yH8I8pZi6xSPbK4QmcPYe")
(defn hashpw [pw] (BCrypt/hashpw pw pw-salt))

(def road-runner (entity :user {:email "road-runner@example.com"} {:name "Road Runner" :password (hashpw "meep-meep")}))
(def wiley-coyote (entity :user {:email "coyote@example.com"} {:name "Wiley Coyote" :password (hashpw "light-bulb")}))
;; @c3kit/feature :auth }

(defn -main []
  (init!)
  (println "Seeding data...")
  ;; @c3kit/feature :auth {
  @road-runner
  @wiley-coyote
  ;; @c3kit/feature :auth }
  (System/exit 0))
```

(Note: emails changed from `@acme.com` to `@example.com` to play nicer with the rename — `acme.com` would rewrite to e.g. `my-app.com` which is fine but `example.com` is RFC-safe.)

- [ ] **Step 11: Re-run Layer A**

```bash
cd templates/full-stack-reagent
clj -M:test:spec
clj -M:test:cljs once
```
Expected: both green.

If `:auth` markers broke loading at HEAD because line-toggled deps are required: per Task 5's revision pattern, leave the deps un-marked. Surface the change.

- [ ] **Step 12: Audit marker block count**

```bash
grep -rcE '@c3kit/feature' templates/full-stack-reagent/src templates/full-stack-reagent/dev templates/full-stack-reagent/deps.edn | grep -v ':0$'
```

Report counts per file. Total for `:auth` should be ≤14 blocks (spec §13). If over: extract `acme.auth.middleware` ns (or similar), follow up in a separate commit.

- [ ] **Step 13: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add -A templates/full-stack-reagent/
git commit -m "feat(t1): add :auth feature markers

- deps.edn: line-toggles for jbcrypt, ring-anti-forgery,
  google-api-client, and :seed alias
- acme.main (clj): gate require + destination/configure! call
- acme.config: gate :jwt-secret entries in dev/staging/production
- acme.routes: gate user API/ajax/web routes + ws-handlers
- acme.http: gate JWT/session/anti-forgery middleware + requires
- acme.schema (cljc): gate user.schema/all term in (def full …)
- acme.main (cljs): gate auth-page requires + user/install-and-connect!
- acme.routes (cljs): gate forgot/recover/user page registrations
- dev/acme/seed.clj: gate user fixtures + their deref calls in -main;
  switch seed emails to @example.com

Layer A (clj -M:test:spec + clj -M:test:cljs once) stays green."
```

---

## Task 9: Add DB Selection Markers

**Why:** Spec §6.3 — distinct var names per backend so all four blocks coexist at HEAD. Memory-backend is the HEAD default `:bucket` so the template runs cleanly without scaffolding.

**Files:**
- Modify: `templates/full-stack-reagent/deps.edn`
- Modify: `templates/full-stack-reagent/src/clj/acme/config.clj`

- [ ] **Step 1: Confirm Layer A green**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green.

- [ ] **Step 2: Add DB line-toggles in `deps.edn`**

Edit `deps.edn`. Replace:

```clojure
com.datomic/peer {:mvn/version "1.0.7482"}
```

With:

```clojure
;; @c3kit/db :datomic-pro = com.datomic/peer {:mvn/version "1.0.7482"}
;; @c3kit/db :sqlite      = org.xerial/sqlite-jdbc {:mvn/version "3.46.0.0"}
;; @c3kit/db :postgres    = org.postgresql/postgresql {:mvn/version "42.7.4"}
```

(Memory backend needs no extra dep — `c3kit/bucket` already includes the memory impl.)

If `clj -M:test:spec` at HEAD requires the datomic peer (it currently does, the proprietary `:bucket` is datomic-local), removing the bare line breaks Layer A. Mitigation: leave `com.datomic/peer` unmarked in `deps.edn` at HEAD; the CLI marker stripper sees no marker for it and leaves it alone. For the JDBC drivers, mark them with `;; @c3kit/db :<id> =`.

Revised:
```clojure
com.datomic/peer {:mvn/version "1.0.7482"}  ; baseline; harmless if user picks another :db
;; @c3kit/db :sqlite   = org.xerial/sqlite-jdbc {:mvn/version "3.46.0.0"}
;; @c3kit/db :postgres = org.postgresql/postgresql {:mvn/version "42.7.4"}
```

This leaves the datomic dep present unconditionally. Acceptable cost (it's a single dep; users on memory/sqlite/postgres ignore it; the dep is now free on Maven Central).

- [ ] **Step 3: Rewrite `acme.config` with distinct var names per backend**

Edit `templates/full-stack-reagent/src/clj/acme/config.clj` to match spec §6.3 verbatim. The current proprietary file uses `datomic-base` + `datomic-local`/`datomic-staging`/`datomic-production`. Add sibling clusters for sqlite, postgres, memory with distinct names. Then change each env def to default `:bucket` to the memory variant + add the four `@c3kit/db = :bucket …` line-toggles.

Concrete result (replacing the existing `acme.config` body — keep the `ns` form and `(def env …)` / `(def host …)` / `(def bucket …)` / `(defn link …)` at the bottom):

```clojure
(def ^:private base
  {:analytics-code "console.log('google analytics would have loaded for this page');"
   :log-level      :info
   ;; @c3kit/feature :csp {
   :csp            {:enabled?       false
                    :enforce?       false
                    :report-handler nil
                    :policy         nil}
   ;; @c3kit/feature :csp }
   })

;; @c3kit/db :datomic-pro {
(def datomic-base
  {:impl                :datomic
   :migration-dir       "acme.migrations"
   :migration-ns-prefix "m"
   :migration-ns        'acme.migrations
   :full-schema         'acme.schema/full})

(def datomic-local      (merge datomic-base {:uri "datomic:dev://localhost:4334/acme"}))
(def datomic-staging    (merge datomic-base {:uri "datomic:dev://localhost:4334/acme-staging"}))
(def datomic-production (merge datomic-base {:uri "datomic:dev://localhost:4334/acme-production"}))
;; @c3kit/db :datomic-pro }

;; @c3kit/db :sqlite {
(def sqlite-base
  {:impl                :jdbc
   :dialect             :sqlite
   :migration-dir       "acme.migrations"
   :migration-ns-prefix "m"
   :migration-ns        'acme.migrations
   :full-schema         'acme.schema/full})

(def sqlite-local      (merge sqlite-base {:connection-uri "jdbc:sqlite:db/dev.sqlite"}))
(def sqlite-staging    (merge sqlite-base {:connection-uri "jdbc:sqlite:db/staging.sqlite"}))
(def sqlite-production (merge sqlite-base {:connection-uri "jdbc:sqlite:db/production.sqlite"}))
;; @c3kit/db :sqlite }

;; @c3kit/db :postgres {
(def postgres-base
  {:impl                :jdbc
   :dialect             :postgres
   :migration-dir       "acme.migrations"
   :migration-ns-prefix "m"
   :migration-ns        'acme.migrations
   :full-schema         'acme.schema/full})

(def postgres-local      (merge postgres-base {:connection-uri "jdbc:postgresql://localhost/acme_dev"}))
(def postgres-staging    (merge postgres-base {:connection-uri "jdbc:postgresql://localhost/acme_staging"}))
(def postgres-production (merge postgres-base {:connection-uri "jdbc:postgresql://localhost/acme_production"}))
;; @c3kit/db :postgres }

;; memory backend defined unconditionally — HEAD default and a valid wizard choice
(def memory-local      {:impl :memory :full-schema 'acme.schema/full})
(def memory-staging    {:impl :memory :full-schema 'acme.schema/full})
(def memory-production {:impl :memory :full-schema 'acme.schema/full})

(def email-to-log {:client :to-log})
(def admin-email "Acme <admin@acme.com>")

(def development
  (assoc base
    :email email-to-log
    :bucket memory-local                           ;; HEAD default; replaced by line below at scaffold
    ;; @c3kit/db :datomic-pro = :bucket datomic-local
    ;; @c3kit/db :sqlite      = :bucket sqlite-local
    ;; @c3kit/db :postgres    = :bucket postgres-local
    ;; @c3kit/db :memory      = :bucket memory-local
    :host "http://localhost:8123"
    :log-level :trace
    ;; @c3kit/feature :auth = :jwt-secret "ACME_DEV_SECRET"
    ))

(def staging
  (assoc base
    :email email-to-log
    :bucket memory-staging
    ;; @c3kit/db :datomic-pro = :bucket datomic-staging
    ;; @c3kit/db :sqlite      = :bucket sqlite-staging
    ;; @c3kit/db :postgres    = :bucket postgres-staging
    ;; @c3kit/db :memory      = :bucket memory-staging
    :host "https://acme-staging.example.com"
    :log-level :trace
    ;; @c3kit/feature :auth = :jwt-secret "ACME_STAGING_SECRET"
    ))

(def production
  (assoc base
    :email email-to-log
    :bucket memory-production
    ;; @c3kit/db :datomic-pro = :bucket datomic-production
    ;; @c3kit/db :sqlite      = :bucket sqlite-production
    ;; @c3kit/db :postgres    = :bucket postgres-production
    :host "https://acme.example.com"
    :analytics-code "console.log('Replace me with Real Google Analytics Code.');"
    ;; @c3kit/feature :auth = :jwt-secret "ACME_PRODUCTION_SECRET"
    ))

(def environment (app/find-env "cc.env" "CC_ENV"))
(def development? (= "development" environment))
(def production? (= "production" environment))

(def env
  (case environment
    "staging" staging
    "production" production
    development))

(def host (:host env))
(def bucket (:bucket env))

(defn link [& parts] (apply str host parts))
```

Note: `staging` and `production` hosts changed from `cleancoders.com` to `example.com` to keep the OSS template free of Clean Coders branding in domain examples.

- [ ] **Step 4: Re-run Layer A**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green. The memory backend boots successfully at HEAD because every env now defaults to `memory-{local,staging,production}`.

If any spec currently asserts on `datomic:dev://...` URIs or similar, update the spec to the new memory default or guard it.

- [ ] **Step 5: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add -A templates/full-stack-reagent/deps.edn templates/full-stack-reagent/src/clj/acme/config.clj
git commit -m "feat(t1): add @c3kit/db markers + memory-default at HEAD

- deps.edn: line-toggle for sqlite-jdbc and postgresql drivers;
  datomic peer stays unconditional (free; harmless for non-datomic users)
- acme.config: distinct var names per backend (datomic-local,
  sqlite-local, postgres-local, memory-local). HEAD default for
  :bucket is memory-{local,staging,production}; @c3kit/db = lines
  swap it at scaffold time. Hook (Task 10) deletes the HEAD-default
  line in the scaffolded output.
- Update staging/production host examples from cleancoders.com to
  example.com.

Layer A passes against the un-scaffolded template tree running on
the memory backend at HEAD."
```

---

## Task 10: Write `c3kit-template.bb` Post-Scaffold Hook

**Why:** Spec §7 — generates `bin/db` based on selected DB, reconciles the HEAD-default `:bucket` line in `config.clj` so the scaffolded `(assoc … :bucket … :bucket …)` doesn't have two `:bucket` keys, drops `:seed` alias when `:auth` off (defense-in-depth), residue grep.

**Files:**
- Create: `templates/full-stack-reagent/c3kit-template.bb`
- Create: `templates/full-stack-reagent/bin/db.template.datomic-pro`
- Create: `templates/full-stack-reagent/bin/db.template.sqlite`
- Create: `templates/full-stack-reagent/bin/db.template.postgres`
- Create: `templates/full-stack-reagent/bin/db.template.memory`

- [ ] **Step 1: Write per-backend `bin/db` templates**

Create `templates/full-stack-reagent/bin/db.template.datomic-pro`:

```bash
#!/usr/bin/env bash
set -euo pipefail

DATOMIC_VERSION="1.0.7482"
DATOMIC_HOME="${HOME}/.c3kit/datomic-pro/datomic-pro-${DATOMIC_VERSION}"
ZIP_URL="https://datomic-pro-downloads.s3.amazonaws.com/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.zip"

if [[ ! -d "$DATOMIC_HOME" ]]; then
  echo "Datomic Pro transactor not found at $DATOMIC_HOME."
  read -rp "Download Datomic Pro $DATOMIC_VERSION (~300MB)? [y/N] " confirm
  if [[ ! "$confirm" =~ ^[yY]$ ]]; then
    echo "Aborted. To install manually, download from https://www.datomic.com/get-datomic.html"
    exit 1
  fi
  mkdir -p "${HOME}/.c3kit/datomic-pro"
  curl -fsSL "$ZIP_URL" -o "${HOME}/.c3kit/datomic-pro/datomic-pro.zip"
  unzip -q "${HOME}/.c3kit/datomic-pro/datomic-pro.zip" -d "${HOME}/.c3kit/datomic-pro"
  rm "${HOME}/.c3kit/datomic-pro/datomic-pro.zip"
fi

cd "$DATOMIC_HOME"
exec bin/transactor config/samples/dev-transactor-template.properties
```

Create `templates/full-stack-reagent/bin/db.template.sqlite`:

```bash
#!/usr/bin/env bash
set -euo pipefail
mkdir -p db
touch db/dev.sqlite
echo "SQLite ready at db/dev.sqlite"
```

Create `templates/full-stack-reagent/bin/db.template.postgres`:

```bash
#!/usr/bin/env bash
set -euo pipefail
DB_NAME="acme_dev"
if ! command -v createdb >/dev/null 2>&1; then
  echo "createdb not found. Install Postgres first:"
  echo "  macOS:  brew install postgresql && brew services start postgresql"
  echo "  Debian: sudo apt install postgresql && sudo systemctl start postgresql"
  exit 1
fi
createdb "$DB_NAME" 2>/dev/null || echo "Database $DB_NAME already exists (ok)"
echo "Postgres database '$DB_NAME' ready."
```

Create `templates/full-stack-reagent/bin/db.template.memory`:

```bash
#!/usr/bin/env bash
echo "In-memory backend selected; no DB process needed."
echo "The bucket starts with the application."
exit 0
```

- [ ] **Step 2: Write the hook script**

Create `templates/full-stack-reagent/c3kit-template.bb`:

```clojure
#!/usr/bin/env bb
;; Post-scaffold hook for templates/full-stack-reagent
;; CLI sub-spec §7.2 stage 3 invokes this script with the scaffold dir as $1.
;; Reads .c3kit-create-context.edn (CLI sub-spec §10 prerequisite #2), then:
;;   1. Generates bin/db from the selected :db
;;   2. Reconciles the HEAD-default :bucket line in config.clj
;;   3. Drops :seed alias from deps.edn when :auth is off (defense-in-depth)
;;   4. Greps for residual @c3kit markers — exits non-zero if any remain

(ns c3kit-template
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def scaffold-dir (or (first *command-line-args*)
                      (do (println "ERROR: scaffold dir required as arg 1")
                          (System/exit 1))))

(def context-file (str scaffold-dir "/.c3kit-create-context.edn"))

(when-not (fs/exists? context-file)
  (println "ERROR: context file not found at" context-file)
  (println "This hook expects the CLI to write .c3kit-create-context.edn")
  (println "before invocation (T1 spec §10 prereq #2).")
  (System/exit 2))

(def ctx (edn/read-string (slurp context-file)))
(def selected-db (:db ctx))
(def auth-on? (get-in ctx [:features :auth]))
(def name-underscore (get-in ctx [:name-variants :underscore]))

;; --- 1. Generate bin/db ---

(defn install-bin-db []
  (let [src (str scaffold-dir "/bin/db.template." (name selected-db))
        dst (str scaffold-dir "/bin/db")]
    (when-not (fs/exists? src)
      (println "ERROR: db template not found for" selected-db ":" src)
      (System/exit 3))
    (let [content (-> (slurp src)
                      (str/replace "acme_dev" (str name-underscore "_dev")))]
      (spit dst content)
      (fs/set-posix-file-permissions dst "rwxr-xr-x"))
    ;; remove all bin/db.template.* siblings
    (doseq [f (fs/list-dir (str scaffold-dir "/bin"))
            :let [n (fs/file-name f)]
            :when (str/starts-with? n "db.template.")]
      (fs/delete f))))

;; --- 2. Reconcile config.clj :bucket lines ---

(defn reconcile-bucket-lines []
  (let [config-path (str scaffold-dir "/src/clj/" name-underscore "/config.clj")
        content     (slurp config-path)
        ;; Remove the HEAD-default line. The line is tagged exactly:
        ;; "    :bucket memory-local                           ;; HEAD default; replaced by line below at scaffold"
        ;; (and the staging/production analogues)
        cleaned     (str/replace content
                                 #"\s*:bucket\s+memory-(local|staging|production)\s+;;\s*HEAD default; replaced by line below at scaffold\n"
                                 "\n")]
    (spit config-path cleaned)))

;; --- 3. Drop :seed alias from deps.edn when :auth off ---

(defn maybe-drop-seed-alias []
  (when-not auth-on?
    (let [deps-path (str scaffold-dir "/deps.edn")
          content   (slurp deps-path)
          ;; The :seed alias line is gone after marker stripping anyway, but
          ;; if any user-facing reference to it remains, fix.
          ;; (Defense-in-depth — no-op in normal flow.)
          cleaned   (str/replace content
                                 #"\n\s*:seed\s+\{[^}]+\}"
                                 "")]
      (spit deps-path cleaned))))

;; --- 4. Residue grep ---

(defn grep-residue []
  (let [residues (atom [])]
    (doseq [f (file-seq (java.io.File. scaffold-dir))
            :when (and (.isFile f)
                       (not (str/includes? (.getPath f) "/.git/"))
                       (let [n (.getName f)]
                         (or (str/ends-with? n ".clj")
                             (str/ends-with? n ".cljc")
                             (str/ends-with? n ".cljs")
                             (str/ends-with? n ".edn")
                             (str/ends-with? n ".md")
                             (str/ends-with? n ".html")
                             (str/ends-with? n ".js")
                             (str/ends-with? n ".yml"))))]
      (let [content (slurp f)
            ;; Allow occurrences inside fenced code blocks of READMEs (escaped form).
            ;; A literal "@c3kit/feature" or "@c3kit/db" anywhere else is residue.
            patt    #"@c3kit/(feature|db)\s+!?:?\S*\s*[{}=]"]
        (when (re-find patt content)
          (swap! residues conj (.getPath f)))))
    (when (seq @residues)
      (println "ERROR: residual markers found in:")
      (doseq [p @residues] (println "  " p))
      (System/exit 4))))

(install-bin-db)
(reconcile-bucket-lines)
(maybe-drop-seed-alias)
(grep-residue)

(println "DB script generated for :" (name selected-db))
```

- [ ] **Step 3: Test the hook in isolation**

A unit-style test for the hook: simulate a scaffold dir + context file.

Create `templates/full-stack-reagent/spec/hook_test.bb`:

```clojure
#!/usr/bin/env bb
(require '[babashka.fs :as fs])
(require '[clojure.string :as str])

(def tmp (str (fs/create-temp-dir {:prefix "hook-test-"})))

;; Fixture: minimal scaffold dir
(fs/create-dirs (str tmp "/bin"))
(fs/create-dirs (str tmp "/src/clj/my_app"))
(spit (str tmp "/bin/db.template.sqlite") "#!/usr/bin/env bash\necho 'sqlite ready for acme_dev'\n")
(spit (str tmp "/bin/db.template.memory") "#!/usr/bin/env bash\necho 'memory'\n")
(spit (str tmp "/src/clj/my_app/config.clj")
      "(def development\n  (assoc base\n    :bucket memory-local                           ;; HEAD default; replaced by line below at scaffold\n    :bucket sqlite-local\n    :host \"http://localhost:8123\"))\n")
(spit (str tmp "/deps.edn") "{:paths [\"src\"]}\n")
(spit (str tmp "/.c3kit-create-context.edn")
      "{:name \"my-app\"\n :name-variants {:hyphen \"my-app\" :underscore \"my_app\" :pascal \"MyApp\"}\n :db :sqlite\n :features {:content true :ssr true :csp false :markdownc true :auth true}\n :secrets []\n :template :full-stack-reagent\n :template-version \"0.1.0\"\n :cli-version \"0.1.0\"}\n")

;; Run hook
(let [hook (str (System/getProperty "user.dir") "/c3kit-template.bb")]
  (let [{:keys [exit out err]} (babashka.process/sh "bb" hook tmp)]
    (println "exit:" exit)
    (println "stdout:" out)
    (println "stderr:" err)
    (when-not (zero? exit)
      (System/exit exit))))

;; Assertions
(let [bin-db (str tmp "/bin/db")]
  (assert (fs/exists? bin-db) "bin/db should exist")
  (assert (str/includes? (slurp bin-db) "my_app_dev") "bin/db should be token-renamed")
  (assert (not (fs/exists? (str tmp "/bin/db.template.sqlite"))) "templates should be removed")
  (assert (not (fs/exists? (str tmp "/bin/db.template.memory"))) "templates should be removed"))

(let [config (slurp (str tmp "/src/clj/my_app/config.clj"))]
  (assert (not (str/includes? config "memory-local")) "HEAD-default line should be removed")
  (assert (str/includes? config "sqlite-local") "scaffolded :bucket should remain"))

(fs/delete-tree tmp)
(println "PASS")
```

Run it:
```bash
cd templates/full-stack-reagent
bb spec/hook_test.bb
```
Expected: `PASS` printed; exit 0.

If it fails: read the assertion that fired, fix the hook, re-run.

- [ ] **Step 4: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add templates/full-stack-reagent/bin/db.template.*
git add templates/full-stack-reagent/c3kit-template.bb
git add templates/full-stack-reagent/spec/hook_test.bb
git commit -m "feat(t1): post-scaffold hook c3kit-template.bb + bin/db templates

- Per-backend bin/db.template.* fragments (datomic-pro/sqlite/postgres/memory)
- Hook reads .c3kit-create-context.edn (CLI prereq #2), then:
    1. Installs bin/db from the selected backend template (token-renamed)
    2. Removes the HEAD-default :bucket memory-* lines in config.clj
    3. Drops :seed alias from deps.edn when :auth off (defense-in-depth)
    4. Greps for residual @c3kit markers; exits non-zero if any remain
- spec/hook_test.bb verifies the hook in isolation with a fixture scaffold dir"
```

---

## Task 11: Write Scaffolded-Project README + Template-Dev README + LICENSE

**Why:** Spec §9 — two READMEs (one for template contributors, one for end users) plus MIT LICENSE.

**Files:**
- Create: `templates/full-stack-reagent/README.scaffold.md`
- Create or modify: `templates/full-stack-reagent/README.md` (was proprietary; now template-dev README)
- Create: `templates/full-stack-reagent/LICENSE`

- [ ] **Step 1: Write the scaffolded-project README**

Create `templates/full-stack-reagent/README.scaffold.md`:

```markdown
# Acme

Full-stack Clojure + ClojureScript project scaffolded from the
[`c3kit-jig`](https://github.com/cleancoders/c3kit-jig)
`full-stack-reagent` template.

## System Requirements

- Java 17+
- Clojure CLI (`brew install clojure`)
- (Optional) Node + npm if you opted into SSR

## Database

The template generated a `bin/db` script for your chosen backend.

```sh
bin/db                 # start (or initialize) the database
clj -M:test:migrate    # run migrations
clj -M:test:seed       # seed dev data (auth only)
```

If you chose `:datomic-pro`, the first run of `bin/db` will prompt you
to download the Datomic Pro transactor (~300 MB) to `~/.c3kit/datomic-pro/`.
Datomic Pro is free as of 2023; no `my.datomic.com` credentials needed.

## Compile Assets

```sh
clj -M:test:css once    # CSS once
clj -M:test:css         # CSS auto-watch
clj -M:test:cljs once   # CLJS once
clj -M:test:cljs        # CLJS auto-watch
clj -M:test:cljss       # CSS + CLJS auto-watch (combined)
```

Production:
```sh
CC_ENV=production clj -M:test:css once
CC_ENV=production clj -M:test:cljs once
```

## Run

```sh
clj -M:test:run    # server only
clj -M:test:dev    # server + specs + cljs in one process
clj -M:repl        # REPL
```

## Test

```sh
clj -M:test:spec        # Clojure specs
clj -M:test:spec -a     # Clojure specs (auto-rerun)
clj -M:test:cljs once   # ClojureScript specs
clj -M:test:cljs auto   # ClojureScript specs (auto-rerun)
```

## Production Email

The template ships with `acme.email/client-send-email :to-log` only
(prints emails to the log). To send real email via AWS SES, add to
`deps.edn`:

```clojure
com.amazonaws/aws-java-sdk-ses {:mvn/version "1.12.797"}
```

Then add a `:ses` defmethod to `acme.email`:

```clojure
(defmethod client-send-email :ses [_ email]
  ;; … your AWS SES client wiring …
  )
```

And in `acme.config`, change the production `:email` value to `{:client :ses}`.

## Deployment

This template doesn't prescribe a deployment target. Common options:

- **Standalone JVM** — `clj -X:uberjar` (or your build of choice) and run on
  any host with Java 17+.
- **AWS** — re-add the AWS SDK deps (see "Production Email"), build an uberjar,
  deploy to EC2 or ECS.
- **Container** — write a `Dockerfile` based on `eclipse-temurin:17-jre`.

See the c3kit-jig wiki for deployment recipes contributed by the community.

## Adding a New `:kind`

1. Add the schema to `acme.schema/full`.
2. Add an implementation of `acme.test-data/-init-kind!` with the `:kind`.
3. Add records in `acme.test-data/deps`.

## Optional Features

The wizard offered five toggleable features at scaffold time. To re-enable
a disabled feature, look at the proprietary template at the original
repo for the deleted code; the marker comments document what each
feature touched.

Features:

- **Content pipeline (`:content`)** — drop markdown content under
  `content/<type>/<permalink>/`; routes auto-register.
- **SSR/prerender (`:ssr`)** — `(defmethod acme.page/prerender? :my-page [_] true)`
  opts a page in; Node + `resources/prerender/prerender.js` produce
  HTML + markdown caches.
- **CSP (`:csp`)** — Content Security Policy middleware; toggle per env
  in `acme.config`.
- **Client-side markdown (`:markdownc`)** — `acme.markdownc` parses markdown
  to hiccup in the browser; useful for AI-agent payloads.
- **JWT auth (`:auth`)** — signin/signup/forgot/recover flows + JWT cookie
  middleware + user kind + social-login kind.
```

- [ ] **Step 2: Write the template-dev README**

Overwrite `templates/full-stack-reagent/README.md`:

```markdown
# Template: full-stack-reagent

Source for the `c3kit-create` `full-stack-reagent` template. End users
don't read this file — it's for contributors who maintain the template.

The README that ships with scaffolded projects is `README.scaffold.md`
(the CLI renames it to `README.md` after copying).

## What this template produces

A Clojure + ClojureScript app scaffold with:

- Clojure backend (Compojure, c3kit/wire HTTP)
- Reagent frontend (CLJS + secretary/accountant routing)
- c3kit/{apron, bucket, wire, scaffold} ecosystem
- Optional features (defaults in parens):
  - `:content` (on) — markdown content pipeline + auto-routes
  - `:ssr` (on) — Reagent SSR via Node prerender
  - `:csp` (off) — Content Security Policy middleware
  - `:markdownc` (on) — client-side markdown rendering (CLJC)
  - `:auth` (on) — JWT auth + user kind + signin/signup/forgot/recover
- Database backends:
  - `:datomic-pro` (default) — Datomic Pro free tier, single-jar transactor
  - `:sqlite` — JDBC SQLite (single-file)
  - `:postgres` — JDBC Postgres
  - `:memory` — c3kit/bucket in-memory (dev only)

## Scaffold the template locally

From the c3kit-jig repo root with a built CLI uberscript:

```sh
./cli/dist/c3kit-create.bb test-app \
  --template-dir templates \
  --template full-stack-reagent \
  --db sqlite \
  --feature csp=true \
  --yes
```

## Run the template tree at HEAD

The template is a runnable Clojure project at HEAD (literal `acme` tokens
preserved; marker comments are invisible to the Clojure reader). The
in-memory backend is the HEAD default `:bucket`.

```sh
cd templates/full-stack-reagent
clj -M:test:spec       # all specs pass on the memory backend
clj -M:test:cljs once  # cljs compile + tests
```

## Marker syntax cheat-sheet

Markers gate optional features and DB selection. The CLI strips them at
scaffold time per the rules in
[`docs/specs/2026-05-12-c3kit-create-cli-design.md`](../../docs/specs/2026-05-12-c3kit-create-cli-design.md) §6.1.

```clojure
;; @c3kit/feature :csp {           ;; block on/off
…
;; @c3kit/feature :csp }

;; @c3kit/feature :csp = (require …)   ;; line-toggle (kept verbatim when on)

;; @c3kit/feature !:auth {         ;; inverse — included when off
…
;; @c3kit/feature !:auth }

;; @c3kit/db :sqlite {             ;; same shape for db selection
…
;; @c3kit/db :sqlite }
```

## Post-scaffold hook

`c3kit-template.bb` runs after marker stripping and rename. It:

1. Reads `.c3kit-create-context.edn` (CLI sub-spec §7.2).
2. Generates `bin/db` from the selected `:db`.
3. Reconciles the HEAD-default `:bucket` line in `config.clj`.
4. Drops the `:seed` alias when `:auth` is off (defense-in-depth).
5. Greps for residual `@c3kit/*` markers; exits non-zero if any remain.

Unit test: `bb spec/hook_test.bb`.

## CI coverage

`.github/workflows/template-full-stack-reagent.yml` in the c3kit-jig
repo scaffolds this template across a `{db × feature-combo}` matrix and
runs `clj -M:test:spec` + `clj -M:test:cljs once` against each output.
```

- [ ] **Step 3: Write LICENSE**

Create `templates/full-stack-reagent/LICENSE` with the standard MIT text:

```
MIT License

Copyright (c) 2026 Clean Coders

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 4: Run Layer A**

```bash
cd templates/full-stack-reagent && clj -M:test:spec
```
Expected: green.

- [ ] **Step 5: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add templates/full-stack-reagent/README.md templates/full-stack-reagent/README.scaffold.md templates/full-stack-reagent/LICENSE
git commit -m "docs(t1): scaffold + template-dev READMEs and MIT LICENSE

- README.scaffold.md: OSS-flavored README the CLI installs in user projects
- README.md: template-dev guide for T1 contributors
- LICENSE: MIT"
```

---

## Task 12: Wait for CLI Prerequisites

**Why:** Layer B (scaffold-and-assert) requires the four CLI prerequisites from spec §10. This task is a sync point.

- [ ] **Step 1: Confirm CLI prerequisites have landed**

Check the CLI branch (or main, depending on where the CLI agent merges):

```bash
git fetch origin
git log origin/cli/v0.1 --oneline | head -20
# or:
git log origin/main --oneline | grep -i 'feat(cli)'
```

Look for commits implementing:
1. Per-feature CLI flag (`--feature x=y`) — check `cli/src/c3kit_create/args.clj`
2. Scaffold context file (`.c3kit-create-context.edn`) — check `cli/src/c3kit_create/hook.clj`
3. README.scaffold.md rename — check `cli/src/c3kit_create/main.clj` or wherever the move happens
4. `--db <id>` flag — check `cli/src/c3kit_create/args.clj`

- [ ] **Step 2: If any are missing, ping the user**

If prerequisites are incomplete:

> "CLI prerequisites for T1 verification: [N missing]. T1 marker work (Tasks 1–11) is committed and Layer A passes. Layer B (scaffold-and-assert across `{db × feature-combo}` matrix) is blocked on the CLI additions in `docs/handoffs/2026-05-12-cli-prerequisites-for-t1.md`. Resume Task 13 when those land."

Stop the plan here; Tasks 13+ will resume when the CLI agent finishes.

- [ ] **Step 3: If all are present, build the CLI uberscript and confirm it can scaffold**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig/cli
bb uberscript dist/c3kit-create.bb -m c3kit-create.main
test -f dist/c3kit-create.bb && echo "uberscript ok"
./dist/c3kit-create.bb --version
./dist/c3kit-create.bb --list
```

Expected: uberscript built, `--version` prints, `--list` shows `full-stack-reagent`.

Quick scaffold smoke:
```bash
cd /tmp
/Users/alex-root-roatch/current-projects/c3kit-jig/cli/dist/c3kit-create.bb \
  smoke-test \
  --template-dir /Users/alex-root-roatch/current-projects/c3kit-jig/templates \
  --template full-stack-reagent \
  --db memory --yes
ls smoke-test/
rm -rf smoke-test
```

Expected: a `smoke-test/` dir is produced with renamed files (no `acme` references), `bin/db`, and no `c3kit-template.{edn,bb}`.

- [ ] **Step 4: No commit required** (this task is a sync point only).

---

## Task 13: Write `dev/verify-scaffold.bb` Harness

**Why:** Layer B verifier. Takes a combo name, scaffolds via the CLI, runs assertions from `spec/combos/<combo>.expected.edn`, returns exit 0 on green.

**Files:**
- Create: `templates/full-stack-reagent/dev/verify-scaffold.bb`
- Create: `templates/full-stack-reagent/spec/combos/memory-defaults.expected.edn`

- [ ] **Step 1: Write the first combo's expected file (smallest combo: `memory + defaults`)**

Create `templates/full-stack-reagent/spec/combos/memory-defaults.expected.edn`:

```clojure
{:db       :memory
 :features {:content true :ssr true :csp false :markdownc true :auth true}
 :name     "my-app"

 :must-exist     ["src/clj/my_app/main.clj"
                  "src/clj/my_app/config.clj"
                  "src/clj/my_app/content.clj"
                  "src/clj/my_app/prerender.clj"
                  "src/clj/my_app/user.clj"
                  "src/clj/my_app/user/web.clj"
                  "src/cljc/my_app/schema.cljc"
                  "src/cljc/my_app/markdownc.cljc"
                  "src/cljc/my_app/user/schema.cljc"
                  "src/cljs/my_app/main.cljs"
                  "src/cljs/my_app/user.cljs"
                  "src/cljs/my_app/forgot_password.cljs"
                  "src/cljs/my_app/recover_password.cljs"
                  "src/cljs/my_app/content_page.cljs"
                  "dev/my_app/seed.clj"
                  "content/blog"
                  "resources/prerender/prerender.js"
                  "package.json"
                  "bin/db"
                  "deps.edn"
                  "LICENSE"
                  "README.md"]

 :must-not-exist ["src/clj/my_app/security/csp.clj"
                  "spec/clj/my_app/security/csp_spec.clj"
                  "bin/db.template.memory"
                  "bin/db.template.sqlite"
                  "c3kit-template.edn"
                  "c3kit-template.bb"
                  "README.scaffold.md"
                  ".c3kit-create-context.edn"]

 :file-contains  {"src/clj/my_app/config.clj"  ["memory-local"
                                                ":bucket memory-local"
                                                "MyApp"]
                  "src/clj/my_app/main.clj"   ["my_app.content"
                                               "my_app.prerender"
                                               "my_app.user.web"]
                  "deps.edn"                  ["com.cleancoders.c3kit/apron"
                                               "org.mindrot/jbcrypt"
                                               "markdown-to-hiccup"]
                  "README.md"                 ["# My-App" "bin/db"]
                  "bin/db"                    ["In-memory backend"]}

 :file-not-contains {"src/clj/my_app/config.clj"  ["HEAD default"
                                                   ":csp"
                                                   "sqlite-local"
                                                   "datomic-local"
                                                   "postgres-local"]
                     "deps.edn"                   ["my.datomic.com"
                                                   "aws-java-sdk"
                                                   "@c3kit/"]
                     "README.md"                  ["acme"
                                                   "Acme"
                                                   "ACME_"]}}
```

- [ ] **Step 2: Write the verifier script**

Create `templates/full-stack-reagent/dev/verify-scaffold.bb`:

```clojure
#!/usr/bin/env bb
;; Usage: bb dev/verify-scaffold.bb --combo memory-defaults
;;
;; Reads spec/combos/<combo>.expected.edn, invokes c3kit-create against
;; templates/full-stack-reagent in --template-dir mode, then asserts.

(ns verify-scaffold
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(def opts-spec
  [["-c" "--combo COMBO" "Combo name (matches spec/combos/<combo>.expected.edn)"]
   ["-C" "--cli PATH"   "Path to c3kit-create uberscript"
    :default (str (System/getProperty "user.dir") "/../../cli/dist/c3kit-create.bb")]])

(defn fail [msg]
  (println "FAIL:" msg)
  (System/exit 1))

(defn run [{:keys [combo cli]}]
  (when-not combo (fail "missing --combo"))
  (let [combo-file (str "spec/combos/" combo ".expected.edn")
        _          (when-not (fs/exists? combo-file)
                     (fail (str "combo file not found: " combo-file)))
        expected   (edn/read-string (slurp combo-file))
        tmp        (str (fs/create-temp-dir {:prefix "verify-scaffold-"}))
        target     (str tmp "/" (:name expected))
        feat-flags (for [[k v] (:features expected)]
                     (str "--feature " (name k) "=" (boolean v)))
        cli-args   (concat [cli (:name expected)
                            "--template-dir" "../../templates"
                            "--template" "full-stack-reagent"
                            "--db" (name (:db expected))
                            "--yes" "--no-git"]
                           (str/split (str/join " " feat-flags) #"\s+"))
        _          (println "Scaffolding:" (str/join " " cli-args))
        _          (fs/create-dirs tmp)
        {:keys [exit out err]} (p/sh {:dir tmp} cli-args)
        _          (println out)
        _          (when (seq err) (println "stderr:" err))
        _          (when-not (zero? exit) (fail (str "CLI exit " exit)))]

    ;; must-exist
    (doseq [p (:must-exist expected)]
      (when-not (fs/exists? (str target "/" p))
        (fail (str "must-exist missing: " p))))

    ;; must-not-exist
    (doseq [p (:must-not-exist expected)]
      (when (fs/exists? (str target "/" p))
        (fail (str "must-not-exist present: " p))))

    ;; file-contains
    (doseq [[p strs] (:file-contains expected)
            s strs]
      (let [content (slurp (str target "/" p))]
        (when-not (str/includes? content s)
          (fail (str "file-contains miss: " p " missing " (pr-str s))))))

    ;; file-not-contains
    (doseq [[p strs] (:file-not-contains expected)
            s strs]
      (let [content (slurp (str target "/" p))]
        (when (str/includes? content s)
          (fail (str "file-not-contains hit: " p " contained " (pr-str s))))))

    ;; residue greps (always)
    (doseq [pat ["acme" "Acme" "ACME_" "@c3kit/feature" "@c3kit/db"]]
      (let [grep (p/sh ["grep" "-rE" pat target])
            hit  (:out grep)]
        (when (seq (str/trim hit))
          ;; Filter out grep hits inside README.md scaffolded examples
          ;; that may legitimately mention these patterns in escaped fenced
          ;; code blocks. Those are flagged for the README test only.
          (let [lines (->> (str/split-lines hit)
                           (remove #(str/includes? % "README.md")))]
            (when (seq lines)
              (println "RESIDUE for" pat ":")
              (doseq [l lines] (println "  " l))
              (fail (str "residue: " pat)))))))

    ;; clj -M:test:spec inside scaffold
    (let [{:keys [exit]} (p/sh {:dir target} ["clj" "-M:test:spec"])]
      (when-not (zero? exit) (fail "clj -M:test:spec failed in scaffold")))

    ;; clj -M:test:cljs once inside scaffold (optional — skip for memory combo if CI cold-start is too slow)
    (let [{:keys [exit]} (p/sh {:dir target} ["clj" "-M:test:cljs" "once"])]
      (when-not (zero? exit) (fail "clj -M:test:cljs once failed in scaffold")))

    (fs/delete-tree tmp)
    (println "PASS:" combo)))

(let [{:keys [options errors]} (cli/parse-opts *command-line-args* opts-spec)]
  (when errors (fail (str "args: " errors)))
  (run options))
```

- [ ] **Step 3: Run the verifier against the smallest combo**

```bash
cd templates/full-stack-reagent
bb dev/verify-scaffold.bb --combo memory-defaults
```
Expected: `PASS: memory-defaults`; exit 0.

Likely failure modes:
- CLI path wrong → adjust `--cli`.
- A `:must-exist` path is wrong → fix the combo edn.
- A `:file-contains` substring is wrong → re-read the scaffolded file and fix the assertion.
- `clj -M:test:spec` fails in scaffold → root cause: a marker was wrong; trace which file the spec failure points to, fix the marker, re-run.

Iterate on combo edn + markers until green.

- [ ] **Step 4: Commit**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git add templates/full-stack-reagent/dev/verify-scaffold.bb
git add templates/full-stack-reagent/spec/combos/memory-defaults.expected.edn
git commit -m "test(t1): verify-scaffold.bb + memory-defaults combo expected

Layer B harness: scaffolds the template via the CLI, asserts
file tree + content + residue + spec/cljs green. memory-defaults
combo is green."
```

---

## Task 14: Per-Combo Expected Files

**Why:** Verify each `{db × feature-combo}` matrix entry. Each combo is a copy-edit of `memory-defaults.expected.edn` with the appropriate `:must-not-exist` additions per disabled features.

**Files (one per combo):**
- Create: `templates/full-stack-reagent/spec/combos/sqlite-defaults.expected.edn`
- Create: `templates/full-stack-reagent/spec/combos/postgres-defaults.expected.edn`
- Create: `templates/full-stack-reagent/spec/combos/datomic-pro-defaults.expected.edn`
- Create: `templates/full-stack-reagent/spec/combos/memory-minimal.expected.edn`
- Create: `templates/full-stack-reagent/spec/combos/memory-csp-on.expected.edn`
- Create: `templates/full-stack-reagent/spec/combos/memory-no-auth.expected.edn`
- Create: `templates/full-stack-reagent/spec/combos/memory-no-ssr-no-content.expected.edn`

For each combo: copy `memory-defaults.expected.edn`, change `:db`, change `:features`, update `:must-exist` / `:must-not-exist` / `:file-contains` / `:file-not-contains` to match.

- [ ] **Step 1: Write `sqlite-defaults.expected.edn`**

Diff vs `memory-defaults`:
- `:db :sqlite`
- `:file-contains` for `src/clj/my_app/config.clj` includes `"sqlite-local"`, `":bucket sqlite-local"`.
- `:file-not-contains` for `config.clj` adds `"memory-local"`, `"datomic-local"`, `"postgres-local"`.
- `:file-contains` for `deps.edn` includes `"org.xerial/sqlite-jdbc"`.
- `:file-contains` for `bin/db` includes `"SQLite ready"`.

Run:
```bash
bb dev/verify-scaffold.bb --combo sqlite-defaults
```
Iterate to green; commit when green.

- [ ] **Step 2: Write `postgres-defaults.expected.edn`**

Diff: `:db :postgres`; `:file-contains` swap to postgres; `:file-contains` for `deps.edn` includes `"org.postgresql/postgresql"`; `:file-contains` for `bin/db` includes `"createdb"`.

Note: running this combo locally requires a Postgres server; you may need to start one or skip the `clj -M:test:spec` step. In CI, postgres is a service container.

Run; iterate; commit.

- [ ] **Step 3: Write `datomic-pro-defaults.expected.edn`**

Diff: `:db :datomic-pro`; `:file-contains` for `bin/db` includes `"Datomic Pro"`; `:file-contains` for `config.clj` includes `"datomic-local"`.

Same note as postgres re: running locally — needs the transactor. Run only if you have it installed.

Run; iterate; commit.

- [ ] **Step 4: Write `memory-minimal.expected.edn` (all features off)**

`:features` = `{:content false :ssr false :csp false :markdownc false :auth false}`.

`:must-not-exist` extends to include every disabled feature's `:delete-when-off` paths from the manifest. The combo should produce a bare-bones scaffold:
- no `content/`
- no `resources/prerender/`
- no `package.json`
- no `acme.content`, `acme.markdown`, `acme.prerender`
- no auth files
- no markdownc

Run; iterate; commit. This is the most demanding test for `:must-not-exist`.

- [ ] **Step 5: Write `memory-csp-on.expected.edn`**

`:features` defaults except `:csp true`. Verifies the `:csp` block markers turn on cleanly.

`:file-contains` for `config.clj` includes `":csp"`. `:must-exist` includes `src/clj/my_app/security/csp.clj`.

Run; iterate; commit.

- [ ] **Step 6: Write `memory-no-auth.expected.edn`**

`:features` defaults except `:auth false`. Heaviest "off" test — confirms all 14ish auth markers strip cleanly.

`:must-not-exist` includes all `:auth` `:delete-when-off` paths. `:file-not-contains` for `src/clj/my_app/http.clj` includes `"wrap-jwt"`, `"wrap-anti-forgery"`, `"wrap-session"`. `:file-not-contains` for `src/cljc/my_app/schema.cljc` includes `"user.schema"`.

Run; iterate; commit (likely the highest-friction combo — expect to find marker gaps and fix iteratively).

- [ ] **Step 7: Write `memory-no-ssr-no-content.expected.edn`**

`:features` = defaults except `:ssr false :content false`. Verifies the inverse paths in `acme.main` + `acme.layouts`.

Run; iterate; commit.

---

## Task 15: CI Workflow

**Why:** Spec §8 — `.github/workflows/template-full-stack-reagent.yml` runs the matrix on PRs and on main pushes.

**Files:**
- Create: `.github/workflows/template-full-stack-reagent.yml`

- [ ] **Step 1: Write the workflow**

Create `.github/workflows/template-full-stack-reagent.yml`:

```yaml
name: template-full-stack-reagent

on:
  push:
    branches: [main]
    paths:
      - 'templates/full-stack-reagent/**'
      - 'cli/**'
      - '.github/workflows/template-full-stack-reagent.yml'
  pull_request:
    paths:
      - 'templates/full-stack-reagent/**'
      - 'cli/**'
      - '.github/workflows/template-full-stack-reagent.yml'
  workflow_dispatch:

jobs:
  build-cli:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: DeLaGuardo/setup-clojure@13.0
        with:
          cli: latest
          bb: latest
      - run: cd cli && bb uberscript dist/c3kit-create.bb -m c3kit-create.main
      - uses: actions/upload-artifact@v4
        with:
          name: c3kit-create-uberscript
          path: cli/dist/c3kit-create.bb

  template-at-head:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: DeLaGuardo/setup-clojure@13.0
        with:
          cli: latest
      - run: cd templates/full-stack-reagent && clj -M:test:spec

  scaffold-matrix:
    needs: build-cli
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        combo:
          - memory-defaults
          - sqlite-defaults
          - memory-minimal
          - memory-csp-on
          - memory-no-auth
          - memory-no-ssr-no-content
    steps:
      - uses: actions/checkout@v4
      - uses: DeLaGuardo/setup-clojure@13.0
        with:
          cli: latest
          bb: latest
      - uses: actions/download-artifact@v4
        with:
          name: c3kit-create-uberscript
          path: cli/dist/
      - run: chmod +x cli/dist/c3kit-create.bb
      - run: cd templates/full-stack-reagent && bb dev/verify-scaffold.bb --combo ${{ matrix.combo }} --cli ${{ github.workspace }}/cli/dist/c3kit-create.bb

  postgres-scaffold:
    needs: build-cli
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
          POSTGRES_HOST_AUTH_METHOD: trust
        ports: ['5432:5432']
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4
      - uses: DeLaGuardo/setup-clojure@13.0
        with:
          cli: latest
          bb: latest
      - uses: actions/download-artifact@v4
        with:
          name: c3kit-create-uberscript
          path: cli/dist/
      - run: chmod +x cli/dist/c3kit-create.bb
      - run: cd templates/full-stack-reagent && bb dev/verify-scaffold.bb --combo postgres-defaults --cli ${{ github.workspace }}/cli/dist/c3kit-create.bb

  datomic-scaffold:
    needs: build-cli
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: DeLaGuardo/setup-clojure@13.0
        with:
          cli: latest
          bb: latest
      - uses: actions/cache@v4
        with:
          path: ~/.c3kit/datomic-pro
          key: datomic-pro-1.0.7482
      - uses: actions/download-artifact@v4
        with:
          name: c3kit-create-uberscript
          path: cli/dist/
      - run: chmod +x cli/dist/c3kit-create.bb
      - run: cd templates/full-stack-reagent && bb dev/verify-scaffold.bb --combo datomic-pro-defaults --cli ${{ github.workspace }}/cli/dist/c3kit-create.bb

  macos-smoke:
    needs: build-cli
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: DeLaGuardo/setup-clojure@13.0
        with:
          cli: latest
          bb: latest
      - uses: actions/download-artifact@v4
        with:
          name: c3kit-create-uberscript
          path: cli/dist/
      - run: chmod +x cli/dist/c3kit-create.bb
      - run: cd templates/full-stack-reagent && bb dev/verify-scaffold.bb --combo memory-defaults --cli ${{ github.workspace }}/cli/dist/c3kit-create.bb
```

- [ ] **Step 2: Validate the YAML locally**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/template-full-stack-reagent.yml'))" && echo "yaml ok"
```
Expected: `yaml ok`. (If python3 isn't available, use `bb -e "(require '[clj-yaml.core :as y]) (y/parse-string (slurp \".github/workflows/template-full-stack-reagent.yml\"))"`, or skip — GitHub will catch syntax on push.)

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/template-full-stack-reagent.yml
git commit -m "ci(t1): template-full-stack-reagent matrix workflow

Per spec §8:
- build-cli job builds the CLI uberscript as an artifact
- template-at-head runs clj -M:test:spec inside templates/full-stack-reagent
- scaffold-matrix exercises memory/sqlite + feature combos via verify-scaffold.bb
- postgres-scaffold uses a postgres service container
- datomic-scaffold caches the transactor download
- macos-smoke runs one memory-defaults scaffold on macos-latest"
```

---

## Task 16: PR to Main

**Why:** Surface the finished T1 work to the user for merge.

- [ ] **Step 1: Confirm everything is green**

```bash
cd /Users/alex-root-roatch/current-projects/c3kit-jig
git status
cd templates/full-stack-reagent && clj -M:test:spec && cd ../..
for c in memory-defaults sqlite-defaults memory-minimal memory-csp-on memory-no-auth memory-no-ssr-no-content; do
  (cd templates/full-stack-reagent && bb dev/verify-scaffold.bb --combo $c) || echo "FAIL: $c"
done
```
Expected: all green; no `FAIL:` lines.

- [ ] **Step 2: Push the branch and open the PR**

Ask the user before pushing — pushes are an externally-visible action.

```bash
git push -u origin template/full-stack-reagent
gh pr create --base main --head template/full-stack-reagent --title "feat: T1 — templates/full-stack-reagent" --body "$(cat <<'EOF'
## Summary

- Adapts the proprietary Clean Coders starter into `templates/full-stack-reagent/`
- 5 toggleable features (content, ssr, csp, markdownc, auth) + 4 db backends (datomic-pro, sqlite, postgres, memory)
- Manifest (`c3kit-template.edn`) + post-scaffold hook (`c3kit-template.bb`)
- Layer A: `clj -M:test:spec` green on the template tree at HEAD
- Layer B: matrix of scaffold-and-assert combos green in CI

## Spec & plan

- Spec: `docs/specs/2026-05-12-template-full-stack-reagent-design.md`
- Plan: `docs/plans/2026-05-12-template-full-stack-reagent-plan.md`

## Test plan

- [ ] CI green
- [ ] Reviewer runs `bb dev/verify-scaffold.bb --combo memory-defaults` locally and confirms PASS
- [ ] Reviewer confirms a scaffolded project (any combo) `clj -M:test:run` boots and serves `GET /` 200

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Confirm PR URL printed**

Hand the URL to the user.

---

## Self-Review

**1. Spec coverage:** Walked through each section of `docs/specs/2026-05-12-template-full-stack-reagent-design.md`:

- §1 Goal — Task 1 (copy), Tasks 4–9 (markers), Task 10 (hook), Task 11 (READMEs).
- §2 Scope & non-goals — Task 2 strips proprietary refs; AWS SDKs / `bin/setup` removal explicit.
- §3 Directory layout — Tasks 1, 2, 10, 11 land all listed files/dirs.
- §4 Open-Q resolutions — All baked into the relevant task content (Q1 → Datomic install in Task 10 bin/db; Q2 → 4 db options in Task 3 manifest; Q3 → no docker-compose; Q4–Q5 → schema/seed in Task 8; Q6 → AWS dropped in Task 2; Q7 → auth scope in Task 8; Q8 → marker placements in Tasks 4–8; Q9 → CI in Task 15; Q10 → all three envs preserved in Task 9).
- §5 Manifest — Task 3 lands it verbatim.
- §6 Marker inventory — Tasks 4–9 each implement one feature's markers; Task 9 handles DB markers.
- §7 Post-scaffold hook — Task 10 implements every responsibility (1–7).
- §8 CI workflow — Task 15 lands it; combo coverage matches.
- §9 READMEs — Task 11 writes both + LICENSE.
- §10 Cross-cutting CLI prerequisites — Task 0 memo; Task 12 sync point.
- §11 Marker audit — Task 8 step 12 explicitly counts auth blocks against the ≤14 ceiling.
- §12 Risks — addressed inline: Task 5 documents the dep-marker friction risk; Task 8 step 6 documents the auth-hotspot extraction option.
- §13 Success criteria — Task 16 step 1 walks them.
- §14 Out of scope — not implemented (correctly).

**2. Placeholder scan:** Reviewed for "TBD", "TODO", "implement later", "fill in details", "add appropriate error handling", "similar to Task N". Found two intentional references to the proprietary file (Tasks 6 step 4, Task 8 step 9, Task 8 step 10): "Open the file with `Read` first to locate the exact lines". Acceptable because the proprietary tree at `/Users/alex-root-roatch/current-projects/starter` is the source-of-truth and accessible at execution time; the exact lines depend on the proprietary file's layout. The task gives clear acceptance criteria (wrap the markdown-rendering path / wrap the auth pages / etc.) and the file is short. Each "open the file" instruction is followed by a concrete assertion the change must satisfy.

**3. Type consistency:** Variable names across tasks: `datomic-local`/`sqlite-local`/`postgres-local`/`memory-local` consistent throughout Tasks 9, 10, 11, 13, 14. Hook helper fns (`install-bin-db`, `reconcile-bucket-lines`, `maybe-drop-seed-alias`, `grep-residue`) consistent in Task 10 + matching script. Combo file shape `{:db :features :name :must-exist :must-not-exist :file-contains :file-not-contains}` consistent across Tasks 13 + 14.

**Issues found and fixed inline:**
- Task 5 initially proposed marking the `markdown-to-hiccup` dep — caught the Layer-A breakage and revised to whole-file-deletes-only, with a corresponding spec amendment step.
- Task 9 step 2 caught the same friction for `com.datomic/peer` and revised to leave the dep unconditional in `deps.edn`.

Both revisions are documented in their respective tasks, not hidden — the engineer reading the plan sees the friction and the resolution.
