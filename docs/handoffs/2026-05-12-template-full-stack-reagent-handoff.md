# Handoff: `templates/full-stack-reagent` Sub-Spec

**Date:** 2026-05-12
**Author of handoff:** previous brainstorming session
**Target reader:** the next agent who picks up this work
**Mode:** start from brainstorm; do NOT begin coding until a spec is written, reviewed, and a plan is drafted

---

## What you are picking up

You're delivering **T1 — the full-stack Reagent template** for the open-source `c3kit-starter` project. T1 is one of three Phase 1 deliverables alongside the CLI and the FE-only vanilla CLJS template (T5). The CLI sub-spec + implementation plan are already written; T1 is the next unblocked sub-project.

Your full sequence:

1. Brainstorm the T1 sub-spec using `superpowers:brainstorming` (mandatory — do not skip).
2. Save the spec to `docs/specs/<date>-template-full-stack-reagent-design.md`, commit.
3. Write the implementation plan using `superpowers:writing-plans`.
4. Save the plan to `docs/plans/<date>-template-full-stack-reagent-plan.md`, commit.
5. Pause for user review.
6. Execute via `superpowers:subagent-driven-development` once approved.

The brainstorm + spec write phase alone will fill a session. Don't try to do it all at once.

---

## Repo state (read first)

```
/Users/alex-root-roatch/current-projects/c3kit-starter   ← work here
├── README.md
├── CONTRIBUTING.md
├── LICENSE                          (MIT)
├── cli/.gitkeep                     (CLI impl plan exists but not yet executed)
├── templates/.gitkeep               (where T1 will land: templates/full-stack-reagent/)
├── docs/
│   ├── specs/
│   │   ├── 2026-05-12-c3kit-starter-roadmap-design.md      ← READ THIS
│   │   └── 2026-05-12-c3kit-create-cli-design.md           ← READ THIS
│   └── plans/
│       └── 2026-05-12-c3kit-create-cli-plan.md             ← skim
└── .github/workflows/ci.yml         (placeholder)

Remote: github.com/cleancoders/c3kit-starter   (private until release)
Branch state: `main` has roadmap spec + CLI sub-spec + CLI impl plan.
```

`git log --oneline` to confirm where things stand before you start.

**Branch you should create for T1 work:**

```sh
cd /Users/alex-root-roatch/current-projects/c3kit-starter
git switch main && git pull
git switch -c template/full-stack-reagent
```

All your commits land on `template/full-stack-reagent`; merge to `main` via PR after the spec, plan, and execution are done.

---

## Source material — the existing proprietary starter

The starter being open-sourced lives at:

```
/Users/alex-root-roatch/current-projects/starter
```

**Do NOT modify that directory.** It is the original Clean Coders proprietary template that drives client work; treat it as read-only source-of-truth that you adapt into `templates/full-stack-reagent/` inside this OSS repo.

Things you'll find there worth understanding before you brainstorm:

- `deps.edn` — full dep list (Datomic Peer, c3kit/{apron,bucket,wire,scaffold}, AWS SDKs, Hiccup, Compojure, jbcrypt, anti-forgery, commonmark, markdown-to-hiccup).
- `bin/setup` — the bash rename script. The OSS template will NOT ship this; the CLI handles rename. Read it to understand what tokens get renamed (`acme`, `Acme`, `acme_*`, `ACME_*_SECRET`).
- `src/clj/acme/main.clj`, `src/clj/acme/config.clj`, `src/clj/acme/content.clj`, `src/clj/acme/prerender.clj`, `src/clj/acme/security/csp.clj` — the feature-bearing files you'll wire feature markers around.
- `src/cljc/acme/markdownc.cljc` — the client-side markdown lib.
- `resources/prerender/prerender.js` — Node SSR entry.
- `README.md` — the canonical "this is what the starter does" doc. Steal the structure for the OSS template's README.

Recent commits relevant to features:

- `8357f46 docs: content pipeline, SSR, CSP, frontend markdown plugin`
- `b178faa feat: optional frontend markdown plugin (acme.markdownc)`
- `37f30ee feat(security): optional CSP plugin (off by default)`
- `2df63b0 feat(ssr): Reagent SSR prerender pipeline with HTML + markdown output`
- `fca6930 feat(content): markdown content pipeline with auto-routes and Accept: text/markdown`

---

## Constraints locked by upstream specs (do NOT re-litigate)

These were decided in the roadmap design and the CLI sub-spec. If the user asks, point them to the source; otherwise treat as fixed.

