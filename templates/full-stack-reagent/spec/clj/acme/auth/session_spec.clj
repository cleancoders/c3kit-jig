(ns acme.auth.session-spec
  (:require [acme.auth.session :as sut]
            [speclj.core :refer [describe it should=]])
  (:import (ring.middleware.session.cookie CookieStore)))

(describe "Session"

  (it "session-config"
    (should= "acme-session" (:cookie-name sut/config))
    (should= {:http-only true :secure true} (:cookie-attrs sut/config))
    (should= CookieStore (class (:store sut/config)))
    (should= "c3kit-session-k!" (-> (:store sut/config) .-secret-key String.))))
