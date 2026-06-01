(ns acme.styles.pages.authentication
  (:require [acme.styles.core :refer [size-plus-2]]))

(def screen
  (list

   [:.authentication
    [:.logo {:margin [[0 "auto" size-plus-2]]
             :max-width "80%"}]]))