| Constraint                                                                  | Source                              |
|-----------------------------------------------------------------------------|-------------------------------------|
| c3kit stays (apron, bucket, wire, scaffold)                                 | roadmap §"c3kit dependencies" — kept |
| Datomic Pro = default DB; SQLite / Postgres / memory = wizard options       | roadmap §"default database" Q&A     |
| Optional features togglable: content / SSR / CSP / markdownc / auth         | roadmap §"wizard prompt scope" Q&A  |
| License MIT                                                                  | roadmap §naming                     |
| Template tree must be runnable as-is at HEAD (literal `acme`, no mustache)  | CLI sub-spec §6.2                   |
| Manifest file: `templates/full-stack-reagent/c3kit-template.edn`             | CLI sub-spec §5                     |
| Feature marker syntax: `;; @c3kit/feature :<id> { … }`, line-eq, inverse    | CLI sub-spec §6.1                   |
| DB marker syntax: `;; @c3kit/db :<id> { … }`                                | CLI sub-spec §6.1                   |
| Secrets are placeholder strings (e.g. `ACME_DEV_SECRET`); CLI fills them    | CLI sub-spec §6.3                   |
| File-level toggles: `:delete-when-off` list in manifest                     | CLI sub-spec §5                     |
| Token map: `{"acme" {:hyphen … :underscore … :pascal …} "ACME_" {:upper-prefix …}}` | CLI sub-spec §6.2          |
| `:next-steps` block printed by CLI is declared in the manifest               | CLI sub-spec §5                     |
| No `bin/setup` in the template                                              | CLI sub-spec §"rename mechanism"    |

If you discover a constraint that's wrong or unworkable, surface it to the user before re-deciding. Don't silently deviate.

---

## Open T1 questions to drive your brainstorm

Roadmap §"Open questions for T1 sub-spec" already flagged these. Resolve them, plus anything new you spot.

1. **Datomic Pro on OSS.** Datomic Pro now has a free tier (no `my.datomic.com` creds needed for current versions). What's the install story for a fresh OSS user choosing `:datomic-pro`? Single-jar download, transactor process, `bin/db` script? Document in the template's README so newcomers aren't stuck.
2. **Which bucket backends ship on day one.** `:datomic-pro :sqlite :postgres :memory` is the roadmap list. Confirm c3kit/bucket actually supports each at the version the template pins. Drop any backend that doesn't have a clean reference impl yet.
3. **`docker-compose.yml` for DB.** Vite-style projects often ship `docker-compose.yml` so `docker compose up` brings up DB locally. Worth shipping for Postgres / Datomic-Pro? Or keep template framework-agnostic and require user to BYO DB?
4. **Datomic vs. SQL schema story.** c3kit/bucket abstracts schema; but Datomic has datalog and SQL backends use JDBC. What goes in the template's `schema.clj`? A minimal `:user` kind? Empty + commented-out?
5. **Seed data.** Proprietary starter has `acme.test-data`. OSS version — keep a minimal seed (one user, one demo content), or empty?
6. **AWS SDKs in deps.edn.** Proprietary template depends on `aws-java-sdk-dynamodb/s3/ses/core`. Required for staging/production. Do we keep them in OSS template or move behind a feature toggle? AWS SDKs are heavy.
7. **JWT auth scope.** The roadmap calls JWT auth one of the toggleable features. Confirm what gets toggled — middleware only, or also user kind / login routes / session handling?
8. **CSP, content pipeline, SSR, markdownc** — these are toggleable; verify the source proprietary code has clean cut-points for marker insertion (no global side-effects that break when stripped).
9. **CI for the template itself.** When `templates/full-stack-reagent/` lands, CI should scaffold it via the CLI (in `--template-dir` local mode) and run `clj -M:test:spec` + `clj -M:test:cljs once` against the scaffolded output. Block PRs that break this.
10. **Per-environment config story.** Proprietary has dev/staging/production configs. OSS keeps all three? Or just dev + production?

---

## Constraints that affect your spec output shape

