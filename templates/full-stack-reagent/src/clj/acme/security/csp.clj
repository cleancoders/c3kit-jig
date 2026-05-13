(ns acme.security.csp
  (:require [c3kit.apron.log :as log]
            [clojure.string :as str]))

(def default-policy
  {:default-src ["'self'"]
   :script-src  ["'self'" "'unsafe-inline'" "'unsafe-eval'"]
   :style-src   ["'self'" "'unsafe-inline'"]
   :img-src     ["'self'" "data:" "https:"]
   :font-src    ["'self'" "https:" "data:"]
   :connect-src ["'self'"]
   :frame-src   ["'self'"]
   :object-src  ["'none'"]
   :base-uri    ["'self'"]
   :form-action ["'self'"]
   :report-uri  "/api/v1/csp-report"})

(defn- directive->str [k v]
  (let [name- (name k)
        body  (if (sequential? v) (str/join " " v) v)]
    (str name- " " body)))

(defn policy->header-value [policy]
  (->> policy
       (map (fn [[k v]] (directive->str k v)))
       (str/join "; ")))

(defn wrap-csp [handler {:keys [enforce? policy] :or {policy default-policy}}]
  (let [header-name  (if enforce?
                       "Content-Security-Policy"
                       "Content-Security-Policy-Report-Only")
        header-value (policy->header-value policy)]
    (fn [request]
      (when-let [response (handler request)]
        (update response :headers assoc header-name header-value)))))

(defn csp-report-handler [request]
  (let [body (when (:body request)
               (try (slurp (:body request)) (catch Exception _ "")))]
    (log/warn "CSP violation report:" body)
    {:status 204 :headers {} :body ""}))
