(ns acme.seed
  (:require [acme.config :as config]
            [acme.init :as init]
            [c3kit.apron.app :as app]
            [c3kit.bucket.api :as db])
  (:import (clojure.lang IDeref)
           ;; @c3kit/feature :auth = (org.mindrot.jbcrypt BCrypt)
           ))

(defn init! []
  (assert config/development? "Seeding the database is only allowed in development")
  (init/install-legend!)
  (app/start! [db/service]))

(defn attr-matches [fields entity attr]
  (let [expected (get fields attr)
        actual   (get entity attr)]
    (= expected actual)))

(defn attrs-match [fields entity]
  (every? (partial attr-matches fields entity) (keys fields)))

(deftype Entity [atm kind search-fields other-fields]
  IDeref
  (deref [this]
    (if-let [e @atm]
      e
      (if-let [e (apply db/ffind-by kind (flatten (seq search-fields)))]
        (if (attrs-match other-fields e)
          (do
            (println "EXISTS:   " (pr-str kind search-fields))
            (reset! atm e))
          (do
            (println "UPDATING: " (pr-str kind search-fields))
            ;(prn "other-fields: " other-fields)
            ;(prn "e: " e)
            (reset! atm (db/tx (merge e other-fields)))))
        (let [entity (merge {:kind kind} search-fields other-fields)]
          (println "CREATING: " (pr-str kind search-fields))
          (reset! atm (db/tx entity)))))))

(defn entity [kind search-fields other-fields] (Entity. (atom nil) kind search-fields other-fields))

;; @c3kit/feature :auth {
;; MDM - we add our own pw hash here because the normal generate different hashes causing confusing UPDATE messages.
(def pw-salt "$2a$11$3yH8I8pZi6xSPbK4QmcPYe")
(defn hashpw [pw] (BCrypt/hashpw pw pw-salt))

(def road-runner (entity :user {:email "road-runner@example.com"} {:name "Road Runner" :password (hashpw "meep-meep")}))
(def wiley-coyote (entity :user {:email "coyote@example.com"} {:name "Wiley Coyote" :password (hashpw "light-bulb")}))
;; @c3kit/feature :auth }

(defn -main []
  (init!)
  ;; @c3kit/feature :auth {
  (println "Seeding data...")
  @road-runner
  @wiley-coyote
  ;; @c3kit/feature :auth }
  ;; @c3kit/feature !:auth {
  (println "No seed data to load. Add entries to acme.seed/-main as needed.")
  ;; @c3kit/feature !:auth }
  (System/exit 0))