The CLI sub-spec defines a manifest schema. Your spec must produce a `c3kit-template.edn` consistent with §5 of that document. Reproduce the schema in your spec under a "Manifest" section, filled in with T1-specific values. Sample skeleton (you'll flesh out each field during brainstorm):

```clojure
{:id          :full-stack-reagent
 :name        "Full-stack Reagent"
 :description "..."
 :version     "0.1.0"
 :min-cli     "0.1.0"
 :tokens      {"acme"  {:hyphen true :underscore true :pascal true}
               "ACME_" {:upper-prefix true}}
 :secrets     [{:placeholder "ACME_DEV_SECRET"        :bytes 24}
               {:placeholder "ACME_STAGING_SECRET"    :bytes 24}
               {:placeholder "ACME_PRODUCTION_SECRET" :bytes 24}]
 :features    [{:id :content   :prompt "Markdown content pipeline?" :default true
                :delete-when-off [...]}
               {:id :ssr       :prompt "SSR/prerender (Reagent + Node)?" :default true
                :delete-when-off [...]}
               {:id :csp       :prompt "Content Security Policy plugin?" :default false
                :delete-when-off [...]}
               {:id :markdownc :prompt "Client-side markdown (CLJC)?" :default true
                :delete-when-off [...]}
               {:id :auth      :prompt "JWT auth?" :default true
                :delete-when-off [...]}]
 :db          {:prompt  "Database"
               :options [{:id :datomic-pro :label "Datomic Pro"}
                         {:id :sqlite      :label "SQLite"}
                         {:id :postgres    :label "Postgres"}
                         {:id :memory      :label "In-memory (dev only)"}]
               :default :datomic-pro}
 :next-steps  [{:cmd "cd {{name}}"        :doc nil}
               {:cmd "clj -M:test:spec"   :doc "run Clojure specs"}
               {:cmd "clj -M:test:cljs"   :doc "run ClojureScript specs (auto-watch)"}
               {:cmd "clj -M:test:css"    :doc "compile CSS (auto-watch)"}
               {:cmd "clj -M:test:cljss"  :doc "compile CLJS + CSS (auto-watch, combined)"}
               {:cmd "clj -M:test:dev"    :doc "run server + specs + cljs in one process"}
               {:cmd "clj -M:test:run"    :doc "run server only"}]
 :hook?       false}
```

`:delete-when-off` paths must be enumerated for each feature based on the proprietary starter's actual file layout — that's brainstorming work.

---

## Suggested brainstorm sequence

The `superpowers:brainstorming` skill checklist applies. Specific question order I'd suggest (not a script — adapt):

1. Verify with the user whether Datomic Pro is reachable as a clean OSS default (Q1 above). If not, escalate before going further — could affect default DB choice.
2. Resolve which bucket backends ship (Q2).
3. Resolve docker-compose y/n (Q3).
4. Resolve schema/seed strategy (Q4 + Q5).
5. AWS SDK fate (Q6).
6. Auth scope (Q7).
7. Audit feature cut-points in source (Q8) — you'll need to read source files; consider dispatching `cavecrew-investigator` for a focused locate pass.
8. CI scaffold-and-test design (Q9).
9. Per-env config (Q10).

After Q&A, present a sectioned design:

- Scope & non-goals
- Source files & directory layout (cataloged from proprietary starter, adapted)
- Manifest (full)
- Per-feature: which paths get marker blocks vs `:delete-when-off`
- DB story per backend
- README that ships with the scaffolded project
- CI workflow for the template itself
- Risks & mitigations
- Success criteria

Get user approval section by section. Save to `docs/specs/<date>-template-full-stack-reagent-design.md`. Commit.

---

## Inter-deliverable coordination

- The CLI implementation plan (`docs/plans/2026-05-12-c3kit-create-cli-plan.md`) is **not yet executed**. T1 spec work does not depend on the CLI being built — you can write the spec and plan in parallel.
- T1 implementation **does** depend on the CLI: scaffolding T1 in CI requires `c3kit-create` working. The T1 implementation plan should treat the CLI as a prerequisite (Task 0: "ensure CLI is built and on PATH").
- T5 (FE-only vanilla CLJS) is a peer to T1. Don't bleed T5 concerns into T1's scope. If you find a constraint that only makes sense across T1+T5 (e.g., shared CSS pipeline conventions), surface it to the user, don't unilaterally design for both.

---

## Memory + tooling notes for the agent picking this up

- The user uses caveman mode (ultra) — terse responses, fragments OK, articles/filler dropped. Code/commits/security normal prose.
- The user is at Clean Coders (alex.root-roatch@cleancoders.com). Treat them as a senior Clojure dev who built the proprietary starter.
- TDD is mandatory globally — every plan task that writes production code must be RED → GREEN.
- This OSS project is part of the c3kit OSS collection. Branding ties to c3kit are intentional.
- Use `superpowers:brainstorming` then `superpowers:writing-plans` then `superpowers:subagent-driven-development`. Do not skip skills.
- Working dir for all T1 work: `/Users/alex-root-roatch/current-projects/c3kit-starter`. Source-of-truth proprietary starter (read-only): `/Users/alex-root-roatch/current-projects/starter`.

Good luck.
