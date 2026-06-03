(ns acme.auth.login
  (:require [acme.core :as cc]
            [acme.forms :as forms]
            [acme.page :as page]
            [acme.auth.user :as user]
            [acme.auth.user.corec :as userc]
            [clojure.string :as str]
            [reagent.core :as reagent]))

(def handle-login-success (juxt (comp user/install-and-connect! :user)
                                (comp cc/goto! :destination)))

(def show-signup? (reagent/atom false))

(defn- non-blank? [state ks] (every? (complement str/blank?) (map state ks)))

(defn signin-form []
  (let [state  (reagent/atom {})
        config (forms/config userc/signin-schema state "/ajax/user/signin" handle-login-success)]
    (fn []
      [:form
       [forms/field-set "Email"
        forms/text-field
        {:id "-email" :placeholder "email" :auto-complete "username"}
        :email config]
       [forms/field-set "Password"
        forms/password-field
        {:id "-password" :placeholder "password" :auto-complete "current-password"}
        :password config]
       [:p [:a {:href "/forgot-password"} "I forgot my password."]]
       [:fieldset
        [forms/submit-button "Sign In" "-signin-button" config
         (non-blank? @state [:email :password])]]
       [:p.margin-top-default.text-align-center
        "Don't have an account? "
        [:a {:href "#" :on-click #(reset! show-signup? true)} "Sign Up"]]])))

(defn signup-form []
  (let [state  (reagent/atom {})
        config (forms/config userc/signup-schema state "/ajax/user/signup" handle-login-success)]
    (fn []
      [:form
       [forms/field-set "Email"
        forms/text-field
        {:id "-signup-email" :type "email" :placeholder "email" :auto-complete "email"}
        :email config]
       [forms/field-set "Password"
        forms/password-field
        {:id "-signup-password" :placeholder "password" :auto-complete "new-password"}
        :password config]
       [forms/field-set "Confirm Password"
        forms/password-field
        {:id "-signup-confirm" :placeholder "confirm password" :auto-complete "new-password"}
        :confirm-password config]
       [:fieldset
        [forms/submit-button "Sign Up" "-signup-button" config
         (and (non-blank? @state [:email :password :confirm-password])
              (= (:password @state) (:confirm-password @state)))]]
       [:p.margin-top-default.text-align-center
        "Already have an account? "
        [:a {:href "#" :on-click #(reset! show-signup? false)} "Sign In"]]])))

(defn auth-forms []
  (if @show-signup?
    [signup-form]
    [signin-form]))

(defmethod page/render :login [_]
  [:main
   [:section.login
    [:div.container.width-300.margin-top-plus-5.margin-bottom-plus-5
     [auth-forms]]]])
