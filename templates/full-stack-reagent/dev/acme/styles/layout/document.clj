(ns acme.styles.layout.document
  (:refer-clojure :exclude [rem])
  (:require [acme.styles.core :refer [black body-line-height font-family primary px rem white]]))

(def screen
(list

[:* :*:before :*:after {
  :box-sizing "border-box"
}]

["::selection" {
  :background-color primary
  :color white
}]

[:body :html {
  :width "100%"
  :height "100%"
}]

[:html {
  :font-size (px 16)
}]


[:body {
  :background-color white
  :color black
  :font-family (font-family "open-sans" "light")
  :line-height body-line-height
}]


))
