# c3kit-starter Phase 0 Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the `c3kit-starter` GitHub repository with an empty monorepo skeleton (CLI dir, templates dir, docs, license, CI scaffold) ready for follow-up sub-spec work on the CLI and Phase 1 templates.

**Architecture:** Local-first git init, structured directory tree, MIT license, GitHub Actions CI placeholder. Remote repo creation runs once via `gh repo create` against the `cleancoders` org. The roadmap spec and this plan are moved from `~/Desktop` into `docs/specs/` and `docs/plans/` as part of the first commit so the new repo owns its design history.

**Tech Stack:** git, GitHub CLI (`gh`), GitHub Actions YAML. No code yet.

**Working directory for all tasks:** `/Users/alex-root-roatch/current-projects/c3kit-starter` (created in Task 1).

---

## File Structure

After bootstrap completes, the repo looks like this:

```
c3kit-starter/
├── .github/
│   └── workflows/
│       └── ci.yml                  # placeholder workflow, runs no-op until templates exist
├── .gitignore                      # OS / editor / Clojure / Node ignores
├── CONTRIBUTING.md                 # placeholder, lists sub-spec roadmap
├── LICENSE                         # MIT, 2026 Clean Coders
├── README.md                       # marketing pitch + install command + status badge
├── cli/                            # empty, populated by CLI sub-spec
│   └── .gitkeep
├── templates/                      # empty, populated by T1/T5 sub-specs
│   └── .gitkeep
└── docs/
    ├── specs/
    │   └── 2026-05-12-c3kit-starter-roadmap-design.md   # moved from Desktop
    └── plans/
        └── 2026-05-12-c3kit-starter-bootstrap-plan.md   # moved from Desktop (this file)
```

Each file has a single responsibility. Empty `cli/` and `templates/` dirs are kept under git via `.gitkeep`.

---

### Task 1: Create local repo directory and initialize git

**Files:**
- Create: `/Users/alex-root-roatch/current-projects/c3kit-starter/` (directory)

- [ ] **Step 1: Verify parent directory exists and target does not**

Run:
```bash
ls -ld /Users/alex-root-roatch/current-projects
test ! -e /Users/alex-root-roatch/current-projects/c3kit-starter && echo "OK: target free"
```
Expected: parent listing succeeds, `OK: target free` prints. If target already exists, stop and ask the user before continuing.

- [ ] **Step 2: Create directory and init git**

Run:
```bash
mkdir /Users/alex-root-roatch/current-projects/c3kit-starter
cd /Users/alex-root-roatch/current-projects/c3kit-starter
git init -b main
```
Expected: `Initialized empty Git repository in /Users/alex-root-roatch/current-projects/c3kit-starter/.git/`.

- [ ] **Step 3: Verify git status**

Run:
```bash
git -C /Users/alex-root-roatch/current-projects/c3kit-starter status
```
Expected:
```
On branch main

No commits yet

nothing to commit (create/copy files and use "git add" to track)
```

No commit yet — Task 9 makes the initial commit.

---

### Task 2: Add MIT LICENSE

**Files:**
- Create: `/Users/alex-root-roatch/current-projects/c3kit-starter/LICENSE`

- [ ] **Step 1: Write the LICENSE file**

Content (literal — copy verbatim):

```
MIT License

Copyright (c) 2026 Clean Coders LLC

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

- [ ] **Step 2: Verify**

Run:
```bash
head -1 /Users/alex-root-roatch/current-projects/c3kit-starter/LICENSE
```
Expected: `MIT License`.

---

### Task 3: Add `.gitignore`

**Files:**
- Create: `/Users/alex-root-roatch/current-projects/c3kit-starter/.gitignore`

- [ ] **Step 1: Write the file**

Content:

```
# macOS
.DS_Store

# Editors / IDEs
.idea/
*.iml
.vscode/
*.swp

# Clojure / CLJS
.cpcache/
.clj-kondo/
.lsp/
target/
out/
classes/
pom.xml
pom.xml.asc

# Node (only present inside templates that use it)
node_modules/

# Generated / build artifacts
resources/public/cljs/
resources/public/js/compiled/
resources/prerendered/

# Local-only env / secrets
.env
.env.local

# Logs
*.log

