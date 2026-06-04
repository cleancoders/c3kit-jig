(ns acme.home
  (:require [acme.page :as page]
            [c3kit.apron.corec :as ccc]
            [c3kit.wire.ajax :as ajax]))

(defn next-steps []
  [:ul#-next-steps.starter-links
   [:li "Edit this page at " [:code "src/cljs/acme/home.cljs"]]
   [:li "Seed dev data — " [:code "clj -M:test:seed"]]
   [:li "Project docs — see " [:code "README.md"]]])

(defn starter []
  [:main#-starter
   [:section.home
    [:div.container.width-750.margin-top-plus-5.margin-bottom-plus-5.text-align-center
     [:h1 "Acme is running 🎉"]
     [:p#-tagline.margin-top-plus-3 "Your full-stack Clojure + ClojureScript app is live."]
     [next-steps]
     ;; @c3kit/feature :content {
     [:p.margin-top-default [:a#-blog-link {:href "/blog"} "Blog"]]
     ;; @c3kit/feature :content }
     ;; @c3kit/feature :auth {
     [:p.margin-top-default [:a#-login-link {:href "/login"} "Sign in →"]]
     ;; @c3kit/feature :auth }
     [:button#-spinner-button.primary
      {:on-click #(ajax/get! "/ajax/spinner" {} ccc/noop)}
      "Test Spinner"]]]])

(defmethod page/render :home [_]
  [starter])

(defmethod page/prerender? :home [_] true)
