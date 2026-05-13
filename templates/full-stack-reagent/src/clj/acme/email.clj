(ns acme.email
  (:require [acme.config :as config]
            [acme.markdown :as markdown]
            [c3kit.apron.log :as log])
  (:import (com.amazonaws.regions Regions)
           (com.amazonaws.services.simpleemail AmazonSimpleEmailServiceClient AmazonSimpleEmailServiceClientBuilder)
           (com.amazonaws.services.simpleemail.model Body Content Destination Message SendEmailRequest)))

(def ses-client
  (delay
    (.build
      (doto (AmazonSimpleEmailServiceClientBuilder/standard)
        (.withRegion Regions/US_WEST_2)))))

(defmulti client-send-email (fn [conf _] (:client conf)))

(defn ->email-list [field]
  (cond (nil? field) []
        (string? field) [field]
        (sequential? field) (filter string? field)
        :else (throw (ex-info "Unknown email address type" {:address field}))))

(defn ses-destination [to cc bcc]
  (doto (Destination. (->email-list to))
    (.setCcAddresses (->email-list cc))
    (.setBccAddresses (->email-list bcc))))

(defn ses-message [subject text html]
  (let [text (or text "Please see the HTML content of this email.")
        html (or html (markdown/->html text))
        body (.withHtml (Body. (Content. text)) (Content. html))]
    (Message. (Content. subject) body)))

(defmethod client-send-email :ses [_ {:keys [from to cc bcc subject text html]}]
  (log/info "SES: sending email to:" to "-" subject)
  (try
    (let [dest                                   (ses-destination to cc bcc)
          message                                (ses-message subject text html)
          ^AmazonSimpleEmailServiceClient client @ses-client
          request                                (SendEmailRequest. from dest message)]
      (.sendEmail client request)
      (log/debug "SES email sent"))
    (catch Exception e
      (log/error "Email error:")
      (log/error e))))

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
