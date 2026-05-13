(ns acme.config
  (:require [c3kit.apron.app :as app]))

(def ^:private base
  {:analytics-code "console.log('google analytics would have loaded for this page');"
   :log-level      :info
   ;; @c3kit/feature :csp {
   :csp            {:enabled?       false
                    :enforce?       false
                    :report-handler nil
                    :policy         nil}
   ;; @c3kit/feature :csp }
   })

;; @c3kit/db :datomic-pro {
(def datomic-base
  {:impl                :datomic
   :migration-dir       "acme.migrations"
   :migration-ns-prefix "m"
   :migration-ns        'acme.migrations
   :full-schema         'acme.schema/full})

(def datomic-local      (merge datomic-base {:uri "datomic:dev://localhost:4334/acme"}))
(def datomic-staging    (merge datomic-base {:uri "datomic:dev://localhost:4334/acme-staging"}))
(def datomic-production (merge datomic-base {:uri "datomic:dev://localhost:4334/acme-production"}))
;; @c3kit/db :datomic-pro }

;; @c3kit/db :sqlite {
(def sqlite-base
  {:impl                :jdbc
   :dialect             :sqlite
   :migration-dir       "acme.migrations"
   :migration-ns-prefix "m"
   :migration-ns        'acme.migrations
   :full-schema         'acme.schema/full})

(def sqlite-local      (merge sqlite-base {:connection-uri "jdbc:sqlite:db/dev.sqlite"}))
(def sqlite-staging    (merge sqlite-base {:connection-uri "jdbc:sqlite:db/staging.sqlite"}))
(def sqlite-production (merge sqlite-base {:connection-uri "jdbc:sqlite:db/production.sqlite"}))
;; @c3kit/db :sqlite }

;; @c3kit/db :postgres {
(def postgres-base
  {:impl                :jdbc
   :dialect             :postgres
   :migration-dir       "acme.migrations"
   :migration-ns-prefix "m"
   :migration-ns        'acme.migrations
   :full-schema         'acme.schema/full})

(def postgres-local      (merge postgres-base {:connection-uri "jdbc:postgresql://localhost/acme_dev"}))
(def postgres-staging    (merge postgres-base {:connection-uri "jdbc:postgresql://localhost/acme_staging"}))
(def postgres-production (merge postgres-base {:connection-uri "jdbc:postgresql://localhost/acme_production"}))
;; @c3kit/db :postgres }

;; memory backend defined unconditionally — HEAD default + valid wizard choice
(def memory-local      {:impl :memory :full-schema 'acme.schema/full})
(def memory-staging    {:impl :memory :full-schema 'acme.schema/full})
(def memory-production {:impl :memory :full-schema 'acme.schema/full})

(def email-to-log {:client :to-log})
(def admin-email "Acme <admin@example.com>")

(def development
  (assoc base
    :email email-to-log
    :bucket memory-local                           ;; HEAD default; replaced by line below at scaffold
    ;; @c3kit/db :datomic-pro = :bucket datomic-local
    ;; @c3kit/db :sqlite      = :bucket sqlite-local
    ;; @c3kit/db :postgres    = :bucket postgres-local
    ;; @c3kit/db :memory      = :bucket memory-local
    :host "http://localhost:8123"
    :log-level :trace
    ;; @c3kit/feature :auth = :jwt-secret "ACME_DEV_SECRET"
    ))

(def staging
  (assoc base
    :email email-to-log
    :bucket memory-staging                         ;; HEAD default; replaced by line below at scaffold
    ;; @c3kit/db :datomic-pro = :bucket datomic-staging
    ;; @c3kit/db :sqlite      = :bucket sqlite-staging
    ;; @c3kit/db :postgres    = :bucket postgres-staging
    ;; @c3kit/db :memory      = :bucket memory-staging
    :host "https://acme-staging.example.com"
    :log-level :trace
    ;; @c3kit/feature :auth = :jwt-secret "ACME_STAGING_SECRET"
    ))

(def production
  (assoc base
    :email email-to-log
    :bucket memory-production                      ;; HEAD default; replaced by line below at scaffold
    ;; @c3kit/db :datomic-pro = :bucket datomic-production
    ;; @c3kit/db :sqlite      = :bucket sqlite-production
    ;; @c3kit/db :postgres    = :bucket postgres-production
    ;; @c3kit/db :memory      = :bucket memory-production
    :host "https://acme.example.com"
    :analytics-code "console.log('Replace me with Real Google Analytics Code.');"
    ;; @c3kit/feature :auth = :jwt-secret "ACME_PRODUCTION_SECRET"
    ))

(def environment (app/find-env "cc.env" "CC_ENV"))
(def development? (= "development" environment))
(def production? (= "production" environment))

(def env
  (case environment
    "staging" staging
    "production" production
    development))

(def host (:host env))
(def bucket (:bucket env))

(defn link [& parts] (apply str host parts))
