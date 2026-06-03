# Starter Homepage + Dedicated /login Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give freshly-scaffolded projects a Vite-style starter homepage at `/`, and move signin/signup onto a dedicated `/login` page for auth builds.

**Architecture:** `home.cljs` becomes a shared starter page (both builds, ignores auth state). The signin/signup forms move out of `home.cljs` into a new `acme.auth.login` namespace rendering a `:login` page, wired with cljs + clj routes — following the existing separate-auth-page pattern (`acme.auth.forgot-password`). All auth-only additions are gated with `;; @c3kit/feature :auth` markers; files under `auth/` dirs are auto-deleted for no-auth scaffolds.

**Tech Stack:** Clojure, ClojureScript, Reagent, secretary/accountant routing, compojure (backend), Speclj + c3kit.wire spec-helper (tests). Template uses literal `acme` tokens + feature markers.

**Conventions learned from the codebase:**
- This is a **template tree** that is itself runnable at HEAD (literal `acme`, markers are comments). Run cljs specs from `templates/full-stack-reagent/` with `clj -M:test:cljs once`.
- `;; @c3kit/feature :auth { … }` = block marker: code between is LIVE at HEAD, removed when auth off.
- `;; @c3kit/feature :auth = <code>` = line-toggle: the `<code>` after `= ` is a COMMENT at HEAD, expanded to live code only in auth=true scaffolds.
- Files under any `auth/` directory are auto-deleted for no-auth scaffolds (no marker needed).
- Element ids in tests use a leading `-` (e.g. `#-spinner-button`); selectors come from `c3kit.wire.spec-helper`.

**Working directory for all paths:** `templates/full-stack-reagent/` inside the repo.

---

## File Structure

- `src/cljs/acme/auth/login.cljs` — NEW (auth). Holds signin/signup forms + `page/render :login`. Auto-deleted for no-auth.
- `spec/cljs/acme/auth/login_spec.cljs` — NEW (auth). Tests the login forms. Auto-deleted for no-auth.
- `src/cljs/acme/home.cljs` — REWRITE. Shared Vite-style starter; drops all auth/forms code; keeps Test Spinner; auth-gated `Sign in →` link.
- `spec/cljs/acme/home_spec.cljs` — REWRITE. Shared, auth-safe (no auth requires); auth-gated link test.
- `src/cljs/acme/routes.cljs` — MODIFY. Add `/login` defroute (auth block).
- `spec/cljs/acme/routes_spec.cljs` — MODIFY. Add `/login` route test (auth block).
- `src/clj/acme/routes.clj` — MODIFY. Add `/login` web route (auth block).
- `spec/clj/acme/routes_spec.clj` — MODIFY. Add `/login` web-route test (auth block).
- `src/cljs/acme/main.cljs` — MODIFY. Require `acme.auth.login` (auth line-toggle).
- `c3kit-template.edn` — MODIFY. Remove `home_spec.cljs` from `:auth` extras so it ships for both builds.

No new CSS: reuse existing utility classes (`container`, `width-300`, `text-align-center`, margin utils).

---

## Task 1: Create the `:login` page (auth only)

Move the signin/signup forms out of `home.cljs` into a new `acme.auth.login`
namespace. TDD: write the spec first.

**Files:**
- Create: `spec/cljs/acme/auth/login_spec.cljs`
- Create: `src/cljs/acme/auth/login.cljs`

- [ ] **Step 1: Write the failing spec**

Create `spec/cljs/acme/auth/login_spec.cljs`:

