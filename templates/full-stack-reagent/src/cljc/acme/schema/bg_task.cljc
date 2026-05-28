(ns acme.schema.bg-task
  (:require [c3kit.apron.schema :as schema]))

(def bg-task
  {:kind        (schema/kind :bg-task)
   :id          schema/id
   :key         {:type :keyword}
   :last-ran-at {:type :instant :db [:no-history]}})
