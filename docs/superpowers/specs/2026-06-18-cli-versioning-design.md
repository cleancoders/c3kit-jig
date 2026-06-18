# c3kit-jig CLI Versioning & Release Design

**Date:** 2026-06-18
**Status:** Approved (pending spec review)

## Problem

Running the public installer fails:

```
▸ looking up latest c3kit-jig release
curl: (56) The requested URL returned error: 404
```

Root cause: no GitHub release exists. `cli/install.sh` (and `version.clj`)
resolve `releases/latest` from the GitHub API, which 404s when the repo has
zero releases. The repo also has zero tags, so the existing `release.yml`
workflow (triggered on `tags: ['cli-v*']`) has never run.

Beyond the immediate 404, the project's versioning diverges from the c3kit
convention and has no single source of truth: the version is hardcoded in
`version.clj` as `(def CURRENT "0.1.0-SNAPSHOT")`, tags use a `cli-v` prefix,
and `CHANGES.md` leads with `### Unreleased`.

## Goal

Adopt a versioning + release model for the CLI that is cohesive with the
c3kit library modules (`apron`, `wire`, `bucket`, `scaffold`), and cut the
first release so the installer works.

## c3kit Convention (reference)

Each c3kit module versions independently with:

- `VERSION` file at repo root — bare semver, no prefix (e.g. `4.0.1`).
- `CHANGES.md` — `### X.Y.Z` headings; the top heading must match `VERSION`
  (not `### Unreleased`, not a stale version).
- git tags — bare semver (`2.7.1`), no `v` / module prefix.
- Release via one command from inside the module (`clj -T:build deploy`),
  whose build `tag` task verifies a clean tree, checks the tag is absent,
  `git tag $VERSION`, and `git push --tags`.

c3kit modules publish jars to Clojars. c3kit-jig instead distributes a
Babashka uberscript via GitHub Releases — so the *distribution mechanism*
differs, but the *versioning source of truth* (VERSION + CHANGES + bare-semver
tags) is adopted as-is.

## Key Architectural Fact

The installed CLI **fetches templates at runtime** by shallow-cloning
`cleancoders/c3kit-jig` at ref `main` (`main.clj`: `DEFAULT-REF "main"`,
`clone-repo!` in `fetch.clj`). Therefore:

- Adding a template to `main` makes it immediately available to every
  already-installed CLI — no CLI release required.
- Templates do **not** need individual version files, and there is **no**
  CLI-version ↔ template-version compatibility matrix to maintain.
- Users who want templates frozen at a point in time pass
  `--template-ref <tag-or-branch>`.

Conclusion: **the CLI binary is the only artifact that is versioned.** The
"template version" is simply whatever ref the user fetches (default `main`).

## Design

### 1. Single source of truth

- Add root `VERSION` containing bare semver. First public release is `1.0.0`
  (tag is bare `1.0.0`, no `v` prefix, per c3kit convention).
- Root `CHANGES.md` already exists. Enforce the c3kit discipline: top heading
  is `### <VERSION>`. Fold the current `### Unreleased` bullet into the
  released version's section at release time.

### 2. Bake VERSION into the uberscript at build

The uberscript runs standalone after install, so it cannot read `VERSION` at
runtime. The version is baked in at build:

- `version.clj` keeps a placeholder: `(def ^:const CURRENT "0.0.0-DEV")`.
- The `bb build` task (in `cli/bb.edn`) reads root `VERSION` and, after
  producing `dist/c3kit-jig.bb`, replaces the placeholder literal
  `0.0.0-DEV` with the real version via string substitution (the same
  post-processing step that already prepends the shebang).
- Running the CLI from source (`bb c3kit-jig version`) reports `0.0.0-DEV`;
  the built/installed artifact reports the real version. This is acceptable
  and documented.

### 3. Drop the `cli-v` tag prefix

- `version.clj`: change the up-to-date check from
  `(= tag (str "cli-v" CURRENT))` to `(= tag CURRENT)`.
- `release.yml`: change the trigger from `tags: ['cli-v*']` to a bare-semver
  glob (`tags: ['[0-9]*.[0-9]*.[0-9]*']`).
