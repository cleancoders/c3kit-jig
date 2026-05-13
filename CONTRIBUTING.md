# Contributing to c3kit-starter

Thanks for your interest. The project is in Phase 0 bootstrap and active design — most areas are not yet open for code contributions, but feedback on the roadmap is welcome via GitHub issues.

## Design-first workflow

Every non-trivial change starts as a spec, then a plan, then code.

1. **Spec** — written using the [`superpowers:brainstorming`](https://github.com/anthropics/claude-code/tree/main/skills) skill. Lives under `docs/specs/YYYY-MM-DD-<topic>-design.md`.
2. **Plan** — written using the [`superpowers:writing-plans`](https://github.com/anthropics/claude-code/tree/main/skills) skill. Lives under `docs/plans/YYYY-MM-DD-<topic>-plan.md`.
3. **Code** — implements one plan, in small commits.

The current roadmap is in [`docs/specs/2026-05-12-c3kit-starter-roadmap-design.md`](docs/specs/2026-05-12-c3kit-starter-roadmap-design.md).

## Phase 1 roadmap

Three sub-projects are unblocked once the repo is bootstrapped. Each gets its own sub-spec before any code is written:

- [x] `c3kit-create` CLI sub-spec + plan + implementation
- [ ] `templates/full-stack-reagent` sub-spec + plan + implementation
- [ ] `templates/fe-vanilla` sub-spec + plan + implementation

## Code style

To be defined in the CLI sub-spec. Expect: `clj-kondo`, `cljfmt`, and `bb` tasks for lint/test.

## License

By contributing, you agree your contributions are licensed under the MIT License (see [`LICENSE`](LICENSE)).
