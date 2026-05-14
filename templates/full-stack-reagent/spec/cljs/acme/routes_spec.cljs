(ns acme.routes-spec
  (:require-macros [acme.spec-helperc :refer [it-routes]]
                   [speclj.core :refer [around before context describe it redefs-around should-be-nil should-have-invoked should= stub with-redefs with-stubs]])
  (:require [acme.core :as core]
            [acme.page :as page]
            ;; @c3kit/feature :auth {
            [acme.recover-password :as recover-password]
            ;; @c3kit/feature :auth }
            [acme.routes :as sut]
            [c3kit.wire.js :as wjs]
            [reagent.core :as r]
            [secretary.core :as secretary]
            [speclj.core]))

(describe "Routes"
  (with-stubs)
  (before (page/clear!)
          (secretary/reset-routes!)
          (sut/app-routes))

  (context "dispatch!"
    (around [it] (with-redefs [sut/load-page! (stub :load-page!)] (it)))

    (it-routes "/" :home)
    ;; @c3kit/feature :auth {
    (it-routes "/forgot-password" :forgot-password)
    (it-routes "/recover-password/blah" :recover-password
      (should= "blah" @recover-password/recovery-token))
    ;; @c3kit/feature :auth }

    (it "stores hash fragment in thing-to-scroll-to"
      (reset! core/thing-to-scroll-to nil)
      (sut/dispatch! "/#SOMETHING")
      (should= "SOMETHING" @core/thing-to-scroll-to))

    (it "clears thing-to-scroll-to when URL has no hash"
      (reset! core/thing-to-scroll-to "stale")
      (sut/dispatch! "/")
      (should-be-nil @core/thing-to-scroll-to))

    (it-routes "/#TARGET" :home (should= "TARGET" @core/thing-to-scroll-to))

    (context "sandbox"
      (before (sut/sandbox-routes))

      (it-routes "/sandbox/example-toy" :sandbox/example-toy)
      )
    )

  (context "load-page!"
    (redefs-around [page/transition (stub :transition)
                    page/install-page! (stub :install-page!)
                    wjs/scroll-to-top (stub :scroll-to-top)
                    wjs/page-title= (stub :page-title=)
                    core/scroll-to-thing-in-url (stub :scroll-to-thing-in-url)
                    r/after-render (fn [f] (f))])

    (it "scrolls to thing in url after render"
      (sut/load-page! :home)
      (should-have-invoked :scroll-to-thing-in-url)))
  )
