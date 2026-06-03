# Starter homepage + dedicated `/login` page

**Date:** 2026-06-03
**Template:** `full-stack-reagent`

## Problem

A freshly-scaffolded project has a poor first-run homepage:

- **Auth on:** `/` (the `:home` page) renders the signin/signup form inline
  when logged out, and a "Welcome, <name>" + Test Spinner stub when logged
  in. The login form *is* the homepage.
- **Auth off:** `/` is a near-blank "Welcome to Acme" + Test Spinner under a
  navbar with the Clean Coders logo.

Neither orients a developer who just built the project. We want a
Vite-style "your app is running" starter homepage, and — for auth builds —
to move login onto its own `/login` page instead of squatting on `/`.

## Goals

1. A Vite-style starter homepage shown at `/` for **both** auth and no-auth
   builds. It ignores auth state (identical whether or not a user is logged
   in).
2. For auth builds, login (signin/signup) lives on a dedicated `/login`
   page, following the existing separate-auth-page pattern
   (`acme.auth.forgot-password`, `acme.auth.recover-password`).

## Non-goals (scope guards)

- No redesign of the navbar or the CSS system.
- No change to the signin/signup API, AJAX endpoints, or the forms library.
- No logged-in dashboard or redirect: signed-in users see the same starter
  page at `/`.
- No client-side status panel (db/env/api-version) — that was considered and
  cut (YAGNI).

## Design

### 1. Home page — `src/cljs/acme/home.cljs` (both builds)

`(defmethod page/render :home …)` becomes a Vite-style starter card:

- A heading: `"<Project> is running 🎉"` (literal `Acme` token in the
  template; renamed at scaffold time).
- One paragraph: the app is live; full-stack Clojure + ClojureScript.
- A "next steps" card with links:
  - the c3kit-jig wiki,
  - adding a page (`/clojure:creating-pages` workflow / README),
  - seeding dev data (`clj -M:test:seed`),
  - the project README.
- The **Test Spinner** button is kept (demonstrates the AJAX pipeline:
  `GET /ajax/spinner`).
- **Auth builds only** (marker-gated): a `Sign in →` link to `/login` on the
  card, so `/login` is discoverable.

Remove from `home.cljs`: the `if @user/current` branch, `signin-form`,
`signup-form`, `show-signup?`, `non-blank?`, `welcome`, `auth-forms`, and
`handle-login-success`. After this, `home.cljs` no longer requires the auth
/ forms namespaces. The only auth-gated difference in `home.cljs` is the
single `Sign in →` link.

Keep `(defmethod page/prerender? :home [_] true)`.

### 2. Login page — `src/cljs/acme/auth/login.cljs` (auth only)

New namespace holding what moved out of `home.cljs`: `signin-form`,
`signup-form`, `show-signup?`, `non-blank?`, and `handle-login-success`.

- `(defmethod page/render :login …)` renders the signin/signup toggle (the
  body that `:home` previously showed to logged-out users).
- `handle-login-success` is unchanged: `install-and-connect!` the returned
  user, then `goto!` the server-provided `:destination` (which lands on `/`
  = the starter page).
- Register the namespace in `main.cljs` requires under the `:auth` marker
  (side-effecting `defmethod page/render :login`).

### 3. Routing

- **cljs** (`src/cljs/acme/routes.cljs`): add, under the `:auth` marker,
  `(defroute "/login" [] (load-page! :login))`.
- **clj** (`src/clj/acme/routes.clj`): add `["/login" :get]
  acme.layouts/web-rich-client` to the `:auth` block of
  `web-routes-handlers` (mirrors the existing `/forgot-password` entry).
- No prerender for `/login` (mirrors `/forgot-password`; it's a form).

### 4. Styling

Reuse existing utility classes (`container`, `width-300`, margin/spacing
utilities). Add a small `.starter-card` style section under
`dev/acme/styles/` **only if** the existing utilities are insufficient for a
tidy card. No broader CSS work.

### 5. Tests (TDD — write failing first)

- **`spec/cljs/acme/home_spec.cljs`**: assert the starter content renders
  (heading, next-steps links, Test Spinner button); assert the signin form
  is **absent**. Update/replace the existing auth-on home assertions that
  expect a signin form on `/`.
- **`spec/cljs/acme/auth/login_spec.cljs`** (new, auth only): `:login`
  renders the signin form; the Sign Up toggle switches to the signup form.
- **`spec/cljs/acme/routes_spec.cljs`**: `/login` resolves to the `:login`
  page (auth).
- **clj `routes_spec`**: a `GET /login` web route exists (auth).
- Verify both `--feature auth=true` and `auth=false` scaffolds compile cljs
  and pass specs.

## Files touched

- `src/cljs/acme/home.cljs` — rewrite render; drop auth/forms.
- `src/cljs/acme/auth/login.cljs` — new (auth).
- `src/cljs/acme/main.cljs` — require `acme.auth.login` (auth marker).
- `src/cljs/acme/routes.cljs` — `/login` defroute (auth marker).
- `src/clj/acme/routes.clj` — `/login` web route (auth marker).
- `spec/cljs/acme/home_spec.cljs` — update.
- `spec/cljs/acme/auth/login_spec.cljs` — new (auth).
- `spec/cljs/acme/routes_spec.cljs` — `/login` (auth).
- clj `routes_spec` — `/login` (auth).
- Possibly `dev/acme/styles/…` — minimal `.starter-card` style.

All auth-only additions are gated with `;; @c3kit/feature :auth` markers so
no-auth scaffolds stay clean (no `/login`, no login ns, no Sign-in link).