- The download URLs in `version.clj` / `install.sh` already interpolate the
  tag verbatim, so bare-semver tags work without further change.

### 4. `bb release` task (cut a release)

Add a `release` task to `cli/bb.edn` mirroring c3kit's build `tag` task, so a
release is one command from inside the repo:

1. Verify clean working tree (`git diff` / `git status --short` empty); abort
   otherwise.
2. Read root `VERSION`.
3. Verify `CHANGES.md` top heading is `### <VERSION>`; abort otherwise.
4. Verify the tag does not already exist; abort otherwise.
5. `git tag <VERSION>` and `git push origin <VERSION>`.

Pushing the semver tag triggers `release.yml`, which runs `bb test`,
`bb build` (now baking VERSION), computes the sha256, and publishes
`c3kit-jig.bb` + `c3kit-jig.bb.sha256` as release assets. Build/publish stay
in CI for reproducibility; the local task only tags and pushes.

### 5. First release

After the above lands and `CHANGES.md` top reads `### 1.0.0`:

- Run `bb release` (tags `1.0.0`, pushes).
- CI publishes the assets.
- Verify the installer succeeds end-to-end.

### 6. Documentation

The versioning + release model must be documented so contributors and
maintainers can follow it.

**`CONTRIBUTING.md`:**
- Reconcile the existing CHANGES instruction. Today it says "Update
  `CHANGES.md` with a one-line entry under the current `Unreleased` (or
  pending version) section." Keep that for contributors — during development,
  entries accumulate under `### Unreleased`.
- Add a **Releasing** section (maintainer-facing) describing the full flow:
  1. Bump root `VERSION` (semver: patch/minor/major).
  2. Rename the `### Unreleased` heading in `CHANGES.md` to `### <VERSION>`
     (the top heading must match `VERSION` at release time).
  3. Commit on `main`, tree clean, synced with origin.
  4. Run `bb release` (from `cli/`) — tags bare semver, pushes.
  5. CI builds + publishes `c3kit-jig.bb` + `.sha256` as release assets.
  6. Verify the installer end-to-end.
- Note the guards `bb release` enforces (clean tree, CHANGES matches VERSION,
  tag absent) so the process is self-checking.

**`README.md`:**
- Add a short **Versioning** note: bare-semver, source of truth is root
  `VERSION`, releases distributed via GitHub Releases, CLI self-upgrades via
  `c3kit-jig upgrade`. Templates track `main` (or `--template-ref`) and are
  not separately versioned.
- Add `VERSION` and `CHANGES.md` to the repo-layout block.

## Out of Scope / Minor

- `cli/dist/c3kit-jig.bb` is a committed build artifact carrying a stale baked
  version. CI rebuilds it for releases, so it is not load-bearing.
  Recommend (optional) gitignoring `cli/dist/` in a follow-up; not required
  for this change.

## Testing

- `version.clj`: unit-test that the up-to-date comparison matches a bare-semver
  tag and rejects a `cli-v`-prefixed one (TDD: existing `version_spec.clj`).
- `bb build`: assert the produced uberscript contains the version read from
  `VERSION` and no longer contains the `0.0.0-DEV` placeholder.
- `bb release`: guard logic (clean-tree, CHANGES-match, tag-exists) is the
  risky surface — cover with tests or a `--dry-run` that prints the actions
  without tagging.
- End-to-end: after the first real release, run the installer against the
  public repo and confirm install + `c3kit-jig version` + `upgrade`.

## Files Touched

- `VERSION` (new, root)
- `CHANGES.md` (root — heading discipline)
- `cli/src/c3kit_jig/version.clj` (drop `cli-v`, placeholder constant)
- `cli/bb.edn` (`build` bakes VERSION; new `release` task)
- `.github/workflows/release.yml` (bare-semver tag trigger)
- `cli/spec/c3kit_jig/version_spec.clj` (tests)
- `CONTRIBUTING.md` (Releasing section; CHANGES reconciliation)
- `README.md` (Versioning note; repo-layout block)
