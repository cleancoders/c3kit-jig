(ns acme.layout
  (:require [acme.modal :as modal]
            [acme.page :as page]
            ;; @c3kit/feature :auth {
            [acme.auth.user :as user]
            ;; @c3kit/feature :auth }
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.rest :as rest]))

(defn default []
  [:div#content
   [flash/flash-root]
   (when (or (ajax/activity?) (rest/activity?)) [:div.site-spinner])
   [:header
    [:div.container.horizontal-inset-plus-1.inliner.space-between
     [:a {:href "/"} [:img.logo {:src "/images/logos/cc-emblem.png"}]]
     ;; @c3kit/feature :auth {
     [:div {:class "user-menu-container"}
      (user/render-menu)]
     ;; @c3kit/feature :auth }
     ]]
   (page/render @page/current)
   [modal/modal]])
