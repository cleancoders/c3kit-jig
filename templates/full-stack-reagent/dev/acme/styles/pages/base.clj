(ns acme.styles.pages.base
  (:require [acme.styles.core :refer [px]]
            [garden.def :refer [defstyles]]))

(defstyles screen

  [:.not-found {:text-align "center"
                :display    "block"}

   [:img {:width (px 400)
          :height (px 400)
          :margin ["ato"]}]])
