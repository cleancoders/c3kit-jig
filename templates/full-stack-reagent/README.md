# Clean Coders Starter App

A full-stack Clojure/ClojureScript starter template for new projects.

## Getting Started

After creating your project from this template, run the setup script to personalize it:

    bin/setup <project-name>

For example:

    bin/setup my-cool-app

This will:
- Rename all `acme` namespaces, directories, and references to your project name
- Generate unique JWT secrets for each environment
- Reset this README for your new project

### Setup
#### System Requirements

    # Java 1.17

    # Clojure command line
    brew install clojure

CSS and Javascript need to be compiled:

    # compile just the css once
    clj -M:test:css once

    # compile css whenever style files are changed
    clj -M:test:css auto

    # compile just cljs to javascript once (also runs tests)
    clj -M:test:cljs once

    # compile cljs and run tests when ever a file changes
    clj -M:test:cljs

For production:

    CC_ENV=production clj -M:test:css once
    CC_ENV=production clj -M:test:cljs once

### Database Setup

    # Run the database
    You'll need a Datamic Pro database running locally, like the one in the Clean Coders repo. 

    # Run Migrations
    clj -M:test:migrate

    # Seed Development Database
    clj -M:test:seed

### Adding a new `:kind`

1. Add the schema to `acme.schema/full`
2. Add an implementation of `acme.test-data/-init-kind!` with the `:kind`
3. Add appropriate records in `acme.test-data/deps`

### Running tests

    # clojure specs:
    clj -M:test:spec

    # clojure specs automatically running when fileds are changed:
    clj -M:test:spec -a

    # clojurescript specs
    clj -M:test:cljs once

    # clojurescript specs automatically running when files are changed:
    clj -M:test:cljs auto

    # recompile css & cljs specs automatically when files are changed:
    clj -M:test:dev-

### Development

    # run the server
    clj -M:test:run

    # run the server, specs, and cljs in one process
    clj -M:test:dev

    # start the REPL
    clj -M:repl

## Content Pipeline

Drop a directory under `content/<type>/<permalink>/` containing:

- `meta.edn` — `{:title "..." :description "..." :published? true :published-at "YYYY-MM-DD" :tags [...]}`
- `content.md` — the body
- (optional) images, referenced via standard markdown image syntax

The starter scans `content/` at boot and auto-registers:

- `GET /<type>` — index page (rich-client HTML or markdown depending on `Accept`).
- `GET /<type>/:permalink` — detail page.
- `GET /api/v1/content/<type>/<permalink>` — JSON payload returning `{:meta {...} :body <hiccup>}` for the frontend.

Directory names that collide with reserved routes (`api`, `ajax`, `sandbox`, `signout`, etc.) cause `acme.content/load!` to throw on boot.

### Markdown Negotiation

Content routes and prerendered pages honor `Accept: text/markdown` (and `text/plain` as a fallback). Useful for AI agents that prefer source over rendered HTML.

```
curl -H "Accept: text/markdown" http://localhost:8123/blog/2026-05-12-hello-world
```

For prerendered pages, the markdown file is produced by `turndown` during prerender. If the markdown file isn't present, the handler falls back to the HTML shell.

## Server-Side Rendering (SSR)

Pages can opt into pre-rendering via `(defmethod acme.page/prerender? :my-page [_] true)`. On server boot:

1. `acme.prerender/prerender!` checks for `resources/prerender/prerender.js` and `node`.
2. Builds a transit payload (config + content posts).
3. Shells out to `node resources/prerender/prerender.js <payload-path>`.
4. The Node process renders each opted-in page to a string via `reagent.dom.server/render-to-string` and writes `<key>.html` + `<key>.md` into `resources/prerendered/`.
5. Page route handlers use `acme.layouts/web-prerendered` to serve the cached HTML as `:seo/preview` (replaced by the live CLJS app on boot — "clear-and-render", no hydration).

Setup:

```
npm install
clj -M:test:cljs once    # compiles both the dev bundle and the prerender bundle
clj -M:run               # boot the server; prerender runs after server start
```

The pipeline self-disables when `prerender.js` isn't built or `node` isn't installed — guard logs a warning and the server continues.

## Optional: Content Security Policy

Off by default. Enable per environment in `src/clj/acme/config.clj`:

```clojure
:csp {:enabled? true
      :enforce? false       ;; true emits Content-Security-Policy; false emits ...-Report-Only
      :policy   nil}        ;; nil uses acme.security.csp/default-policy
```

The default policy is conservative (`'self'` + `'unsafe-inline'`/`'unsafe-eval'` for the Closure dev loader). Production deployments should narrow `script-src` to specific hosts. Violation reports POST to `/api/v1/csp-report` and log via `c3kit.apron.log/warn`.

## Optional: Frontend Markdown Rendering

`acme.markdownc` (a CLJC ns) parses markdown to hiccup client-side. Not required by default. To use it in a component:

```clojure
(:require [acme.markdownc :as md])

(defn my-component []
  [:div (md/->hiccup "# Hi from the browser")])
```

Component-syntax shortcodes in markdown bodies are supported via `(md/register-component! :my-tag my-component-fn)` and `(md/resolve-components hiccup-tree)`.
