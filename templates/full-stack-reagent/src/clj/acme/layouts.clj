(ns acme.layouts
  (:require [acme.config :as config]
            [acme.http-util :as http-util]
            [acme.layoutc :as layoutc]
            ;; @c3kit/feature :auth {
            [acme.auth.user :as user]
            [c3kit.apron.legend :as legend]
            ;; @c3kit/feature :auth }
            [c3kit.apron.utilc :as utilc]
            [c3kit.wire.api :as api]
            [c3kit.wire.assets :refer [add-fingerprint]]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.jwt :as jwt]
            [clojure.string :as str]
            [hiccup.element :as elem]
            [hiccup.page :as page]
            [hiccup.util :as hutil]
            [ring.util.response :as response]))

(def default-title "Acme")
(def default-description "The Acme App")
(def default-image "/images/logos/cc-emblem.png")
(defn title [options] (or (:title options) default-title))

(defn social-meta [options]
  (list
    [:meta {:property "og:url" :content (or (:og/url options) (str config/host "/"))}]
    [:meta {:property "og:title" :content (or (:og/title options) (title options))}]
    [:meta {:property "og:description" :content (or (:og/description options) default-description)}]
    [:meta {:property "og:image" :content (or (:og/image options) default-image)}]
    [:meta {:name "twitter:card" :content "summary"}]
    [:meta {:name "twitter:site" :content "@thecleancoders"}]))

(defn default
  ([body] (default body {}))
  ([body options]
   (-> (response/response
         (page/html5
           [:head
            [:title (title options)]
            [:meta {:charset "utf-8"}]
            [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, minimum-scale=1.0"}]
            (social-meta options)
            [:link {:rel "icon" :sizes "16x16" :type "image/png" :href "/images/favicons/favicon-16x16.png"}]
            [:link {:rel "icon" :sizes "32x32" :type "image/png" :href "/images/favicons/favicon-32x32.png"}]
            [:link {:rel "icon" :sizes "192x192" :type "image/png" :href "/images/favicons/favicon-192x192.png"}]
            [:link {:rel "icon" :sizes "512x512" :type "image/png" :href "/images/favicons/favicon-512x512.png"}]
            [:link {:rel "apple-touch-icon" :sizes "180x180" :href "/images/favicons/apple-touch-icon.png"}]
            [:link {:rel "manifest" :href "/images/favicons/site.webmanifest"}]
            [:script {:src "https://kit.fontawesome.com/982c21555a.js" :crossorigin "anonymous"}]
            (if config/development?
              (list
                (page/include-js "/cljs/goog/base.js")
                (page/include-js "/cljs/acme_dev.js"))
              (page/include-js (add-fingerprint "/cljs/acme.js")))
            (:head options)                                 ;; MDM - must go after js so we can include js-fns, and before css, so we can override styles as needed
            (page/include-css (add-fingerprint (or (:css options) "/css/acme.css")))]
           [:body body]))

       (response/content-type "text/html")
       (response/charset "UTF-8"))))

(defn static [& content]
  (default
    [:div#content
     [:header
      [:div.container.horizontal-inset-plus-1.inliner.space-between
       [:a {:href "/"} [:img.logo {:src "/images/logos/cc-emblem.png"}]]
       [:div.user-menu-container]]]
     content]))

(defn not-found []
  (static
    [:main
     [:section
      [:div.container.width-300
       [:h2.margin-bottom-0 "Not Found (404)"]
       [:img.margin-bottom-plus-2 {:src "/images/not-found.jpg"}]
       [:div
        [:p "Lost in the code scene,"]
        [:p "Cursor blinking on the screen,"]
        [:p "She wished her code clean."]]]]]))

(defn client-init
  ([] (client-init {}))
  ([data]
   (let [payload (pr-str (utilc/->transit data))]
     (str "<script type=\"text/javascript\">\n//<![CDATA[\n"
          "acme.main.main(" (str/replace payload "</script>" "<\\/script>") ");"
          "\n//]]>\n</script>"))))

(def rich-client-placeholder (static "Your page is loading..."))

(defn rich-client [payload {:keys [seo/preview] :as options}]
  (default (list [:div#app-root
                  (if preview
                    (hutil/raw-string preview)
                    rich-client-placeholder)]
                 (client-init payload))
           (assoc options
             :head (elem/javascript-tag (str "goog.require('acme.main');")))))

;; @c3kit/feature !:auth {
(defn build-rich-client-payload [request]
  {:user   nil
   :flash  (flash/messages request)
   :config {
            :anti-forgery-token (jwt/client-id request)
            :ws-csrf-token      (jwt/client-id request)
            :api-version        (api/version)
            :environment        config/environment
            :google-client-id   (-> config/env :google-oauth :client-id)
            :acme-root          (-> config/env :cleancoders-auth :url-root)
            :host               config/host
            }})
;; @c3kit/feature !:auth }

;; @c3kit/feature :auth {
(defn build-rich-client-payload [request]
  {:user   (some-> request user/current legend/present!)
   :flash  (flash/messages request)
   :config {
            :anti-forgery-token (jwt/client-id request)
            :ws-csrf-token      (jwt/client-id request)
            :api-version        (api/version)
            :environment        config/environment
            :google-client-id   (-> config/env :google-oauth :client-id)
            :acme-root          (-> config/env :cleancoders-auth :url-root)
            :host               config/host
            }})
;; @c3kit/feature :auth }

(defn web-rich-client
  "Load the default web page and let the client side take over."
  ([request] (web-rich-client request {}))
  ([request options] (rich-client (build-rich-client-payload request) options)))

(defn- page-key->basename [page-key]
  (if (= :home page-key) "index" (name page-key)))

(defn prerendered-html [page-key]
  (let [f (java.io.File. (str "resources/prerendered/" (page-key->basename page-key) ".html"))]
    (when (.exists f) (slurp f))))

(defn prerendered-markdown [page-key]
  (let [f (java.io.File. (str "resources/prerendered/" (page-key->basename page-key) ".md"))]
    (when (.exists f) (slurp f))))

;; @c3kit/feature !:ssr {
(defn web-home [request] (web-rich-client request {}))
;; @c3kit/feature !:ssr }

;; @c3kit/feature :ssr {
(defn web-prerendered [page-key]
  (fn [request]
    (if (http-util/wants-markdown? request)
      (if-let [md (prerendered-markdown page-key)]
        {:status  200
         :headers {"Content-Type" "text/markdown; charset=utf-8"}
         :body    md}
        (web-rich-client request {:seo/preview (prerendered-html page-key)}))
      (web-rich-client request {:seo/preview (prerendered-html page-key)}))))

(defn web-home [request] ((web-prerendered :home) request))
;; @c3kit/feature :ssr }

