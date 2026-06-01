(ns acme.spec-helper
  (:require [acme.email :as email]
            [acme.init :as init]
            ;; @c3kit/feature :auth {
            [acme.auth.user.core :as user]
            [acme.auth.user.web :as user.web]
            [c3kit.bucket.api :as db]
            [c3kit.wire.spec-helper :as wire-helper]
            ;; @c3kit/feature :auth }
            [c3kit.wire.routes :as wire.routes]
            [speclj.core :refer [around it stub]]
            [speclj.stub :as stub]
            [taoensso.timbre :as timbre]))

(timbre/merge-config! {:appenders {:println {:enabled? false}}}) ; keep spec output clean (no timestamped/level log lines)
(init/install-legend!)
(init/configure-api!)
(wire.routes/init! {:reload? true}) ;this solves a problem of route specs failing on re-run when using autorunner
;; @c3kit/feature :content {
(require '[acme.content.core])
(acme.content.core/load!)
;; @c3kit/feature :content }

(defn stub-email []
  (around [it]
    (with-redefs [email/send-email (stub :send-email)]
      (it))))

(defn last-email [] (first (stub/last-invocation-of :send-email)))

;; @c3kit/feature :auth {
(defn speedy-hash [pw] (str "*hash*" pw "*hash*"))

(defn with-fast-password-hash []
  (around [it]
    (with-redefs [user/hash-password  speedy-hash
                  user/check-password (fn [pw hash] (= (speedy-hash pw) hash))]
      (it))))
;; @c3kit/feature :auth }

;(defmacro test-web-handler-requires-lemon-user [handler]
;  `(it (str (name '~handler) " requires lemon user")
;     (let [response# (~handler {})]
;       (wire-helper/should-redirect-to response# "/login")
;       (should= "Please log in to proceed with your request." (flash/first-msg-text response#))
;       (should= :warn (flash/first-msg-level response#)))))
;
;; @c3kit/feature :auth {
(defmacro test-ajax-handler-requires-user [handler]
  `(it (str (name '~handler) " requires user")
     (let [response# (~handler {})]
       (wire-helper/should-ajax-redirect-to response# "/" "Please sign in to proceed."))))

(defmacro test-ajax-handler-requires-admin [handler]
  `(it (str (name '~handler) " requires admin")
     (let [response# (~handler {})]
       (wire-helper/should-ajax-redirect-to response# "/" "Please sign in to proceed."))
     (let [user#     (db/tx :kind :user :email "joe@example.com")
           response# (~handler (user.web/authorize-user {} user#))]
       (wire-helper/should-ajax-redirect-to response# "/" "Access is restricted to admins."))))
;; @c3kit/feature :auth }
