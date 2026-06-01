(ns acme.home
  (:require [acme.page :as page]
            ;; @c3kit/feature :auth {
            [acme.core :as cc]
            [acme.forms :as forms]
            [acme.auth.user :as user]
            [acme.auth.user.corec :as userc]
            [clojure.string :as str]
            [reagent.core :as reagent]
            ;; @c3kit/feature :auth }
            [c3kit.apron.corec :as ccc]
            [c3kit.wire.ajax :as ajax]))

;; @c3kit/feature :auth {
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

(defn welcome []
  (if @user/data-fetched?
    [:ul.interactive.small-margin-bottom
     [:h1 (str "Welcome to Acme, " (:name @user/current))]
     [:button#-spinner-button.primary
      {:on-click #(ajax/get! "/ajax/spinner" {} ccc/noop)}
      "Test Spinner"]]
    [:div#-spinner.text-align-center.margin-bottom [:div.spinner-medium-grey]]))

(defn auth-forms []
  (if @show-signup?
    [signup-form]
    [signin-form]))

(defmethod page/render :home [_]
  [:main
   [:section.home
    [:div.container.width-300.margin-top-plus-5.margin-bottom-plus-5
     (if @user/current
       (welcome)
       [auth-forms])]]])
;; @c3kit/feature :auth }

;; @c3kit/feature !:auth {
(defn welcome []
  [:ul.interactive.small-margin-bottom
   [:h1 "Welcome to Acme"]
   [:button#-spinner-button.primary
    {:on-click #(ajax/get! "/ajax/spinner" {} ccc/noop)}
    "Test Spinner"]])

(defmethod page/render :home [_]
  [:main
   [:section.home
    [:div.container.width-300.margin-top-plus-5.margin-bottom-plus-5
     [welcome]]]])
;; @c3kit/feature !:auth }

(defmethod page/prerender? :home [_] true)