```clojure
(ns acme.auth.login-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-have-invoked-ajax-post]]
                   [speclj.core :refer [before context describe it should should-not should-not-have-invoked should= with-stubs]])
  (:require [acme.auth.login :as sut]
            [acme.layout :as layout]
            [acme.page :as page]
            [acme.test-data :as test-data]
            [acme.auth.user :as user]
            [c3kit.bucket.api :as db]
            [c3kit.wire.spec-helper :as wire-helper]))

(describe "Login"
  (with-stubs)
  (wire-helper/stub-ajax)
  (wire-helper/stub-ws)
  (wire-helper/with-root-dom)
  (test-data/with-memory-kinds :user)
  (before (db/clear)
          (page/clear!)
          (reset! sut/show-signup? false)
          (user/clear!)
          (page/install-page! :login)
          (wire-helper/render [layout/default])
          (wire-helper/flush))

  (context "signin form"
    (it "is disabled when no email or password"
      (should (wire-helper/disabled? "#-signin-button"))
      (wire-helper/click! "#-signin-button")
      (should-not-have-invoked :ajax/post!))

    (it "is disabled when no email"
      (wire-helper/change! "#-password" "password")
      (should= "password" (wire-helper/value "#-password"))
      (should (wire-helper/disabled? "#-signin-button"))
      (wire-helper/click! "#-signin-button")
      (should-not-have-invoked :ajax/post!))

    (it "is disabled when no password"
      (wire-helper/change! "#-email" "test@test.com")
      (should= "test@test.com" (wire-helper/value "#-email"))
      (should (wire-helper/disabled? "#-signin-button"))
      (wire-helper/click! "#-signin-button")
      (should-not-have-invoked :ajax/post!))

    (it "is clickable when email & password"
      (wire-helper/change! "#-email" "test@test.com")
      (wire-helper/change! "#-password" "password")
      (should-not (wire-helper/disabled? "#-signin-button"))
      (wire-helper/click! "#-signin-button")
      (should-have-invoked-ajax-post "/ajax/user/signin" {:email "test@test.com" :password "password"})))

  (context "signup form"
    (before (reset! sut/show-signup? true)
            (wire-helper/flush))

    (it "is disabled when fields are empty"
      (should (wire-helper/disabled? "#-signup-button"))
      (wire-helper/click! "#-signup-button")
      (should-not-have-invoked :ajax/post!))

    (it "is disabled when passwords don't match"
      (wire-helper/change! "#-signup-email" "test@test.com")
      (wire-helper/change! "#-signup-password" "password1")
      (wire-helper/change! "#-signup-confirm" "password2")
      (should (wire-helper/disabled? "#-signup-button")))

    (it "is clickable when all fields valid"
      (wire-helper/change! "#-signup-email" "test@test.com")
      (wire-helper/change! "#-signup-password" "password")
      (wire-helper/change! "#-signup-confirm" "password")
      (should-not (wire-helper/disabled? "#-signup-button"))
      (wire-helper/click! "#-signup-button")
      (should-have-invoked-ajax-post "/ajax/user/signup" {:email "test@test.com" :password "password" :confirm-password "password"}))))
```

- [ ] **Step 2: Run the spec, verify it fails**

Run: `clj -M:test:cljs once`
Expected: FAIL — `acme.auth.login` namespace / `sut/show-signup?` not found (compile error).

- [ ] **Step 3: Create the login namespace**

Create `src/cljs/acme/auth/login.cljs` (forms copied verbatim from the old
`home.cljs` auth block):

```clojure
(ns acme.auth.login
  (:require [acme.core :as cc]
            [acme.forms :as forms]
            [acme.page :as page]
            [acme.auth.user :as user]
            [acme.auth.user.corec :as userc]
            [clojure.string :as str]
            [reagent.core :as reagent]))

(def handle-login-success (juxt (comp user/install-and-connect! :user)
                                (comp cc/goto! :destination)))

(def show-signup? (reagent/atom false))

(defn- non-blank? [state ks] (every? (complement str/blank?) (map state ks)))

(defn signin-form []
  (let [state  (reagent/atom {})
        config (forms/config userc/signin-schema state "/ajax/user/signin" handle-login-success)]
    (fn []
      [:form
       [forms/field-set "Email"
        forms/text-field
        {:id "-email" :placeholder "email" :auto-complete "username"}
        :email config]
       [forms/field-set "Password"
        forms/password-field
        {:id "-password" :placeholder "password" :auto-complete "current-password"}
        :password config]
       [:p [:a {:href "/forgot-password"} "I forgot my password."]]
       [:fieldset
        [forms/submit-button "Sign In" "-signin-button" config
         (non-blank? @state [:email :password])]]
       [:p.margin-top-default.text-align-center
        "Don't have an account? "
        [:a {:href "#" :on-click #(reset! show-signup? true)} "Sign Up"]]])))

(defn signup-form []
  (let [state  (reagent/atom {})
        config (forms/config userc/signup-schema state "/ajax/user/signup" handle-login-success)]
    (fn []
      [:form
       [forms/field-set "Email"
        forms/text-field
        {:id "-signup-email" :type "email" :placeholder "email" :auto-complete "email"}
        :email config]
       [forms/field-set "Password"
        forms/password-field
        {:id "-signup-password" :placeholder "password" :auto-complete "new-password"}
        :password config]
       [forms/field-set "Confirm Password"
        forms/password-field
        {:id "-signup-confirm" :placeholder "confirm password" :auto-complete "new-password"}
        :confirm-password config]
       [:fieldset
        [forms/submit-button "Sign Up" "-signup-button" config
         (and (non-blank? @state [:email :password :confirm-password])
              (= (:password @state) (:confirm-password @state)))]]
       [:p.margin-top-default.text-align-center
        "Already have an account? "
        [:a {:href "#" :on-click #(reset! show-signup? false)} "Sign In"]]])))

(defn auth-forms []
  (if @show-signup?
    [signup-form]
    [signin-form]))

(defmethod page/render :login [_]
  [:main
   [:section.login
    [:div.container.width-300.margin-top-plus-5.margin-bottom-plus-5
     [auth-forms]]]])
```

