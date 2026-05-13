(ns acme.spec-helper
  (:require [c3kit.bucket.api :as db]
            [acme.user.core :as user]
            [acme.user.web :as user.web]
            [acme.email :as email]
            [acme.init :as init]
            [c3kit.apron.log :as log]
            [c3kit.wire.routes :as wire.routes]
            [c3kit.wire.spec-helper :as wire-helper]
            [speclj.core :refer :all]
            [speclj.stub :as stub]))

(log/warn!)
(init/install-legend!)
(init/configure-api!)
(wire.routes/init! {:reload? true}) ;this solves a problem of route specs failing on re-run when using autorunner
(require '[acme.content])
(acme.content/load!)

(defn stub-email []
  (around [it]
    (with-redefs [email/send-email (stub :send-email)]
      (it))))

(defn last-email [] (first (stub/last-invocation-of :send-email)))

(defn speedy-hash [pw] (str "*hash*" pw "*hash*"))

(defn with-fast-password-hash []
  (around [it]
    (with-redefs [user/hash-password  speedy-hash
                  user/check-password (fn [pw hash] (= (speedy-hash pw) hash))]
      (it))))

;(defmacro test-web-handler-requires-lemon-user [handler]
;  `(it (str (name '~handler) " requires lemon user")
;     (let [response# (~handler {})]
;       (wire-helper/should-redirect-to response# "/login")
;       (should= "Please log in to proceed with your request." (flash/first-msg-text response#))
;       (should= :warn (flash/first-msg-level response#)))))
;
(defmacro test-ajax-handler-requires-user [handler]
  `(it (str (name '~handler) " requires user")
     (let [response# (~handler {})]
       (wire-helper/should-ajax-redirect-to response# "/" "Please sign in to proceed."))))

(defmacro test-ajax-handler-requires-admin [handler]
  `(it (str (name '~handler) " requires admin")
     (let [response# (~handler {})]
       (wire-helper/should-ajax-redirect-to response# "/" "Please sign in to proceed."))
     (let [user#     (db/tx :kind :user :email "joe@acme.com")
           response# (~handler (user.web/authorize-user {} user#))]
       (wire-helper/should-ajax-redirect-to response# "/" "Access is restricted to admins."))))
