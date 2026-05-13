(ns acme.version
  (:require [c3kit.wire.restc :as restc]))

(def api-version "1.0.0")

(defn api-get [_request]
  (restc/ok
    {:server-version  api-version
     :client-policies {"1.0.0" {:action :none}
                       "0.x.x" {:action :warn}}})) ; or block
