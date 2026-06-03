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
