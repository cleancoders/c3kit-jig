(ns acme.recover-password-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-have-invoked-ajax-post]]
                   [speclj.core :refer [before describe it should should-not with-stubs]])
  (:require [acme.core :as core]
            [acme.layout :as layout]
            [acme.page :as page]
            [acme.recover-password :as sut]
            [c3kit.wire.spec-helper :as wire]))

(describe "Recover Password"
  (with-stubs)
  (wire/stub-ajax)
  (wire/with-root-dom)
  (before (page/clear!)
          (page/install-page! :recover-password)
          (reset! sut/recovery-token "recovery-token")
          (wire/render [layout/default]))

  (it "is disabled when either password is blank or passwords are not equal"
    (should (wire/disabled? "#-recover"))

    (wire/change! "#-password-1" "blah")
    (should (wire/disabled? "#-recover"))

    (wire/change! "#-password-1" "\r\n\t ")
    (wire/change! "#-password-2" "\r\n\t ")
    (should (wire/disabled? "#-recover"))

    (wire/change! "#-password-2" "blah")
    (should (wire/disabled? "#-recover"))

    (wire/change! "#-password-1" "bah")
    (should (wire/disabled? "#-recover"))

    (wire/change! "#-password-1" "blah")
    (should-not (wire/disabled? "#-recover")))

  (it "resets a password with the recovery token"
    (wire/change! "#-password-1" "blah")
    (wire/change! "#-password-2" "blah")
    (wire/click! "#-recover")
    (should-have-invoked-ajax-post "/ajax/recover-password" {:recovery-token "recovery-token" :password "blah"} core/go-home!))
  )
