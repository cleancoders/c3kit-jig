(ns acme.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:require [accountant.core :as accountant]
            ;; @c3kit/feature :content = [acme.content.page :as content-page]
            [acme.core :as core]
            [acme.page :as page]
            ;; @c3kit/feature :auth {
            [acme.auth.recover-password :as recover-password]
            ;; @c3kit/feature :auth }
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]
            [clojure.string :as str]
            [reagent.core :as r]
            [secretary.core :as secretary]))

(defn- strip-hash [uri]
  (first (str/split uri #"#" 2)))

(defn- hash-fragment [uri]
  (second (str/split uri #"#" 2)))

(defn dispatch! [uri]
  (log/debug "dispatching: " uri)
  (reset! core/thing-to-scroll-to (hash-fragment uri))
  (secretary/dispatch! (strip-hash uri)))

(defn locate-route [route]
  (let [result (secretary/locate-route (strip-hash route))]
    (log/debug "locate-route: " route " -> " result)
    result))

(defn- hook-browser-navigation! []
  (accountant/configure-navigation! {:nav-handler dispatch! :path-exists? locate-route}))

(defn load-page! [page]
  (page/transition page)
  (wjs/scroll-to-top)
  (wjs/page-title= (page/title page))
  (page/install-page! page)
  (r/after-render #(core/scroll-to-thing-in-url core/thing-to-scroll-to)))

(defn sandbox-routes []
  (defroute "/sandbox/:sandbox-page" [sandbox-page] (load-page! (keyword (str "sandbox/" sandbox-page)))))

(defn app-routes []
  (secretary/set-config! :prefix "")

  (defroute "/" [] (load-page! :home))
  ;; @c3kit/feature :auth {
  (defroute "/forgot-password" [] (load-page! :forgot-password))
  (defroute "/recover-password/:recovery-token" [recovery-token]
    (reset! recover-password/recovery-token recovery-token)
    (load-page! :recover-password))
  ;; @c3kit/feature :auth }

  (sandbox-routes)

  ;; @c3kit/feature :content {
  (defroute "/:content-type" [content-type]
    (content-page/install! content-type nil)
    (load-page! :content/page))

  (defroute "/:content-type/:permalink" [content-type permalink]
    (content-page/install! content-type permalink)
    (load-page! :content/page))
  ;; @c3kit/feature :content }

  (hook-browser-navigation!))
