### 0.1.1
 * Fix: the installed CLI crashed on launch — `bb uberscript` inlined `cljfmt.core`, whose load-time `read-resource` macro fails when its resource isn't bundled. cljfmt is now resolved lazily (`requiring-resolve`) so it loads from its jar at runtime instead of being baked into the uberscript.

### 0.1.0
 * Initial CLI: `c3kit-jig create`, `list`, `upgrade`, `version`.
 * Installer script (`cli/install.sh`) — detects Babashka and git, installs Babashka if missing.
 * `templates/full-stack-reagent` scaffolded; phase-1 template work in progress.
 * Initial OSS scaffolding: CODE_OF_CONDUCT, SECURITY, CHANGES, issue and PR templates, require-linked-issue workflow.
 * Up-front update check: `create` and `list` surface a newer release (cached daily; `C3KIT_NO_UPDATE_CHECK` opts out).
