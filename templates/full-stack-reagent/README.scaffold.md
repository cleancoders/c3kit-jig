# Acme

Full-stack Clojure + ClojureScript project scaffolded from the
[`c3kit-starter`](https://github.com/cleancoders/c3kit-starter)
`full-stack-reagent` template.

## System Requirements

- Java 17+
- Clojure CLI (`brew install clojure`)
- (Optional) Node + npm if you opted into SSR

## Database

The template generated a `bin/db` script for your chosen backend.

```sh
bin/db                 # start (or initialize) the database
clj -M:test:migrate    # run migrations
clj -M:test:seed       # seed dev data (auth only)
```

If you chose `:datomic-pro`, the first run of `bin/db` will prompt you
to download the Datomic Pro transactor (~300 MB) to `~/.c3kit/datomic-pro/`.
Datomic Pro is free as of 2023; no `my.datomic.com` credentials needed.

## Compile Assets

```sh
clj -M:test:css once    # CSS once
clj -M:test:css         # CSS auto-watch
clj -M:test:cljs once   # CLJS once
clj -M:test:cljs        # CLJS auto-watch
clj -M:test:cljss       # CSS + CLJS auto-watch (combined)
```

Production:
```sh
CC_ENV=production clj -M:test:css once
CC_ENV=production clj -M:test:cljs once
```

## Run

```sh
clj -M:test:run    # server only
clj -M:test:dev    # server + specs + cljs in one process
clj -M:repl        # REPL
```

## Test

```sh
clj -M:test:spec        # Clojure specs
clj -M:test:spec -a     # Clojure specs (auto-rerun)
clj -M:test:cljs once   # ClojureScript specs
clj -M:test:cljs auto   # ClojureScript specs (auto-rerun)
```

## Production Email

The template ships with `acme.email/client-send-email :to-log` only
(prints emails to the log). To send real email via AWS SES, add to
`deps.edn`:

```clojure
com.amazonaws/aws-java-sdk-ses {:mvn/version "1.12.797"}
```

Then add a `:ses` defmethod to `acme.email`:

```clojure
(defmethod client-send-email :ses [_ email]
  ;; … your AWS SES client wiring …
  )
```

And in `acme.config`, change the production `:email` value to `{:client :ses}`.

## Deployment

This template doesn't prescribe a deployment target. Common options:

- **Standalone JVM** — `clj -X:uberjar` (or your build of choice) and run on
  any host with Java 17+.
- **AWS** — re-add the AWS SDK deps (see "Production Email"), build an uberjar,
  deploy to EC2 or ECS.
- **Container** — write a `Dockerfile` based on `eclipse-temurin:17-jre`.

See the c3kit-starter wiki for deployment recipes contributed by the community.

## Adding a New `:kind`

1. Add the schema to `acme.schema/full`.
2. Add an implementation of `acme.test-data/-init-kind!` with the `:kind`.
3. Add records in `acme.test-data/deps`.

## Optional Features

The wizard offered five toggleable features at scaffold time. To re-enable
a disabled feature, look at the proprietary template at the original
repo for the deleted code; the marker comments document what each
feature touched.

Features:

- **Content pipeline (`:content`)** — drop markdown content under
  `content/<type>/<permalink>/`; routes auto-register.
- **SSR/prerender (`:ssr`)** — `(defmethod acme.page/prerender? :my-page [_] true)`
  opts a page in; Node + `resources/prerender/prerender.js` produce
  HTML + markdown caches.
- **CSP (`:csp`)** — Content Security Policy middleware; toggle per env
  in `acme.config`.
- **Client-side markdown (`:markdownc`)** — `acme.markdownc` parses markdown
  to hiccup in the browser; useful for AI-agent payloads.
- **JWT auth (`:auth`)** — signin/signup/forgot/recover flows + JWT cookie
  middleware + user kind + social-login kind.
