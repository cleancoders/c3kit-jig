(ns acme.errors
  (:require
    [c3kit.apron.log :as log]
    [c3kit.apron.time :as time]
    [clojure.pprint :as pprint]
    [clojure.stacktrace :as stacktrace]
    [hiccup.core :as hiccup]
    [ring.util.response :as response]
    [acme.config :as config]
    [acme.email :as email]
    [acme.layouts :as layouts]
    ))

(defn web-error [_] (throw (ex-info "This is merely a test" {:foo "bar"})))

(defn production-error-page []
  (layouts/static
    [:main
     [:section
      [:div.container.small-width.margin-top-plus-5
       [:div.card
        [:div.container.text-align-center
         [:h2.small-margin-bottom "An error has occurred"]
         [:p "We have been notified, and are looking into it. Our apologies for the inconvenience."
          "If you continue to receive this error, please contact us at "
          [:a {:target "_blank" :href "mailto:support@cleancoders.com"} "support@cleancoders.com"] "."]]]]]]))

(defn pretty-printed [x] (with-out-str (pprint/pprint x)))

(defn- build-error-html [request e]
  (let [request        (-> request
                           (update :params dissoc :password :confirm "password" "confirm")
                           (update :query-params dissoc :password :confirm "password" "confirm"))
        request-str    (pretty-printed request)
        user           (-> request :session :lemon-user)
        user-str       (pretty-printed (into {} user))
        stacktrace-str (with-out-str (stacktrace/print-stack-trace e))
        row            (fn [title value]
                         [:tr
                          [:th {:style "vertical-align:top; text-align:left; padding:0 0.5em 0.1em 0"} title]
                          [:td {:style "font-family:monospace; white-space:pre-wrap"} value]])]
    (hiccup/html
      [:style "pre{white-space:pre-wrap}"]
      [:h2 "Error in \"" config/environment "\" environment."]
      [:table
       (row "Message" (.getMessage e))
       (row "Route" (:uri request))
       (row "Params" (pretty-printed (:params request)))
       (row "User" (if (some? user) (:email user) "[guest]"))]
      [:h2 "Stacktrace"] [:pre stacktrace-str]
      [:h2 "Request"] [:pre request-str]
      [:h2 "User"] [:pre user-str])))

(defn internal-error [to m]
  {:to      to
   :from    "errors@cleancoders.com"
   :subject (format "[acme] 500 error in [%s] at [%s]"
                    config/environment
                    (time/unparse :iso8601 (time/now)))
   :html    (:body m)})

(defn send-error-email
  ([error-html]
   (email/send-email (internal-error "support@cleancoders.com" {:body error-html})))
  ([request e]
   (let [error-html (build-error-html request e)]
     (send-error-email error-html))))

(defn wrap-errors [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (log/error (with-out-str (stacktrace/print-stack-trace e)))
        (let [error-html (build-error-html request e)]
          (when-not config/development? (send-error-email error-html))
          (if config/production?
            (-> (production-error-page)
                (response/status 500))
            (-> (response/response error-html)
                (response/status 500)
                (response/content-type "text/html"))))))))

