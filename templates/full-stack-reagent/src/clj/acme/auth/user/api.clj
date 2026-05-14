(ns acme.auth.user.api
  (:require [acme.config :as config]
            [acme.auth.user.apple]
            [acme.auth.user.core :as user]
            [acme.auth.user.corec :as userc]
            [acme.auth.user.google]
            [acme.auth.user.web :as user.web]
            [c3kit.apron.legend :as legend]
            [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [c3kit.wire.flashc :as flashc]
            [c3kit.wire.restc :as restc]))

(defn- handle [request action build-success-payload]
  (let [errors-or-user (action (:body request))]
    (if (:errors errors-or-user)
      (restc/bad-request errors-or-user)
      (-> (restc/ok (build-success-payload errors-or-user))
          (user.web/authorize-user errors-or-user)))))

(defn api-signin [request]
  (handle request user/attempt-signin legend/present!))

(defn api-signup [request]
  (handle request user/attempt-signup legend/present!))

(defn api-forgot-password [request]
  (handle request user/attempt-forgot-password (constantly "ok")))

(defn api-reset-password [request]
  (let [token    (-> request :params :recovery-token)
        password (-> request :body :password)
        request  (assoc request :body {:recovery-token   token
                                       :password         password
                                       :confirm-password password})]
    (handle request user/attempt-password-reset (constantly "ok"))))

;; region ----- social auth -----

(def social-request-schema
  {:params {:type     {:provider {:type :keyword :validate schema/present? :message "is required"}}
            :validate schema/present?}
   :body   {:type     {:jwt {:type :string :validate schema/present? :message "is required"}}
            :validate schema/present?}})

(defn- maybe-invalid-signature [jwt]
  (when-not jwt (restc/bad-request {:flash-messages [(flashc/error "jwt/payload is invalid")]})))

(defn- authorize-social [provider {:keys [sub] :as jwt}]
  (let [social-login (or (db/ffind-by :social-login :provider provider :social-id sub)
                         (user/new-social-login! provider jwt))
        found-user   (-> social-login :user-id db/entity)]
    (-> (restc/response 302 nil {"Location" (config/link "/")})
        (user.web/authorize-user found-user))))

(defn api-social-auth [request]
  (let [conformed (schema/conform social-request-schema request)
        provider  (get-in conformed [:params :provider])
        jwt       (delay (user/<-jwt-payload provider (get-in request [:body :jwt])))]
    (or (when (schema/error? conformed) (restc/bad-request conformed))
        (maybe-invalid-signature @jwt)
        (authorize-social provider @jwt))))

;; endregion ^^^^^ social auth ^^^^^