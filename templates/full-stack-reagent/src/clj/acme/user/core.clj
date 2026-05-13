(ns acme.user.core
  (:require [acme.config :as config]
            [acme.email :as email]
            [acme.user.corec :as userc]
            [buddy.core.keys.jwk.proto :as jwk]
            [buddy.core.keys.jwk.rsa]
            [buddy.sign.jwt :as buddy]
            [c3kit.apron.log :as log]
            [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [clojure.string :as str])
  (:import (org.mindrot.jbcrypt BCrypt)))

;; region ----- request -----

(defn id [req] (-> req :jwt/payload :user-id))

(defn load-from-request [request]
  (when-let [id (id request)]
    (db/entity id)))

(defn current [request]
  (if-let [user (:user request)]
    @user
    (load-from-request request)))

;; endregion ^^^^^ request ^^^^^

(defmulti pkeys identity)

(defn hash-password [password] (BCrypt/hashpw password (BCrypt/gensalt 11)))
(defn check-password [password hash] (BCrypt/checkpw password hash))

(defn verify-password [password hash]
  (if-not (and password hash)
    false
    (check-password password hash)))

(defn authorized-user [{:keys [email password] :as _credentials}]
  (when (and email password)
    (let [user (db/ffind-by :user :email (str/lower-case email))]
      (when (verify-password password (:password user))
        user))))

(defn- lower-case [s] (when s (str/lower-case s)))
(def lower-case-email {:email {:coerce lower-case}})
(def signin-schema (schema/merge-schemas userc/signin-schema lower-case-email))

(defn attempt-signin
  "Returns the user if the credentials are valid, otherwise a {:errors {}} map."
  [credentials]
  (let [credentials (schema/conform signin-schema credentials)
        user        (delay (db/ffind-by :user :email (:email credentials)))]
    (or (when (schema/error? credentials) {:errors (schema/message-map credentials)})
        (when (nil? @user) {:errors {:email "invalid credentials"}})
        (when (not (check-password (:password credentials) (:password @user))) {:errors {:email "invalid credentials"}})
        @user)))

;; region ----- signup -----

(defn maybe-email-used [{:keys [email]}]
  (when (db/ffind-by :user :email email)
    {:errors {:email "already in use"}}))

(defn new-user [email password]
  {:kind     :user
   :email    email
   :password (hash-password password)})

(defn signup! [{:keys [email password] :as _entity}]
  (db/tx (new-user email password)))

(def signup-schema (schema/merge-schemas userc/signup-schema lower-case-email))

(defn attempt-signup
  "Returns the new user if the credentials are valid, otherwise a {:errors {}} map."
  [signup-entity]
  (let [conformed (schema/conform signup-schema signup-entity)]
    (or (when (schema/error? conformed) {:errors (schema/message-map conformed)})
        (maybe-email-used conformed)
        (signup! conformed))))

;; endregion ^^^^^ signup ^^^^^

;; region ----- recover -----

(def recovery-request-schema
  {:email {:type :string :validations [schema/required userc/email-format]}})

(defn recovery-email [to permalink]
  {:to      to
   :from    config/admin-email
   :subject "Acme: Recover your account"
   :text    (str "Click this link to recover your Acme account:\n\n" config/host "/recover-password/" permalink)})

(defn recover! [email]
  (if-let [user (db/ffind-by :user :email email)]
    (let [recovery-token (random-uuid)
          recovery-email (recovery-email email recovery-token)]
      (db/tx user :recovery-token recovery-token)
      (email/send-email recovery-email))
    (log/warn "attempted account recovery for non-existent user" email))
  {})

(defn attempt-forgot-password [recover-entity]
  (let [conformed (schema/conform recovery-request-schema recover-entity)
        email     (:email conformed)]
    (or (when (schema/error? conformed) {:errors (schema/message-map conformed)})
        (recover! email))))

;; endregion ^^^^^ recover ^^^^^

;; region ----- reset password -----

(defn attempt-password-reset [request]
  (let [conformed (schema/conform userc/reset-password-schema request)
        user      (delay (db/ffind-by :user :recovery-token (:recovery-token conformed)))]
    (or (when (schema/error? conformed) {:errors (schema/message-map conformed)})
        (when @user
          (let [hash (hash-password (:password conformed))]
            (db/tx @user :password hash :recovery-token nil))))))

;; endregion ^^^^^ reset password ^^^^^

;; region ----- social login -----

(defn <-jwt-payload [provider jwt]
  (let [jwt-header (buddy/decode-header jwt)
        pkeys-map  (pkeys provider)
        pkey       (jwk/jwk->public-key (pkeys-map (:kid jwt-header)))]
    (try (buddy/unsign jwt pkey {:alg :rs256})
         (catch Exception _ nil))))

(defn new-social-user! [{:keys [email name]}]
  (db/tx {:kind  :user
          :email email
          :name  name}))

(defn new-social-login! [provider {:keys [sub email] :as jwt}]
  (let [user (or (userc/existing-user email)
                 (new-social-user! jwt))]
    (db/tx {:kind      :social-login
            :provider  provider
            :user-id   (:id user)
            :social-id sub})))

;; endregion ^^^^^ social login ^^^^^
