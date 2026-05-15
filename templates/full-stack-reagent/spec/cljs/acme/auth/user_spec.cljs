(ns acme.auth.user-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-have-invoked-ws should-not-select should-select]]
                   [speclj.core :refer [before context describe it should should-have-invoked should= stub with-stubs]])
  (:require [acme.core :as cc]
            [acme.layout :as layout]
            [acme.page :as page]
            [acme.spec-helper]
            [acme.test-data :as test-data]
            [acme.auth.user :as sut]
            [c3kit.wire.spec-helper :as wire-helper]
            ;; @c3kit/feature :websocket = [c3kit.wire.websocket :as ws]
            ))

(describe "User"
  (with-stubs)
  (wire-helper/stub-ajax)
  (wire-helper/stub-ws)
  (wire-helper/with-root-dom)
  (test-data/with-memory-kinds :user)
  (before (page/clear!))

  ;; @c3kit/feature :websocket {
  (it "install-and-connect!"
    (with-redefs [ws/start! (stub :ws/start!)]
      (sut/install-and-connect! @test-data/road-runner)
      (should= @test-data/road-runner @sut/current)
      (should-have-invoked :ws/start!)
      (should-have-invoked-ws :user/fetch-data nil sut/data-fetched!)))

  (it "fetch-data recorded"
    (with-redefs [ws/start! (stub :ws/start!)]
      (sut/install-and-connect! @test-data/road-runner)
      (should-have-invoked-ws :user/fetch-data nil sut/data-fetched!)
      (sut/data-fetched! [])
      (should @sut/data-fetched?)))
  ;; @c3kit/feature :websocket }

  ;; @c3kit/feature !:websocket {
  (it "install-and-connect! marks data-fetched? immediately"
    (sut/install-and-connect! @test-data/road-runner)
    (should= @test-data/road-runner @sut/current)
    (should @sut/data-fetched?))
  ;; @c3kit/feature !:websocket }

  (context "menu"

    (before (sut/install! @test-data/road-runner)
            (page/install-page! :helper/blank)
            (wire-helper/render [layout/default]))

    (it "no user => no menu"
      (sut/install! nil)
      (wire-helper/flush)
      (should-not-select "#-user-menu"))

    (it "structure"
      (should-select "#-user-menu"))

    (it "logout menu item"
      (with-redefs [cc/goto! (stub :core/goto!)]
        (wire-helper/click! "#-sign-out-menu-item")
        (should-have-invoked :core/goto! {:with ["/signout"]})))
    )
  )
