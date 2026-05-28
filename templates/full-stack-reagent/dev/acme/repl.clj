(ns acme.repl
  (:require
   [acme.init :as init]
   [acme.main :as main]
   [c3kit.apron.app :as app]
   [c3kit.apron.log :as log]
   [c3kit.bucket.migrator :as m]))

(defn config-from-service []
  (let [config  (:bucket/config app/app)
        schemas (:bucket/schemas app/app)
        impl    (:bucket/impl app/app)]
    (when-not (some? config) (throw (ex-info "bucket service must be started in order to sync-schemas" {})))
    (assoc config :-db impl :-schemas schemas)))

(defn -ensure-migration-schema! [{:keys [-db] :as config}]
  (let [schema (m/migration-schema config)]
    (swap! (.-legend -db) assoc (-> schema :kind :value) schema)
    (when-not (m/-schema-exists? -db schema)
      (m/-install-schema! -db schema)
      (log/warn "Installed 'migration' schema because it was missing."))))

(println "Welcome to the Acme REPL!")
(println "Initializing")
(init/install-legend!)
(main/start-db)
(-ensure-migration-schema! (config-from-service))
(require '[c3kit.bucket.api :as db])