- [ ] **Step 4: Run the spec, verify it passes**

Run: `clj -M:test:cljs once`
Expected: PASS — the `Login` describe block green (existing specs still green).

- [ ] **Step 5: Commit**

```bash
git add spec/cljs/acme/auth/login_spec.cljs src/cljs/acme/auth/login.cljs
git commit -m "feat(template): add :login page with signin/signup forms (auth)"
```

---

## Task 2: Rewrite `home.cljs` as the shared starter page

Replace the auth/no-auth home renders with a single shared Vite-style
starter. Make `home_spec` auth-safe and ship it for both builds.

**Files:**
- Modify: `src/cljs/acme/home.cljs` (full rewrite)
- Modify: `spec/cljs/acme/home_spec.cljs` (full rewrite)
- Modify: `c3kit-template.edn` (remove home_spec from auth extras)

- [ ] **Step 1: Rewrite the home spec (failing)**

Replace the entire contents of `spec/cljs/acme/home_spec.cljs` with:

```clojure
(ns acme.home-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-have-invoked-ajax-get should-not-select should-select]]
                   [speclj.core :refer [before describe it with-stubs]])
  (:require [acme.home]
            [acme.layout :as layout]
            [acme.page :as page]
            [acme.spec-helper]
            [c3kit.wire.spec-helper :as wire-helper]))

(describe "Home"
  (with-stubs)
  (wire-helper/stub-ajax)
  (wire-helper/with-root-dom)
  (before (page/clear!)
          (page/install-page! :home)
          (wire-helper/render [layout/default])
          (wire-helper/flush))

  (it "renders the starter page"
    (should-select "#-starter"))

  (it "shows the next-steps list"
    (should-select "#-next-steps"))

  (it "does not show a signin form"
    (should-not-select "#-signin-button"))

  (it "has a Test Spinner demo button"
    (should-select "#-spinner-button")
    (wire-helper/click! "#-spinner-button")
    (should-have-invoked-ajax-get "/ajax/spinner"))

  ;; @c3kit/feature :auth {
  (it "links to the login page"
    (should-select "#-login-link"))
  ;; @c3kit/feature :auth }
  )
```

- [ ] **Step 2: Run the spec, verify it fails**

Run: `clj -M:test:cljs once`
Expected: FAIL — old `home.cljs` still renders a signin form / no `#-starter`,
so `should-select "#-starter"` and `should-not-select "#-signin-button"` fail.

- [ ] **Step 3: Rewrite `home.cljs`**

Replace the entire contents of `src/cljs/acme/home.cljs` with:

```clojure
(ns acme.home
  (:require [acme.page :as page]
            [c3kit.apron.corec :as ccc]
            [c3kit.wire.ajax :as ajax]))

(defn next-steps []
  [:ul#-next-steps.starter-links
   [:li [:a {:href "https://github.com/cleancoders/c3kit-jig/wiki" :target "_blank"} "c3kit-jig wiki & guides"]]
   [:li "Add a page — edit " [:code "src/cljs/acme/home.cljs"] " and register a route"]
   [:li "Seed dev data — " [:code "clj -M:test:seed"]]
   [:li "Project docs — see " [:code "README.md"]]])

(defn starter []
  [:main#-starter
   [:section.home
    [:div.container.width-300.margin-top-plus-5.margin-bottom-plus-5.text-align-center
     [:img.logo.margin-bottom-plus-1 {:src "/images/logos/cc-emblem.png"}]
     [:h1 "Acme is running 🎉"]
     [:p "Your full-stack Clojure + ClojureScript app is live."]
     [next-steps]
     ;; @c3kit/feature :auth {
     [:p.margin-top-default [:a#-login-link {:href "/login"} "Sign in →"]]
     ;; @c3kit/feature :auth }
     [:button#-spinner-button.primary
      {:on-click #(ajax/get! "/ajax/spinner" {} ccc/noop)}
      "Test Spinner"]]]])

(defmethod page/render :home [_]
  [starter])

(defmethod page/prerender? :home [_] true)
```

- [ ] **Step 4: Run the spec, verify it passes**

