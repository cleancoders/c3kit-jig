(ns acme.home
  (:require [acme.core :as cc]
            [acme.layoutc :as layoutc]
            [acme.page :as page]
            [acme.user :as user]
            [c3kit.apron.corec :as ccc]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.js :as wjs]
            [clojure.string :as str]
            [reagent.core :as reagent]))

(def handle-login-success (juxt (comp user/install-and-connect! :user)
                                (comp cc/goto! :destination)))

(defn attempt-login [username password]
  (ajax/post! "/ajax/user/signin" {:username username :password password} handle-login-success))

(defn attempt-signup [email password confirm-password]
  (ajax/post! "/ajax/user/signup" {:email email :password password :confirm-password confirm-password} handle-login-success))

(def show-signup? (reagent/atom false))

(defn signin-form []
  (let [username (reagent/atom "")
        password (reagent/atom "")]
    (fn []
      [:form
       [:fieldset.small-margin-bottom
        [:label "Email"]
        [:input#-username {:type          "text"
                           :placeholder   "email"
                           :auto-complete "username"
                           :on-change     #(reset! username (wjs/e-text %))}]]
       [:fieldset.small-margin-bottom
        [:label "Password"]
        [:input#-password {:type          "password"
                           :placeholder   "password"
                           :auto-complete "current-password"
                           :on-change     #(reset! password (wjs/e-text %))}]
        [:p [:a {:href "/forgot-password"} "I forgot my password."]]]
       [:fieldset
        [:button.primary
         {:id       "-signin-button"
          :disabled (some str/blank? [@username @password])
          :on-click (wjs/nod-n-do #(attempt-login @username @password))}
         "Sign In"]]
       [:p.margin-top-default.text-align-center
        "Don't have an account? "
        [:a {:href "#" :on-click #(reset! show-signup? true)} "Sign Up"]]])))

(defn signup-form []
  (let [email            (reagent/atom "")
        password         (reagent/atom "")
        confirm-password (reagent/atom "")]
    (fn []
      [:form
       [:fieldset.small-margin-bottom
        [:label "Email"]
        [:input#-signup-email {:type          "email"
                               :placeholder   "email"
                               :auto-complete "email"
                               :on-change     #(reset! email (wjs/e-text %))}]]
       [:fieldset.small-margin-bottom
        [:label "Password"]
        [:input#-signup-password {:type          "password"
                                  :placeholder   "password"
                                  :auto-complete "new-password"
                                  :on-change     #(reset! password (wjs/e-text %))}]]
       [:fieldset.small-margin-bottom
        [:label "Confirm Password"]
        [:input#-signup-confirm {:type          "password"
                                 :placeholder   "confirm password"
                                 :auto-complete "new-password"
                                 :on-change     #(reset! confirm-password (wjs/e-text %))}]]
       [:fieldset
        [:button.primary
         {:id       "-signup-button"
          :disabled (or (some str/blank? [@email @password @confirm-password])
                        (not= @password @confirm-password))
          :on-click (wjs/nod-n-do #(attempt-signup @email @password @confirm-password))}
         "Sign Up"]]
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

(defmethod page/prerender? :home [_] true)
