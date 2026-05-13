(ns acme.styles.elements.lists
  (:refer-clojure :exclude [rem])
  (:require [acme.styles.core :refer :all]))

(def screen
(list

[:ol :ul :li {
  :position "relative"
}]

))
