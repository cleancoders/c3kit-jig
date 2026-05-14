(ns acme.auth.destination
  "Save the destination of an unauthenticated user while they deal with the signin process.  Once authenticated,
  send them where they wanted to go.

  Use of this namespace requires the following:
  * session support. Recommend: ring.middleware.session.cookie/cookie-store.
  * /redirect route pointing at web-redirect in this namespace
  * configure! must be called on startup
  "
  (:require [clojure.string :as str]
            [hiccup.page :as page]
            [ring.util.response :as response])
  (:import (java.net URI URISyntaxException)))

(defn post-redirect-form-hiccup [dest]
  [:div
   [:form#redirect {:action         (:uri dest)
                    :method         :post
                    :accept-charset "utf-8"}
    (for [[key value] (seq (:params dest))]
      [:input {:type "hidden" :name (name key) :value value}])]
   [:script "setTimeout(function() { document.getElementById('redirect').submit(); }, 0);"]])

(defprotocol DestinationAdapter
  (current-user [this request] "returns the current user from the request, or nil")
  (default-uri-for-user [this user] "returns the desired uri for a given user")
  (post-redirect-response [this destination] "returns hiccup for a page to display while the user is being redirected."))

(deftype DefaultDestinationAdapter []
  DestinationAdapter
  (current-user [_this request] (:user request))
  (default-uri-for-user [_this _user] "/")
  (post-redirect-response [_this destination]
    (-> (response/response
          (page/html5
            [:body
             [:h1 "Redirect"]
             [:p "You are being redirected.  One moment please..."]
             (post-redirect-form-hiccup destination)]))
        (response/content-type "text/html")
        (response/charset "UTF-8"))))

(def -adapter (atom (DefaultDestinationAdapter.)))

(defn configure! [destination-adapter]
  (reset! -adapter destination-adapter))

(defn copy-session [response request] (assoc response :session (:session request)))

(defn save-in-session [response dest]
  (assoc-in response [:session :destination] dest))

(defn from-session [request] (-> request :session :destination))

(defn referer-path [request]
  (when-let [referer (get-in request [:headers "referer"])]
    (try
      (let [url (.toURL (URI. referer))]
        (.getPath url))
      (catch URISyntaxException _e
        ;; MDM - Hackers like to put sql or shell commands into the referer header.
        "/"))))

(defn extract [request]
  (let [method (or (:request-method request) :get)
        uri    (:uri request)]
    (if (= :post method)
      {:method :post :uri uri :params (:params request)}
      (if (and uri (str/starts-with? uri "/ajax/"))
        {:method :get :uri (referer-path request)}
        {:method :get :uri uri :query-string (:query-string request)}))))

(defn preserve [response request]
  (let [destination (extract request)]
    (-> response
        (copy-session request)
        (save-in-session destination))))

(defn post? [dest] (= :post (:method dest)))

(defn build-uri [dest]
  (when-let [uri (:uri dest)]
    (if-let [query-string (:query-string dest)]
      (str uri "?" query-string)
      uri)))

(defn default-user-destination [user]
  (cond (nil? user) "/"
        (:confirmation-token user) "/signup-success"
        :else "/memories"))

(defn calculate-destination [request user]
  (if-let [dest (from-session request)]
    dest
    {:method :get :uri (default-uri-for-user @-adapter user)}))

(defn add-to-payload [response user]
  ;; MDM - assumes session has already been copied to response
  (let [dest (calculate-destination response user)]
    (if (post? dest)
      (assoc-in response [:body :payload :destination] "/redirect")
      (-> response
          (assoc-in [:body :payload :destination] (build-uri dest))
          (update :session dissoc :destination)))))

(defn web-redirect
  ([request] (web-redirect request (current-user @-adapter request)))
  ([request user]
   (let [dest (calculate-destination request user)]
     (if (post? dest)
       (-> (post-redirect-response @-adapter dest)
           (update :session dissoc :destination))
       (-> (response/redirect (build-uri dest))
           (update :session dissoc :destination))))))
