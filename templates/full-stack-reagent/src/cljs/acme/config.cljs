(ns acme.config
  (:require [reagent.core :as reagent]))

(def state (reagent/atom {}))
(def bucket {:impl :re-memory})

(defn install! [new-config]
  (when new-config
    (assert (map? new-config) "Config must come as a map")
    (swap! state merge new-config)))

(def environment (reagent/track #(:environment @state)))
;; @c3kit/feature :websocket {
(def ws-csrf-token (reagent/track #(:ws-csrf-token @state)))
;; @c3kit/feature :websocket }
(def development? (reagent/track #(= "development" @environment)))
(def production? (reagent/track #(= "production" @environment)))
