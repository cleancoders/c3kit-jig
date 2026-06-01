# Migrations

Database migration scripts live here, named `YYYYMMDD_description.clj`
(e.g. `20260601_add_user_token_version.clj`).

Each migration namespace defines `up` and (optionally) `down`:

```clojure
(ns acme.migrations.20260601_add_user_token_version
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.migrator :as m]))

(defn up []
  (m/add-attribute! :user :token-version {:type :long}))

(defn down []
  (m/remove-attribute! :user :token-version))
```

Run pending migrations with:

    clj -M:test:migrate

Migrations also run automatically at server boot via `c3kit.bucket.migration/service`
(wired in `acme.main/all-services`).