Run: `clj -M:test:cljs once`
Expected: PASS — `Home` describe green (incl. the auth `#-login-link` test,
since auth is live at HEAD). `Login` describe (Task 1) still green.

- [ ] **Step 5: Ship home_spec for both builds**

In `c3kit-template.edn`, find the `:auth` feature `:extras` vector:

```clojure
               {:id      :auth
                :prompt  "JWT auth?"
                :default true
                :extras  ["src/cljc/acme/schema/user.cljc"
                          "spec/cljs/acme/home_spec.cljs"]}
```

Remove the `home_spec.cljs` line so it is no longer auth-deleted:

```clojure
               {:id      :auth
                :prompt  "JWT auth?"
                :default true
                :extras  ["src/cljc/acme/schema/user.cljc"]}
```

- [ ] **Step 6: Commit**

```bash
git add src/cljs/acme/home.cljs spec/cljs/acme/home_spec.cljs c3kit-template.edn
git commit -m "feat(template): replace home with shared Vite-style starter page"
```

---

## Task 3: Wire the `/login` routes

Add the `/login` route on the cljs router, the clj web router, and require
the login namespace in `main.cljs`. All auth-gated.

**Files:**
- Modify: `src/cljs/acme/routes.cljs`
- Modify: `spec/cljs/acme/routes_spec.cljs`
- Modify: `src/clj/acme/routes.clj`
- Modify: `spec/clj/acme/routes_spec.clj`
- Modify: `src/cljs/acme/main.cljs`

- [ ] **Step 1: Add failing cljs route test**

In `spec/cljs/acme/routes_spec.cljs`, inside the auth marker block in the
`dispatch!` context, add the `/login` route assertion after the
`/forgot-password` line:

```clojure
    ;; @c3kit/feature :auth {
    (it-routes "/forgot-password" :forgot-password)
    (it-routes "/login" :login)
    (it-routes "/recover-password/blah" :recover-password
      (should= "blah" @recover-password/recovery-token))
    ;; @c3kit/feature :auth }
```

- [ ] **Step 2: Add failing clj route test**

In `spec/clj/acme/routes_spec.clj`, inside the auth marker block of the web
routes (kept alphabetical), add the `/login` test between `/google/oauth`
and `/recover-password/foo`:

```clojure
  ;; @c3kit/feature :auth {
  (test-route "/apple/oauth" :post acme.auth.user.web/web-apple-oauth-login)
  (test-route "/forgot-password" :get acme.layouts/web-rich-client)
  (test-route "/google/oauth" :post acme.auth.user.web/web-google-oauth-login)
  (test-route "/login" :get acme.layouts/web-rich-client)
  (test-route "/recover-password/foo" :get acme.layouts/web-rich-client)
  ;; @c3kit/feature :auth }
```

- [ ] **Step 3: Run both route specs, verify they fail**

Run: `clj -M:test:cljs once` and `clj -M:test:spec`
Expected: cljs FAIL — `/login` dispatches but no secretary route, so
`load-page!` is not invoked with `:login`. clj FAIL — `routes/handler` does
not invoke `web-rich-client` for `/login` (no such route yet).

- [ ] **Step 4: Add the cljs defroute**

In `src/cljs/acme/routes.cljs`, inside `app-routes`, in the auth marker
block, add the `/login` defroute after `/forgot-password`:

```clojure
  ;; @c3kit/feature :auth {
  (defroute "/forgot-password" [] (load-page! :forgot-password))
  (defroute "/login" [] (load-page! :login))
  (defroute "/recover-password/:recovery-token" [recovery-token]
    (reset! recover-password/recovery-token recovery-token)
    (load-page! :recover-password))
  ;; @c3kit/feature :auth }
```

- [ ] **Step 5: Add the clj web route**

In `src/clj/acme/routes.clj`, in `web-routes-handlers`, inside the auth
marker block, add the `/login` entry after `/google/oauth`:

```clojure
     ;; @c3kit/feature :auth {
     ["/forgot-password" :get]                  acme.layouts/web-rich-client
     ["/google/oauth" :post]                    acme.auth.user.web/web-google-oauth-login
     ["/login" :get]                            acme.layouts/web-rich-client
     ["/apple/oauth" :post]                     acme.auth.user.web/web-apple-oauth-login
     ["/recover-password/:recovery-token" :get] acme.layouts/web-rich-client
     ["/redirect" :get]                         acme.auth.destination/web-redirect
     ["/signout" :any]                          acme.auth.user.web/web-signout
     ["/signout/:reason" :any]                  acme.auth.user.web/web-signout
     ["/user/websocket" :any]                   acme.auth.user.web/websocket-open
     ;; @c3kit/feature :auth }
```

