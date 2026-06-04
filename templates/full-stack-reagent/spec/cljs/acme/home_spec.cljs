(ns acme.home-spec
  (:require-macros [c3kit.wire.spec-helperc :refer [should-have-invoked-ajax-get should-not-select should-select]]
                   [speclj.core :refer [before describe it with-stubs]])
  (:require [acme.home]
            [acme.layout :as layout]
            [acme.page :as page]
            [acme.spec-helper]
            [c3kit.wire.spec-helper :as wire-helper]))

(describe "Home"
  (with-stubs)
  (wire-helper/stub-ajax)
  (wire-helper/with-root-dom)
  (before (page/clear!)
          (page/install-page! :home)
          (wire-helper/render [layout/default])
          (wire-helper/flush))

  (it "renders the starter page"
    (should-select "#-starter"))

  (it "shows the next-steps list"
    (should-select "#-next-steps"))

  ;; @c3kit/feature :content {
  (it "links to the blog"
    (should-select "#-blog-link"))
  ;; @c3kit/feature :content }

  (it "does not show a signin form"
    (should-not-select "#-signin-button"))

  (it "has a Test Spinner demo button"
    (should-select "#-spinner-button")
    (wire-helper/click! "#-spinner-button")
    (should-have-invoked-ajax-get "/ajax/spinner"))

  ;; @c3kit/feature :auth {
  (it "links to the login page"
    (should-select "#-login-link"))
  ;; @c3kit/feature :auth }
  )
