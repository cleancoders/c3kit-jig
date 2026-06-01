(ns acme.main
  (:require [acme.config :as config]
            ;; @c3kit/feature :content = [acme.content.core]
            [acme.init :as init]
            ;; @c3kit/feature :ssr = [acme.ssr.prerender]
            [c3kit.apron.app :as app]
            [c3kit.apron.log :as log]
            [c3kit.apron.util :as util]
            [c3kit.bucket.bg :as bg]
            [c3kit.bucket.api :as db])
  (:import (java.lang Runtime Thread)))

(def scheduled-tasks [])
(def schedule-bg-tasks (partial bg/start-scheduled-tasks scheduled-tasks))
(def cancel-bg-tasks (partial bg/stop-scheduled-tasks scheduled-tasks))

(defn start-env [app] (app/start-env app "acme.env" "ACME_ENV"))

(def env (app/service 'acme.main/start-env 'c3kit.apron.app/stop-env))
(def http (app/service 'acme.http/start 'acme.http/stop))
(def bg-tasks (app/service 'acme.main/schedule-bg-tasks 'acme.main/cancel-bg-tasks))

;; @c3kit/feature :websocket {
(def all-services [env db/service http @(util/resolve-var 'c3kit.wire.websocket/service) bg/service bg-tasks])
;; @c3kit/feature :websocket }
;; @c3kit/feature !:websocket {
(def all-services [env db/service http bg/service bg-tasks])
;; @c3kit/feature !:websocket }
(def refresh-services [db/service bg/service bg-tasks])

(defn maybe-init-dev []
  (when config/development?
    (let [refresh-init (util/resolve-var 'c3kit.apron.refresh/init)]
      (refresh-init refresh-services "acme" ['acme.http 'acme.main]))))

(def start-db #(app/start! [db/service]))
(def start-all #(app/start! all-services))
(def stop-all #(app/stop! all-services))

(defn -main []
  (log/report "----- STARTING Acme SERVER -----")
  (log/report "acme environment: " config/environment)
  (log/set-level! (config/env :log-level :warn))
  (init/install-legend!)
  (init/configure-api!)
  ;; @c3kit/feature :content {
  (acme.content.core/load!)
  ;; @c3kit/feature :content }
  ;; @c3kit/feature :auth {
  (let [configure!              (util/resolve-var 'acme.auth.destination/configure!)
        ->AcmeDestinationAdapter (util/resolve-var 'acme.auth.user.web/->AcmeDestinationAdapter)]
    (configure! (->AcmeDestinationAdapter)))
  ;; @c3kit/feature :auth }
  (maybe-init-dev)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-all))
  (.addShutdownHook (Runtime/getRuntime) (Thread. shutdown-agents))
  (start-all)
  ;; @c3kit/feature :ssr {
  (acme.ssr.prerender/prerender!)
  ;; @c3kit/feature :ssr }
  )
