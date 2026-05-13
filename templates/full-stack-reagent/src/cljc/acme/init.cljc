(ns acme.init
  (:require #?(:cljs [acme.core :as core])
            #?(:cljs [acme.page :as page])
            #?(:cljs [acme.rest :as rest])
            #?(:cljs [reagent.core :as reagent])
            #?(:cljs [c3kit.bucket.re-memory])
            [c3kit.apron.legend :as legend]
            [c3kit.bucket.api :as db]
            [c3kit.bucket.memory]
            [c3kit.wire.api :as api]
            [acme.config :as config]
            [acme.schema :as schema]))

(defn install-legend! []
  (let [schemas (reduce
                  (fn [m {:keys [kind] :as s}]
                    (assoc m (:value kind) s))
                  {} schema/full)]
    (legend/init! (merge schemas
                         {:db/retract legend/retract}))))

#?(:cljs (defn install-reagent-db-atom! []
           (db/set-impl! (db/create-db config/bucket schema/full))))

#?(:clj
   (defn- safe-version-from-js-file [path]
     ;; api/version-from-js-file throws when the JS bundle hasn't been built
     ;; (a common state in fresh scaffolds and CI). Fall back to "dev" so
     ;; spec runs don't require a prior `clojure -M:test:cljs once`.
     (try (api/version-from-js-file path)
          (catch Exception _ "dev"))))

(defn configure-api! []
  (api/configure! #?(:clj  {:ws-handlers 'acme.routes/ws-handlers
                            :version     (safe-version-from-js-file (if config/development? "public/cljs/acme_dev.js" "public/cljs/acme.js"))}
                     :cljs {:redirect-fn core/goto!}))
  #?(:cljs (rest/configure!)))
