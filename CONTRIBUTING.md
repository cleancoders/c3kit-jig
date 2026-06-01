# Contributing to c3kit-jig

Thanks for your interest! c3kit-jig is the project scaffolder for the [c3kit](https://github.com/cleancoders/c3kit) family of libraries.

## Getting Started

1. Fork and clone the repo.
2. Install [Babashka](https://babashka.org) (`brew install borkdude/brew/babashka` or see upstream docs). Most CLI work only needs `bb`.
3. For template work, install a JDK 21+ and the [Clojure CLI](https://clojure.org/guides/install_clojure).
4. Run the test suite to confirm a green baseline:

```sh
cd cli
bb test          # CLI specs
bb lint          # clj-kondo
shellcheck install.sh
```

Template specs live under `templates/<name>/spec/` and run with `clojure -M:test:spec` inside the template tree.

For any change under `templates/**`, also run the verification harness end-to-end before opening a PR:

```sh
cd verification
bb verify-all   # coupling scan + every combo at its declared tier
```

CI runs the same harness on every push. See [README §Verification harness](README.md#verification-harness) for per-check details, tiers, and prerequisites.

## Design-first workflow

Non-trivial work flows through specs and plans before code:

1. **Spec** under `docs/specs/YYYY-MM-DD-<topic>-design.md` (use the `superpowers:brainstorming` skill).
2. **Plan** under `docs/plans/YYYY-MM-DD-<topic>-plan.md` (use the `superpowers:writing-plans` skill).
3. **Code** implements one plan, in small commits.

Current roadmap: [`docs/specs/2026-05-12-c3kit-jig-roadmap-design.md`](docs/specs/2026-05-12-c3kit-jig-roadmap-design.md).

## Workflow

**All pull requests must be linked to an open issue.** PRs without a linked issue will be closed without review by the `require-linked-issue` workflow. Open (or find) an issue first, get a thumbs-up from a maintainer that the change is wanted, then start work. This protects everyone's time — yours and ours.

- Open or find an issue describing the bug or proposed change. Wait for maintainer acknowledgement before starting work on anything non-trivial.
- Create a feature branch off `main`.
- **Use TDD.** Write a failing spec first, then the minimum code to make it pass, then refactor.
- Keep commits small and focused. Write descriptive commit messages.
- Update `CHANGES.md` with a one-line entry under the current `Unreleased` (or pending version) section.
- If you change CLI surface or template options, update the README and the relevant spec.

## Code Style

- Idiomatic Clojure / Babashka: prefer `->` / `->>` threading, keep functions small and focused.
- `!`-suffix for fns that throw or mutate; `->type` / `<-type` symmetry for converters.
- Don't column-align values in maps; use single spaces.
- `bb lint` (clj-kondo) and `shellcheck` must pass.
- No `:refer :all`. Every `:require` must list its referred symbols explicitly. Canonical `clj-kondo.edn` enforces this with `:refer-all {:level :error}`.

## Template editing

- The template's `.clj-kondo/config.edn` is the **loose** config — relaxed enough that the marker-laden raw template does not yell at you in an editor. Scaffolds do not inherit it.
- `.scaffold-clj-kondo/config.edn` holds the **strict** rules that ship into scaffolds (project-specific excludes + the canonical via `:config-paths`). The CLI render step swaps it into `.clj-kondo/` on the way out. The harness lints scaffolds under this config — keep it strict.
- The canonical `clj-kondo.edn` and `cljfmt.edn` at the repo root are symlinked into both the template and `verification/`. Add new universally-true rules (c3kit / speclj idioms) there. Project-specific suppressions belong in the template's `.scaffold-clj-kondo/config.edn` or loose `.clj-kondo/config.edn`, never in the canonical.
- Specs must not leak log output: the `cljs-run` check fails on any timestamp / level marker in spec stdout. Wrap noisy `describe` forms with `(around [it] (log/capture-logs (it)))`.
- Static feature-coupling: any reference from one feature's code into another feature's namespace must sit inside a matching `;; @c3kit/feature :id { … }` block. `bb scan-coupling` runs once per template before any combo and rejects unmarked cross-feature refs.

## Submitting a PR

1. Confirm your PR is linked to an open issue (use `Closes #N` in the description).
2. Ensure `bb test`, `bb lint`, and `shellcheck cli/install.sh` pass.
3. If your change touches `templates/**`, the template CI must pass (`.github/workflows/template-full-stack-reagent.yml`).
4. Open a PR against `main`.
5. Describe what changed and why.

## Reporting Bugs / Requesting Features

Open an issue with the appropriate template. Include:
- c3kit-jig version (`c3kit-jig version`)
- Babashka version
- Template ID (if applicable)
- A minimal reproduction
- Expected vs actual behavior

## Security

Do not file public issues for security-sensitive reports. See [SECURITY.md](SECURITY.md) for the private reporting channel.

## License

By contributing, you agree your contributions are licensed under the MIT License (see [`LICENSE`](LICENSE)).
