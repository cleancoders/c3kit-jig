(ns acme.styles.elements.tables
  (:require [acme.styles.core :refer [border-radius light-grey px size-0 size-minus-1]]))

(def screen
  (list

   [:table {:border [[(px 1) "solid" light-grey]]
            :border-radius border-radius
            :padding size-0
            :width "100%"}]

   [:th :td {:padding size-minus-1
             :text-align "left"}]))
