# Migration Specs

Speclj specs for migrations live here, named `YYYYMMDD_description_spec.clj`
and tagged `:migration` so they can be included/excluded from the default test run.

```clojure
(ns acme.migration-specs.20260601-add-user-token-version-spec
  (:require [acme.migrations.20260601-add-user-token-version :as sut]
            [speclj.core :refer :all]))

(describe "20260601 add user token-version"
  (tags :migration)
  ;; ...
  )
```

Filter the default spec run with `-t=~migration` and run the migration suite
on demand with `-t=migration`.