# bb uberscript build output (CLI release artifacts live under cli/dist/)
cli/dist/
```

- [ ] **Step 2: Verify**

Run:
```bash
grep -c '^' /Users/alex-root-roatch/current-projects/c3kit-starter/.gitignore
```
Expected: an integer ≥ 25 (the file should have at least the lines shown above).

---

### Task 4: Add top-level `README.md`

**Files:**
- Create: `/Users/alex-root-roatch/current-projects/c3kit-starter/README.md`

- [ ] **Step 1: Write the file**

Content:

````markdown
# c3kit-starter

Vite-style scaffolder for full-stack Clojure / ClojureScript projects, part of the [c3kit](https://github.com/cleancoders/c3kit) collection.

> **Status:** Phase 0 bootstrap. The CLI and templates are not yet implemented. See [`docs/specs/2026-05-12-c3kit-starter-roadmap-design.md`](docs/specs/2026-05-12-c3kit-starter-roadmap-design.md) for the roadmap.

## Install (planned)

```sh
curl -fsSL https://raw.githubusercontent.com/cleancoders/c3kit-starter/main/cli/install.sh | bash
```

The installer detects [Babashka](https://babashka.org) and `git`, installs Babashka if missing, then drops the `c3kit-create` command onto your `PATH`.

## Usage (planned)

```sh
c3kit-create                # interactive wizard
c3kit-create my-app         # wizard with project name pre-filled
c3kit-create --list         # list available templates
```

## Templates (planned)

| ID                       | Description                                                  | Status     |
|--------------------------|--------------------------------------------------------------|------------|
| `full-stack-reagent`     | Clojure backend + Reagent frontend (c3kit, Datomic, opt SSR) | phase 1    |
| `fe-vanilla`             | ClojureScript SPA, no framework                              | phase 1    |
| `fe-reagent`             | ClojureScript SPA with Reagent + optional SSG build          | phase 2    |
| `fe-ssg`                 | Static-site generator (Clojure-Astro)                        | phase 2    |
| `full-stack-non-reagent` | Backend + non-Reagent CLJS frontend                          | phase 2    |

## Repo layout

```
cli/         # bb CLI source + installer (phase 1)
templates/   # template trees, one dir per template (phase 1+)
docs/        # design specs and implementation plans
```

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). All design work flows through specs in `docs/specs/` and plans in `docs/plans/`.

## License

MIT — see [`LICENSE`](LICENSE).
````

- [ ] **Step 2: Verify**

Run:
```bash
head -1 /Users/alex-root-roatch/current-projects/c3kit-starter/README.md
```
Expected: `# c3kit-starter`.

---

### Task 5: Add `CONTRIBUTING.md`

**Files:**
- Create: `/Users/alex-root-roatch/current-projects/c3kit-starter/CONTRIBUTING.md`

- [ ] **Step 1: Write the file**

Content:

````markdown
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

- [ ] `c3kit-create` CLI sub-spec + plan + implementation
- [ ] `templates/full-stack-reagent` sub-spec + plan + implementation
- [ ] `templates/fe-vanilla` sub-spec + plan + implementation

## Code style

To be defined in the CLI sub-spec. Expect: `clj-kondo`, `cljfmt`, and `bb` tasks for lint/test.

## License

By contributing, you agree your contributions are licensed under the MIT License (see [`LICENSE`](LICENSE)).
````

- [ ] **Step 2: Verify**

Run:
```bash
head -1 /Users/alex-root-roatch/current-projects/c3kit-starter/CONTRIBUTING.md
```
Expected: `# Contributing to c3kit-starter`.

---

### Task 6: Create empty `cli/` and `templates/` directories with `.gitkeep`

**Files:**
- Create: `/Users/alex-root-roatch/current-projects/c3kit-starter/cli/.gitkeep`
- Create: `/Users/alex-root-roatch/current-projects/c3kit-starter/templates/.gitkeep`

- [ ] **Step 1: Create directories**

Run:
```bash
mkdir /Users/alex-root-roatch/current-projects/c3kit-starter/cli
mkdir /Users/alex-root-roatch/current-projects/c3kit-starter/templates
```
Expected: silent success.

- [ ] **Step 2: Create `.gitkeep` placeholders**

Write `/Users/alex-root-roatch/current-projects/c3kit-starter/cli/.gitkeep` with the literal content:

```
# Placeholder — replaced by CLI sub-spec implementation. Do not delete until cli/ has tracked files.
```

Write `/Users/alex-root-roatch/current-projects/c3kit-starter/templates/.gitkeep` with the literal content:

```
# Placeholder — replaced by first template (full-stack-reagent or fe-vanilla). Do not delete until templates/ has tracked files.
```

- [ ] **Step 3: Verify**

Run:
```bash
ls -1 /Users/alex-root-roatch/current-projects/c3kit-starter/cli /Users/alex-root-roatch/current-projects/c3kit-starter/templates
```
Expected output:
```
/Users/alex-root-roatch/current-projects/c3kit-starter/cli:
.gitkeep

/Users/alex-root-roatch/current-projects/c3kit-starter/templates:
.gitkeep
```

