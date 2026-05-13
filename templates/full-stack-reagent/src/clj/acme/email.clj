(ns acme.email
  (:require [acme.config :as config]
            [c3kit.apron.log :as log]))

;; OSS template ships only :to-log. To send real email (e.g. via AWS SES),
;; add com.amazonaws/aws-java-sdk-ses to deps.edn and define a (defmethod
;; client-send-email :ses [_ email] …).

(defmulti client-send-email (fn [conf _] (:client conf)))

(defn ->email-list [field]
  (cond (nil? field) []
        (string? field) [field]
        (sequential? field) (filter string? field)
        :else (throw (ex-info "Unknown email address type" {:address field}))))

(defmethod client-send-email :to-log [_ {:keys [to cc bcc from subject text html]}]
  (let [divider (apply str (repeat 80 "="))]
    (log/report (str divider "\n"
                     "[To]      " to "\n"
                     "[Cc]      " cc "\n"
                     "[Bcc]     " bcc "\n"
                     "[From]    " from "\n"
                     "[Subject] " subject "\n"
                     "\nText Body:\n"
                     text
                     "\n\nHTML Body:\n"
                     html
                     divider))))

(defn send-email [email]
  (client-send-email (:email config/env) email))
