# CLI Update-Availability Notification Design

**Date:** 2026-06-18
**Status:** Approved (pending spec review)

## Problem

The CLI can already self-update (`c3kit-jig upgrade` → `version/check-and-download!`), but nothing tells a user that an update *exists*. Users on a stale CLI never learn to run `upgrade`. This is the second half of the versioning work: once releases are cut (see `2026-06-18-cli-versioning-design.md`), the installed CLI should surface "a newer version is available."

## Goal

When a newer release exists, proactively tell the user and point them at `c3kit-jig upgrade` — **before** they invest work that a newer CLI might change, and without slowing down or breaking normal use, CI, or offline runs.

## Existing pieces (reused, not rebuilt)

- `version/current` — the baked CLI version (e.g. `1.0.0`).
- `version/fetch-latest-tag!` — GETs the latest GitHub release tag.
- `version/semver-compare` — compares two bare-semver strings.
- `version/check-and-download!` — the `upgrade` command's downloader.

## Design

### New namespace: `c3kit-jig.update-check`

Keeps all check/notify logic out of `main`. Split into pure logic (unit-tested) and thin IO glue.

**Pure (TDD-tested):**
- `update-message [current latest] -> String|nil` — returns the notice string when `latest` is strictly newer than `current` (via `semver-compare`), else `nil`. The single source of the "is there an update" decision and its wording.
- `stale? [checked-at now ttl-ms] -> boolean` — true when `checked-at` is nil or older than `ttl-ms` before `now`.
- `parse-cache [edn-string] -> {:checked-at long :latest-tag string}|nil` and `render-cache [m] -> string` — tolerant of a missing/garbage file (returns nil on parse failure).

**IO glue (thin, not unit-tested — integration-checked):**
- `cache-file` — `~/.c3kit/update-check.edn` (home via `user.home`).
- `read-cache` / `write-cache!` — read/parse and render/write, swallowing IO errors.
- `latest-tag! []` — return the latest tag, using the cache when fresh (<TTL); otherwise fetch with a 2s ceiling (`(deref (future (version/fetch-latest-tag!)) 2000 nil)`), update the cache on success, and fall back to the stale cached tag (or nil) on timeout/offline. Never throws.
- `available-update []` — returns `{:current <baked> :latest <tag>}` when an update exists and checking is enabled, else nil. Honors the disable guards (below).

### Disable guards (checking is skipped entirely when any hold)

- `C3KIT_NO_UPDATE_CHECK` env var is set (any non-empty value).
- Not an interactive terminal (`(System/console)` is nil — covers pipes, CI, redirected output).

These are checked inside `available-update` so every caller inherits them.

### Per-command wiring in `main.clj`

The check runs **up front**, before the command does its work — so the user can abort and upgrade before the Q&A flow and scaffold, rather than discovering the update after a full build they must redo.

- **`create`:** before prompting/scaffolding, call `available-update`. If an update exists:
  - Interactive (TTY) and not `--yes`: prompt `Update available (1.0.0 → 1.2.0). Continue anyway? [Y/n]` (Enter/`y` → proceed; `n` → print `Run \`c3kit-jig upgrade\` to update.` and exit 0 without scaffolding).
  - Non-interactive or `--yes`: the guards already suppress the check in non-TTY; under `--yes` in a TTY, print the notice to stderr and proceed (never block automation).
- **`list`:** before listing, if an update exists, print the notice to stderr, then list normally.
- **Excluded:** `upgrade`, `version`, `help`, and any errored command. (`upgrade`/`version` already deal with versions directly; help/errors should stay clean.)

All notices and prompts go to **stderr**, leaving stdout parseable.

### Cache format

```clojure
{:checked-at 1718700000000   ; epoch millis of last successful fetch
 :latest-tag "1.2.0"}
```

TTL is a constant in the namespace: `86400000` ms (24h).

## Error handling

Every failure mode is silent and non-fatal: unreadable/corrupt cache → treated as missing; fetch timeout or network error → fall back to cached tag or skip; unwritable cache → skip the write. The update check never changes a command's exit status or blocks its work (except the explicit `create` "continue?" prompt, which the user controls).

## Testing

- `update-message`: newer `latest` → message containing both versions and `upgrade`; equal or older → nil.
- `stale?`: nil `checked-at` → true; `now - checked-at > ttl` → true; within ttl → false.
- `parse-cache`: valid edn → map; garbage/empty → nil. `render-cache` round-trips with `parse-cache`.
- Integration (manual, per the build's existing style): with a cache pinning an older `:latest-tag`, `create` prompts and `list` prints the notice; with `C3KIT_NO_UPDATE_CHECK=1` or piped stdout, neither does; offline with a stale cache stays silent within the 2s ceiling.

## Files Touched

- `cli/src/c3kit_jig/update_check.clj` (new)
- `cli/spec/c3kit_jig/update_check_spec.clj` (new)
- `cli/src/c3kit_jig/main.clj` (wire create/list)
- `README.md` (note the auto-notice under Versioning/usage)
- `CHANGES.md` (entry under `### 1.0.0` — this ships in the as-yet-uncut first release, not a new Unreleased section)

## Out of Scope (YAGNI)

Background/daemon checks, auto-upgrade without consent, a `--check-updates`/TTL flag, beta/channel selection, notifying on every command (only `create`/`list` carry it).
