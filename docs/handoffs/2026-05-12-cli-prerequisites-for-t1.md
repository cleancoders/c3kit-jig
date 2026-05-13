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
