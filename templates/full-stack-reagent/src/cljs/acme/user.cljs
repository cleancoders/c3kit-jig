(ns acme.user
  (:require
    [acme.core :as core]
    [acme.page :as page]
    [c3kit.bucket.api :as db]
    [c3kit.wire.websocket :as ws]
    [reagent.core :as reagent]
    ))

(def user-id (reagent/atom nil))
(def current (reagent/track #(db/entity @user-id)))
(def data-fetched? (reagent/atom false))

(defn install! [user] (reset! user-id (:id user)))
(def clear! #(reset! user-id nil))

(defn data-fetched! [data]
  (db/tx* data)
  (reset! data-fetched? true))

(defn install-and-connect! [user]
  (when user
    (db/tx user)
    (install! user)
    (ws/start!) ;; remove is you don't need websockets
    (ws/call! :user/fetch-data nil data-fetched!)))

(defmulti render-menu-items identity)
(defmethod render-menu-items :default [_] nil)

(defn render-menu []
  (when @current
    [:div#-user-menu.user-menu-container
     [:div.user-menu-toggle.fa-solid.fa-bars]
     [:div.user-menu
      [:nav
       [:ul
        (render-menu-items @page/current)
        [:li#-sign-out-menu-item {:on-click #(core/goto! "/signout")} "Sign Out"]]]]]))

