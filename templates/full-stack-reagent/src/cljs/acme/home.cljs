(ns acme.home
  (:require [acme.page :as page]
            [c3kit.apron.corec :as ccc]
            [c3kit.wire.ajax :as ajax]))

(defn next-steps []
  [:ul#-next-steps.starter-links
   [:li [:a {:href "https://github.com/cleancoders/c3kit-jig/wiki" :target "_blank"} "c3kit-jig wiki & guides"]]
   [:li "Add a page — edit " [:code "src/cljs/acme/home.cljs"] " and register a route"]
   [:li "Seed dev data — " [:code "clj -M:test:seed"]]
   [:li "Project docs — see " [:code "README.md"]]])

(defn starter []
  [:main#-starter
   [:section.home
    [:div.container.width-300.margin-top-plus-5.margin-bottom-plus-5.text-align-center
     [:img.logo.margin-bottom-plus-1 {:src "/images/logos/cc-emblem.png"}]
     [:h1 "Acme is running 🎉"]
     [:p "Your full-stack Clojure + ClojureScript app is live."]
     [next-steps]
     ;; @c3kit/feature :auth {
     [:p.margin-top-default [:a#-login-link {:href "/login"} "Sign in →"]]
     ;; @c3kit/feature :auth }
     [:button#-spinner-button.primary
      {:on-click #(ajax/get! "/ajax/spinner" {} ccc/noop)}
      "Test Spinner"]]]])

(defmethod page/render :home [_]
  [starter])

(defmethod page/prerender? :home [_] true)
