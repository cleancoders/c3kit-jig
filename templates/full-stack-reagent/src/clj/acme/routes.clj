(ns acme.routes
  (:require [acme.config :as config]
            [acme.destination :as destination]
            [acme.version :as version]
            [c3kit.apron.corec :as ccc]
            [c3kit.apron.util :as util]
            [c3kit.wire.ajax :as ajax]
            [c3kit.wire.rest :as rest]
            [c3kit.wire.restc :as restc]
            [c3kit.wire.routes :as wire.routes]
            [clojure.string :as str]
            [compojure.core :as compojure :refer [defroutes routes]]
            [ring.util.response :as response]))

(defn api-not-found-handler [request]
  (restc/not-found (str "API not found: " (:path-info request))))

(defn wrap-prefix [handler prefix not-found-handler]
  (fn [request]
    (let [path (or (:path-info request) (:uri request))]
      (when (str/starts-with? path prefix)
        (let [request (assoc request :path-info (subs path (count prefix)))]
          (if-let [response (handler request)]
            response
            (not-found-handler request)))))))

(def resolve-handler
  (if config/development?
    (fn [handler-sym] (util/resolve-var handler-sym))
    (memoize (fn [handler-sym] (util/resolve-var handler-sym)))))

(defn lazy-handle
  "Reduces load burden of this ns, which is useful in development.
  Runtime errors will occur for missing handlers, but all the routes should be tested in routes_spec.
  Assumes all handlers take one parameter, request."
  [handler-sym request]
  (let [handler (resolve-handler handler-sym)]
    (handler request)))

(defmacro lazy-routes
  "Creates compojure route for each entry where the handler is lazily loaded.
  Why are params a hash-map instead of & args? -> Intellij nicely formats hash-maps as tables :-)"
  [table]
  `(routes
     ~@(for [[[path method] handler-sym] table]
         (let [method (if (= :any method) nil method)]
           (compojure/compile-route method path 'req `((lazy-handle '~handler-sym ~'req)))))))

(defn redirect-handler [path]
  (let [segments (str/split path #"/")
        segments (map #(if (str/starts-with? % ":") (keyword (subs % 1)) %) segments)]
    (fn [request]
      (let [params   (:params request)
            segments (map #(if (keyword? %) (get params %) %) segments)
            dest     (str/join "/" segments)]
        (response/redirect dest)))))

(defmacro redirect-routes [table]
  `(routes
     ~@(for [[[path method] dest] table]
         (let [method (if (= :any method) nil method)]
           (compojure/compile-route method path 'req `((redirect-handler ~dest)))))))

(def ws-handlers
  {
   :user/fetch-data 'acme.user.web/ws-fetch-user-data
   })

(defn sleep-for-10 [] (Thread/sleep 10000))
(defn spinner [_]
  (sleep-for-10)
  (ajax/ok {} nil))

(def api-handler
  (let [csp-on?   (-> config/env :csp :enabled?)
        primary   (wire.routes/lazy-routes
                    {
                     ["/version" :get]                              acme.version/api-get
                     ["/v1/content/:type/:permalink" :get]          acme.content/api-fetch-post
                     ["/user/signin" :post]                         acme.user.api/api-signin
                     ["/user/signup" :post]                         acme.user.api/api-signup
                     ["/user/forgot-password" :post]                acme.user.api/api-forgot-password
                     ["/user/reset-password/:recovery-token" :post] acme.user.api/api-reset-password
                     ["/user/social/:provider" :post]               acme.user.api/api-social-auth
                     })
        csp-extra (wire.routes/lazy-routes
                    {["/v1/csp-report" :post] acme.security.csp/csp-report-handler})]
    (-> (if csp-on? (compojure/routes primary csp-extra) primary)
        (rest/wrap-rest {:keywords? true})
        (wire.routes/wrap-prefix "/api" api-not-found-handler))))

(def ajax-routes-handler
  (-> (lazy-routes
        {
         ["/forgot-password" :post]  acme.user.ajax/ajax-forgot-password
         ["/recover-password" :post] acme.user.ajax/ajax-reset-password
         ["/spinner" :get]           acme.routes/spinner
         ["/user/csrf-token" :get]   acme.user.ajax/ajax-csrf-token
         ["/user/signin" :post]      acme.user.ajax/ajax-signin
         ["/user/signup" :post]      acme.user.ajax/ajax-signup
         })
      (wrap-prefix "/ajax" ajax/api-not-found-handler)
      ajax/wrap-ajax))

(def web-routes-handlers
  (lazy-routes
    {
     ["/" :get]                                 acme.layouts/web-home
     ["/error" :any]                            acme.errors/web-error
     ["/forgot-password" :get]                  acme.layouts/web-rich-client
     ["/google/oauth" :post]                    acme.user.web/web-google-oauth-login
     ["/apple/oauth" :post]                     acme.user.web/web-apple-oauth-login
     ["/recover-password/:recovery-token" :get] acme.layouts/web-rich-client
     ["/redirect" :get]                         acme.destination/web-redirect
     ["/signout" :any]                          acme.user.web/web-signout
     ["/signout/:reason" :any]                  acme.user.web/web-signout
     ["/user/websocket" :any]                   acme.user.web/websocket-open
     }))

(def dev-handler
  (lazy-routes
    {
     ["/sandbox" :get]                 acme.sandbox.core/index
     ["/sandbox/" :get]                acme.sandbox.core/index
     ["/sandbox/:page" :get]           acme.sandbox.core/handler
     ["/sandbox/:page/:ns" :get]       acme.sandbox.core/handler
     ["/sandbox/:page/:ns1/:ns2" :get] acme.sandbox.core/handler
     }))

(defn- content-routes-handler [request] ((acme.content/build-routes) request))

(defroutes handler
  api-handler
  ajax-routes-handler
  web-routes-handlers
  content-routes-handler
  (if config/production? ccc/noop dev-handler)
  )
