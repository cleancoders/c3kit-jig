(ns acme.test-data
  (:require [acme.schema.full :as schema]
            #?(:clj [acme.spec-helper :as helper])
            ;; @c3kit/feature :auth {
            #?(:clj [acme.auth.user.core :as user])
            ;; @c3kit/feature :auth }
            [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helperc]
            [reagent.core :as reagent]
            [speclj.core :refer [before after]]))

(deftype Entity [atm kind]
  ;; MDM - An Entity is reloaded from the database each time is de-referenced (@).
  ;; It's super convenient for test code.
  #?(:clj clojure.lang.IDeref :cljs cljs.core/IDeref)
  (#?(:clj deref :cljs -deref) [_]
    (if @atm
      (db/reload @atm)
      (log/warn (str "Using nil entity.  Maybe add (with-kinds " (or kind "<kind") ")")))))

(defn e-atom [entity] #?(:clj (.atm entity) :cljs (.-atm entity)))
(defn entity [kind] (Entity. (atom nil) kind))

(def initialized-entities (atom []))

(defn clear-entities! []
  (doseq [entity @initialized-entities]
    (reset! (e-atom entity) nil))
  (reset! initialized-entities []))

(defn init-entity! [entity & opt-args]
  (let [values (ccc/->options opt-args)]
    (reset! (e-atom entity) (db/tx values))
    (swap! initialized-entities conj entity)))

;; @c3kit/feature :auth {
(def road-runner (entity :user))
(def coyote (entity :user))
;; @c3kit/feature :auth }

(defmulti -init-kind! identity)

;; @c3kit/feature :auth {
(defmethod -init-kind! :user [_]
  #?(:clj (when-not (= user/hash-password helper/speedy-hash)
            (log/report "SLOW PASSWORD HASH! Add (helper/with-fast-password-hash) to your spec")))
  (init-entity! road-runner (db/tx :kind :user
                                   :name "Road Runner"
                                   :email "road-runner@example.com"
                                   :password #?(:clj (user/hash-password "meep-meep") :cljs nil)))
  (init-entity! coyote (db/tx :kind :user
                              :name "Wiley Coyote"
                              :email "coyote@example.com"
                              :password #?(:clj (user/hash-password "light-bulb") :cljs nil))))
;; @c3kit/feature :auth }

;; @c3kit/feature !:auth {
(def deps
  ;; Add entities here with a list of entities they depend on (shallow).
  {:all []})
;; @c3kit/feature !:auth }

;; @c3kit/feature :auth {
(def deps
  ;; Add entities here with a list of entities they depend on (shallow).
  {:user []
   :all  [:user]})
;; @c3kit/feature :auth }

(defmethod -init-kind! :all [_])

(def initialized-kinds (atom #{}))

(defn- maybe-init-kind! [kind]
  (when-not (contains? @initialized-kinds kind)
    (-init-kind! kind)
    (swap! initialized-kinds conj kind)))

(defn init! [& kinds]
  (assert (seq kinds))
  (loop [kinds kinds]
    (if-let [kind (first kinds)]
      (if-let [reqs (seq (remove @initialized-kinds (get deps kind)))]
        (recur (concat reqs kinds))
        (do
          (maybe-init-kind! kind)
          (recur (rest kinds))))
      @initialized-kinds)))

(def datomic-config {:impl :datomic :uri "datomic:mem://test"})

;; @c3kit/feature !:auth {
(defn with-kinds [config & kinds]
  (list
   (helperc/with-schemas config (map vector schema/full))
   (before (reset! initialized-kinds #{})
           (apply init! kinds))
   (after (clear-entities!))))
;; @c3kit/feature !:auth }

;; @c3kit/feature :auth {
(defn with-kinds [config & kinds]
  (list
   (helperc/with-schemas config (map vector schema/full))
   #?(:clj (helper/with-fast-password-hash))
   (before (reset! initialized-kinds #{})
           (apply init! kinds))
   (after (clear-entities!))))
;; @c3kit/feature :auth }

(defn with-memory-kinds [& kinds]
  (apply with-kinds {:impl :memory :store #?(:clj (atom nil) :cljs (reagent/atom nil))} kinds))

(defn with-memory-schema []
  (helperc/with-schemas {:impl :memory :store #?(:clj (atom nil) :cljs (reagent/atom nil))}
    (map vector schema/full)))

#?(:clj (defn with-db-kinds [& kinds]
          (apply with-memory-kinds datomic-config kinds)))
