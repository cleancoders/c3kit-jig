(ns acme.rest
  (:require [acme.core :as core]
            [c3kit.apron.corec :as ccc]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.flashc :as flashc]
            [c3kit.wire.rest :as rest]))

(def unauthenticated-msg "Please sign in to proceed")
(def unauthorized-msg "You don't have access to this resource")
(def not-found-msg "Resource not found!")
(def server-error-msg "Sorry, we weren't able to complete your request")

(defn flash-handler [msg & [f]]
  (fn [_resp]
    (flash/add! (flashc/error msg))
    (when f (f))))

(def default-handlers
  {401 (flash-handler unauthenticated-msg #(core/goto! "/"))  ; "/" is configurable - apps with signin page can change to "/signin"
   403 (flash-handler unauthorized-msg)
   404 (flash-handler not-found-msg)
   500 (flash-handler server-error-msg)})

(defn configure! []
  (rest/configure!
   {:rest/unwrap-body?        true
    :rest/response-middleware (comp rest/wrap-user-handlers
                                    (partial rest/wrap-response-codes default-handlers)
                                    rest/wrap-success-handler)}))

(defn request! [method url request handler opts]
  (method url (assoc-in request [:headers "accept"] "application/transit+json") handler opts))

(defn get! [url request handler & opts]
  (request! rest/get! url request handler (ccc/->options opts)))

(defn post! [url request handler & opts]
  (request! rest/post! url request handler (ccc/->options opts)))

(defn put! [url request handler & opts]
  (request! rest/put! url request handler (ccc/->options opts)))

(defn delete! [url request handler & opts]
  (request! rest/delete! url request handler (ccc/->options opts)))
