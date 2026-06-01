(ns acme.core
  (:require [accountant.core :as accountant]
            [c3kit.wire.js :as wjs]
            [reagent.core :as r]
            [secretary.core :as secretary]))

(def thing-to-scroll-to (r/atom nil))

(defn goto! [path]
  (when path
    (if (secretary/locate-route path)
      (accountant/navigate! path)
      (wjs/redirect! path))))

(defn go-home! [] (goto! "/"))

(defn scroll-to-thing-in-url [thing-atom]
  (when-let [thing (wjs/element-by-id @thing-atom)]
    (wjs/scroll-into-view thing {:behavior "auto"
                                 :block    "center"})))
