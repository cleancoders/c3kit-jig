(ns acme.schema
  (:require [acme.bg-task :as bg-task]
            [acme.user.schema :as user.schema]))

(def full
  (concat
    [bg-task/bg-task]
    user.schema/all))