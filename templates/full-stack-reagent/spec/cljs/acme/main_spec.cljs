(ns acme.main-spec
  (:require-macros [c3kit.apron.log :refer [capture-logs]]
                   [speclj.core :refer [around before context describe it should-have-invoked should-not-have-invoked should= stub with-stubs]])
  (:require [accountant.core :as accountant]
            [acme.config :as config]
            [acme.main :as sut]
            [acme.page :as page]
            [acme.routes :as router]
            [acme.user :as user]
            [c3kit.apron.log :as log]
            [c3kit.apron.utilc :as util]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.flashc :as flashc]
            [c3kit.wire.spec-helper :as wire-helper]
            [reagent.dom :as dom]))

(describe "Main"
  (with-stubs)
  (wire-helper/stub-ajax)
  (wire-helper/stub-ws)
  (before (page/clear!))

  (it "dispatches current page"
    (with-redefs [accountant/dispatch-current! (stub :dispatch-current!)
                  router/app-routes (stub :app-routes)
                  dom/render (stub :render)]
      (sut/dispatch-and-render)
      (should-have-invoked :dispatch-current!)))

  (context "main"

    (around [it]
      (with-redefs [sut/dispatch-and-render (stub :dispatch-and-render)
                    log/all! (stub :all!)]
        (log/capture-logs
          (it))))

    (it "installs user"
      (let [user {:kind :user :id 123}]
        (sut/main (util/->transit {:user user}))
        (should= user @user/current)))

    (it "installs flash"
      (let [flash (flashc/warn "Hello")]
        (sut/main (util/->transit {:flash [flash]}))
        (should= "Hello" (flash/last-msg))))

    (it "installs config"
      (sut/main (util/->transit {:config {:environment "blah"}}))
      (should= "blah" @config/environment))
    )
  )
