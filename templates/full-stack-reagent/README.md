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
- Built-in: Content Security Policy middleware (`acme.security.csp`,
  toggle per env in `acme.config`)
- Optional features (defaults in parens):
  - `:content` (on) — markdown content pipeline + auto-routes
  - `:ssr` (on) — Reagent SSR via Node prerender
  - `:markdownc` (on) — client-side markdown rendering (CLJC)
  - `:auth` (on) — JWT auth + user kind + signin/signup/forgot/recover
- Database backends:
  - `:datomic-pro` (default) — Datomic Pro free tier, single-jar transactor
  - `:sqlite` — JDBC SQLite (single-file)
  - `:postgres` — JDBC Postgres
  - `:memory` — c3kit/bucket in-memory (dev only)

## Scaffold the template locally

From the c3kit-starter repo root with a built CLI uberscript:

```sh
./cli/dist/c3kit-create.bb test-app \
  --template-dir templates \
  --template full-stack-reagent \
  --db sqlite \
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
;; @c3kit/feature :auth {          ;; block on/off
…
;; @c3kit/feature :auth }

;; @c3kit/feature :auth = (require …)   ;; line-toggle (kept verbatim when on)

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

`.github/workflows/template-full-stack-reagent.yml` in the c3kit-starter
repo scaffolds this template across a `{db × feature-combo}` matrix and
runs `clj -M:test:spec` + `clj -M:test:cljs once` against each output.
