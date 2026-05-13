(ns acme.user.ajax
  (:require [acme.destination :as destination]
            [acme.user.core :as user]
            [acme.user.web :as web]
            [c3kit.apron.legend :as legend]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.jwt :as jwt]
            [clojure.string]))

(defn- user-response [request user]
  (-> {:user (legend/present! user)}
      (ajax/ok "Welcome to Acme")
      (web/signin-user request user)))

(defn ajax-signin [request]
  (let [result (user/attempt-signin (:params request))]
    (if (:errors result)
      (ajax/fail result "Invalid username or password")
      (user-response request result))))

(defn ajax-signup [request]
  (let [result (user/attempt-signup (:params request))]
    (if (:errors result)
      (ajax/fail result "Unable to create account")
      (user-response request result))))

(defn ajax-forgot-password [request]
  (let [result (user/attempt-forgot-password (:params request))]
    (if (:errors result)
      (ajax/fail nil "Missing email address.")
      (ajax/ok nil "Please check your email to recover your account."))))

(defn ajax-reset-password [request]
  (let [params (:params request)
        result (user/attempt-password-reset params)]
    (if-let [errors (:errors result)]
      (cond
        (and (:password errors) (clojure.string/blank? (:password params)))
        (ajax/fail nil "Missing password")
        (:recovery-token errors)
        (ajax/fail nil "Missing recovery token")
        :else (ajax/fail nil "Validation error"))
      (if result
        (user-response request result)
        (ajax/ok nil)))))

(defn redirect-with-warning [request url message]
  (-> (ajax/redirect url message)
      (destination/preserve request)))

(defn valid-user? [request]
  (boolean (some-> request :user deref :id)))

(def please-signin "Please sign in to proceed with your request.")

(defmacro ensure-user [request & body]
  `(if (valid-user? ~request)
     (do ~@body)
     (redirect-with-warning ~request "/" please-signin)))

(defn ajax-csrf-token [request]
  (let [{:keys [client-id]} (:jwt/payload request)]
    (-> {:ws-csrf-token      client-id
         :anti-forgery-token client-id}
        ajax/ok
        (jwt/copy-payload request))))
