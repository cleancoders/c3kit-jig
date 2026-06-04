(ns acme.styles.pages.content
  (:require [acme.styles.core :refer [body-line-height medium-grey size-0 size-minus-4 size-plus-1 size-plus-2]]))

(def screen
  (list

   [:.content-list {:margin [[size-plus-2 0]]}

    [:h1 {:margin-bottom size-plus-1}]

    [:ul {:list-style "none"
          :margin     0
          :padding    0}]

    [:li {:margin-bottom size-0}

     [:a {:font-weight "bold"}]

     [:.description {:color medium-grey}]]]

   [:.content-post {:margin [[size-plus-2 0]]}

    [:header {:margin-bottom size-plus-1}

     [:.description {:color        medium-grey
                     :margin-top   size-minus-4
                     :font-style   "italic"}]]

    [:.body {:line-height body-line-height}]]))