(Note: `ls -1` without `-a` hides dotfiles. Use `ls -1a` to confirm — output will additionally show `.` and `..`.)

---

### Task 7: Move roadmap spec and this plan into the new repo

**Files:**
- Move: `/Users/alex-root-roatch/Desktop/2026-05-12-c3kit-starter-roadmap-design.md` → `/Users/alex-root-roatch/current-projects/c3kit-starter/docs/specs/2026-05-12-c3kit-starter-roadmap-design.md`
- Move: `/Users/alex-root-roatch/Desktop/2026-05-12-c3kit-starter-bootstrap-plan.md` → `/Users/alex-root-roatch/current-projects/c3kit-starter/docs/plans/2026-05-12-c3kit-starter-bootstrap-plan.md`

- [ ] **Step 1: Create docs subdirectories**

Run:
```bash
mkdir -p /Users/alex-root-roatch/current-projects/c3kit-starter/docs/specs /Users/alex-root-roatch/current-projects/c3kit-starter/docs/plans
```
Expected: silent success.

- [ ] **Step 2: Move the spec**

Run:
```bash
mv /Users/alex-root-roatch/Desktop/2026-05-12-c3kit-starter-roadmap-design.md /Users/alex-root-roatch/current-projects/c3kit-starter/docs/specs/2026-05-12-c3kit-starter-roadmap-design.md
```
Expected: silent success.

- [ ] **Step 3: Move this plan**

Run:
```bash
mv /Users/alex-root-roatch/Desktop/2026-05-12-c3kit-starter-bootstrap-plan.md /Users/alex-root-roatch/current-projects/c3kit-starter/docs/plans/2026-05-12-c3kit-starter-bootstrap-plan.md
```
Expected: silent success. After this step, this plan file is being read from its new location for any subsequent re-runs.

- [ ] **Step 4: Verify both files landed**

Run:
```bash
ls /Users/alex-root-roatch/current-projects/c3kit-starter/docs/specs /Users/alex-root-roatch/current-projects/c3kit-starter/docs/plans
```
Expected:
```
/Users/alex-root-roatch/current-projects/c3kit-starter/docs/plans:
2026-05-12-c3kit-starter-bootstrap-plan.md

/Users/alex-root-roatch/current-projects/c3kit-starter/docs/specs:
2026-05-12-c3kit-starter-roadmap-design.md
```

---

### Task 8: Add GitHub Actions CI placeholder

**Files:**
- Create: `/Users/alex-root-roatch/current-projects/c3kit-starter/.github/workflows/ci.yml`

- [ ] **Step 1: Create workflow directory**

Run:
```bash
mkdir -p /Users/alex-root-roatch/current-projects/c3kit-starter/.github/workflows
```
Expected: silent success.

- [ ] **Step 2: Write the workflow**

Content for `ci.yml`:

```yaml
name: ci

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  placeholder:
    name: placeholder (phase 0)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Report status
        run: |
          echo "c3kit-starter is in Phase 0 bootstrap."
          echo "Real CI jobs (CLI tests, template scaffold checks) will be added"
          echo "when their sub-specs are implemented. See docs/specs/."
```

- [ ] **Step 3: Verify the workflow parses as YAML**

Run:
```bash
python3 -c "import yaml,sys; yaml.safe_load(open('/Users/alex-root-roatch/current-projects/c3kit-starter/.github/workflows/ci.yml')); print('OK')"
```
Expected: `OK`. If `python3` or `pyyaml` is missing, skip this check and rely on GitHub-side validation after push.

---

### Task 9: First commit

**Files:**
- Modify (commit): all files created above.

- [ ] **Step 1: Inspect what will be committed**

Run:
```bash
cd /Users/alex-root-roatch/current-projects/c3kit-starter && git status
```
Expected (order may vary):
```
On branch main

No commits yet

Untracked files:
  (use "git add <file>..." to include in what will be committed)
	.github/
	.gitignore
	CONTRIBUTING.md
	LICENSE
	README.md
	cli/
	docs/
	templates/

nothing added to commit but untracked files present (use "git add" to track)
```

If anything unexpected appears (e.g. `.DS_Store`, editor swap files), stop and investigate before staging.

- [ ] **Step 2: Stage everything (explicit paths, no `git add .`)**

