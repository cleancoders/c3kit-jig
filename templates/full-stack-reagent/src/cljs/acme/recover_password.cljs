(ns acme.recover-password
  (:require [acme.core :as core]
            [acme.page :as page]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.js :as wjs]
            [clojure.string :as str]
            [reagent.core :as reagent]))

(def recovery-token (page/cursor [:recover-password]))

(defn- recover-password! [password]
  (ajax/post! "/ajax/recover-password"
              {:recovery-token @recovery-token
               :password       password}
              core/go-home!))

(defn password-field [{:keys [id label placeholder ratom]}]
  [:fieldset.small-margin-bottom
   [:label label]
   [:input {:id            id
            :type          "password"
            :placeholder   placeholder
            :auto-complete "password"
            :on-change     #(reset! ratom (wjs/e-text %))}]])

(defn form-component []
  (let [password-1 (reagent/atom "")
        password-2 (reagent/atom "")]
    (fn []
      [:div.container.width-300.margin-top-plus-3
       [:form
        [:h2 "Recover Password"]
        (password-field {:id          "-password-1"
                         :label       "Password"
                         :placeholder "Password"
                         :ratom       password-1})
        (password-field {:id          "-password-2"
                         :label       "Verify Password"
                         :placeholder "Verify Password"
                         :ratom       password-2})
        [:fieldset
         [:button#-recover.primary
          {:disabled (or (str/blank? @password-1) (not= @password-1 @password-2))
           :on-click (wjs/nod-n-do recover-password! @password-1)}
          "Reset Password"]]]])))

(defmethod page/render :recover-password [_] [form-component])
