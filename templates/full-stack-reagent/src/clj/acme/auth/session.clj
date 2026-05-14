(ns acme.auth.session
  (:require [ring.middleware.session :as ring-session]
            [ring.middleware.session.cookie :refer [cookie-store]]))

(def cookie-name "acme-session")
(def session-encryption-key (byte-array 16 (.getBytes "c3kit-session-k!")))
(def config {:store        (cookie-store {:key session-encryption-key})
             :cookie-name  cookie-name
             :cookie-attrs {:http-only true :secure true}})

(defn wrap-session [handler] (ring-session/wrap-session handler config))


(defn copy [response request] (assoc response :session (:session request)))

(defn add [response request key value]
  (let [session (or (:session response) (:session request))]
    (assoc response :session (assoc session key value))))
