(ns acme.forgot-password-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-have-invoked-ajax-post]]
                   [speclj.core :refer [before describe it should-not-have-invoked should= with-stubs]])
  (:require [acme.forgot-password]
            [acme.layout :as layout]
            [acme.page :as page]
            [c3kit.apron.corec :as ccc]
            [c3kit.bucket.api :as db]
            [c3kit.wire.spec-helper :as wire-helper]
            [acme.test-data :as test-data]))

(describe "Forgot Password"
  (with-stubs)
  (wire-helper/stub-ajax)
  (wire-helper/with-root-dom)
  (test-data/with-memory-kinds :user)
  (before (db/clear)
          (page/clear!)
          (page/install-page! :forgot-password)
          (wire-helper/render [layout/default]))

  (it "is disabled when no username is entered"
    (wire-helper/click! "#-send")
    (should-not-have-invoked :ajax/get!))

  (it "sends an email to recover password"
    (wire-helper/change! "#-email" "coyote@example.com")
    (should= "coyote@example.com" (wire-helper/value "#-email"))
    (wire-helper/click! "#-send")
    (should-have-invoked-ajax-post "/ajax/forgot-password" {:email "coyote@example.com"} ccc/noop))
  )

