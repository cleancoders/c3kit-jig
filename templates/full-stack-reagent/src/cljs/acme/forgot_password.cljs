(ns acme.forgot-password
  (:require [acme.page :as page]
            [c3kit.apron.corec :as ccc]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.js :as wjs]
            [clojure.string :as str]
            [reagent.core :as reagent]))

(defn forget-password! [email]
  (ajax/post! "/ajax/forgot-password" {:email email} ccc/noop))

(defn component []
  (let [email (reagent/atom "")]
    (fn []
      [:div.container.width-300.margin-top-plus-3
       [:form
        [:h2 "Forgot Password"]
        [:fieldset.small-margin-bottom
         [:label "Email"]
         [:input#-email {:type          "text"
                         :placeholder   "email"
                         :auto-complete "email"
                         :on-change     #(reset! email (wjs/e-text %))}]]
        [:fieldset
         [:button#-send.primary
          {:class    (when (str/blank? @email) "disabled")
           :on-click (wjs/nod-n-do forget-password! @email)}
          "Send Email"]]]])))

(defmethod page/render :forgot-password [_] [component])