Run:
```bash
cd /Users/alex-root-roatch/current-projects/c3kit-starter && \
git add .github/workflows/ci.yml \
        .gitignore \
        CONTRIBUTING.md \
        LICENSE \
        README.md \
        cli/.gitkeep \
        templates/.gitkeep \
        docs/specs/2026-05-12-c3kit-starter-roadmap-design.md \
        docs/plans/2026-05-12-c3kit-starter-bootstrap-plan.md
```
Expected: silent success.

- [ ] **Step 3: Verify staged set**

Run:
```bash
cd /Users/alex-root-roatch/current-projects/c3kit-starter && git status --short
```
Expected:
```
A  .github/workflows/ci.yml
A  .gitignore
A  CONTRIBUTING.md
A  LICENSE
A  README.md
A  cli/.gitkeep
A  docs/plans/2026-05-12-c3kit-starter-bootstrap-plan.md
A  docs/specs/2026-05-12-c3kit-starter-roadmap-design.md
A  templates/.gitkeep
```

- [ ] **Step 4: Commit**

Run:
```bash
cd /Users/alex-root-roatch/current-projects/c3kit-starter && \
git commit -m "$(cat <<'EOF'
chore: bootstrap repo skeleton

Empty cli/ and templates/ dirs (gitkeep), MIT license, top-level README,
CONTRIBUTING, .gitignore, placeholder CI workflow, and the Phase 0
roadmap spec + bootstrap plan moved in from the design session.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```
Expected: `[main (root-commit) <sha>] chore: bootstrap repo skeleton` plus a file-summary block listing 9 files changed.

- [ ] **Step 5: Confirm the commit landed**

Run:
```bash
cd /Users/alex-root-roatch/current-projects/c3kit-starter && git log --oneline
```
Expected: one line, the commit subject `chore: bootstrap repo skeleton`.

---

### Task 10: Create the GitHub remote and push

This task touches a shared external system (GitHub). The agent should pause and confirm with the user before running Step 2 — specifically, that the target org (`cleancoders`) and visibility (`public`) are correct and the user has permission to create repos in that org.

**Files:** none (operates on git remote + GitHub).

- [ ] **Step 1: Verify `gh` is installed and authenticated**

Run:
```bash
gh auth status
```
Expected: a block ending with something like `Logged in to github.com as <user> ...`. If not authenticated, stop and tell the user to run `gh auth login` themselves; do not attempt interactive login from the plan.

- [ ] **Step 2: Confirm target org and visibility with the user, then create the remote**

Confirmation prompt (the agent asks the user, verbatim):

> "About to run `gh repo create cleancoders/c3kit-starter --public --source=. --remote=origin --push`. This creates a public repo under the `cleancoders` org and pushes `main`. OK to proceed, or do you want a different org / private visibility?"

Wait for explicit approval. If user redirects, adjust the command (e.g. `--private`, different org).

Then run (default form):
```bash
cd /Users/alex-root-roatch/current-projects/c3kit-starter && \
gh repo create cleancoders/c3kit-starter --public --source=. --remote=origin --push
```
Expected: lines including `Created repository cleancoders/c3kit-starter on GitHub` and a final `branch 'main' set up to track 'origin/main' from 'origin'.`-style message.

If `gh` errors with `HTTP 403` or similar permission failure, the user lacks repo-create rights in the org. Stop and report to the user.

- [ ] **Step 3: Verify push succeeded**

Run:
```bash
cd /Users/alex-root-roatch/current-projects/c3kit-starter && git log --oneline origin/main
```
Expected: same single commit as Task 9 Step 5.

- [ ] **Step 4: Confirm repo URL with user**

Run:
```bash
gh repo view cleancoders/c3kit-starter --json url --jq .url
```
Expected: `https://github.com/cleancoders/c3kit-starter`. Print this URL back to the user so they can open it.

---

## Success Criteria

- `/Users/alex-root-roatch/current-projects/c3kit-starter` exists with the file layout described under **File Structure**.
- `git log --oneline` shows exactly one commit: `chore: bootstrap repo skeleton`.
- Remote `origin` points at `https://github.com/cleancoders/c3kit-starter.git`, `main` is pushed.
- `~/Desktop/2026-05-12-c3kit-starter-roadmap-design.md` and `~/Desktop/2026-05-12-c3kit-starter-bootstrap-plan.md` no longer exist (moved into the repo).
- The placeholder CI workflow shows up as a passing run on the GitHub Actions tab.

## Next Steps (out of scope for this plan)

1. Brainstorm the `c3kit-create` CLI sub-spec → `docs/specs/<date>-c3kit-create-cli-design.md`.
2. Brainstorm the `templates/full-stack-reagent` sub-spec.
3. Brainstorm the `templates/fe-vanilla` sub-spec.

Each gets its own implementation plan and lands as separate commits / PRs.
