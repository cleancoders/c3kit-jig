(ns acme.routes-spec
  (:require-macros [acme.spec-helperc :refer [it-routes]]
                   [speclj.core :refer [around before context describe it should= stub with-stubs]])
  (:require [acme.page :as page]
            [acme.recover-password :as recover-password]
            [acme.routes :as sut]
            [secretary.core :as secretary]
            [speclj.core]))

(describe "Routes"
  (with-stubs)
  (before (page/clear!)
          (secretary/reset-routes!)
          (sut/app-routes))

  (around [it] (with-redefs [sut/load-page! (stub :load-page!)] (it)))

  (it-routes "/" :home)
  (it-routes "/forgot-password" :forgot-password)
  (it-routes "/recover-password/blah" :recover-password
    (should= "blah" @recover-password/recovery-token))

  (context "sandbox"
    (before (sut/sandbox-routes))

    (it-routes "/sandbox/example-toy" :sandbox/example-toy)
    )
  )
