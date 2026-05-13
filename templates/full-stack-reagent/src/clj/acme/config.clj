(ns acme.config
  (:require [c3kit.apron.app :as app]))

(def ^:private base
  {:analytics-code "console.log('google analytics would have loaded for this page');"
   :log-level      :info
   :csp            {:enabled?       false
                    :enforce?       false
                    :report-handler nil
                    :policy         nil}})

(def datomic-base
  {:impl                :datomic
   :migration-dir       "acme.migrations"
   :migration-ns-prefix "m"
   :migration-ns        'acme.migrations
   :full-schema         'acme.schema/full
   })

(def datomic-local (merge datomic-base {:uri "datomic:dev://localhost:4334/acme"}))
(def datomic-staging (merge datomic-base {:uri "datomic:ddb://us-west-2/cleancoders-acme-staging/acme"}))
(def datomic-production (merge datomic-base {:uri "datomic:ddb://us-west-2/cleancoders-acme-production/acme"}))


(def email-to-log {:client :to-log})
(def email-ses {:client :ses})
(def admin-email "Acme <admin@acme.com>")

(def development
  (assoc base
    :email email-to-log
    :bucket datomic-local
    :host "http://localhost:8123"
    :log-level :trace
    :jwt-secret "ACME_DEV_SECRET"))

(def staging
  (assoc base
    :email email-ses
    :bucket datomic-staging
    :host "https://acme-staging.cleancoders.com"
    :log-level :trace
    :jwt-secret "ACME_STAGING_SECRET"))

(def production
  (assoc base
    :email email-ses
    :bucket datomic-production
    :host "https://acme.cleancoders.com"
    :analytics-code "console.log('Replace me with Real Google Analytics Code.');"
    :jwt-secret "ACME_PRODUCTION_SECRET"))

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
