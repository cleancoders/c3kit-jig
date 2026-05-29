(ns acme.auth.user.web
  (:require [acme.config :as config]
            [acme.auth.destination :as destination]
            [acme.layouts :as layouts]
            [acme.auth.session :as session]
            [acme.auth.user.core :as user]
            [acme.auth.user.corec :as userc]
            [c3kit.apron.legend :as legend]
            [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [c3kit.wire.apic :as apic]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.google :as google]
            [c3kit.wire.jwt :as jwt]
            [c3kit.wire.websocket :as ws]
            [ring.util.response :as response]))

(defn authorize-user
  ([user] (authorize-user {} user))
  ([request user] (jwt/update-payload request assoc :user-id (:id user))))

(defn forbid-user [response] (jwt/update-payload response dissoc :user-id))

(defn user-id [req] (-> req :jwt/payload :user-id))

;; region ----- destination -----

(deftype AcmeDestinationAdapter []
  destination/DestinationAdapter
  (current-user [_ request] (user/current request))
  (default-uri-for-user [_ user]
    (if user "/" "/"))
  (post-redirect-response [_ destination]
    (layouts/static
     [:main
      [:div.floating-panel
       [:h1.uppercase "Redirect"]
       [:p.margin-top-default "You are being redirected.  One moment please..."]
       (destination/post-redirect-form-hiccup destination)]])))

;; endregion ^^^^^ destination ^^^^^

(defn signin-user [response request user]
  (-> (session/copy response request)
      (destination/add-to-payload user)
      (authorize-user user)))

(defmacro ensure-authenticated [request & body]
  `(let [request# ~request]
     (if (user/current request#)
       (do ~@body)
       (-> (response/redirect "/")
           (flash/warn "Please sign in to continue.")
           (assoc-in [:session :destination] (:uri request#))))))

(def reset-password-schema
  {:token {:type        :uuid
           :message     "Invalid recovery token."
           :validations [{:validate schema/present? :message "Missing recovery token."}]}})

(defn web-reset-password [request]
  (let [conformed     (schema/conform reset-password-schema (:params request))
        token         (:token conformed)
        user          (delay (db/ffind-by :user :recovery-token token))
        redirect-home (response/redirect "/")]
    (cond (schema/error? conformed) (flash/error redirect-home (-> conformed schema/message-map :token))
          (nil? @user) (flash/error redirect-home "Unrecognized recovery token.")
          :else (layouts/web-rich-client request))))

;; region ----- OAuth handlers -----

(defn- web-error [request msg]
  (-> (response/redirect (config/link "/"))
      (jwt/copy-payload request)
      (flash/error msg)))

(defn- web-success [user request msg]
  (-> (response/redirect (config/link "/"))
      (jwt/copy-payload request)
      (authorize-user user)
      (flash/success msg)))

(defn- web-authorize-social [request _provider user-data]
  (if-let [existing (userc/existing-user (:email user-data))]
    (web-success existing request "Welcome back!")
    (-> (user/new-social-user! user-data)
        (web-success request "Welcome to Acme!"))))

(defn web-google-oauth-login [request]
  (if-let [id-token (try (google/oauth-verification (-> config/env :google-oauth :client-id) (-> request :params :credential)) (catch Exception _))]
    (let [user-data (some-> id-token google/token->payload)]
      (or (when-not (:email-verified? user-data) (web-error request "This email address has not been verified by google yet"))
          (web-authorize-social request :google user-data)))
    (web-error request "Invalid google credentials")))

(defn web-apple-oauth-login [request]
  (if-let [id-token (-> request :params :id_token)]
    (let [user-data (delay (user/<-jwt-payload :apple id-token))]
      (or (when-not @user-data (web-error request "Invalid Response Token"))
          (web-authorize-social request :apple @user-data)))
    (web-error request "Invalid apple response")))

;; endregion ^^^^^ OAuth handlers ^^^^^

;; region ----- signout -----

(defn web-signout [request]
  (-> (response/redirect config/host)
      (jwt/copy-payload request)
      forbid-user
      (flash/success "You have been signed out")))

;; endregion ^^^^^ signout ^^^^^

;; region ----- websocket -----

(defn websocket-open [request]
  (when (user/current request) (ws/handler request {:read-csrf jwt/client-id})))

(defn ws-fetch-user-data [{:keys [request]}]
  (if-let [current-user (user/current request)]
    (apic/ok (map legend/present! [current-user]))
    (apic/fail nil "Unable to fetch user data without sign in")))

;; endregion ^^^^^ websocket ^^^^^
