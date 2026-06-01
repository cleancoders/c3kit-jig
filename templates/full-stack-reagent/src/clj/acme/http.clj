(ns acme.http
  (:require [acme.config :as config]
            [acme.errors :as errors]
            [acme.layouts :as layouts]
            [c3kit.apron.log :as log]
            ;; @c3kit/feature :auth = [c3kit.apron.time :as time]
            [c3kit.apron.util :as util]
            [c3kit.wire.assets :refer [wrap-asset-fingerprint]]
            [compojure.core :refer [defroutes]]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.flash :refer [wrap-flash]]
            [ring.middleware.head :refer [wrap-head]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]))

(defn refreshable [handler-sym]
  (if config/development?
    (fn [request] (@(util/resolve-var handler-sym) request))
    (util/resolve-var handler-sym)))

(defroutes web-handler
  (refreshable 'acme.routes/handler)
  (route/not-found (layouts/not-found)))

(defn app-handler []
  (if config/development?
    (let [wrap-verbose    (util/resolve-var 'c3kit.apron.verbose/wrap-verbose)
          refresh-handler (util/resolve-var 'c3kit.apron.refresh/refresh-handler)]
      (-> (refresh-handler 'acme.http/web-handler)
          wrap-verbose))
    (util/resolve-var 'acme.http/web-handler)))

(def ^:private security-headers
  {"X-Frame-Options"           "DENY"
   "X-Content-Type-Options"    "nosniff"
   "X-XSS-Protection"          "1; mode=block"
   "Referrer-Policy"           "strict-origin-when-cross-origin"
   "Strict-Transport-Security" "max-age=31536000; includeSubDomains"})

(defn wrap-security-headers [handler]
  (fn [request]
    (when-let [response (handler request)]
      (update response :headers #(merge security-headers %)))))

(defn- maybe-wrap-csp [handler]
  (let [{:keys [enabled?] :as csp-cfg} (:csp config/env)]
    (if enabled?
      (let [wrap-csp       (util/resolve-var 'acme.security.csp/wrap-csp)
            default-policy @(util/resolve-var 'acme.security.csp/default-policy)]
        (wrap-csp handler (merge {:policy default-policy} csp-cfg)))
      handler)))

;; @c3kit/feature :auth {
(defn- wrap-auth-middleware [handler]
  (let [session-wrap   (util/resolve-var 'acme.auth.session/wrap-session)
        anti-forgery   (util/resolve-var 'ring.middleware.anti-forgery/wrap-anti-forgery)
        jwt-wrap       (util/resolve-var 'c3kit.wire.jwt/wrap-jwt)
        create-strat   (util/resolve-var 'c3kit.wire.jwt/create-strategy)]
    (-> handler
        session-wrap
        (anti-forgery {:strategy (create-strat)})
        (jwt-wrap {:cookie-name "acme-token" :secret (:jwt-secret config/env) :lifespan (when config/development? (time/hours 336))}))))
;; @c3kit/feature :auth }

;; MDM - What's all this refresh/development hocus pocus?  An explanation owed.
;;  In development, we want changed code to automatically reload when a request is made.  Although simple in
;;  principle, the mechanics of it give me a headache sometimes.
;;  1) When the app starts, some namespaces are loaded, like this one.  But the refresh code (acme.refresh)
;;      doesn't know which. As far as it knows, nothing has been loaded.  So on the first request, all the namespaces
;;      are reloaded.
;;  2) Some namespaces will/should never get reloaded. See acme.refresh/excludes
;;  3) The root-handler below is expensive to create.  Hence the defonce.  So we carefully pick pieces of the
;;      root-handler to refresh:
;;        - acme.routes/handler - the essence of acme.com
;;        - wrap-session - because it uses the database connection which gets reloaded

(defonce root-handler
  (-> (app-handler)
      maybe-wrap-csp
      wrap-security-headers
      errors/wrap-errors
      wrap-flash
      ;; @c3kit/feature :auth = wrap-auth-middleware
      wrap-keyword-params
      wrap-multipart-params
      wrap-nested-params
      wrap-params
      wrap-cookies
      (wrap-resource "public")
      wrap-asset-fingerprint
      wrap-content-type
      wrap-not-modified
      wrap-head))

(defn start [app]
  (let [port (or (some-> "PORT" System/getenv Integer/parseInt) 8123)]
    (log/info (str "Starting HTTP server: http://localhost:" port))
    (let [server (run-server root-handler {:port port})]
      (assoc app :http server))))

(defn stop [app]
  (when-let [stop-server-fn (:http app)]
    (log/info "Stopping HTTP server")
    (stop-server-fn :timeout 1000))
  (dissoc app :http))