(Insertion point is the new `["/login" :get] … web-rich-client` line; leave
the other entries as they already are.)

- [ ] **Step 6: Require the login namespace in main.cljs**

In `src/cljs/acme/main.cljs`, add a line-toggle require for the login ns
next to the existing forgot-password / recover-password line-toggles:

```clojure
            ;; @c3kit/feature :auth = [acme.auth.forgot-password]
            ;; @c3kit/feature :auth = [acme.auth.login]
            [acme.home]
            [acme.init :as init]
            [acme.layout :as layout]
            ;; @c3kit/feature :auth = [acme.auth.recover-password]
```

(Add only the `[acme.auth.login]` line; the others already exist.)

- [ ] **Step 7: Run all specs, verify they pass**

Run: `clj -M:test:cljs once` and `clj -M:test:spec`
Expected: PASS — both route specs green; all other specs green.

- [ ] **Step 8: Commit**

```bash
git add src/cljs/acme/routes.cljs spec/cljs/acme/routes_spec.cljs \
        src/clj/acme/routes.clj spec/clj/acme/routes_spec.clj \
        src/cljs/acme/main.cljs
git commit -m "feat(template): wire /login route (cljs + clj) and register login ns"
```

---

## Task 4: Verify both auth=true and auth=false scaffolds

Confirm the feature markers produce a clean, compiling project in both
configurations.

**Files:** none (verification only)

- [ ] **Step 1: Build the CLI from source**

Run (from repo root):
```bash
cd cli && bb build && cd ..
```
Expected: writes `cli/dist/c3kit-jig.bb`.

- [ ] **Step 2: Scaffold an auth=true project and run its cljs specs**

Run (from repo root):
```bash
cd /tmp && rm -rf auth-yes && \
bb "$OLDPWD/cli/dist/c3kit-jig.bb" create auth-yes \
  --template-dir "$OLDPWD/templates" --template full-stack-reagent \
  --db memory --feature auth=true --yes --no-git && \
cd auth-yes && clj -M:test:cljs once && clj -M:test:spec
```
Expected: home + login + route specs PASS. `src/cljs/auth_yes/auth/login.cljs`
and `spec/cljs/auth_yes/auth/login_spec.cljs` exist; `/login` link renders.

- [ ] **Step 3: Scaffold an auth=false project and run its cljs specs**

Run (from repo root):
```bash
cd /tmp && rm -rf auth-no && \
bb "$OLDPWD/cli/dist/c3kit-jig.bb" create auth-no \
  --template-dir "$OLDPWD/templates" --template full-stack-reagent \
  --db memory --feature auth=false --yes --no-git && \
cd auth-no && clj -M:test:cljs once && clj -M:test:spec
```
Expected: home + route specs PASS (no login specs). Verify:
```bash
test ! -e /tmp/auth-no/src/cljs/auth_no/auth && \
test ! -e /tmp/auth-no/spec/cljs/auth_no/auth && \
test -e /tmp/auth-no/spec/cljs/auth_no/home_spec.cljs && \
grep -L "login" /tmp/auth-no/src/cljs/auth_no/home.cljs >/dev/null && \
echo "no-auth clean"
```
Expected: prints `no-auth clean` — no `auth/` dirs, `home_spec.cljs` present,
no `/login` link in the no-auth home.

- [ ] **Step 4: Clean up scaffolds**

Run: `rm -rf /tmp/auth-yes /tmp/auth-no`

- [ ] **Step 5: Run the verification harness for the template**

Run (from repo root): `cd verification && bb test && cd ..`
Expected: PASS (the harness unit tests still green; no combo asserts
home_spec presence/absence so combos are unaffected).

---

## Notes for the implementer

- The template tree at HEAD has auth **live** (markers are comments), so `clj
  -M:test:cljs once` from `templates/full-stack-reagent/` exercises the auth
  paths — including the `#-login-link` home test and the whole `Login` spec.
- Do NOT add `home_spec.cljs` back to any deletion list; it is intentionally
  shared now.
- `acme.auth.login` is required in `main.cljs` via a **line-toggle** (commented
  at HEAD, expanded only in auth scaffolds) — matching `acme.auth.forgot-password`.
  The `/login` cljs defroute and clj web route live in **block** markers (live
  at HEAD). This mismatch is intentional and matches the existing forgot-password
  wiring.
- The `goto!`/destination behaviour after signin is unchanged: a successful
  signin lands on `/`, which is now the starter page.
