(ns acme.user
  (:require [c3kit.bucket.api :as db]))

(def id (comp :user-id :jwt/payload))
(defn current [request] (db/entity (id request)))
